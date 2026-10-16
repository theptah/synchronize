package net.ptah.synchronize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ptah.synchronize.server.ServerSyncHandler;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin {

	 
	 
	@WrapOperation(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerLoginNetworkHandler;disconnect(Lnet/minecraft/text/Text;)V"
		)
	)
	private void synchronize$suppressSlowLoginDisconnect(ServerLoginNetworkHandler self, Text reason, Operation<Void> original) {
		if (ServerSyncHandler.isSyncInProgress(self)) {
			return;
		}
		original.call(self, reason);
	}
}
