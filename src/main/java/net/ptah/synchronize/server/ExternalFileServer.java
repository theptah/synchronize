package net.ptah.synchronize.server;

import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.config.ModConfig;
import net.ptah.synchronize.util.CryptoUtil;
import net.ptah.synchronize.util.NetworkUtil;
import net.ptah.synchronize.util.PathSecurity;
import net.ptah.synchronize.util.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import net.fabricmc.loader.api.FabricLoader;

public final class ExternalFileServer {
    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static ScheduledExecutorService cleanupExecutor;
    public static final Map<String, SessionEntry> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static volatile RateLimiter downloadRateLimiter = new RateLimiter(50, 60_000L);

    private ExternalFileServer() {}

    public static void start() {
        ModConfig config = Synchronize.getConfig();
        if (server != null || config == null || config.fileTransferMethod != ModConfig.TransferMethod.EXTERNAL) {
            return;
        }

        int port = config.externalPort;
        downloadRateLimiter = new RateLimiter(
                Math.max(1, config.rateLimitMaxRequests) * 10,
                Math.max(1, config.rateLimitWindowSeconds) * 1000L);
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Synchronize-ExternalSessionCleanup");
            t.setDaemon(true);
            return t;
        });
        serverExecutor = new ThreadPoolExecutor(2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/download", exchange -> {
                try {
                    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1L);
                        return;
                    }

                    String ip = NetworkUtil.getIp(exchange.getRemoteAddress());
                    if (!downloadRateLimiter.tryAcquire(ip)) {
                        exchange.sendResponseHeaders(429, -1L);
                        return;
                    }

                    String query = exchange.getRequestURI().getRawQuery();
                    Map<String, String> params = parseQuery(query);
                    String sessionId = params.get("session");
                    String rawFilePath = params.get("file");
                    if (sessionId == null || rawFilePath == null) {
                        exchange.sendResponseHeaders(400, -1L);
                        return;
                    }

                    SessionEntry session = ACTIVE_SESSIONS.get(sessionId);
                    if (session == null || session.expiresAtMillis < System.currentTimeMillis()
                            || !session.clientIp.equals(ip)) {
                        exchange.sendResponseHeaders(403, -1L);
                        return;
                    }

                    String safePath = rawFilePath.replace('\\', '/');
                    if (!PathSecurity.isSafeRelativePath(safePath)
                            || !FileRegistry.FILE_ENTRIES.containsKey(safePath)) {
                        exchange.sendResponseHeaders(403, -1L);
                        return;
                    }

                    Path targetFile = PathSecurity.resolveUnder(FabricLoader.getInstance().getGameDir(), safePath);
                    if (!Files.isRegularFile(targetFile) || Files.isSymbolicLink(targetFile)) {
                        Synchronize.LOGGER.warn("Refusing non-regular/symlink sync file: {}", safePath);
                        exchange.sendResponseHeaders(404, -1L);
                        return;
                    }

                    byte[] iv = CryptoUtil.generateIv();
                    Cipher cipher = CryptoUtil.getEncryptionCipher(session.aesKey, iv);
                    long payloadLength = (long) iv.length + Files.size(targetFile) + 16L;

                    exchange.getResponseHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate");
                    exchange.getResponseHeaders().add("Pragma", "no-cache");
                    exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
                    exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, payloadLength);

                    try (OutputStream os = exchange.getResponseBody();
                         InputStream is = Files.newInputStream(targetFile)) {
                        os.write(iv);
                        try (CipherOutputStream cos = new CipherOutputStream(os, cipher)) {
                            byte[] buffer = new byte[65_536];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                cos.write(buffer, 0, read);
                            }
                        }
                    }
                    markFileServed(sessionId, safePath);
                } catch (Exception e) {
                    Synchronize.LOGGER.error("HTTP transfer error", e);
                    try {
                        exchange.sendResponseHeaders(500, -1L);
                    } catch (Exception ignored) {
                    }
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(serverExecutor);
            server.start();
            Synchronize.LOGGER.info("External File Server started securely on port {}", port);
        } catch (BindException e) {
            Synchronize.LOGGER.error("CRITICAL ERROR: Port {} is already in use! Change 'externalPort' in config.", port);
        } catch (Exception e) {
            Synchronize.LOGGER.error("Failed to start External File Server.", e);
        }
    }

    public static void markFileServed(String sessionId, String filePath) {
        SessionEntry session = ACTIVE_SESSIONS.get(sessionId);
        if (session != null) session.servedFiles.add(filePath);
    }

    public static boolean hasServedFiles(String sessionId, java.util.Set<String> requiredFiles) {
        SessionEntry session = ACTIVE_SESSIONS.get(sessionId);
        return session != null && session.servedFiles.containsAll(requiredFiles);
    }

    public static void removeSession(String sessionId) {
        if (sessionId != null) ACTIVE_SESSIONS.remove(sessionId);
    }

    public static void scheduleSessionCleanup(String sessionId) {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.schedule(() -> ACTIVE_SESSIONS.remove(sessionId), 5L, TimeUnit.MINUTES);
        }
    }

    public static void registerSession(String sessionId, byte[] aesKey, String clientIp) {
        if (sessionId == null || sessionId.isBlank() || aesKey == null || aesKey.length != 32
                || clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("Invalid external sync session");
        }
        ACTIVE_SESSIONS.put(sessionId,
                new SessionEntry(aesKey.clone(), clientIp, System.currentTimeMillis() + 5 * 60_000L));
        scheduleSessionCleanup(sessionId);
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
        ACTIVE_SESSIONS.clear();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) return result;
        try {
            for (String param : query.split("&", -1)) {
                int eq = param.indexOf('=');
                if (eq <= 0) continue;
                String key = URLDecoder.decode(param.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);
                if (result.putIfAbsent(key, value) != null) {
                    result.clear();
                    return result;
                }
            }
        } catch (IllegalArgumentException e) {
            result.clear();
        }
        return result;
    }

    public static final class SessionEntry {
        final byte[] aesKey;
        final String clientIp;
        final long expiresAtMillis;
        final java.util.Set<String> servedFiles = ConcurrentHashMap.newKeySet();

        SessionEntry(byte[] aesKey, String clientIp, long expiresAtMillis) {
            this.aesKey = aesKey;
            this.clientIp = clientIp;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
