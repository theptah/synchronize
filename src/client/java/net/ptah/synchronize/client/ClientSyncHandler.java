package net.ptah.synchronize.client;

import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.client.profile.ProfileManager;
import net.ptah.synchronize.client.profile.ProfileStorage;
import net.ptah.synchronize.client.screen.ProfileMessageScreen;
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
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/temp/transfer");
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
                ProfileStorage.allow(gameDir, destination);
                PathSecurity.resolveUnder(gameDir, destination);
                PathSecurity.resolveUnder(getTempDir().toPath(), source);
            } catch (Exception ex) {
                throw new SecurityException("Unsafe sync path", ex);
            }
        }
    }

    public static void startApplying(List<String> filesToMove, DownloadProgressScreen screen, Runnable onComplete) {
        final String ticket;
        try { ticket = ProfileManager.ticket(); }
        catch (Exception e) { screen.addError(String.valueOf(e.getMessage())); screen.setPreparationFailed(); return; }
        new Thread(() -> {
            try {
                String id = ProfileManager.prepare(filesToMove, ticket);
                MinecraftClient.getInstance().execute(() -> {
                    try { ProfileManager.requireTicket(ticket); } catch (Exception ignored) { return; }
                    endSyncSession();
                    screen.setProfileSwitch(() -> {
                        try { ProfileManager.queue(id, true); }
                        catch (Exception e) { screen.addError(String.valueOf(e.getMessage())); }
                    });
                    screen.setFullyDone(true);
                    if (versionHandler != null) showVerificationScreen(versionHandler, screen, Text.translatable("synchronize.profiles.restart"));
                });
            } catch (Exception e) {
                Synchronize.LOGGER.error("Failed to prepare server profile", e);
                MinecraftClient.getInstance().execute(() -> {
                    try { ProfileManager.requireTicket(ticket); } catch (Exception ignored) { return; }
                    screen.addError(String.valueOf(e.getMessage()));
                    screen.setPreparationFailed();
                    endSyncSession();
                    if (versionHandler != null) showVerificationScreen(versionHandler, screen, Text.translatable("synchronize.disconnect.download_failed", String.valueOf(e.getMessage())));
                });
            }
        }, "Synchronize-PrepareProfile").start();
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
            File localFile;
            try { localFile = ProfileManager.comparison(destination).toFile(); }
            catch (Exception ex) { throw new SecurityException("Cannot inspect profile file", ex); }
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
                ProfileStorage.allow(gameDir.toPath(), destination);
                if (!ProfileManager.owns(destination)) continue;
                Path target = ProfileManager.comparison(destination);
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

        ProfileManager.cancelDownload();
        clearTempFolder();
        showDisconnectedScreen(client);
    }

    private static volatile ClientLoginNetworkHandler profilePromptHandler;

    private static synchronized CompletableFuture<PacketByteBuf> profilePromptAsync(MinecraftClient client, ClientLoginNetworkHandler handler,
            Map<String, FileEntry> entries, Map<String, Boolean> garbage, SyncPackets.ExternalPrompt external) {
        if (profilePromptHandler == handler || versionHandler != handler) return CompletableFuture.completedFuture(responseBuf(false, List.of()));
        profilePromptHandler = handler;
        CompletableFuture<PacketByteBuf> result = new CompletableFuture<>();
        new Thread(() -> {
            try {
                if (!handler.connection.isOpen()) { result.complete(responseBuf(false, List.of())); return; }
                handleProfilePrompt(client, handler, entries, garbage, external).whenComplete((reply, error) -> {
                    if (error != null) result.completeExceptionally(error); else result.complete(reply);
                });
            } catch (Exception e) {
                Synchronize.LOGGER.error("Could not prepare profile session", e);
                client.execute(() -> {
                    if (versionHandler != handler) return;
                    Screen back = new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(new net.minecraft.client.gui.screen.TitleScreen());
                    showVerificationScreen(handler, new ProfileMessageScreen(back, Text.translatable("synchronize.profiles.error"), Text.literal(String.valueOf(e.getMessage())),
                        Text.translatable("synchronize.profiles.back"), () -> client.setScreen(back), null, null), Text.translatable("synchronize.disconnect.invalid_manifest"));
                });
                result.complete(responseBuf(false, List.of()));
            }
        }, "Synchronize-InspectProfile").start();
        return result;
    }

    private static CompletableFuture<PacketByteBuf> handleProfilePrompt(MinecraftClient client, ClientLoginNetworkHandler handler,
            Map<String, FileEntry> entries, Map<String, Boolean> garbage, SyncPackets.ExternalPrompt external) throws Exception {
        loginConnection = handler.connection;
        ClientLoginNetworkHandlerAccessor accessor = (ClientLoginNetworkHandlerAccessor) handler;
        //? if >=1.19.3 {
        ServerInfo info = accessor.synchronize$getServerInfo();
        //?} else
        /*ServerInfo info = client.getCurrentServerEntry();*/
        String fallback = handler.connection.getAddress().toString();
        ProfileManager.begin(info, fallback, entries);
        for (String path : garbage.keySet()) if (!PathSecurity.isSafeRelativePath(path)) throw new SecurityException("Invalid deletion path");
        MissingFiles missing = getMissingFiles(entries);
        Map<String, Boolean> existingGarbage = getExistingGarbage(garbage);
        activeSyncEntries = entries;
        activeRequestedFiles.clear(); downloadedFiles.clear();
        CompletableFuture<PacketByteBuf> future = new CompletableFuture<>();
        client.execute(() -> {
            if (versionHandler != handler || !handler.connection.isOpen()) { future.complete(responseBuf(false, List.of())); return; }
            Screen parent = accessor.synchronize$getParentScreen();
            beginSyncSession(parent);
            Runnable completedWithoutDownload = () -> {
                try {
                    ProfileManager.prepare(List.of(), ProfileManager.ticket());
                    if (ProfileManager.mustSwitch()) showProfileReady(handler, parent, future);
                    else {
                        endSyncSession(); client.setScreen(parent); future.complete(responseBuf(true, List.of()));
                    }
                } catch (Exception e) {
                    endSyncSession(); future.complete(responseBuf(false, List.of()));
                    abortLogin(Text.translatable("synchronize.disconnect.download_failed", String.valueOf(e.getMessage())));
                }
            };
            Runnable downloadPrompt = () -> {
                if (missing.mandatory.isEmpty() && missing.optionalDisplayNames.isEmpty()) { completedWithoutDownload.run(); return; }
                client.setScreen(new PromptScreen(parent, missing.mandatory, missing.optionalDisplayNames, requested -> {
                    if (requested.isEmpty()) { completedWithoutDownload.run(); return; }
                    activeRequestedFiles.clear(); activeRequestedFiles.addAll(requested);
                    vanillaTotalFiles = Math.max(1, requested.size()); vanillaCompletedFiles.set(0); downloadedFiles.clear();
                    activeProgressScreen = new DownloadProgressScreen(() -> {
                        ProfileManager.cancelDownload(); endSyncSession(); clearTempFolder(); future.complete(responseBuf(false, List.of()));
                        abortLogin(Text.translatable("synchronize.disconnect.aborted_by_user"));
                    }, null);
                    client.setScreen(activeProgressScreen);
                    if (external == null) future.complete(responseBuf(true, requested));
                    else ClientExternalDownloader.startDownload(resolveServerIp(handler), external.sessionId(), external.aesKey(), external.port(), requested, activeProgressScreen, () -> {});
                }, () -> {
                    endSyncSession(); future.complete(responseBuf(false, List.of()));
                    abortLogin(Text.translatable("synchronize.disconnect.aborted_by_user")); client.setScreen(parent);
                }));
            };
            if (existingGarbage.isEmpty()) downloadPrompt.run();
            else client.setScreen(new DeletedFilesPromptScreen(parent, new ArrayList<>(existingGarbage.keySet()), true, paths -> {
                try { ProfileManager.deleteOnSwitch(paths); downloadPrompt.run(); }
                catch (Exception e) { future.complete(responseBuf(false, List.of())); abortLogin(Text.translatable("synchronize.disconnect.deletion_prepare_failed")); }
            }, downloadPrompt, () -> {
                endSyncSession(); future.complete(responseBuf(false, List.of()));
                abortLogin(Text.translatable("synchronize.disconnect.disconnected_by_user")); client.setScreen(parent);
            }));
        });
        return future;
    }

    private static void showProfileReady(ClientLoginNetworkHandler handler, Screen parent, CompletableFuture<PacketByteBuf> future) throws Exception {
        String id = ProfileManager.session();
        MinecraftClient client = MinecraftClient.getInstance();
        Screen back = new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(new net.minecraft.client.gui.screen.TitleScreen());
        Screen screen = new ProfileMessageScreen(back, Text.translatable("synchronize.profiles.ready"), Text.translatable("synchronize.profiles.ready_body"),
                Text.translatable("synchronize.profiles.switch"), () -> {
                    try { ProfileManager.queue(id, true); }
                    catch (Exception e) {
                        Synchronize.LOGGER.error("Cannot queue profile switch", e);
                        client.setScreen(new ProfileMessageScreen(back, Text.translatable("synchronize.profiles.error"), Text.literal(String.valueOf(e.getMessage())), Text.translatable("synchronize.profiles.back"), () -> client.setScreen(back), null, null));
                    }
                }, Text.translatable("synchronize.profiles.back"), () -> client.setScreen(back));
        endSyncSession();
        showVerificationScreen(handler, screen, Text.translatable("synchronize.profiles.restart"));
        future.complete(responseBuf(false, List.of()));
    }

    public static void init() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ProfileManager.cancelDownload();
            endSyncSession();
            PendingUpdateManager.onClientStopping();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientSyncHandler::checkForUnexpectedDisconnect);

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.MOD_VERSION, (client, handler, buf, listenerAdder) -> {
            if (versionHandler != handler) ProfileManager.cancelDownload();
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
            try {
                Map<String, FileEntry> entries = SyncPackets.readFileEntryMap(buf);
                validateEntries(entries);
                SyncPackets.GarbageInfo garbage = SyncPackets.readGarbageInfo(buf);
                return profilePromptAsync(client, handler, entries, garbage.garbage(), null);
            } catch (Exception e) {
                Synchronize.LOGGER.error("Invalid profile manifest", e);
                abortLogin(Text.translatable("synchronize.disconnect.invalid_manifest"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.EXTERNAL_PROMPT, (client, handler, buf, listenerAdder) -> {
            try {
                SyncPackets.ExternalPrompt payload = SyncPackets.readExternalPrompt(buf);
                validateEntries(payload.fileHashes());
                return profilePromptAsync(client, handler, payload.fileHashes(), payload.garbage(), payload);
            } catch (Exception e) {
                Synchronize.LOGGER.error("Invalid external profile manifest", e);
                abortLogin(Text.translatable("synchronize.disconnect.invalid_external_manifest"));
                return CompletableFuture.completedFuture(PacketByteBufs.create());
            }
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.FILE_CHUNK, (client, handler, buf, listenerAdder) -> {
            if (versionHandler != handler) return CompletableFuture.completedFuture(PacketByteBufs.create());
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

                ProfileManager.receive(payload.data().length);
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
                abortLogin(Text.translatable("synchronize.disconnect.download_failed", String.valueOf(e.getMessage())));
            }

            return CompletableFuture.completedFuture(PacketByteBufs.create());
        });

        ClientLoginNetworking.registerGlobalReceiver(SyncPackets.FILE_ERROR, (client, handler, buf, listenerAdder) -> {
            if (versionHandler != handler) return CompletableFuture.completedFuture(PacketByteBufs.create());
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
            if (versionHandler != handler) return CompletableFuture.completedFuture(PacketByteBufs.create());
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