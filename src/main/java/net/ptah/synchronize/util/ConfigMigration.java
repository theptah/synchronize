package net.ptah.synchronize.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;

public final class ConfigMigration {
    private ConfigMigration() {
    }

    public static void run() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path modDir = configDir.resolve(Synchronize.MOD_ID);
        move(configDir.resolve(Synchronize.MOD_ID + ".json"), modDir.resolve("config.json"));
        move(modDir.resolve("file_tree.json"), modDir.resolve(FileRegistry.SYNC_LIST_FILE_NAME));
        renameHandshakeKey(modDir.resolve("config.json"));
    }

    private static void renameHandshakeKey(Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("handshakeCode")) {
                return;
            }
            JsonElement legacy = root.remove("handshakeCode");
            if (!root.has("handshake") || root.get("handshake").isJsonNull()
                    || root.get("handshake").getAsString().trim().isEmpty()) {
                root.add("handshake", legacy);
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            Files.writeString(configFile, gson.toJson(root), StandardCharsets.UTF_8);
            Synchronize.LOGGER.info("[Synchronize] Renamed config key handshakeCode to handshake");
        } catch (Exception e) {
            Synchronize.LOGGER.error("[Synchronize] Failed to migrate handshakeCode in {}", configFile, e);
        }
    }

    private static void move(Path legacy, Path target) {
        if (!Files.isRegularFile(legacy) || Files.exists(target)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFailure) {
            try {
                Files.move(legacy, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Synchronize.LOGGER.error("[Synchronize] Failed to migrate {} to {}", legacy, target, e);
                return;
            }
        }
        Synchronize.LOGGER.info("[Synchronize] Migrated {} to {}", legacy, target);
    }
}
