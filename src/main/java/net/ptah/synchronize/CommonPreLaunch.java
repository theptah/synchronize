package net.ptah.synchronize;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class CommonPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if (Boolean.getBoolean("synchronize.mixinAudit")) {
            runMixinAudit();
        }
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            try {
                Class<?> clazz = Class.forName("net.ptah.synchronize.client.SynchronizePreLaunch");
                PreLaunchEntrypoint entrypoint = (PreLaunchEntrypoint) clazz.getDeclaredConstructor().newInstance();
                entrypoint.onPreLaunch();
            } catch (Throwable e) {
                System.err.println("[Synchronize] Failed to invoke client PreLaunch: " + e.getMessage());
            }
        }
    }

     



    private static void runMixinAudit() {
        int code = 0;
        try {
            org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
            System.out.println("[Synchronize] MIXIN AUDIT PASSED");
        } catch (Throwable t) {
            code = 1;
            System.out.println("[Synchronize] MIXIN AUDIT FAILED");
            t.printStackTrace(System.out);
        }
        System.out.flush();
        Runtime.getRuntime().halt(code);
    }
}
