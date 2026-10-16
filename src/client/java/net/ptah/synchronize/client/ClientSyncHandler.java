package net.ptah.synchronize.client;

import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.client.screen.DownloadProgressScreen;
import net.ptah.synchronize.client.screen.ModUpdateScreen;
import net.ptah.synchronize.client.screen.DeletedFilesPromptScreen;
import net.ptah.synchronize.client.screen.PromptScreen;
import net.ptah.synchronize.client.screen.SyncDisconnectedScreen;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.config.FileRegistry.FileEntry;
import net.ptah.synchronize.network.SyncPackets;
import net.ptah.synchronize.util.PathSecurity;
import net.ptah.synchronize.util.ModJar;
import net.ptah.synchronize.util.HandshakeCrypto;
import net.ptah.synchronize.client.handshake.HandshakeClient;
import net.ptah.synchronize.mixin.client.ClientLoginNetworkHandlerAccessor;
import net.minecraft.client.network.ServerInfo;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

public class ClientSyncHandler {
    private static volatile ClientLoginNetworkHandler verificationHandler;
    private static volatile Screen verificationScreen;
    private static volatile ClientLoginNetworkHandler versionHandler;
    private static ClientLoginNetworkHandler verificationStartedHandler;

    private static synchronized void verifyMismatch(ClientLoginNetworkHandler handler, SyncPackets.ModVersion server) {
         
         
        if (versionHandler != handler || verificationStartedHandler == handler) return;
        verificationStartedHandler = handler;
        Screen parent = ((ClientLoginNetworkHandlerAccessor) handler).synchronize$getParentScreen();
        showVerificationScreen(handler, new ModUpdateScreen(parent, server.version(), server.sha256()),
                Text.translatable("synchronize.disconnect.version_check", server.version()));
    }

    private static void showVerificationScreen(ClientLoginNetworkHandler handler, Screen screen, Text reason) {
        verificationHandler = handler;
        verificationScreen = screen;
        MinecraftClient.getInstance().execute(() -> {
            if (versionHandler != handler) return;
            if (MinecraftClient.getInstance().currentScreen != screen) MinecraftClient.getInstance().setScreen(screen);
            if (handler.connection.isOpen()) handler.connection.disconnect(reason);
        });
    }

    public static void updateVerificationScreen(Screen previous, Screen next) {
        if (verificationScreen == previous) verificationScreen = next;
    }

