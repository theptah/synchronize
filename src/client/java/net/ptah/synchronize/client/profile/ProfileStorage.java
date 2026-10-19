package net.ptah.synchronize.client.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.ptah.synchronize.util.StorageLayout;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public final class ProfileStorage {
    private static final Set<String> SHARED_DIRECTORIES = Set.of(
            "saves", "screenshots", "logs", "crash-reports", "assets", "libraries", "versions",
            "runtime", "runtimes", "java", "jre", "jdk", "natives", "bin", "launcher", "launchers",
            ".launcher", ".fabric", ".cache", "cache", "caches", ".git", ".github", ".gradle",
            ".idea", ".vscode", "gradle", "src", "build", "templates", "synchronize-profiles", "config/synchronize");
    private static final ThreadLocal<java.util.function.Consumer<Progress>> PROGRESS = new ThreadLocal<>();
    public record Progress(String phase, String path, long completed, long total) {}
    private ProfileStorage() {}

    private static void report(String phase, String path, long completed, long total) {
        java.util.function.Consumer<Progress> listener = PROGRESS.get();
        if (listener != null) {
            try { listener.accept(new Progress(phase, path, completed, total)); }
            catch (RuntimeException ignored) {}
        }
    }

    public static Path home(Path root) throws IOException {
        return resolve(root, "config/synchronize/profiles");
    }

    public static Path profile(Path root, String id) throws IOException {
        if (!id.equals("default") && !id.matches("[a-f0-9]{32}")) throw new IOException("Invalid profile ID");
        return resolve(home(root), id);
    }

    public static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new IOException("Unsafe metadata file");
            try (InputStream in = Files.newInputStream(path)) { properties.load(in); }
        }
        return properties;
    }

    public static void write(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent())) throw new IOException("Unsafe metadata path");
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        if (Files.isSymbolicLink(temp)) throw new IOException("Unsafe temporary path");
        try (OutputStream out = Files.newOutputStream(temp)) { properties.store(out, null); }
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static synchronized Properties readSection(Path file, String section) throws IOException {
        Map<String, Object> document = StorageLayout.readJson(file);
        Properties result = new Properties();
        if (!document.containsKey(section)) return result;
        for (var entry : StorageLayout.object(document.get(section)).entrySet()) {
            if (!(entry.getValue() instanceof String value)) throw new IOException("Invalid profile record: " + section);
            result.setProperty(entry.getKey(), value);
        }
        return result;
    }

    private static synchronized void writeSection(Path file, String section, Properties properties) throws IOException {
        Map<String, Object> document = StorageLayout.readJson(file);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) values.put(key, properties.getProperty(key));
        document.put(section, values);
        StorageLayout.writeJson(file, document);
    }

    public static Properties profileData(Path root, String id, String section) throws IOException {
        return readSection(resolve(profile(root, id), "profile.json"), section);
    }

    public static void writeProfileData(Path root, String id, String section, Properties properties) throws IOException {
        writeSection(resolve(profile(root, id), "profile.json"), section, properties);
    }

    private static Properties systemData(Path root, String section) throws IOException {
        return readSection(resolve(home(root), "state.json"), section);
    }

    public static void writeState(Path root, Properties properties) throws IOException { writeSection(resolve(home(root), "state.json"), "state", properties); }
    public static void writeCore(Path root, Properties properties) throws IOException { writeSection(resolve(home(root), "state.json"), "core", properties); }

    private static synchronized void migrateRecords(Path directory, String filename, String[][] sections) throws IOException {
        boolean found = false;
        for (String[] section : sections) if (Files.exists(resolve(directory, section[0]), LinkOption.NOFOLLOW_LINKS)) found = true;
        if (!found) return;
        Path target = resolve(directory, filename);
        Map<String, Object> document = StorageLayout.readJson(target);
        for (String[] section : sections) {
            Path legacy = resolve(directory, section[0]);
            Properties properties = read(legacy);
            if (!document.containsKey(section[1])) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (String key : properties.stringPropertyNames().stream().sorted().toList()) values.put(key, properties.getProperty(key));
                document.put(section[1], values);
            } else {
                for (Object value : StorageLayout.object(document.get(section[1])).values()) if (!(value instanceof String)) throw new IOException("Invalid migrated profile record");
            }
        }
        StorageLayout.writeJson(target, document);
        for (String[] section : sections) Files.deleteIfExists(resolve(directory, section[0]));
    }

    public static synchronized void initialize(Path root) throws IOException {
        Path base = home(root);
        StorageLayout.migrateSharedDirectories(resolve(root, "config/synchronize"));
        migrateRecords(base, "state.json", new String[][] {{"state.properties", "state"}, {"core.properties", "core"}, {"targets.properties", "targets"}});
        try (Stream<Path> children = Files.list(base)) {
            for (Path child : children.toList()) {
                String id = child.getFileName().toString();
                if (!id.equals("default") && !id.matches("[a-f0-9]{32}")) continue;
                if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe profile directory");
                migrateRecords(profile(root, id), "profile.json", new String[][] {{"profile.properties", "metadata"}, {"files.properties", "files"}, {"directories.properties", "directories"}, {"owned.properties", "owned"}, {"delta.properties", "delta"}});
            }
        }
        if (state(root).isEmpty()) {
            Properties s = new Properties(); s.setProperty("active", "default"); s.setProperty("scopeVersion", "2"); writeState(root, s);
        }
        if (metadata(root, "default").isEmpty()) {
            Properties p = new Properties(); p.setProperty("name", "Default"); p.setProperty("ready", "true"); writeProfileData(root, "default", "metadata", p);
        }
    }

    public static Properties state(Path root) throws IOException { return systemData(root, "state"); }
    public static String active(Path root) throws IOException { return state(root).getProperty("active", "default"); }
    public static Properties metadata(Path root, String id) throws IOException { return profileData(root, id, "metadata"); }
    public static boolean ready(Path root, String id) throws IOException { return "true".equals(metadata(root, id).getProperty("ready")); }
    public static String name(Path root, String id) throws IOException { return metadata(root, id).getProperty("name", id); }

    public static List<String> list(Path root) throws IOException {
        List<String> ids = new ArrayList<>(); ids.add("default");
        try (Stream<Path> stream = Files.list(home(root))) {
            for (Path p : stream.sorted().toList()) {
                String id = p.getFileName().toString();
                if (id.matches("[a-f0-9]{32}") && !Files.isSymbolicLink(p) && ready(root, id)) ids.add(id);
            }
        }
        return ids;
    }

    public static synchronized String server(Path root, String endpoint, String display, String minecraft) throws IOException {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (int i = 0; i < 16; i++) hex.append(String.format("%02x", bytes[i]));
            String id = hex.toString();
            Properties p = metadata(root, id);
            if (!p.isEmpty() && !minecraft.equals(p.getProperty("minecraft"))) throw new IOException("Profile belongs to a different Minecraft version");
            p.setProperty("server", endpoint); p.setProperty("name", display); p.setProperty("minecraft", minecraft);
            p.putIfAbsent("ready", "false"); writeProfileData(root, id, "metadata", p);
            return id;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    public static boolean safe(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 512 || raw.indexOf('\0') >= 0) return false;
        String s = raw.replace('\\', '/');
        if (s.startsWith("/") || s.contains(":")) return false;
        for (String segment : s.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(".") || segment.endsWith(" ")) return false;
            if (segment.chars().anyMatch(c -> c < 32)) return false;
            String device = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (device.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return false;
        }
        return true;
    }

    public static Path resolve(Path base, String raw) throws IOException {
        if (!safe(raw)) throw new IOException("Unsafe relative path: " + raw);
        Path abs = base.toAbsolutePath().normalize();
        Files.createDirectories(abs);
        Path real = abs.toRealPath();
        Path result = abs.resolve(raw.replace('\\', '/')).normalize();
        if (!result.startsWith(abs)) throw new IOException("Path escapes root");
        Path ancestor = result;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        Path canonicalAncestor = ancestor.toRealPath();
        if (!canonicalAncestor.startsWith(real)) throw new IOException("Symlink escapes root");
        if (!canonicalAncestor.toString().equalsIgnoreCase(real.resolve(abs.relativize(ancestor)).toString())) throw new IOException("Linked or aliased paths are not managed");
        if (!ancestor.equals(result) && !Files.isDirectory(ancestor)) throw new IOException("A file blocks the target directory");
        Path cursor = result;
        while (cursor != null && cursor.startsWith(abs)) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("Symbolic links are not managed");
            if (cursor.equals(abs)) break;
            cursor = cursor.getParent();
        }
        return result;
    }

    public static Set<String> sharedDirectories(Path root) throws IOException {
        Set<String> paths = new LinkedHashSet<>(SHARED_DIRECTORIES);
        Object setting = StorageLayout.readJson(resolve(root, "config/synchronize/config.json")).get("additionalSharedDirectories");
        if (setting != null && !(setting instanceof String)) throw new IOException("Invalid additionalSharedDirectories setting");
        String additional = setting == null ? "" : (String) setting;
        for (String entry : additional.split(",")) {
            String value = entry.trim().replace('\\', '/');
            if (value.isEmpty()) continue;
            if (!safe(value)) throw new IOException("Invalid shared directory: " + value);
            paths.add(value);
        }
        return paths;
    }

    private static boolean underShared(String path, Set<String> shared) {
        String normalized = path.replace('\\', '/');
        return normalized.regionMatches(true, 0, ".synchronize", 0, ".synchronize".length())
                || shared.stream().anyMatch(p -> normalized.equalsIgnoreCase(p) || (normalized.length() > p.length()
                && normalized.charAt(p.length()) == '/' && normalized.regionMatches(true, 0, p, 0, p.length())));
    }

    private static boolean protectedFilePath(String path, Set<String> shared) {
        return underShared(path, shared) || shared.stream().anyMatch(dir -> dir.length() > path.length()
                && dir.charAt(path.length()) == '/' && dir.regionMatches(true, 0, path, 0, path.length()))
                || path.equalsIgnoreCase("mods") || path.equalsIgnoreCase("launcher_accounts.json")
                || path.equalsIgnoreCase("launcher_profiles.json") || path.equalsIgnoreCase("accounts.json");
    }

    public static boolean shared(Path root, String raw) throws IOException {
        if (!safe(raw)) throw new IOException("Unsafe profile path");
        return underShared(raw, sharedDirectories(root));
    }

    private static void allowDirectory(Path root, String raw) throws IOException {
        if (!safe(raw) || shared(root, raw)) throw new IOException("Protected profile directory: " + raw);
        Path directory = resolve(root, raw);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(directory)) throw new IOException("Profile directory is blocked: " + raw);
    }

    public static void allow(Path root, String raw) throws IOException {
        if (!safe(raw)) throw new IOException("Unsafe profile target");
        Set<String> shared = sharedDirectories(root);
        String p = raw.replace('\\', '/');
        if (protectedFilePath(p, shared)) throw new IOException("Protected profile target: " + raw);
        Path target = resolve(root, raw);
        Path existing = target;
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        String canonical = root.toAbsolutePath().toRealPath().relativize(existing.toRealPath().resolve(existing.relativize(target))).toString().replace('\\', '/');
        if (protectedFilePath(canonical, shared)) throw new IOException("Protected profile target: " + raw);
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || infrastructure(root, raw)) throw new IOException("Protected or non-file target: " + raw);
    }

    public static boolean infrastructure(Path root, String path) throws IOException {
        String normalized = path.replace('\\', '/');
        Properties core = systemData(root, "core");
        if (core.stringPropertyNames().stream().anyMatch(p -> p.equalsIgnoreCase(normalized))) return true;
        Path file = resolve(root, normalized);
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("mods/") || !normalized.endsWith(".jar") || !Files.isRegularFile(file)) return false;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return false;
            String json;
            try (InputStream in = zip.getInputStream(entry)) { json = new String(in.readNBytes(131072), StandardCharsets.UTF_8); }
            var matcher = java.util.regex.Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
            if (matcher.find()) return matcher.group(1).equals("synchronize");
        } catch (java.util.zip.ZipException ignored) { }
        return false;
    }

    public static synchronized void registerTargets(Path root, Set<String> paths) throws IOException {
        Properties targets = systemData(root, "targets");
        Properties baseline = profileData(root, "default", "files");
        for (String raw : paths) {
            String path = raw.replace('\\', '/'); allow(root, path);
            if (!targets.containsKey(path)) {
                if (!active(root).equals("default") && (!"2".equals(state(root).getProperty("scopeVersion")) || path.indexOf('/') < 0)) {
                    Path live = resolve(root, path);
                    if (Files.isRegularFile(live) && !baseline.containsKey(path)) {
                        copy(live, resolve(resolve(profile(root, "default"), "files"), path)); baseline.setProperty(path, "1");
                    }
                }
                targets.setProperty(path, "1");
            }
        }
        writeProfileData(root, "default", "files", baseline);
        writeSection(resolve(home(root), "state.json"), "targets", targets);
    }

    public static Path file(Path root, String id, String path) throws IOException {
        allow(root, path);
        return id.equals(active(root)) ? resolve(root, path) : resolve(resolve(profile(root, id), "files"), path);
    }

    public static Set<String> owned(Path root, String id) throws IOException {
        Set<String> set = new LinkedHashSet<>(profileData(root, id, "owned").stringPropertyNames());
        set.addAll(profileData(root, id, "delta").stringPropertyNames());
        return set;
    }

    public static synchronized void commitDownload(Path root, String id, Path session, Set<String> installed, Set<String> deleted) throws IOException {
        Properties delta = profileData(root, id, "delta");
        Properties owned = profileData(root, id, "owned");
        for (String path : installed) {
            allow(root, path);
            Path source = resolve(session, path);
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Prepared file missing: " + path);
            copy(source, resolve(resolve(profile(root, id), "incoming"), path));
            delta.setProperty(path, "install"); owned.setProperty(path, "1");
        }
        for (String path : deleted) {
            allow(root, path);
            if (owned.containsKey(path)) { delta.setProperty(path, "delete"); owned.remove(path); }
        }
        writeProfileData(root, id, "owned", owned);
        writeProfileData(root, id, "delta", delta);
        Properties p = metadata(root, id); p.setProperty("ready", "true"); writeProfileData(root, id, "metadata", p);
    }

    public static Path comparisonFile(Path root, String id, String path) throws IOException {
        Properties delta = profileData(root, id, "delta");
        if ("install".equals(delta.getProperty(path))) return resolve(resolve(profile(root, id), "incoming"), path);
        if ("delete".equals(delta.getProperty(path))) return resolve(resolve(profile(root, id), "absent"), path);
        return file(root, id, path);
    }

    public static synchronized void queue(Path root, String id, String deleteAfter, String minecraft) throws IOException {
        if (!ready(root, id)) throw new IOException("Profile is not ready");
        Properties stored = profileData(root, id, "files");
        Properties changes = profileData(root, id, "delta");
        for (String path : stored.stringPropertyNames()) {
            if (shared(root, path)) continue;
            allow(root, path);
            if (!id.equals(active(root)) && !changes.containsKey(path) && !Files.isRegularFile(resolve(resolve(profile(root, id), "files"), path))) throw new IOException("Profile backup is incomplete: " + path);
        }
        for (String path : changes.stringPropertyNames()) {
            allow(root, path);
            if (changes.getProperty(path).equals("install") && !Files.isRegularFile(resolve(resolve(profile(root, id), "incoming"), path))) throw new IOException("Profile download is incomplete: " + path);
        }
        for (String path : profileData(root, id, "directories").stringPropertyNames()) {
            if (!shared(root, path)) allowDirectory(root, path);
        }
        if (!id.equals("default") && !minecraft.equals(metadata(root, id).getProperty("minecraft"))) throw new IOException("Minecraft version does not match profile");
        if (deleteAfter != null && (deleteAfter.equals("default") || !deleteAfter.equals(active(root)))) throw new IOException("Invalid active profile deletion");
        Properties s = state(root); s.setProperty("pending", id);
        if (deleteAfter == null) s.remove("deleteAfter"); else s.setProperty("deleteAfter", deleteAfter);
        writeState(root, s);
    }

    public static synchronized void cancel(Path root) throws IOException {
        Properties s = state(root); s.remove("pending"); s.remove("deleteAfter"); writeState(root, s);
    }

    public static synchronized void delete(Path root, String id) throws IOException {
        if (id.equals("default") || id.equals(active(root))) throw new IOException("Cannot directly delete active/default profile");
        if (id.equals(state(root).getProperty("pending"))) cancel(root);
        erase(profile(root, id));
    }

    public static boolean pending(Path root) throws IOException {
        migrateRecords(home(root), "state.json", new String[][] {{"state.properties", "state"}, {"core.properties", "core"}, {"targets.properties", "targets"}});
        return !state(root).getProperty("pending", "").isEmpty() || Files.exists(resolve(home(root), "transaction/journal.properties"));
    }

    private static Set<String> liveFiles(Path root, Set<String> directories) throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        Set<String> shared = sharedDirectories(root);
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(child)) collect(root, child, paths, directories, shared);
            }
        }
        Set<String> targets = new LinkedHashSet<>(systemData(root, "targets").stringPropertyNames());
        targets.add("options.txt");
        for (String path : targets) {
            if (underShared(path, shared)) continue;
            allow(root, path); if (Files.isRegularFile(resolve(root, path))) paths.add(path);
        }
        return paths;
    }

    private static void collect(Path root, Path directory, Set<String> paths, Set<String> directories, Set<String> shared) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        String rel = root.toAbsolutePath().normalize().relativize(directory.toAbsolutePath().normalize()).toString().replace('\\', '/');
        if (underShared(rel, shared)) return;
        report("scanning", rel, 0, -1);
        if (Files.isSymbolicLink(directory)) throw new IOException("Profile cannot manage linked path: " + rel);
        if (Files.isRegularFile(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!infrastructure(root, rel)) { allow(root, rel); paths.add(rel); }
        } else if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            allowDirectory(root, rel); directories.add(rel);
            try (Stream<Path> children = Files.list(directory)) {
                for (Path child : children.toList()) collect(root, child, paths, directories, shared);
            }
        }
    }

    private static void createDirectories(Path root, Path base, Set<String> directories) throws IOException {
        for (String path : directories.stream().sorted(Comparator.comparingInt(String::length)).toList()) {
            allowDirectory(root, path); Files.createDirectories(resolve(base, path));
        }
    }

    private static void removeDirectories(Path root, Set<String> directories) throws IOException {
        for (String path : directories.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            allowDirectory(root, path);
            Path directory = resolve(root, path);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            boolean empty;
            try (Stream<Path> children = Files.list(directory)) { empty = children.findAny().isEmpty(); }
            if (empty) Files.delete(directory);
        }
    }

    private static void snapshot(Path root, String id, Set<String> paths, Set<String> directories, String phase) throws IOException {
        report(phase, "", 0, paths.size());
        Path dir = resolve(profile(root, id), "files");
        Path next = resolve(profile(root, id), "snapshot-next"); erase(next); Files.createDirectories(next);
        Properties inventory = new Properties();
        Properties folders = new Properties();
        createDirectories(root, next, directories);
        for (String path : directories) folders.setProperty(path, "1");
        long saved = 0;
        for (String path : paths) {
            report(phase, path, saved, paths.size());
            allow(root, path); copy(resolve(root, path), resolve(next, path)); inventory.setProperty(path, "1");
            report(phase, path, ++saved, paths.size());
        }
        report(phase + "-metadata", "", 0, -1);
        Path old = resolve(profile(root, id), "snapshot-old"); erase(old);
        if (Files.exists(dir)) Files.move(dir, old);
        try { Files.move(next, dir); }
        catch (IOException e) { if (Files.exists(old) && !Files.exists(dir)) Files.move(old, dir); throw e; }
        writeProfileData(root, id, "directories", folders);
        writeProfileData(root, id, "files", inventory); erase(old);
    }

    private static void preserveLegacyDirectories(Path root, Set<String> paths, Set<String> directories) throws IOException {
        Properties baseline = profileData(root, "default", "files");
        Properties folders = profileData(root, "default", "directories");
        Set<String> tracked = systemData(root, "targets").stringPropertyNames();
        for (String path : paths) {
            if (path.equals("options.txt") || path.startsWith("mods/") || path.startsWith("config/") || tracked.contains(path)) continue;
            if (!baseline.containsKey(path)) {
                copy(resolve(root, path), resolve(resolve(profile(root, "default"), "files"), path)); baseline.setProperty(path, "1");
            }
        }
        for (String path : directories) {
            if (path.equals("mods") || path.startsWith("mods/") || path.equals("config") || path.startsWith("config/")) continue;
            Files.createDirectories(resolve(resolve(profile(root, "default"), "files"), path)); folders.setProperty(path, "1");
        }
        writeProfileData(root, "default", "files", baseline);
        writeProfileData(root, "default", "directories", folders);
    }

    public static synchronized void applyPending(Path root) throws IOException {
        applyPending(root, null);
    }

    public static synchronized void applyPending(Path root, java.util.function.Consumer<Progress> listener) throws IOException {
        java.util.function.Consumer<Progress> previousListener = PROGRESS.get();
        if (listener == null) PROGRESS.remove(); else PROGRESS.set(listener);
        try { applyPendingFiles(root); }
        finally { if (previousListener == null) PROGRESS.remove(); else PROGRESS.set(previousListener); }
    }

    private static void applyPendingFiles(Path root) throws IOException {
        report("preparing", "", 0, -1);
        initialize(root);
        Path lockPath = resolve(home(root), "worker.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock();
             FileChannel gameChannel = FileChannel.open(resolve(home(root), "game.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock gameLock = gameChannel.tryLock()) {
            if (lock == null) throw new IOException("Another profile operation is running");
            if (gameLock == null) throw new IOException("Minecraft is still running in this game directory");
            Path transaction = resolve(home(root), "transaction");
            Path journal = resolve(transaction, "journal.properties");
            if (Files.exists(journal)) {
                Properties j = read(journal);
                if (!"committed".equals(j.getProperty("phase"))) { rollback(root, transaction, j); return; }
                cleanupCommitted(root, transaction, j);
            }
            Properties state = state(root);
            String target = state.getProperty("pending", "");
            if (target.isEmpty()) return;
            if (!ready(root, target)) throw new IOException("Pending profile is not ready");
            String previous = active(root);
            Set<String> liveDirectories = new LinkedHashSet<>();
            report("scanning", "", 0, -1);
            Set<String> live = liveFiles(root, liveDirectories);
            if (!"2".equals(state.getProperty("scopeVersion")) && !previous.equals("default")) preserveLegacyDirectories(root, live, liveDirectories);
            snapshot(root, previous, live, liveDirectories, "saving");
            erase(transaction); Files.createDirectories(transaction);
            Path backup = resolve(transaction, "backup");
            long backedUp = 0;
            report("backup", "", 0, live.size());
            for (String p : live) {
                report("backup", p, backedUp, live.size());
                copy(resolve(root, p), resolve(backup, p));
                report("backup", p, ++backedUp, live.size());
            }
            Path next = resolve(transaction, "next"); Files.createDirectories(next);
            Properties inventory = profileData(root, target, "files");
            Properties delta = profileData(root, target, "delta");
            long prepared = 0;
            long prepareTotal = inventory.size() + delta.size();
            report("preparing-files", "", 0, prepareTotal);
            Set<String> nextDirectories = new LinkedHashSet<>();
            for (String path : profileData(root, target, "directories").stringPropertyNames()) {
                if (!shared(root, path)) nextDirectories.add(path);
            }
            for (String path : new LinkedHashSet<>(inventory.stringPropertyNames())) {
                report("preparing-files", path, prepared, prepareTotal);
                if (shared(root, path)) { inventory.remove(path); report("preparing-files", path, ++prepared, prepareTotal); continue; }
                allow(root, path); copy(resolve(resolve(profile(root, target), "files"), path), resolve(next, path));
                report("preparing-files", path, ++prepared, prepareTotal);
            }
            for (String p : delta.stringPropertyNames()) {
                report("preparing-files", p, prepared, prepareTotal);
                allow(root, p);
                if (delta.getProperty(p).equals("delete")) { Files.deleteIfExists(resolve(next, p)); inventory.remove(p); }
                else { copy(resolve(resolve(profile(root, target), "incoming"), p), resolve(next, p)); inventory.setProperty(p, "1"); }
                report("preparing-files", p, ++prepared, prepareTotal);
            }
            report("preparing", "", 0, -1);
            for (String path : inventory.stringPropertyNames()) {
                int slash = path.lastIndexOf('/');
                while (slash > 0) { nextDirectories.add(path.substring(0, slash)); slash = path.lastIndexOf('/', slash - 1); }
            }
            createDirectories(root, next, nextDirectories);
            Properties j = new Properties(); j.setProperty("previous", previous); j.setProperty("target", target); j.setProperty("phase", "prepared");
            j.setProperty("deleteAfter", state.getProperty("deleteAfter", ""));
            for (String p : liveDirectories) j.setProperty("before-dir:" + p, "1");
            for (String p : nextDirectories) j.setProperty("after-dir:" + p, "1");
            for (String p : live) j.setProperty("before:" + p, "1");
            for (String p : inventory.stringPropertyNames()) j.setProperty("after:" + p, "1");
            write(journal, j);
            try {
                long removed = 0;
                report("removing", "", 0, live.size());
                for (String p : live) {
                    report("removing", p, removed, live.size());
                    allow(root, p); Files.deleteIfExists(resolve(root, p));
                    report("removing", p, ++removed, live.size());
                }
                report("finishing", "", 0, -1);
                removeDirectories(root, liveDirectories);
                createDirectories(root, root, nextDirectories);
                long applied = 0;
                report("applying", "", 0, inventory.size());
                for (String p : inventory.stringPropertyNames()) {
                    report("applying", p, applied, inventory.size());
                    allow(root, p); copy(resolve(next, p), resolve(root, p));
                    report("applying", p, ++applied, inventory.size());
                }
                snapshot(root, target, inventory.stringPropertyNames(), nextDirectories, "snapshot");
                state.setProperty("active", target); state.setProperty("scopeVersion", "2"); state.remove("pending"); state.remove("deleteAfter"); state.remove("error");
                writeState(root, state);
                j.setProperty("phase", "committed"); write(journal, j);
            } catch (IOException e) {
                try { rollback(root, transaction, j); } catch (IOException recovery) { e.addSuppressed(recovery); }
                throw e;
            }
            cleanupCommitted(root, transaction, j);
        } catch (java.nio.channels.OverlappingFileLockException e) { throw new IOException("Another profile operation is running", e); }
    }

    private static void rollback(Path root, Path transaction, Properties j) throws IOException {
        long removeTotal = j.stringPropertyNames().stream().filter(key -> key.startsWith("after:")).count();
        long removed = 0;
        report("rollback-removing", "", 0, removeTotal);
        for (String key : j.stringPropertyNames()) {
            if (key.startsWith("after:")) {
                String p = key.substring(6); report("rollback-removing", p, removed, removeTotal);
                allow(root, p); Files.deleteIfExists(resolve(root, p));
                report("rollback-removing", p, ++removed, removeTotal);
            }
        }
        report("restoring", "", 0, -1);
        Set<String> beforeDirectories = new LinkedHashSet<>();
        Set<String> afterDirectories = new LinkedHashSet<>();
        for (String key : j.stringPropertyNames()) {
            if (key.startsWith("before-dir:")) beforeDirectories.add(key.substring(11));
            if (key.startsWith("after-dir:")) afterDirectories.add(key.substring(10));
        }
        removeDirectories(root, afterDirectories);
        createDirectories(root, root, beforeDirectories);
        long restoreTotal = j.stringPropertyNames().stream().filter(key -> key.startsWith("before:")).count();
        long restored = 0;
        report("restoring", "", 0, restoreTotal);
        for (String key : j.stringPropertyNames()) {
            if (key.startsWith("before:")) {
                String p = key.substring(7); report("restoring", p, restored, restoreTotal);
                allow(root, p); copy(resolve(resolve(transaction, "backup"), p), resolve(root, p));
                report("restoring", p, ++restored, restoreTotal);
            }
        }
        report("finishing", "", 0, -1);
        Properties state = state(root); state.setProperty("active", j.getProperty("previous", "default"));
        state.remove("pending"); state.remove("deleteAfter"); state.setProperty("error", "An interrupted profile switch was rolled back. Please review your profiles.");
        writeState(root, state); erase(transaction);
    }

    private static void cleanupCommitted(Path root, Path transaction, Properties j) throws IOException {
        report("finishing", "", 0, -1);
        String target = j.getProperty("target");
        erase(resolve(profile(root, target), "incoming")); writeProfileData(root, target, "delta", new Properties());
        String remove = j.getProperty("deleteAfter", "");
        if (!remove.isEmpty() && !remove.equals(target) && !remove.equals("default")) erase(profile(root, remove));
        erase(transaction);
    }

    public static void copy(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing or unsafe source: " + source);
        if (Files.isSymbolicLink(target)) throw new IOException("Unsafe destination");
        Files.createDirectories(target.getParent()); Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static void erase(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(directory)) throw new IOException("Refusing linked directory");
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(p)) throw new IOException("Refusing linked file");
                Files.delete(p);
            }
        }
    }
}
