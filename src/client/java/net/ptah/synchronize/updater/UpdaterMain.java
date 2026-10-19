package net.ptah.synchronize.updater;

import net.ptah.synchronize.client.profile.ProfileStorage;
import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdaterMain {
    public static void main(String[] args) {
        if (args.length < 3) return;
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        final String textApplying = argOrDefault(args, 3, "Applying pending files...");
        final String textComplete = argOrDefault(args, 6, "Update complete.");
        long pid = Long.parseLong(args[0]);
        File pendingDir;
        File gameDir;
        try {
            pendingDir = new File(args[1]).getCanonicalFile();
            gameDir = new File(args[2]).getCanonicalFile();
            if (!Files.isDirectory(pendingDir.toPath()) || !Files.isDirectory(gameDir.toPath())
                    || Files.isSymbolicLink(pendingDir.toPath()) || Files.isSymbolicLink(gameDir.toPath())) return;
        } catch (Exception e) { return; }

        new Thread(() -> {
            while (ProcessHandle.of(pid).isPresent()) {
                try { Thread.sleep(500); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            HelperWindow[] windows = new HelperWindow[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                    catch (Exception ignored) {}
                    windows[0] = new HelperWindow();
                    windows[0].setVisible(true);
                });
            } catch (Exception e) { e.printStackTrace(); return; }
            HelperWindow window = windows[0];
            try {
                Path root = gameDir.toPath();
                ProfileStorage.initialize(root);
                Properties state = ProfileStorage.state(root);
                String previous = ProfileStorage.active(root);
                String target = state.getProperty("pending", "");
                Path journal = ProfileStorage.resolve(ProfileStorage.home(root), "transaction/journal.properties");
                if (Files.exists(journal)) {
                    Properties transaction = ProfileStorage.read(journal);
                    if (!"committed".equals(transaction.getProperty("phase"))) {
                        target = transaction.getProperty("previous", "default");
                        window.context("Restoring previous profile", ProfileStorage.name(root, transaction.getProperty("target", previous)) + " → " + ProfileStorage.name(root, target));
                    } else window.context("Finishing profile switch", ProfileStorage.name(root, transaction.getProperty("target", previous)));
                } else if (!target.isEmpty()) {
                    window.context("Switching profile", ProfileStorage.name(root, previous) + " → " + ProfileStorage.name(root, target));
                } else window.context("Applying updates", textApplying);
                ProfileStorage.applyPending(root, window::report);
            } catch (Exception e) { fail(window, e); return; }

            List<File> files = new ArrayList<>();
            window.report(new ProfileStorage.Progress("updating", "", 0, -1));
            collectFiles(pendingDir, files);
            File deletionManifest = new File(pendingDir.getParentFile(), "pending_deletions.json");
            List<String> deletions = readDeletionManifest(deletionManifest);
            long total = (long) files.size() + deletions.size();
            long completed = 0;
            Exception failure = null;
            for (String deletion : deletions) {
                window.report(new ProfileStorage.Progress("deleting", deletion, completed, total));
                try {
                    Path target = updateDestination(gameDir.toPath(), deletion);
                    if (Files.isSymbolicLink(target)) System.err.println("Skipping symbolic link deletion: " + deletion);
                    else { Files.deleteIfExists(target); System.out.println("Deleted " + deletion); }
                } catch (Exception e) { e.printStackTrace(); failure = e; }
                window.report(new ProfileStorage.Progress("deleting", deletion, ++completed, total));
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            if (deletionManifest.exists()) deletionManifest.delete();
            for (File child : files) {
                Path relative = pendingDir.toPath().relativize(child.toPath());
                File dest;
                try {
                    Path source = resolveUnder(pendingDir.toPath(), relative.toString());
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) continue;
                    dest = updateDestination(gameDir.toPath(), relative.toString()).toFile();
                } catch (Exception e) {
                    System.err.println("Skipping unsafe update path: " + relative); failure = e; continue;
                }
                String name = relative.toString();
                window.report(new ProfileStorage.Progress("updating", name, completed, total));
                try {
                    Files.createDirectories(dest.getParentFile().toPath());
                    Files.move(child.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Moved " + name);
                } catch (Exception e) { e.printStackTrace(); failure = e; }
                window.report(new ProfileStorage.Progress("updating", name, ++completed, total));
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            cleanEmptyDirectories(pendingDir);
            if (failure != null) { fail(window, failure); return; }
            window.complete(textComplete);
            try { Thread.sleep(3500); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> { window.dispose(); System.exit(0); });
        }, "Synchronize Helper").start();
    }

    private static void fail(HelperWindow window, Exception error) {
        error.printStackTrace();
        window.fail(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(window, error.getMessage(), "Synchronize Helper error", JOptionPane.ERROR_MESSAGE);
            window.dispose(); System.exit(1);
        });
    }

    private static String argOrDefault(String[] args, int index, String fallback) {
        if (args.length > index && args[index] != null && !args[index].isBlank() && !args[index].startsWith("synchronize.")) {
            return args[index];
        }
        return fallback;
    }

    private static List<String> readDeletionManifest(File manifest) {
        List<String> result = new ArrayList<>();
        if (!manifest.isFile()) return result;
        try {
            String raw = Files.readString(manifest.toPath());
            Matcher matcher = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(raw);
            while (matcher.find()) {
                String path = matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
                if (path.length() <= 512) result.add(path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static void collectFiles(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            Path childPath = child.toPath();
            if (Files.isSymbolicLink(childPath)) continue;
            if (Files.isDirectory(childPath, LinkOption.NOFOLLOW_LINKS)) collectFiles(child, result);
            else if (Files.isRegularFile(childPath, LinkOption.NOFOLLOW_LINKS)) result.add(child);
        }
    }

    private static Path updateDestination(Path root, String raw) throws Exception {
        if (net.ptah.synchronize.client.profile.ProfileStorage.shared(root, raw)) throw new java.io.IOException("Protected shared directory: " + raw);
        return net.ptah.synchronize.client.profile.ProfileStorage.resolve(root, raw);
    }

    private static Path resolveUnder(Path base, String raw) throws Exception {
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0 || raw.length() > 512) {
            throw new SecurityException("Invalid path");
        }
        String normalizedRaw = raw.replace('\\', '/');
        if (normalizedRaw.startsWith("/") || normalizedRaw.startsWith("//")
                || (normalizedRaw.length() >= 2 && Character.isLetter(normalizedRaw.charAt(0)) && normalizedRaw.charAt(1) == ':')) {
            throw new SecurityException("Absolute path");
        }
        for (String segment : normalizedRaw.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) throw new SecurityException("Traversal path");
        }

        Path baseAbs = base.toAbsolutePath().normalize();
        Path baseReal = baseAbs.toRealPath();
        Path candidate = baseAbs.resolve(normalizedRaw).normalize();
        if (!candidate.startsWith(baseAbs)) throw new SecurityException("Path escape");

        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null || !existing.toRealPath().startsWith(baseReal)) throw new SecurityException("Symlink escape");
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && !candidate.toRealPath().startsWith(baseReal)) {
            throw new SecurityException("Resolved path escape");
        }
        return candidate;
    }

    private static void cleanEmptyDirectories(File dir) {
        if (!dir.exists()) return;
        File[] children = dir.listFiles();
        if (Files.isSymbolicLink(dir.toPath())) return;
        if (children != null) {
            for (File child : children) {
                if (Files.isDirectory(child.toPath(), LinkOption.NOFOLLOW_LINKS)) cleanEmptyDirectories(child);
            }
        }
        dir.delete();
    }
}
