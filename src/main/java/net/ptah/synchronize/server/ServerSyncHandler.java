package net.ptah.synchronize.server;

import net.ptah.synchronize.util.Translations;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.compat.NetworkCompat;
import net.ptah.synchronize.compat.TextCompat;
import net.ptah.synchronize.config.ModConfig;
import net.ptah.synchronize.network.SyncPackets;
import net.ptah.synchronize.util.CryptoUtil;
import net.ptah.synchronize.util.HandshakeCrypto;
import net.ptah.synchronize.util.NetworkUtil;
import net.ptah.synchronize.util.RateLimiter;
import net.ptah.synchronize.util.PathSecurity;
import net.ptah.synchronize.util.ModJar;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ServerSyncHandler {
    private static volatile ExecutorService transferThreadPool;

    public static synchronized void start() {
        if (transferThreadPool == null || transferThreadPool.isShutdown()) {
            transferThreadPool = new ThreadPoolExecutor(
                    2, 4, 30L, java.util.concurrent.TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(32), r -> {
                        Thread thread = new Thread(r, "Synchronize-Transfer");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
            reloadRateLimiterFromConfig(Synchronize.getConfig());
        }
    }

    private static final int CHUNK_SIZE = 65536;
    private static final Map<ServerLoginNetworkHandler, CompletableFuture<Void>> PENDING_TRANSFERS = new ConcurrentHashMap<>();
    private static final Map<ServerLoginNetworkHandler, Set<String>> COMPLETED_TRANSFERS = new ConcurrentHashMap<>();
    private static final Map<ServerLoginNetworkHandler, String> EXTERNAL_SESSIONS = new ConcurrentHashMap<>();
    private static final Set<ServerLoginNetworkHandler> ACTIVE_LOGIN_HANDLERS = ConcurrentHashMap.newKeySet();
    private static final Set<ServerLoginNetworkHandler> TRANSFER_READY = ConcurrentHashMap.newKeySet();
    private static final Map<ServerLoginNetworkHandler, CompletableFuture<Void>> VERSION_GATES = new ConcurrentHashMap<>();
    private static final Map<ServerLoginNetworkHandler, byte[]> HANDSHAKE_NONCES = new ConcurrentHashMap<>();
    private static final Set<ServerLoginNetworkHandler> HANDSHAKE_ACCEPTED = ConcurrentHashMap.newKeySet();
    private static final Map<ServerLoginNetworkHandler, SyncPackets.ModVersion> REJECTED_CLIENTS = new ConcurrentHashMap<>();
    private static final RateLimiter HANDSHAKE_ATTEMPTS = new RateLimiter(5, 60_000L);

    private static volatile RateLimiter bulkRequestRateLimiter = new RateLimiter(5, 60_000L);

    public static boolean isSyncInProgress(ServerLoginNetworkHandler handler) {
        return ACTIVE_LOGIN_HANDLERS.contains(handler);
    }

    public static void init() {
        ModConfig startupConfig = Synchronize.getConfig();
        if (startupConfig != null) {
            bulkRequestRateLimiter = new RateLimiter(startupConfig.rateLimitMaxRequests, startupConfig.rateLimitWindowSeconds * 1000L);
        }

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            if (!SyncServerLifecycle.shouldSync(server, handler)) {
                return;
            }
            ModConfig config = Synchronize.getConfig();
            if (config == null) {
                return;
            }

            ACTIVE_LOGIN_HANDLERS.add(handler);
            Synchronize.LOGGER.info("[Synchronize] Starting main mod version check during login...");

            CompletableFuture<Void> gate = new CompletableFuture<>();
            VERSION_GATES.put(handler, gate);
            synchronizer.waitFor(gate);
            PacketByteBuf versionBuf = PacketByteBufs.create();
            SyncPackets.writeModVersion(versionBuf, ModJar.version(), ModJar.sha256());
            byte[] handshakeNonce = null;
            if (HandshakeCrypto.isUsableCode(config.handshake)) {
                handshakeNonce = HandshakeCrypto.newNonce();
                HANDSHAKE_NONCES.put(handler, handshakeNonce);
            }
            SyncPackets.writeHandshakeOffer(versionBuf, handshakeNonce);
            NetworkCompat.sendLogin(sender, SyncPackets.MOD_VERSION, versionBuf);
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.MOD_VERSION, (server, handler, understood, buf, synchronizer, responseSender) -> {
            CompletableFuture<Void> gate = VERSION_GATES.remove(handler);
            try {
                if (!SyncServerLifecycle.shouldSync(server, handler) || !ACTIVE_LOGIN_HANDLERS.contains(handler)) {
                    return;
                }
                if (!understood) {
                    ACTIVE_LOGIN_HANDLERS.remove(handler);
                    handleMissingMod(handler);
                    return;
                }
                byte[] handshakeNonce = HANDSHAKE_NONCES.remove(handler);
                SyncPackets.ModVersion reply;
                SyncPackets.HandshakeAttempt attempt;
                try {
                    reply = SyncPackets.readModVersion(buf);
                    attempt = SyncPackets.readHandshakeAttempt(buf);
                } catch (RuntimeException e) {
                    ACTIVE_LOGIN_HANDLERS.remove(handler);
                    handler.disconnect(Translations.player("synchronize.disconnect.invalid_request"));
                    return;
                }
                if (!isSameMainMod(reply) && attempt != null) {
                    handleHandshakeAttempt(handler, responseSender, reply, attempt, handshakeNonce);
                    return;
                }
                if (!isSameMainMod(reply)) {
                    ACTIVE_LOGIN_HANDLERS.remove(handler);
                    Synchronize.LOGGER.warn("[Synchronize] Refusing login: client Synchronize jar (v{}, {}) differs from the server's (v{}, {}).",
                            reply.version(), shortHash(reply.sha256()), ModJar.version(), shortHash(ModJar.sha256()));
                    REJECTED_CLIENTS.put(handler, reply);
                    PacketByteBuf rejected = PacketByteBufs.create();
                    SyncPackets.writeModVersion(rejected, ModJar.version(), ModJar.sha256());
                     
                     
                     
                    NetworkCompat.sendLogin(responseSender, SyncPackets.CLIENT_REJECTED, rejected);
                    return;
                }
                ModConfig config = Synchronize.getConfig();
                if (config == null) {
                    ACTIVE_LOGIN_HANDLERS.remove(handler);
                    return;
                }
                sendSyncPrompt(handler, responseSender, config);
            } finally {
                if (gate != null) {
                    gate.complete(null);
                }
            }
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.CLIENT_REJECTED,
                (server, handler, understood, buf, synchronizer, responseSender) -> {
            SyncPackets.ModVersion rejected = REJECTED_CLIENTS.remove(handler);
            if (rejected == null) return;
             
            handler.disconnect(Translations.player("synchronize.disconnect.version_mismatch", ModJar.version(), rejected.version(),
                    OfficialReleaseInfo.downloadPageUrl()));
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.HANDSHAKE, (server, handler, understood, buf, synchronizer, responseSender) -> {
            boolean accepted = HANDSHAKE_ACCEPTED.remove(handler);
            if (!SyncServerLifecycle.shouldSync(server, handler) || !ACTIVE_LOGIN_HANDLERS.contains(handler)) {
                return;
            }
            if (!accepted || !understood) {
                ACTIVE_LOGIN_HANDLERS.remove(handler);
                handler.disconnect(Translations.player("synchronize.disconnect.invalid_state"));
                return;
            }
            ModConfig config = Synchronize.getConfig();
            if (config == null) {
                ACTIVE_LOGIN_HANDLERS.remove(handler);
                return;
            }
            sendSyncPrompt(handler, responseSender, config);
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.PROMPT, (server, handler, understood, buf, synchronizer, responseSender) -> {
            handlePromptResponse(server, handler, understood, buf, synchronizer, responseSender, false);
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.EXTERNAL_PROMPT, (server, handler, understood, buf, synchronizer, responseSender) -> {
            handlePromptResponse(server, handler, understood, buf, synchronizer, responseSender, true);
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.FILE_CHUNK, (server, handler, understood, buf, synchronizer, responseSender) -> {
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.FILE_ERROR, (server, handler, understood, buf, synchronizer, responseSender) -> {
        });

        ServerLoginNetworking.registerGlobalReceiver(SyncPackets.TRANSFER_COMPLETE, (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (!SyncServerLifecycle.shouldSync(server, handler)) {
                return;
            }
            if (!understood || !ACTIVE_LOGIN_HANDLERS.contains(handler)) {
                handler.disconnect(Translations.player("synchronize.disconnect.invalid_state"));
                return;
            }
            if (!TRANSFER_READY.remove(handler)) {
                Synchronize.LOGGER.warn("[Synchronize] Ignoring premature TRANSFER_COMPLETE from {}", handler);
                handler.disconnect(Translations.player("synchronize.disconnect.not_completed"));
                return;
            }
            Synchronize.LOGGER.info("[Synchronize] Player completed server-side file sync. Resuming login.");
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            COMPLETED_TRANSFERS.remove(handler);
            CompletableFuture<Void> pending = PENDING_TRANSFERS.remove(handler);
            if (pending != null && !pending.isDone()) {
                pending.complete(null);
            }
        });

        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> {
            CompletableFuture<Void> gate = VERSION_GATES.remove(handler);
            if (gate != null && !gate.isDone()) {
                gate.complete(null);
            }
            HANDSHAKE_NONCES.remove(handler);
            HANDSHAKE_ACCEPTED.remove(handler);
            REJECTED_CLIENTS.remove(handler);
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            TRANSFER_READY.remove(handler);
            COMPLETED_TRANSFERS.remove(handler);
            String sessionId = EXTERNAL_SESSIONS.remove(handler);
            if (sessionId != null) {
                ExternalFileServer.removeSession(sessionId);
            }
            CompletableFuture<Void> pending = PENDING_TRANSFERS.remove(handler);
            if (pending != null && !pending.isDone()) {
                pending.complete(null);
            }
        });
    }

    private static void sendSyncPrompt(ServerLoginNetworkHandler handler, PacketSender sender, ModConfig config) {
        if (config.fileTransferMethod == ModConfig.TransferMethod.EXTERNAL) {
            try {
                String sessionId = UUID.randomUUID().toString();
                byte[] aesKey = CryptoUtil.generateAesKey();
                String clientIp = NetworkUtil.getIp(handler.connection.getAddress());
                ExternalFileServer.registerSession(sessionId, aesKey, clientIp);
                EXTERNAL_SESSIONS.put(handler, sessionId);

                PacketByteBuf buf = PacketByteBufs.create();
                SyncPackets.writeExternalPrompt(buf, sessionId, aesKey, config.externalPort, FileRegistry.FILE_ENTRIES,
                        FileRegistry.GARBAGE_DESTINATIONS);
                NetworkCompat.sendLogin(sender, SyncPackets.EXTERNAL_PROMPT, buf);
            } catch (Exception e) {
                Synchronize.LOGGER.error("Error starting external sync session", e);
                ACTIVE_LOGIN_HANDLERS.remove(handler);
            }
        } else {
            PacketByteBuf buf = PacketByteBufs.create();
            SyncPackets.writeFileEntryMap(buf, FileRegistry.FILE_ENTRIES);
            SyncPackets.writeGarbageInfo(buf, FileRegistry.GARBAGE_DESTINATIONS);
            NetworkCompat.sendLogin(sender, SyncPackets.PROMPT, buf);
        }
    }

    private static boolean isSameMainMod(SyncPackets.ModVersion reply) {
        String serverHash = ModJar.sha256();
        return !serverHash.isEmpty() && !reply.sha256().isEmpty()
                && serverHash.equalsIgnoreCase(reply.sha256());
    }

    private static void handleHandshakeAttempt(ServerLoginNetworkHandler handler, PacketSender responseSender,
            SyncPackets.ModVersion reply, SyncPackets.HandshakeAttempt attempt, byte[] serverNonce) {
        ModConfig config = Synchronize.getConfig();
        String code = config == null ? null : config.handshake;
        String player = handler.getConnectionInfo();
        if (serverNonce == null || !HandshakeCrypto.isUsableCode(code)) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            Synchronize.LOGGER.warn("[Synchronize] Refusing handshake from {}: the handshake bypass is disabled on this server.", player);
            handler.disconnect(Translations.player("synchronize.disconnect.handshake_disabled"));
            return;
        }
        String ip = NetworkUtil.getIp(handler.connection.getAddress());
        if (!HANDSHAKE_ATTEMPTS.tryAcquire(ip)) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            Synchronize.LOGGER.warn("[Synchronize] Refusing handshake from {}: too many attempts.", player);
            handler.disconnect(Translations.player("synchronize.disconnect.handshake_rate_limited"));
            return;
        }
        String serverVersion = SyncPackets.wireVersion(ModJar.version());
        String serverHash = ModJar.sha256();
        byte[] expected = HandshakeCrypto.clientProof(code, serverNonce, attempt.clientNonce(), serverVersion, serverHash,
                reply.version(), reply.sha256());
        if (!HandshakeCrypto.matches(expected, attempt.clientProof())) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            Synchronize.LOGGER.warn("[Synchronize] Refusing handshake from {}: wrong handshake code.", player);
            handler.disconnect(Translations.player("synchronize.disconnect.handshake_rejected"));
            return;
        }
        Synchronize.LOGGER.warn("[Synchronize] {} joined via handshake with a different Synchronize jar (client v{}, {} / server v{}, {}).",
                player, reply.version(), shortHash(reply.sha256()), ModJar.version(), shortHash(serverHash));
        HANDSHAKE_ACCEPTED.add(handler);
        PacketByteBuf proofBuf = PacketByteBufs.create();
        SyncPackets.writeHandshakeProof(proofBuf, HandshakeCrypto.serverProof(code, serverNonce, attempt.clientNonce(),
                serverVersion, serverHash, reply.version(), reply.sha256()));
        NetworkCompat.sendLogin(responseSender, SyncPackets.HANDSHAKE, proofBuf);
    }

    private static String shortHash(String hash) {
        return hash == null || hash.length() < 12 ? String.valueOf(hash) : hash.substring(0, 12);
    }

    private static void handleMissingMod(ServerLoginNetworkHandler handler) {
        ModConfig config = Synchronize.getConfig();
        if (config != null && config.limboEnabled) {
            String ip = NetworkUtil.getIp(handler.connection.getAddress());
            LimboManager.markPendingLimbo(ip);
            Synchronize.LOGGER.info("[Synchronize] Client without the mod is allowed through login for limbo handling.");
        } else {
            handler.disconnect(buildModRequiredMessage(config));
        }
    }

    private static void handlePromptResponse(MinecraftServer server, ServerLoginNetworkHandler handler, boolean understood, PacketByteBuf buf, ServerLoginNetworking.LoginSynchronizer synchronizer, PacketSender responseSender, boolean external) {
        if (!SyncServerLifecycle.shouldSync(server, handler) || !ACTIVE_LOGIN_HANDLERS.contains(handler)) {
            return;
        }
        if (!understood) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            handleMissingMod(handler);
            return;
        }

        SyncPackets.Response response;
        try {
            response = SyncPackets.readResponse(buf);
        } catch (RuntimeException e) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            handler.disconnect(Translations.player("synchronize.disconnect.invalid_request"));
            return;
        }
        if (!response.accepted()) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            handler.disconnect(Translations.player("synchronize.disconnect.aborted_by_user"));
            return;
        }

        for (String requestedFile : response.requestedFiles()) {
            if (!PathSecurity.isSafeRelativePath(requestedFile) || !FileRegistry.FILE_ENTRIES.containsKey(requestedFile)) {
                ACTIVE_LOGIN_HANDLERS.remove(handler);
                handler.disconnect(Translations.player("synchronize.disconnect.invalid_file_requested"));
                return;
            }
        }

        if (external) {
            String sessionId = EXTERNAL_SESSIONS.get(handler);
            if (sessionId == null || !ExternalFileServer.hasServedFiles(sessionId, Set.copyOf(response.requestedFiles()))) {
                ACTIVE_LOGIN_HANDLERS.remove(handler);
                if (sessionId != null) ExternalFileServer.removeSession(sessionId);
                EXTERNAL_SESSIONS.remove(handler);
                handler.disconnect(Translations.player("synchronize.disconnect.not_fully_downloaded"));
                return;
            }
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            EXTERNAL_SESSIONS.remove(handler);
            ExternalFileServer.removeSession(sessionId);
            return;
        }

        String requesterIp = NetworkUtil.getIp(handler.connection.getAddress());
        if (!bulkRequestRateLimiter.tryAcquire(requesterIp)) {
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            handler.disconnect(Translations.player("synchronize.disconnect.rate_limited"));
            return;
        }

        CompletableFuture<Void> transferDone = new CompletableFuture<>();
        PENDING_TRANSFERS.put(handler, transferDone);
        COMPLETED_TRANSFERS.put(handler, ConcurrentHashMap.newKeySet());
        synchronizer.waitFor(transferDone);
        try {
            startTransferSequence(handler, responseSender, response.requestedFiles(), transferDone);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            transferDone.complete(null);
            PENDING_TRANSFERS.remove(handler);
            COMPLETED_TRANSFERS.remove(handler);
            ACTIVE_LOGIN_HANDLERS.remove(handler);
            handler.disconnect(Translations.player("synchronize.disconnect.server_busy"));
        }
    }

    public static Text buildModRequiredMessage(ModConfig config) {
         
         
         
         
        String version = OfficialReleaseInfo.serverVersion();
        String url = OfficialReleaseInfo.downloadPageUrl();
        boolean clickable = config != null && config.limboEnabled;

        MutableText message = Text.literal("\n")
            .append(Translations.player("synchronize.required.title").styled(style -> style.withColor(Formatting.GOLD).withBold(true)))
            .append(Text.literal("\n"))
            .append(Translations.player("synchronize.required.description", version).styled(style -> style.withColor(Formatting.GRAY)))
            .append(Text.literal("\n\n"));

        if (clickable) {
            message.append(Translations.player("synchronize.required.click_to_download").styled(style -> style
                .withColor(Formatting.GREEN).withBold(true)
                .withClickEvent(TextCompat.openUrl(url))
                .withHoverEvent(TextCompat.showText(Translations.player("synchronize.required.click_hover", version)))));
        } else {
            message.append(Translations.player("synchronize.required.download_files", version).styled(style -> style.withColor(Formatting.GRAY)))
                   .append(Text.literal("\n"))
                   .append(Text.literal(url).styled(style -> style.withColor(Formatting.AQUA)));
        }

        message.append(Text.literal("\n\n"))
            .append(Translations.player("synchronize.required.install_hint",
                    Text.literal(".minecraft/mods").styled(style -> style.withColor(Formatting.YELLOW)))
                .styled(style -> style.withColor(Formatting.GRAY)))
            .append(Text.literal("\n"));

        return message;
    }

     
     
     
     
     
     
     
     
     
     
     

    private static void startTransferSequence(ServerLoginNetworkHandler handler, PacketSender sender, List<String> requestedFiles, CompletableFuture<Void> transferDone) {
        ExecutorService pool = transferThreadPool;
        if (pool == null || pool.isShutdown()) {
            throw new java.util.concurrent.RejectedExecutionException("Synchronization services are stopped");
        }
        pool.submit(() -> {
            try {
                for (String relativePath : requestedFiles) {
                    if (transferDone.isDone() || Thread.currentThread().isInterrupted() || !handler.connection.isOpen()) {
                        break;
                    }

                    String safePath = relativePath.replace('\\', '/');
                    if (safePath.contains("..")) {
                        continue;
                    }
                    if (!FileRegistry.FILE_ENTRIES.containsKey(safePath)) {
                        continue;
                    }

                    File targetFile = new File(FabricLoader.getInstance().getGameDir().toFile(), safePath);
                    if (targetFile.exists() && !targetFile.isDirectory()) {
                        try (RandomAccessFile raf = new RandomAccessFile(targetFile, "r")) {
                            long fileLength = targetFile.length();
                            int totalChunks = (int) Math.ceil(fileLength / (double) CHUNK_SIZE);
                            byte[] buffer = new byte[CHUNK_SIZE];

                            boolean complete = true;
                            for (int i = 0; i < totalChunks; i++) {
                                if (transferDone.isDone() || Thread.currentThread().isInterrupted() || !handler.connection.isOpen()) {
                                    complete = false;
                                    break;
                                }
                                int bytesRead = raf.read(buffer);
                                if (bytesRead == -1) {
                                    complete = false;
                                    break;
                                }

                                byte[] chunk = bytesRead == CHUNK_SIZE ? buffer : Arrays.copyOf(buffer, bytesRead);
                                PacketByteBuf chunkBuf = PacketByteBufs.create();
                                SyncPackets.writeFileChunk(chunkBuf, safePath, chunk, i + 1, totalChunks);
                                NetworkCompat.sendLogin(sender, SyncPackets.FILE_CHUNK, chunkBuf);

                                if ((i + 1) % 64 == 0) {
                                    Thread.sleep(1L);
                                }
                            }
                            synchronized (ServerSyncHandler.class) {
                                Set<String> completed = COMPLETED_TRANSFERS.get(handler);
                                if (complete && !transferDone.isDone() && completed != null) {
                                    completed.add(safePath);
                                }
                            }
                        }
                    } else {
                        PacketByteBuf errorBuf = PacketByteBufs.create();
                        errorBuf.writeString(safePath);
                        NetworkCompat.sendLogin(sender, SyncPackets.FILE_ERROR, errorBuf);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Synchronize.LOGGER.error("Error transferring file sequence", e);
            } finally {
                synchronized (ServerSyncHandler.class) {
                    if (!transferDone.isDone()) {
                        if (handler.connection.isOpen()) {
                            Set<String> completed = COMPLETED_TRANSFERS.get(handler);
                            boolean allRequestedFilesSent = completed != null && completed.containsAll(requestedFiles)
                                    && completed.size() == requestedFiles.size();
                            if (allRequestedFilesSent) {
                                TRANSFER_READY.add(handler);
                                NetworkCompat.sendLogin(sender, SyncPackets.TRANSFER_COMPLETE, PacketByteBufs.create());
                            } else {
                                TRANSFER_READY.remove(handler);
                                ACTIVE_LOGIN_HANDLERS.remove(handler);
                                Synchronize.LOGGER.error("[Synchronize] Transfer incomplete; refusing TRANSFER_COMPLETE for {}. Requested={}, completed={}",
                                        handler, requestedFiles.size(), completed == null ? 0 : completed.size());
                                handler.disconnect(Translations.player("synchronize.disconnect.transfer_failed"));
                                if (!transferDone.isDone()) {
                                    transferDone.complete(null);
                                }
                            }
                        } else {
                            ACTIVE_LOGIN_HANDLERS.remove(handler);
                            TRANSFER_READY.remove(handler);
                            if (!transferDone.isDone()) {
                                transferDone.complete(null);
                            }
                        }
                    }
                }
            }
        });
    }

    public static synchronized void shutdown() {
        REJECTED_CLIENTS.clear();
        VERSION_GATES.values().forEach(future -> future.complete(null));
        VERSION_GATES.clear();
        PENDING_TRANSFERS.values().forEach(future -> future.complete(null));
        PENDING_TRANSFERS.clear();
        COMPLETED_TRANSFERS.clear();
        TRANSFER_READY.clear();
        EXTERNAL_SESSIONS.values().forEach(ExternalFileServer::removeSession);
        EXTERNAL_SESSIONS.clear();
        ACTIVE_LOGIN_HANDLERS.clear();
        ExecutorService pool = transferThreadPool;
        transferThreadPool = null;
        if (pool != null) {
            pool.shutdownNow();
        }
        reloadRateLimiterFromConfig(Synchronize.getConfig());
    }

    public static void reloadRateLimiterFromConfig(ModConfig config) {
        if (config != null) {
            bulkRequestRateLimiter = new RateLimiter(config.rateLimitMaxRequests, config.rateLimitWindowSeconds * 1000L);
        }
    }
}