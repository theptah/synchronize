package net.ptah.synchronize.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.util.OfficialReleases;
import net.ptah.synchronize.util.ModJar;

public final class ModUpdater {
    private ModUpdater() {
    }

    public enum Outcome {
        UPDATE_AVAILABLE,
        CLIENT_MODIFIED,
        SERVER_NOT_OFFICIAL,
        CHECK_FAILED
    }

    public record CheckResult(Outcome outcome, OfficialReleases.Lookup lookup, String pageUrl, String error) {}

    public static boolean matchesServer(String serverVersion, String serverHash) {
        String localHash = ModJar.sha256();
        return serverHash != null && !serverHash.isEmpty() && !localHash.isEmpty()
                && localHash.equalsIgnoreCase(serverHash);
    }

    public static Path scratchDir() {
        return ClientSyncHandler.getTempDir().toPath().resolve("self_update");
    }

    public static CheckResult check(String serverVersion, String serverHash) {
        try {
            if (serverHash == null || serverHash.isBlank()) {
                throw new IOException("The server's Synchronize jar hash is unavailable");
            }
             
             
             
            List<OfficialReleases.Release> releases = OfficialReleases.fetchReleases();
            OfficialReleases.Lookup lookup = OfficialReleases.lookup(releases, serverVersion, serverHash,
                    scratchDir().resolve("server"));
            String page = lookup.bestPageUrl(serverVersion);
            if (!lookup.verified()) {
                return new CheckResult(Outcome.SERVER_NOT_OFFICIAL, lookup, page, null);
            }
            String clientHash = ModJar.sha256();
            if (clientHash.isBlank()) {
                throw new IOException("The client's Synchronize jar hash is unavailable");
            }
            OfficialReleases.Lookup clientLookup = OfficialReleases.lookup(releases, ModJar.version(), clientHash,
                    scratchDir().resolve("client"));
            if (!clientLookup.verified()) {
                return new CheckResult(Outcome.CLIENT_MODIFIED, lookup, page, null);
            }
             
             
            return new CheckResult(Outcome.UPDATE_AVAILABLE, lookup, page, null);
        } catch (Exception e) {
            Synchronize.LOGGER.error("[Synchronize] Could not verify the Synchronize version with GitHub", e);
            return new CheckResult(Outcome.CHECK_FAILED, null, OfficialReleases.fallbackReleasePage(serverVersion), e.getMessage());
        }
    }

    public static String downloadAndStage(CheckResult result, String serverHash, LongConsumer progress) throws Exception {
        OfficialReleases.Asset asset = result.lookup().matchedAsset();
        if (asset == null || !OfficialReleases.isSafeJarName(asset.name())) {
            throw new IOException("No official asset available");
        }

        Optional<String> currentRel = ModJar.relativeToGameDir();
        if (currentRel.isEmpty()) {
            throw new IOException("The current Synchronize jar is not inside the game directory; install the update manually.");
        }
        String oldRel = currentRel.get();
        int slash = oldRel.lastIndexOf('/');
        String folder = slash >= 0 ? oldRel.substring(0, slash) : "mods";
        String newRel = folder + "/" + asset.name();

        Path file = result.lookup().downloadedFile();
        if (file == null || !Files.isRegularFile(file)) {
            file = scratchDir().resolve(asset.name());
            String hash = OfficialReleases.download(asset, file, progress);
            if (!hash.equalsIgnoreCase(serverHash)) {
                Files.deleteIfExists(file);
                throw new SecurityException("Downloaded file does not match the server's Synchronize jar");
            }
        } else {
            String hash = FileRegistry.calculateHash(file);
            if (!hash.equalsIgnoreCase(serverHash)) {
                Files.deleteIfExists(file);
                throw new SecurityException("Downloaded file does not match the server's Synchronize jar");
            }
            if (progress != null) progress.accept(Files.size(file));
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Cancelled");
        }
        PendingUpdateManager.stage(file.toFile(), newRel);
        if (!newRel.equalsIgnoreCase(oldRel)) {
            PendingUpdateManager.appendDeletions(List.of(oldRel));
        }
        Synchronize.LOGGER.info("[Synchronize] Staged official Synchronize update: {} -> {} (applies when the game closes)", oldRel, newRel);
        return newRel;
    }

    public static void cleanupScratch() {
        try {
            Path dir = scratchDir();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir)) {
                     
                    stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }
}
