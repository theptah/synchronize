package net.ptah.synchronize;

import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.config.ModConfig;
import net.ptah.synchronize.server.LimboManager;
import net.ptah.synchronize.server.ModCommands;
import net.ptah.synchronize.server.ServerSyncHandler;
import net.ptah.synchronize.server.SyncServerLifecycle;
import net.ptah.synchronize.util.ConfigMigration;
import net.ptah.synchronize.util.HandshakeCrypto;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Synchronize implements ModInitializer {
    public static final String MOD_ID = "synchronize";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;

    @Override
    public void onInitialize() {
        ConfigMigration.run();
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        LimboManager.init();
        FileRegistry.load();
        ServerSyncHandler.init();
        ModCommands.register();

        SyncServerLifecycle.init();
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static void updateConfig(ModConfig newConfig) {
        config = newConfig;
    }

    public static synchronized void ensureHandshake() {
        ModConfig current = config;
        if (current == null || HandshakeCrypto.isUsableCode(current.handshake)) {
            return;
        }
        current.handshake = HandshakeCrypto.generateCode();
        try {
            AutoConfig.getConfigHolder(ModConfig.class).save();
            LOGGER.info("[Synchronize] Generated a new handshake code and saved it to the config.");
        } catch (Exception e) {
            LOGGER.error("[Synchronize] Failed to save the generated handshake code", e);
        }
    }
}