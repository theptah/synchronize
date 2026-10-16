package net.ptah.synchronize.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class PathSecurity {
    public static final int MAX_RELATIVE_PATH_LENGTH = 512;

    private PathSecurity() {}

    public static boolean isSafeRelativePath(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_RELATIVE_PATH_LENGTH) {
            return false;
        }
        if (raw.indexOf('\0') >= 0) {
            return false;
        }
        String normalized = raw.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            return false;
        }
        if (normalized.length() >= 2 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':') {
            return false;
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.equals("..") || segment.equals(".")) {
                return false;
            }
            String device = segment.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
            if (device.equals("CON") || device.equals("PRN") || device.equals("AUX") || device.equals("NUL")
                    || device.matches("COM[1-9]") || device.matches("LPT[1-9]")) {
                return false;
            }
        }
        return true;
    }

    public static Path resolveUnder(Path base, String raw) throws IOException {
        if (!isSafeRelativePath(raw)) {
            throw new IOException("Unsafe relative path");
        }

        Path baseAbs = base.toAbsolutePath().normalize();
        Files.createDirectories(baseAbs);
        Path baseReal = baseAbs.toRealPath();
        Path candidate = baseAbs.resolve(raw.replace('\\', '/')).normalize();
        if (!candidate.startsWith(baseAbs)) {
            throw new IOException("Path escapes base directory");
        }

        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new IOException("No existing ancestor for path");
        }
        Path existingReal = existing.toRealPath();
        if (!existingReal.startsWith(baseReal)) {
            throw new IOException("Symlinked path escapes base directory");
        }

        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path candidateReal = candidate.toRealPath();
            if (!candidateReal.startsWith(baseReal)) {
                throw new IOException("Resolved path escapes base directory");
            }
        }
        return candidate;
    }
}
