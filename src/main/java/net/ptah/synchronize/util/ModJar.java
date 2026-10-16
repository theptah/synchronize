package net.ptah.synchronize.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;

public final class ModJar {
    private static volatile boolean hashComputed;
    private static volatile String cachedHash = "";

    private ModJar() {
    }

    public static Optional<Path> path() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Synchronize.MOD_ID);
        if (container.isEmpty()) {
            return Optional.empty();
        }
        List<Path> paths = container.get().getOrigin().getPaths();
        if (paths.isEmpty()) {
            return Optional.empty();
        }
        Path path = paths.get(0);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(Synchronize.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static String sha256() {
        if (!hashComputed) {
            synchronized (ModJar.class) {
                if (!hashComputed) {
                    String hash = "";
                    Optional<Path> jar = path();
                    if (jar.isPresent()) {
                        try {
                            hash = FileRegistry.calculateHash(jar.get()).toLowerCase(Locale.ROOT);
                        } catch (Exception e) {
                            Synchronize.LOGGER.error("[Synchronize] Could not hash the Synchronize jar", e);
                        }
                    }
                    cachedHash = hash;
                    hashComputed = true;
                }
            }
        }
        return cachedHash;
    }

    public static Optional<String> relativeToGameDir() {
        Optional<Path> jar = path();
        if (jar.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            Path abs = jar.get().toAbsolutePath().normalize();
            if (!abs.startsWith(gameDir)) {
                return Optional.empty();
            }
            String rel = gameDir.relativize(abs).toString().replace('\\', '/');
            return PathSecurity.isSafeRelativePath(rel) ? Optional.of(rel) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean isSynchronizeJar(Path file) {
        if (file == null) {
            return false;
        }
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            Optional<Path> self = path();
            if (self.isPresent()) {
                try {
                    if (Files.isSameFile(self.get(), file)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
            return declaresSynchronizeModId(file);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSelfHash(String hash) {
        String selfHash = sha256();
        return hash != null && !selfHash.isEmpty() && selfHash.equalsIgnoreCase(hash);
    }

    private static boolean declaresSynchronizeModId(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null || entry.getSize() > 1024 * 1024) {
                return false;
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    JsonElement id = obj.get("id");
                    return id != null && id.isJsonPrimitive() && Synchronize.MOD_ID.equals(id.getAsString());
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