    public static void afterLoginDisconnected(ClientLoginNetworkHandler handler) {
        if (verificationHandler != handler) return;
        Screen desired = verificationScreen;
        verificationHandler = null;
        verificationScreen = null;
        if (desired == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (versionHandler != handler) return;
            if (!(client.currentScreen instanceof ModUpdateScreen)
                    && !(client.currentScreen instanceof net.ptah.synchronize.client.screen.UntrustedModScreen)) {
                client.setScreen(desired);
            }
        });
    }

    private static DownloadProgressScreen activeProgressScreen;
    private static int vanillaTotalFiles = 1;
    private static final AtomicInteger vanillaCompletedFiles = new AtomicInteger(0);
    private static final List<String> downloadedFiles = new CopyOnWriteArrayList<>();
    private static ClientConnection loginConnection;
    private static volatile byte[] pendingServerProof;
    private static volatile boolean syncSessionActive;
    private static volatile boolean disconnectScreenShown;
    private static Screen syncReturnScreen;
    private static volatile Map<String, FileEntry> activeSyncEntries = Map.of();
    private static OutputStream activeChunkStream;
    private static String activeChunkPath;
    private static int activeChunkNumber;
    private static int activeTotalChunks;
    private static long activeChunkBytes;
    private static final Set<String> activeRequestedFiles = new HashSet<>();
    private static final long MAX_SYNC_FILE_BYTES = 512L * 1024L * 1024L;
    public static Map<String, FileEntry> getActiveSyncEntries() {
        return activeSyncEntries;
    }

    private static void beginSyncSession(Screen returnScreen) {
        syncSessionActive = true;
        disconnectScreenShown = false;
        syncReturnScreen = returnScreen;
    }

    private static void endSyncSession() {
        syncSessionActive = false;
        disconnectScreenShown = false;
        syncReturnScreen = null;
    }

    public static void dismissDisconnectScreen() {
        endSyncSession();
    }

    private static void showDisconnectedScreen(MinecraftClient client) {
        if (!syncSessionActive || disconnectScreenShown || loginConnection == null || loginConnection.isOpen()) {
            return;
        }

        disconnectScreenShown = true;
        Screen returnScreen = syncReturnScreen;
        client.setScreen(new SyncDisconnectedScreen(returnScreen));
    }

    private static boolean isModsDestination(String destination) {
        String normalized = destination == null ? "" : destination.replace('\\', '/');
        return normalized.equalsIgnoreCase("mods")
                || normalized.toLowerCase(Locale.ROOT).startsWith("mods/");
    }

    private static boolean requiresRestartForEntry(String sourceKey) {
        FileEntry entry = activeSyncEntries.get(sourceKey);
        String destination = entry == null || entry.destination == null || entry.destination.isBlank()
                ? sourceKey
                : entry.destination;
        return isModsDestination(destination) || (entry != null && entry.restart && !isModsDestination(destination));
    }

    private static boolean targetsMainModJar(String destination, String hash) {
        if (hash != null && ModJar.isSelfHash(hash)) {
            return true;
        }
        String normalized = destination == null ? "" : destination.replace('\\', '/');
        if (ModJar.relativeToGameDir().map(rel -> rel.equalsIgnoreCase(normalized)).orElse(false)) {
            return true;
        }
        try {
            Path target = PathSecurity.resolveUnder(FabricLoader.getInstance().getGameDir(), normalized);
            return Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS) && ModJar.isSynchronizeJar(target);
        } catch (Exception e) {
            return false;
        }
    }

    public static File getTempDir() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/temp");
    }

    private static void closeActiveChunkStreamQuietly() {
        if (activeChunkStream != null) {
            try {
                activeChunkStream.close();
            } catch (Exception e) {
                Synchronize.LOGGER.warn("Error closing in-progress chunk stream for {}", activeChunkPath, e);
            }
            activeChunkStream = null;
            activeChunkPath = null;
            activeChunkNumber = 0;
            activeTotalChunks = 0;
            activeChunkBytes = 0L;
        }
    }

    public static void clearTempFolder() {
        closeActiveChunkStreamQuietly();
        File tempDir = getTempDir();
        if (tempDir.exists()) {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (Files.isSymbolicLink(file.toPath())) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        } catch (Exception e) {
            Synchronize.LOGGER.warn("Could not delete temp file: {}", file.getName());
        }
    }

    private static String destinationFor(String sourceKey) {
        FileEntry entry = activeSyncEntries.get(sourceKey);
        return (entry != null && entry.destination != null && !entry.destination.isBlank()) ? entry.destination : sourceKey;
    }

    public static void markDownloaded(String path) {
        downloadedFiles.add(path);
    }

    private static void validateEntries(Map<String, FileEntry> entries) {
        if (entries == null || entries.size() > SyncPackets.MAX_SYNC_ENTRIES) {
            throw new SecurityException("Invalid sync manifest");
        }
        Path gameDir = FabricLoader.getInstance().getGameDir();
        for (Map.Entry<String, FileEntry> e : entries.entrySet()) {
            String source = e.getKey();
            FileEntry meta = e.getValue();
            String destination = meta == null || meta.destination == null || meta.destination.isBlank() ? source : meta.destination;
            if (!PathSecurity.isSafeRelativePath(source) || !PathSecurity.isSafeRelativePath(destination)) {
                throw new SecurityException("Invalid sync path");
            }
            if (meta != null && meta.restart && isModsDestination(destination)) {
                throw new SecurityException("Invalid restart flag for mods destination");
            }
            if (meta == null || meta.hash == null || !meta.hash.matches("[0-9a-fA-F]{64}")) {
                throw new SecurityException("Invalid sync hash");
            }
            try {
                PathSecurity.resolveUnder(gameDir, destination);
                PathSecurity.resolveUnder(getTempDir().toPath(), source);
            } catch (Exception ex) {
                throw new SecurityException("Unsafe sync path", ex);
            }
        }
    }

    public static void startApplying(List<String> filesToMove, DownloadProgressScreen screen, Runnable onComplete) {
        boolean needsRestart = false;
        for (String key : filesToMove) {
            if (requiresRestartForEntry(key)) {
                needsRestart = true;
                break;
            }
        }

        final boolean restartRequired = needsRestart;

        new Thread(() -> {
            File tempDir = getTempDir();
            File gameDir = MinecraftClient.getInstance().runDirectory;
            int total = Math.max(1, filesToMove.size());
            int current = 0;

            for (String key : filesToMove) {
                String destination = destinationFor(key);
                try {
                    Path sourcePath = PathSecurity.resolveUnder(tempDir.toPath(), key);
                    if (Files.exists(sourcePath) && Files.isRegularFile(sourcePath) && !Files.isSymbolicLink(sourcePath)
                            && ModJar.isSynchronizeJar(sourcePath)) {
                        Files.deleteIfExists(sourcePath);
                        Synchronize.LOGGER.warn("[Synchronize] Refusing to install a Synchronize jar through the sync list: {}", destination);
                        final String finalDestination = destination;
                        MinecraftClient.getInstance().execute(() -> screen.addError(I18n.translate("synchronize.log.main_mod_skipped", finalDestination)));
                    } else if (Files.exists(sourcePath) && Files.isRegularFile(sourcePath) && !Files.isSymbolicLink(sourcePath)) {
                        PathSecurity.resolveUnder(gameDir.toPath(), destination);
                        if (requiresRestartForEntry(key)) {
                            PendingUpdateManager.stage(sourcePath.toFile(), destination);
                            final String finalDestination = destination;
                            MinecraftClient.getInstance().execute(() -> screen.addLog(I18n.translate("synchronize.log.staged", finalDestination)));
                        } else {
                            Path dest = PathSecurity.resolveUnder(gameDir.toPath(), destination);
                            Files.createDirectories(dest.getParent());
                            Files.move(sourcePath, dest, StandardCopyOption.REPLACE_EXISTING);
                            final String finalDestination = destination;
                            MinecraftClient.getInstance().execute(() -> screen.addLog(I18n.translate("synchronize.log.applied", finalDestination)));
                        }
                    }
                } catch (Exception e) {
                    Synchronize.LOGGER.error("Error applying file {}", destination, e);
                    final String finalDestination = destination;
                    MinecraftClient.getInstance().execute(() -> screen.addError(I18n.translate("synchronize.log.apply_error", finalDestination)));
                }
                ++current;
                final int currentCopy = current;
                final String movingName = new File(destination).getName();
                MinecraftClient.getInstance().execute(() -> {
                    float prog = (float) currentCopy / (float) total;
                    screen.updateProgress(prog, Text.translatable("synchronize.progress.moving", currentCopy, total, movingName));
                });
            }

            clearTempFolder();
            MinecraftClient.getInstance().execute(() -> {
                screen.setFullyDone(restartRequired);
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }, "Synchronize-Apply").start();
    }

    private static final class MissingFiles {
        final List<String> mandatory = new ArrayList<>();
        final Map<String, String> optionalDisplayNames = new LinkedHashMap<>();
    }

    private static MissingFiles getMissingFiles(Map<String, FileEntry> entries) {
        MissingFiles result = new MissingFiles();
        File gameDir = MinecraftClient.getInstance().runDirectory;

        for (Map.Entry<String, FileEntry> e : entries.entrySet()) {
            String key = e.getKey();
            FileEntry meta = e.getValue();
            String destination = (meta.destination != null && !meta.destination.isBlank()) ? meta.destination : key;
            try {
                PathSecurity.resolveUnder(gameDir.toPath(), destination);
                PathSecurity.resolveUnder(getTempDir().toPath(), key);
            } catch (Exception ex) {
                throw new SecurityException("Unsafe sync manifest path", ex);
            }
            if (targetsMainModJar(destination, meta.hash)) {
                Synchronize.LOGGER.warn("[Synchronize] Ignoring sync entry '{}' targeting the Synchronize main mod jar.", key);
                continue;
            }
            File localFile = new File(gameDir, destination);
            boolean missing;

            if (!localFile.exists()) {
                missing = true;
            } else {
                try {
                    String localHash = FileRegistry.calculateHash(localFile.toPath());
                    missing = !localHash.equals(meta.hash);
                } catch (Exception ex) {
                    missing = true;
                }
            }

            if (!missing) {
                continue;
            }

            if (meta.optional) {
                int lastSlash = destination.lastIndexOf('/');
                String displayName = lastSlash >= 0 ? destination.substring(lastSlash + 1) : destination;
                result.optionalDisplayNames.put(key, displayName);
            } else {
                result.mandatory.add(key);
            }
        }
        return result;
    }

    private static Map<String, Boolean> getExistingGarbage(Map<String, Boolean> garbage) {
        Map<String, Boolean> existing = new LinkedHashMap<>();
        File gameDir = MinecraftClient.getInstance().runDirectory;
        for (Map.Entry<String, Boolean> entry : garbage.entrySet()) {
            String destination = entry.getKey().replace('\\', '/');
            if (!PathSecurity.isSafeRelativePath(destination)) throw new SecurityException("Invalid garbage path");
            try {
                Path target = PathSecurity.resolveUnder(gameDir.toPath(), destination);
                if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    if (targetsMainModJar(destination, null)) {
                        Synchronize.LOGGER.warn("[Synchronize] Ignoring removal request for the Synchronize main mod jar: {}", destination);
                        continue;
                    }
                    existing.put(destination, Boolean.TRUE.equals(entry.getValue()));
                }
            } catch (Exception ex) {
                throw new SecurityException("Unsafe garbage path", ex);
            }
        }
        return existing;
    }

    private static void deleteFilesNow(List<String> destinations) {
        File gameDir = MinecraftClient.getInstance().runDirectory;
        for (String destination : destinations) {
            try {
                Path target = PathSecurity.resolveUnder(gameDir.toPath(), destination);
                if (Files.isSymbolicLink(target)) {
                    Synchronize.LOGGER.warn("Skipping symbolic link deletion: {}", destination);
                    continue;
                }
                Files.deleteIfExists(target);
                Synchronize.LOGGER.info("[Synchronize] Deleted server-removed file: {}", destination);
            } catch (Exception e) {
                Synchronize.LOGGER.error("Failed to delete server-removed file {}", destination, e);
            }
        }
    }

    private static void abortLogin(Text reason) {
        if (loginConnection != null && loginConnection.isOpen()) {
            loginConnection.disconnect(reason);
        }
    }

    private static String resolveServerIp(ClientLoginNetworkHandler handler) {
        SocketAddress remoteAddress = handler.connection.getAddress();
        if (remoteAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getHostString();
        }
        return "127.0.0.1";
    }

    private static PacketByteBuf responseBuf(boolean accepted, List<String> files) {
        PacketByteBuf buf = PacketByteBufs.create();
        SyncPackets.writeResponse(buf, accepted, files);
        return buf;
    }

    private static void checkForUnexpectedDisconnect(MinecraftClient client) {
        if (!syncSessionActive || disconnectScreenShown || loginConnection == null || loginConnection.isOpen()) {
            return;
        }

        if (client.currentScreen instanceof SyncDisconnectedScreen) {
            return;
        }

        clearTempFolder();
        showDisconnectedScreen(client);
    }

    public static void init() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            endSyncSession();
            PendingUpdateManager.onClientStopping();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientSyncHandler::checkForUnexpectedDisconnect);

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.MOD_VERSION, (client, handler, buf, listenerAdder) -> {
            versionHandler = handler;
            loginConnection = handler.connection;
            verificationHandler = null;
            verificationScreen = null;
            SyncPackets.ModVersion server;
            byte[] serverNonce;
            try {
                server = SyncPackets.readModVersion(buf);
                serverNonce = SyncPackets.readHandshakeOffer(buf);
            } catch (RuntimeException e) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_request"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }

            pendingServerProof = null;
            PacketByteBuf reply = PacketByteBufs.create();
            SyncPackets.writeModVersion(reply, ModJar.version(), ModJar.sha256());
            if (ModUpdater.matchesServer(server.version(), server.sha256())) {
                return CompletableFuture.completedFuture(reply);
            }

            ClientLoginNetworkHandlerAccessor accessor = (ClientLoginNetworkHandlerAccessor) handler;
            //? if >=1.19.3 {
            ServerInfo serverInfo = accessor.synchronize$getServerInfo();
            //?} else
            /*ServerInfo serverInfo = MinecraftClient.getInstance().getCurrentServerEntry();*/
            if (HandshakeClient.consumeArmed(serverInfo)) {
                String code = HandshakeClient.codeOf(serverInfo);
                if (serverNonce == null || !HandshakeCrypto.isUsableCode(code)) {
                    abortLogin(Text.translatable("synchronize.disconnect.handshake_unavailable"));
                    return CompletableFuture.completedFuture(reply);
                }
                byte[] clientNonce = HandshakeCrypto.newNonce();
                String clientVersion = SyncPackets.wireVersion(ModJar.version());
                String clientHash = ModJar.sha256();
                byte[] proof = HandshakeCrypto.clientProof(code, serverNonce, clientNonce, server.version(), server.sha256(),
                        clientVersion, clientHash);
                pendingServerProof = HandshakeCrypto.serverProof(code, serverNonce, clientNonce, server.version(), server.sha256(),
                        clientVersion, clientHash);
                SyncPackets.writeHandshakeAttempt(reply, clientNonce, proof);
                Synchronize.LOGGER.warn("[Synchronize] Attempting handshake with the server (server v{}, client v{}).",
                        server.version(), ModJar.version());
                return CompletableFuture.completedFuture(reply);
            }
            HandshakeClient.remember(serverInfo, accessor.synchronize$getParentScreen(), serverNonce != null);

            Synchronize.LOGGER.warn("[Synchronize] Server runs Synchronize {} ({}), client runs {} ({}). Verifying with GitHub.",
                    server.version(), server.sha256(), ModJar.version(), ModJar.sha256());
            verifyMismatch(handler, server);
            return CompletableFuture.completedFuture(reply);
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.CLIENT_REJECTED, (client, handler, buf, listenerAdder) -> {
            if (versionHandler != handler) {
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            SyncPackets.ModVersion server;
            try {
                server = SyncPackets.readModVersion(buf);
            } catch (RuntimeException e) {
                if (handler.connection.isOpen()) handler.connection.disconnect(Text.translatable("synchronize.disconnect.invalid_request"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            loginConnection = handler.connection;
            endSyncSession();
            verifyMismatch(handler, server);
            return CompletableFuture.completedFuture(PacketByteBufs.create());
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.HANDSHAKE, (client, handler, buf, listenerAdder) -> {
            loginConnection = handler.connection;
            byte[] expected = pendingServerProof;
            pendingServerProof = null;
            byte[] proof;
            try {
                proof = SyncPackets.readHandshakeProof(buf);
            } catch (RuntimeException e) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_request"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            if (!HandshakeCrypto.matches(expected, proof)) {
                Synchronize.LOGGER.warn("[Synchronize] The server could not prove the handshake code. Disconnecting.");
                abortLogin(Text.translatable("synchronize.disconnect.handshake_server_unverified"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            Synchronize.LOGGER.warn("[Synchronize] Handshake accepted: joining with an unverified Synchronize version ({}).", ModJar.version());
            return CompletableFuture.completedFuture(PacketByteBufs.create());
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.PROMPT, (client, handler, buf, listenerAdder) -> {
            loginConnection = handler.connection;
            Map<String, FileEntry> entries;
            SyncPackets.GarbageInfo garbageInfo;
            try {
                entries = SyncPackets.readFileEntryMap(buf);
                validateEntries(entries);
                garbageInfo = SyncPackets.readGarbageInfo(buf);
                for (String path : garbageInfo.garbage().keySet()) {
                    if (!PathSecurity.isSafeRelativePath(path)) throw new SecurityException("Invalid garbage path");
                }
            } catch (RuntimeException e) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_manifest"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            MissingFiles missing = getMissingFiles(entries);
            Map<String, Boolean> existingGarbage = getExistingGarbage(garbageInfo.garbage());

            if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty() && existingGarbage.isEmpty()) {
                return CompletableFuture.completedFuture(responseBuf(true, List.of()));
            }

            activeSyncEntries = entries;
            CompletableFuture<PacketByteBuf> future = new CompletableFuture<>();

            client.execute(() -> {
                Screen currentScreen = client.currentScreen;
                beginSyncSession(currentScreen);
                Runnable showDownloadPrompt = () -> client.setScreen(new PromptScreen(currentScreen, missing.mandatory, missing.optionalDisplayNames, requestedFiles -> {
                    if (requestedFiles.isEmpty()) {
                        endSyncSession();
                        client.setScreen(currentScreen);
                        future.complete(responseBuf(true, requestedFiles));
                        return;
                    }
                    activeRequestedFiles.clear();
                    activeRequestedFiles.addAll(requestedFiles);
                    vanillaTotalFiles = Math.max(1, requestedFiles.size());
                    vanillaCompletedFiles.set(0);
                    downloadedFiles.clear();

                    activeProgressScreen = new DownloadProgressScreen(() -> {
                        endSyncSession();
                        clearTempFolder();
                        abortLogin(Text.translatable("synchronize.disconnect.aborted_by_user"));
                    }, null);
                    client.setScreen(activeProgressScreen);
                    future.complete(responseBuf(true, requestedFiles));
                }, () -> {
                    endSyncSession();
                    future.complete(responseBuf(false, List.of()));
                    client.setScreen(currentScreen);
                }));

                if (existingGarbage.isEmpty()) {
                    showDownloadPrompt.run();
                } else {
                    boolean garbageNeedsRestart = existingGarbage.values().stream().anyMatch(Boolean.TRUE::equals);
                    client.setScreen(new DeletedFilesPromptScreen(currentScreen, new ArrayList<>(existingGarbage.keySet()), garbageNeedsRestart, destinations -> {
                        if (garbageNeedsRestart) {
                            try {
                                PendingUpdateManager.appendDeletions(destinations);
                                client.scheduleStop();
                            } catch (Exception e) {
                                Synchronize.LOGGER.error("Failed to stage garbage files", e);
                                endSyncSession();
                                if (!future.isDone()) future.complete(responseBuf(false, List.of()));
                                abortLogin(Text.translatable("synchronize.disconnect.deletion_prepare_failed"));
                            }
                        } else {
                            deleteFilesNow(destinations);
                            if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty()) {
                                endSyncSession();
                                client.setScreen(currentScreen);
                                if (!future.isDone()) future.complete(responseBuf(true, List.of()));
                            } else {
                                showDownloadPrompt.run();
                            }
                        }
                    }, () -> {
                        if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty()) {
                            endSyncSession();
                            client.setScreen(currentScreen);
                            if (!future.isDone()) future.complete(responseBuf(true, List.of()));
                        } else {
                            showDownloadPrompt.run();
                        }
                    }, () -> {
                        endSyncSession();
                        if (!future.isDone()) future.complete(responseBuf(false, List.of()));
                        abortLogin(Text.translatable("synchronize.disconnect.disconnected_by_user"));
                    }));
                }

            });
            return future;
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.EXTERNAL_PROMPT, (client, handler, buf, listenerAdder) -> {
            loginConnection = handler.connection;
            SyncPackets.ExternalPrompt payload;
            try {
                payload = SyncPackets.readExternalPrompt(buf);
                validateEntries(payload.fileHashes());
                for (String path : payload.garbage().keySet()) {
                    if (!PathSecurity.isSafeRelativePath(path)) throw new SecurityException("Invalid garbage path");
                }
            } catch (RuntimeException e) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_external_manifest"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            MissingFiles missing = getMissingFiles(payload.fileHashes());
            Map<String, Boolean> existingGarbage = getExistingGarbage(payload.garbage());

            if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty() && existingGarbage.isEmpty()) {
                return CompletableFuture.completedFuture(responseBuf(true, List.of()));
            }

            activeSyncEntries = payload.fileHashes();
            String serverIp = resolveServerIp(handler);
            CompletableFuture<PacketByteBuf> future = new CompletableFuture<>();

            client.execute(() -> {
                Screen currentScreen = client.currentScreen;
                beginSyncSession(currentScreen);
                Runnable showDownloadPrompt = () -> client.setScreen(new PromptScreen(currentScreen, missing.mandatory, missing.optionalDisplayNames, requestedFiles -> {
                    if (requestedFiles.isEmpty()) {
                        endSyncSession();
                        client.setScreen(currentScreen);
                        if (!future.isDone()) {
                            future.complete(responseBuf(true, requestedFiles));
                        }
                        return;
                    }

                    activeRequestedFiles.clear();
                    activeRequestedFiles.addAll(requestedFiles);
                    downloadedFiles.clear();

                    activeProgressScreen = new DownloadProgressScreen(() -> {
                        endSyncSession();
                        if (!future.isDone()) {
                            future.complete(responseBuf(false, requestedFiles));
                        }
                        clearTempFolder();
                        abortLogin(Text.translatable("synchronize.disconnect.aborted_by_user"));
                    }, null);
                    client.setScreen(activeProgressScreen);

                    ClientExternalDownloader.startDownload(serverIp, payload.sessionId(), payload.aesKey(), payload.port(), requestedFiles, activeProgressScreen, () -> {
                        if (!future.isDone()) {
                            future.complete(responseBuf(true, requestedFiles));
                        }
                    });
                }, () -> {
                    endSyncSession();
                    future.complete(responseBuf(false, List.of()));
                    client.setScreen(currentScreen);
                }));

                if (existingGarbage.isEmpty()) {
                    showDownloadPrompt.run();
                } else {
                    boolean garbageNeedsRestart = existingGarbage.values().stream().anyMatch(Boolean.TRUE::equals);
                    client.setScreen(new DeletedFilesPromptScreen(currentScreen, new ArrayList<>(existingGarbage.keySet()), garbageNeedsRestart, destinations -> {
                        if (garbageNeedsRestart) {
                            try {
                                PendingUpdateManager.appendDeletions(destinations);
                                client.scheduleStop();
                            } catch (Exception e) {
                                Synchronize.LOGGER.error("Failed to stage garbage files", e);
                                endSyncSession();
                                if (!future.isDone()) future.complete(responseBuf(false, List.of()));
                                abortLogin(Text.translatable("synchronize.disconnect.deletion_prepare_failed"));
                            }
                        } else {
                            deleteFilesNow(destinations);
                            if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty()) {
                                endSyncSession();
                                client.setScreen(currentScreen);
                                if (!future.isDone()) future.complete(responseBuf(true, List.of()));
                            } else {
                                showDownloadPrompt.run();
                            }
                        }
                    }, () -> {
                        if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty()) {
                            endSyncSession();
                            client.setScreen(currentScreen);
                            if (!future.isDone()) future.complete(responseBuf(true, List.of()));
                        } else {
                            showDownloadPrompt.run();
                        }
                    }, () -> {
                        endSyncSession();
                        if (!future.isDone()) future.complete(responseBuf(false, List.of()));
                        abortLogin(Text.translatable("synchronize.disconnect.disconnected_by_user"));
                    }));
                }

            });
            return future;
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.FILE_CHUNK, (client, handler, buf, listenerAdder) -> {
            loginConnection = handler.connection;
            SyncPackets.FileChunk payload;
            try {
                payload = SyncPackets.readFileChunk(buf);
            } catch (RuntimeException e) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_chunk"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }

            String safePath = payload.filePath().replace('\\', '/');
            if (!PathSecurity.isSafeRelativePath(safePath) || !activeSyncEntries.containsKey(safePath) || !activeRequestedFiles.contains(safePath)) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_file_path"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
            if (payload.data().length > SyncPackets.MAX_CHUNK_SIZE || payload.totalChunks() > SyncPackets.MAX_TOTAL_CHUNKS) {
                abortLogin(Text.translatable("synchronize.disconnect.invalid_file_size"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }

            if (activeProgressScreen != null && payload.currentChunk() < payload.totalChunks()) {
                final String receivingName = new File(safePath).getName();
                client.execute(() -> {
                    if (activeProgressScreen == null) return;
                    float chunkProgress = (float) payload.currentChunk() / (float) payload.totalChunks();
                    float overallProgress = ((float) vanillaCompletedFiles.get() + chunkProgress) / (float) Math.max(1, vanillaTotalFiles);
                    activeProgressScreen.updateProgress(overallProgress, Text.translatable("synchronize.progress.receiving", vanillaCompletedFiles.get() + 1, vanillaTotalFiles, receivingName));
                });
            }

            try {
                Path targetPath = PathSecurity.resolveUnder(getTempDir().toPath(), safePath);
                File targetFile = targetPath.toFile();

                if (payload.currentChunk() == 1) {
                    closeActiveChunkStreamQuietly();
                    Files.createDirectories(targetPath.getParent());
                    activeChunkStream = new BufferedOutputStream(
                        Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), 65536);
                    activeChunkPath = safePath;
                    activeChunkNumber = 0;
                    activeTotalChunks = payload.totalChunks();
                    activeChunkBytes = 0L;
                }

                if (activeChunkStream == null || !safePath.equals(activeChunkPath)
                        || payload.totalChunks() != activeTotalChunks
                        || payload.currentChunk() != activeChunkNumber + 1) {
                    abortLogin(Text.translatable("synchronize.disconnect.invalid_chunk_order"));
                    closeActiveChunkStreamQuietly();
                    return CompletableFuture.completedFuture(PacketByteBufs.create());
                }

                activeChunkStream.write(payload.data());
                activeChunkBytes += payload.data().length;
                activeChunkNumber = payload.currentChunk();
                if (activeChunkBytes > MAX_SYNC_FILE_BYTES) {
                    abortLogin(Text.translatable("synchronize.disconnect.file_too_large"));
                    closeActiveChunkStreamQuietly();
                    Files.deleteIfExists(targetPath);
                    return CompletableFuture.completedFuture(PacketByteBufs.create());
                }

                if (payload.currentChunk() == payload.totalChunks()) {
                    closeActiveChunkStreamQuietly();
                    try {
                        String expectedHash = activeSyncEntries.get(safePath).hash;
                        String actualHash = FileRegistry.calculateHash(targetFile.toPath());
                        if (!actualHash.equalsIgnoreCase(expectedHash)) {
                            Files.deleteIfExists(targetFile.toPath());
                            abortLogin(Text.translatable("synchronize.disconnect.hash_mismatch", safePath));
                            return CompletableFuture.completedFuture(PacketByteBufs.create());
                        }
                    } catch (Exception e) {
                        Synchronize.LOGGER.error("Hash validation failed for {}", safePath, e);
                        abortLogin(Text.translatable("synchronize.disconnect.hash_validation_failed", safePath));
                        return CompletableFuture.completedFuture(PacketByteBufs.create());
                    }

                    if (activeProgressScreen != null) {
                        client.execute(() -> {
                            float chunkProgress = (float) payload.currentChunk() / (float) payload.totalChunks();
                            float overallProgress = ((float) vanillaCompletedFiles.get() + chunkProgress) / (float) vanillaTotalFiles;
                            activeProgressScreen.updateProgress(overallProgress, Text.translatable("synchronize.progress.receiving", vanillaCompletedFiles.get() + 1, vanillaTotalFiles, new File(safePath).getName()));

                            vanillaCompletedFiles.incrementAndGet();
                            downloadedFiles.add(safePath);
                            activeProgressScreen.addLog(I18n.translate("synchronize.log.downloaded", safePath));
                        });
                    }
                }
            } catch (Exception e) {
                Synchronize.LOGGER.error("Failed to write chunk to disk for {}", safePath, e);
                closeActiveChunkStreamQuietly();
            }

            return CompletableFuture.completedFuture(PacketByteBufs.create());
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.FILE_ERROR, (client, handler, buf, listenerAdder) -> {
            String filePath = buf.readString();
            if (activeProgressScreen != null) {
                client.execute(() -> {
                    activeProgressScreen.addError(I18n.translate("synchronize.log.server_missing_file", new File(filePath).getName()));
                    activeProgressScreen.setHasMissingFiles();
                    vanillaCompletedFiles.incrementAndGet();
                });
            }
            return CompletableFuture.completedFuture(PacketByteBufs.create());
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.TRANSFER_COMPLETE, (client, handler, buf, listenerAdder) -> {
            CompletableFuture<PacketByteBuf> future = new CompletableFuture<>();
            client.execute(() -> {
                if (activeProgressScreen != null) {
                    if (!activeRequestedFiles.isEmpty() && !downloadedFiles.containsAll(activeRequestedFiles)) {
                        abortLogin(Text.translatable("synchronize.disconnect.missing_files"));
                        return;
                    }
                    activeProgressScreen.setNetworkFinished(() -> startApplying(new ArrayList<>(downloadedFiles), activeProgressScreen, () -> {
                        activeSyncEntries = Map.of();
                        activeRequestedFiles.clear();
                        activeProgressScreen.setOnContinueAction(() -> {
                            endSyncSession();
                            future.complete(PacketByteBufs.create());
                        });
                    }));
                } else {
                    future.complete(PacketByteBufs.create());
                }
            });
            return future;
        });
    }
}