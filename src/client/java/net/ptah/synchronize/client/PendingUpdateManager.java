package net.ptah.synchronize.client;

import net.minecraft.util.Language;
import net.fabricmc.loader.api.FabricLoader;
import net.ptah.synchronize.util.PathSecurity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

public final class PendingUpdateManager {
    private PendingUpdateManager() {}

    public static File getPendingDir() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/pending");
    }

    public static File getPendingDeletionsFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/pending_deletions.json");
    }

    public static synchronized void stageDeletions(List<String> destinations) throws IOException {
        File manifest = getPendingDeletionsFile();
        Files.createDirectories(manifest.toPath().getParent());
        Files.createDirectories(getPendingDir().toPath());
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < destinations.size(); i++) {
            String path = destinations.get(i).replace('\\', '/');
            if (!PathSecurity.isSafeRelativePath(path)) throw new IOException("Unsafe deletion path");
            if (i > 0) json.append(",\n");
            json.append("  \"").append(path.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        json.append("\n]\n");
        Files.writeString(manifest.toPath(), json.toString(), StandardCharsets.UTF_8);
    }

    public static synchronized void appendDeletions(List<String> destinations) throws IOException {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(readPendingDeletions());
        for (String destination : destinations) {
            merged.add(destination.replace('\\', '/'));
        }
        stageDeletions(new ArrayList<>(merged));
    }

    private static List<String> readPendingDeletions() {
        List<String> result = new ArrayList<>();
        File manifest = getPendingDeletionsFile();
        if (!manifest.isFile()) return result;
        try {
            String raw = Files.readString(manifest.toPath(), StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(raw);
            while (matcher.find()) {
                String path = matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
                if (PathSecurity.isSafeRelativePath(path)) result.add(path);
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    public static synchronized void stage(File sourceTempFile, String destRelativePath) throws IOException {
        Path tempDir = ClientSyncHandler.getTempDir().toPath().toAbsolutePath().normalize();
        Path source = sourceTempFile.toPath().toAbsolutePath().normalize();
        Path pendingDir = getPendingDir().toPath().toAbsolutePath().normalize();

        Files.createDirectories(tempDir);
        Files.createDirectories(pendingDir);

        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe pending source");
        }

        Path tempReal = tempDir.toRealPath();
        Path sourceReal = source.toRealPath();
        if (!sourceReal.startsWith(tempReal)) {
            throw new IOException("Pending source is outside temp directory");
        }

        Path relativeSource = tempReal.relativize(sourceReal);
        if (relativeSource.getNameCount() == 0) {
            throw new IOException("Invalid pending source");
        }

        Path verifiedSource = PathSecurity.resolveUnder(tempDir, relativeSource.toString());
        if (Files.isSymbolicLink(verifiedSource) || !Files.isRegularFile(verifiedSource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe pending source");
        }

        Path pendingFile = PathSecurity.resolveUnder(pendingDir, destRelativePath);
        Files.createDirectories(pendingFile.getParent());
        Files.move(verifiedSource, pendingFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public static synchronized boolean hasPending() {
        return countPendingFiles(getPendingDir()) > 0 || getPendingDeletionsFile().isFile();
    }

    public static int countPendingFiles(File dir) {
        if (!dir.exists()) return 0;
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            Path childPath = child.toPath();
            if (Files.isSymbolicLink(childPath)) continue;
            count += Files.isDirectory(childPath, LinkOption.NOFOLLOW_LINKS) ? countPendingFiles(child) : 1;
        }
        return count;
    }

    public static List<File> getAllPendingFiles() {
        List<File> result = new ArrayList<>();
        collectFiles(getPendingDir(), result);
        return result;
    }

    private static void collectFiles(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            Path childPath = child.toPath();
            if (Files.isSymbolicLink(childPath)) continue;
            if (Files.isDirectory(childPath, LinkOption.NOFOLLOW_LINKS)) {
                collectFiles(child, result);
            } else if (Files.isRegularFile(childPath, LinkOption.NOFOLLOW_LINKS)) {
                result.add(child);
            }
        }
    }

    public static void cleanEmptyDirectories(File dir) {
        if (!dir.exists()) return;
        File[] children = dir.listFiles();
        if (Files.isSymbolicLink(dir.toPath())) return;
        if (children != null) {
            for (File child : children) {
                if (Files.isDirectory(child.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    cleanEmptyDirectories(child);
                }
            }
        }
        dir.delete();
    }

    public static synchronized void onClientStopping() {
        if (hasPending()) {
            spawnUpdater(false);
        }
    }

    private static String updaterText(String key) {
        try {
            return Language.getInstance().get(key);
        } catch (Exception e) {
            return "";
        }
    }

    public static void spawnUpdater(boolean forceExit) {
        try {
            File gameDir = FabricLoader.getInstance().getGameDir().toFile();
            File pendingDir = getPendingDir();
            File updaterDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/updater");
            File classFile = new File(updaterDir, "net/ptah/synchronize/updater/UpdaterMain.class");
            classFile.getParentFile().mkdirs();

            try (InputStream in = PendingUpdateManager.class.getResourceAsStream("/net/ptah/synchronize/updater/UpdaterMain.class")) {
                if (in != null) {
                    Files.copy(in, classFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    System.err.println("[Synchronize] Could not find UpdaterMain.class in jar! Cannot spawn safe updater.");
                    return;
                }
            }

            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                File javawBin = new File(javaHome + File.separator + "bin" + File.separator + "javaw.exe");
                if (javawBin.exists()) {
                    javaBin = javawBin.getAbsolutePath();
                }
            }

            long pid = ProcessHandle.current().pid();

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-cp",
                    updaterDir.getAbsolutePath(),
                    "net.ptah.synchronize.updater.UpdaterMain",
                    String.valueOf(pid),
                    pendingDir.getAbsolutePath(),
                    gameDir.getAbsolutePath(),
                    updaterText("synchronize.updater.applying"),
                    updaterText("synchronize.updater.deleting"),
                    updaterText("synchronize.updater.moving"),
                    updaterText("synchronize.updater.complete")
            );
            pb.start();

            System.out.println("[Synchronize] Spawned safe standalone Java updater.");
            if (forceExit) {
                System.out.println("[Synchronize] Exiting current JVM to release file locks.");
                System.exit(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
