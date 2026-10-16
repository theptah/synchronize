package net.ptah.synchronize.updater;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdaterMain {
    public static void main(String[] args) {
        if (args.length < 3) return;
        final String textApplying = argOrDefault(args, 3, "Applying pending files...");
        final String textDeleting = argOrDefault(args, 4, "Deleting %s...");
        final String textMoving = argOrDefault(args, 5, "Moving: %s");
        final String textComplete = argOrDefault(args, 6, "Update complete.");
        long pid = Long.parseLong(args[0]);
        File pendingDir;
        File gameDir;
        try {
            pendingDir = new File(args[1]).getCanonicalFile();
            gameDir = new File(args[2]).getCanonicalFile();
            if (!Files.isDirectory(pendingDir.toPath()) || !Files.isDirectory(gameDir.toPath())
                    || Files.isSymbolicLink(pendingDir.toPath()) || Files.isSymbolicLink(gameDir.toPath())) {
                return;
            }
        } catch (Exception e) {
            return;
        }

        new Thread(() -> {
            while (ProcessHandle.of(pid).isPresent()) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

            JFrame frame = new JFrame();
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            frame.setSize(450, 140);
            frame.setLocationRelativeTo(null);

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 3));
            panel.setBackground(new Color(40, 40, 40));

            JLabel titleLabel = new JLabel("Synchronize", SwingConstants.CENTER);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 10, 10, 10));

            JLabel statusLabel = new JLabel(textApplying, SwingConstants.CENTER);
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            statusLabel.setForeground(new Color(200, 200, 200));
            statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(false);
            progressBar.setMinimum(0);
            progressBar.setMaximum(100);
            progressBar.setValue(0);
            progressBar.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
            progressBar.setBackground(new Color(40, 40, 40));
            progressBar.setForeground(new Color(0, 150, 255));

            panel.add(titleLabel, BorderLayout.NORTH);

            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.setBackground(new Color(40, 40, 40));
            centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
            centerPanel.add(progressBar, BorderLayout.CENTER);
            centerPanel.add(statusLabel, BorderLayout.SOUTH);
            panel.add(centerPanel, BorderLayout.CENTER);
            frame.add(panel);
            frame.setVisible(true);

            List<File> files = new ArrayList<>();
            collectFiles(pendingDir, files);
            File deletionManifest = new File(pendingDir.getParentFile(), "pending_deletions.json");
            List<String> deletions = readDeletionManifest(deletionManifest);
            int total = files.size() + deletions.size();
            if (total == 0) total = 1;
            final int totalCount = total;
            int progressIndex = 0;

            for (String deletion : deletions) {
                final String fileName = deletion;
                final int current = ++progressIndex;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(textDeleting.replace("%s", fileName));
                    progressBar.setValue((int) ((current / (float) totalCount) * 100));
                });
                try {
                    Path target = resolveUnder(gameDir.toPath(), deletion);
                    if (Files.isSymbolicLink(target)) {
                        System.err.println("Skipping symbolic link deletion: " + deletion);
                    } else {
                        Files.deleteIfExists(target);
                        System.out.println("Deleted " + deletion);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            if (deletionManifest.exists()) deletionManifest.delete();

            for (int i = 0; i < files.size(); i++) {
                File child = files.get(i);
                Path relative = pendingDir.toPath().relativize(child.toPath());
                File dest;
                try {
                    Path source = resolveUnder(pendingDir.toPath(), relative.toString());
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) continue;
                    dest = resolveUnder(gameDir.toPath(), relative.toString()).toFile();
                } catch (Exception e) {
                    System.err.println("Skipping unsafe update path: " + relative);
                    continue;
                }
                final String fileName = relative.toString();
                final int current = ++progressIndex;

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(textMoving.replace("%s", fileName));
                    progressBar.setValue((int) ((current / (float) totalCount) * 100));
                });

                try {
                    Files.createDirectories(dest.getParentFile().toPath());
                    Files.move(child.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Moved " + fileName);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }

            cleanEmptyDirectories(pendingDir);

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(textComplete);
                statusLabel.setForeground(new Color(100, 255, 100));
                progressBar.setValue(100);
            });

            try { Thread.sleep(3500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
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
