package net.ptah.synchronize.mixin.client;

//? if >=1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.fabric.impl.client.registry.sync.ClientRegistrySyncHandler;
import net.ptah.synchronize.client.compat.RegistryMismatchHandler;
import org.spongepowered.asm.mixin.Mixin;

 






@Mixin(value = ClientRegistrySyncHandler.class, remap = false)
public abstract class ClientRegistrySyncHandlerMixin {
    @WrapMethod(method = "apply")
    //? if >=1.21.11 {
    /^private static void synchronize$suppressRemapFailure(net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload data,
                                                         Operation<Void> original) {
    ^///?} else
    private static void synchronize$suppressRemapFailure(net.fabricmc.fabric.impl.registry.sync.packet.RegistryPacketHandler.SyncedPacketData data, Operation<Void> original) {
        RegistryMismatchHandler.callSuppressing(() -> original.call(data));
    }
}
*///?}
