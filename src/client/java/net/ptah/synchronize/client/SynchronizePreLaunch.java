package net.ptah.synchronize.client;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class SynchronizePreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if (PendingUpdateManager.hasPending()) {
            PendingUpdateManager.spawnUpdater(true);
        }
    }
}
