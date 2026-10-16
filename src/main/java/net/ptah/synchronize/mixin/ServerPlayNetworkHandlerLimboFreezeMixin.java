package net.ptah.synchronize.mixin;

import net.ptah.synchronize.server.LimboManager;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerLimboFreezeMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void synchronize$freezePlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (LimboManager.isLimbo(this.player)) {
            LimboManager.sendClientZeroRotation(this.player);
            ci.cancel();
        }
    }

    @Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
    private void synchronize$freezeVehicleMove(VehicleMoveC2SPacket packet, CallbackInfo ci) {
        if (LimboManager.isLimbo(this.player)) {
            LimboManager.sendClientZeroRotation(this.player);
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void synchronize$freezePlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (LimboManager.isLimbo(this.player)) {
            ci.cancel();
        }
    }
}