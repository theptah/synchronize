package net.ptah.synchronize.mixin;

import net.ptah.synchronize.server.LimboManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityLimboCollisionMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void synchronize$blockPickupForLimbo(PlayerEntity player, CallbackInfo ci) {
        if (LimboManager.isLimbo(player)) {
            ci.cancel();
        }
    }
}
