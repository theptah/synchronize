package net.ptah.synchronize.client;

import net.ptah.synchronize.util.SyncLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class SynchronizeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientSyncHandler.init();
		new Thread(net.ptah.synchronize.util.ModJar::sha256, "Synchronize-SelfHash").start();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SyncLogger.shutdown());
	}
}
