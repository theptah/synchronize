package net.ptah.synchronize.client.compat;

import net.ptah.synchronize.Synchronize;

 



public final class RegistryMismatchHandler {
    private RegistryMismatchHandler() {
    }

    public static void callSuppressing(Runnable remap) {
        try {
            remap.run();
        } catch (Throwable t) {
            if (!isRemapException(t)) {
                throw sneaky(t);
            }
            Synchronize.LOGGER.warn("[Synchronize] Fabric Registry Mismatch Detected!");
            Synchronize.LOGGER.warn("[Synchronize] Suppressing vanilla disconnect to allow file downloads.", t);
        }
    }

    private static boolean isRemapException(Throwable t) {
         
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur.getClass().getName().equals("net.fabricmc.fabric.impl.registry.sync.RemapException")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable t) throws E {
        throw (E) t;
    }
}
