package net.ptah.synchronize.server;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.util.OfficialReleases;
import net.ptah.synchronize.util.ModJar;

public final class OfficialReleaseInfo {
    private static volatile String releasePageUrl;
    private static volatile boolean started;

    private OfficialReleaseInfo() {
    }

    public static String serverVersion() {
        return ModJar.version();
    }

    public static String downloadPageUrl() {
        String resolved = releasePageUrl;
        return resolved != null ? resolved : OfficialReleases.fallbackReleasePage(serverVersion());
    }

    public static synchronized void refreshAsync() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(OfficialReleaseInfo::resolve, "Synchronize-ReleaseLookup");
        thread.setDaemon(true);
        thread.start();
    }

    private static void resolve() {
        String version = serverVersion();
        String hash = ModJar.sha256();
        Path scratch = FabricLoader.getInstance().getConfigDir().resolve("synchronize/temp_verify");
        try {
            OfficialReleases.Lookup lookup = OfficialReleases.lookup(version, hash, scratch);
            releasePageUrl = lookup.bestPageUrl(version);
            if (lookup.verified()) {
                Synchronize.LOGGER.info("[Synchronize] Server jar verified against the official GitHub release ({}).", releasePageUrl);
            } else if (hash.isEmpty()) {
                Synchronize.LOGGER.warn("[Synchronize] Could not hash the server jar (dev environment?); clients cannot auto-update to it.");
            } else {
                Synchronize.LOGGER.warn("[Synchronize] The server's Synchronize jar (v{}) does NOT match any official GitHub release of {}. "
                        + "Clients will refuse to connect. Install the official jar from {}", version, OfficialReleases.REPOSITORY_URL, OfficialReleases.RELEASES_URL);
            }
        } catch (Exception e) {
            Synchronize.LOGGER.warn("[Synchronize] Could not query GitHub for the official release page: {}", e.toString());
        } finally {
            try {
                if (Files.isDirectory(scratch)) {
                    try (var stream = Files.list(scratch)) {
                        stream.forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
                    }
                    Files.deleteIfExists(scratch);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
