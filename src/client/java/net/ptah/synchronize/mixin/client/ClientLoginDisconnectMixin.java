package net.ptah.synchronize.mixin.client;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.ptah.synchronize.client.ClientSyncHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 
@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginDisconnectMixin {
    @Inject(method = "onDisconnected", at = @At("TAIL"))
    private void synchronize$keepVerificationScreen(CallbackInfo ci) {
        ClientSyncHandler.afterLoginDisconnected((ClientLoginNetworkHandler) (Object) this);
    }
}
