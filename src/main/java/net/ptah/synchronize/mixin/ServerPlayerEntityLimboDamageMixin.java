package net.ptah.synchronize.mixin;

import net.ptah.synchronize.server.LimboManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityLimboDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
     
    //? if >=1.21.2 {
    /*private void synchronize$blockDamageForLimbo(net.minecraft.server.world.ServerWorld world, DamageSource source, float amount,
                                                 CallbackInfoReturnable<Boolean> cir) {
    *///?} else
    private void synchronize$blockDamageForLimbo(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (LimboManager.isLimbo((ServerPlayerEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}