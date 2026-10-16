package net.ptah.synchronize.mixin.client;

//? if <1.20.2 {
import net.ptah.synchronize.Synchronize;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.impl.client.registry.sync.FabricRegistryClientInit;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistryPacketHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 
 
@Mixin(value = FabricRegistryClientInit.class, remap = false)
public class FabricRegistryClientInitMixin {
	@Inject(method = "registerSyncPacketReceiver", at = @At("HEAD"), cancellable = true)
	private void synchronize$suppressRegistryDisconnect(RegistryPacketHandler packetHandler, CallbackInfo ci) {
		ClientPlayNetworking.registerGlobalReceiver(packetHandler.getPacketId(), (client, handler, buf, responseSender) ->
			RegistrySyncManager.receivePacket(client, packetHandler, buf, RegistrySyncManager.DEBUG || !client.isInSingleplayer(), e -> {
				Synchronize.LOGGER.warn("[Synchronize] Fabric Registry Mismatch Detected!");
				Synchronize.LOGGER.warn("[Synchronize] Suppressing vanilla disconnect to allow file downloads.", e);
			})
		);
		ci.cancel();
	}
}
//?}
