package net.ptah.synchronize.mixin.client;

//? if >=1.20.2 && <1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.fabricmc.fabric.impl.registry.sync.RemapException;
import net.fabricmc.fabric.impl.registry.sync.RemappableRegistry;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.client.compat.RegistryMismatchHandler;
import org.spongepowered.asm.mixin.Mixin;

 







@Mixin(value = RegistrySyncManager.class, remap = false)
public abstract class RegistrySyncManagerMixin {
    @WrapMethod(method = "apply")
    private static void synchronize$suppressRemoteRemapFailure(Map<Identifier, Object2IntMap<Identifier>> map,
                                                               RemappableRegistry.RemapMode mode,
                                                               Operation<Void> original) throws RemapException {
        if (mode != RemappableRegistry.RemapMode.REMOTE) {
            original.call(map, mode);
            return;
        }
        RegistryMismatchHandler.callSuppressing(() -> original.call(map, mode));
    }
}
*///?}
