package net.ptah.synchronize.mixin;

import net.ptah.synchronize.server.LimboManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityLimboMovementMixin {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void synchronize$blockMoveForLimbo(MovementType type, Vec3d movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayerEntity player && LimboManager.isLimbo(player)) {
            ci.cancel();
        }
    }
}
