package net.ptah.synchronize.server;

import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;

public final class SyncServerLifecycle {
    private static MinecraftServer currentServer;
    private static boolean servicesRunning;

    private SyncServerLifecycle() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            synchronized (SyncServerLifecycle.class) {
                currentServer = server;
                servicesRunning = false;
            }
            update(server);
        });
        ServerTickEvents.START_SERVER_TICK.register(SyncServerLifecycle::update);
        ServerLifecycleEvents.SERVER_STOPPING.register(SyncServerLifecycle::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(SyncServerLifecycle::stop);
    }

    private static boolean allowsSync(MinecraftServer server) {
        return server.isDedicated() || server.isRemote();
    }

    private static synchronized void update(MinecraftServer server) {
        if (server != currentServer) {
            return;
        }
        if (!allowsSync(server)) {
            if (servicesRunning) {
                stopServices(server);
            }
            return;
        }
        if (servicesRunning) {
            return;
        }
        Synchronize.LOGGER.info("[Synchronize] Enabling synchronization for dedicated/LAN server.");
        Synchronize.ensureHandshake();
        FileRegistry.autoRefresh();
        ServerSyncHandler.start();
         
        ExternalFileServer.start();
         
        OfficialReleaseInfo.refreshAsync();
        servicesRunning = true;
    }

    public static synchronized boolean shouldSync(MinecraftServer server, ServerLoginNetworkHandler handler) {
        if (server != currentServer || handler.connection.isLocal() || !allowsSync(server)) {
            return false;
        }
        update(server);
        return servicesRunning;
    }

    private static synchronized void stop(MinecraftServer server) {
        if (server != currentServer) {
            return;
        }
        currentServer = null;
        stopServices(server);
    }

    private static void stopServices(MinecraftServer server) {
        servicesRunning = false;
        ServerSyncHandler.shutdown();
        ExternalFileServer.stop();
         
         
        LimboManager.shutdown(server);
        VanillaRegistryFilter.reset();
        Synchronize.LOGGER.info("[Synchronize] Synchronization services stopped; session state cleared.");
    }
}
