package net.ptah.synchronize.client;

import net.ptah.synchronize.util.SyncLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class SynchronizeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		net.ptah.synchronize.client.profile.ProfileManager.initialize();
		ClientSyncHandler.init();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(net.ptah.synchronize.client.profile.ProfileManager::tick);
		new Thread(net.ptah.synchronize.util.ModJar::sha256, "Synchronize-SelfHash").start();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SyncLogger.shutdown());
	}
}
