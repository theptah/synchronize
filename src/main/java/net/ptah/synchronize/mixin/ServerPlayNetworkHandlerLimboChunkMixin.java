package net.ptah.synchronize.mixin;

import net.ptah.synchronize.server.LimboManager;
import net.ptah.synchronize.server.LimboPacketFilter;
//? if >=1.19.4 {
import net.minecraft.network.packet.Packet;
//?} else
/*import net.minecraft.network.Packet;*/
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.20.2 {
import net.minecraft.network.PacketCallbacks;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Shadow;
//?} else {
/*import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?}
//? if >=1.21.6 {
/*import io.netty.channel.ChannelFutureListener;
*///?} elif >=1.20.2 {
/*import net.minecraft.network.PacketCallbacks;
*///?}

 






//? if <1.20.2 {
@Mixin(ServerPlayNetworkHandler.class)
//?} else
/*@Mixin(ServerCommonNetworkHandler.class)*/
public abstract class ServerPlayNetworkHandlerLimboChunkMixin {
    //? if <1.20.2 {
    @Shadow public ServerPlayerEntity player;

    //? if >=1.19.4 {
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "sendPacket(Lnet/minecraft/network/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At("HEAD"), cancellable = true)
    *///?}
    private void synchronize$allowlistForLimbo(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (LimboManager.shouldSuppressWorldData(this.player) && !LimboPacketFilter.isAllowedInLimbo(this.player, packet)) {
            ci.cancel();
        }
    }
    //?} else {
    /*@ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true)
    private Packet<?> synchronize$filterConfigurationPacket(Packet<?> packet) {
        if (packet != null && (Object) this instanceof ServerConfigurationNetworkHandler handler
                && LimboPacketFilter.isPendingLimbo(handler.connection)) {
            Packet<?> filtered = LimboPacketFilter.filterConfigurationPacket(packet);
            return filtered != null ? filtered : packet;
        }
        return packet;
    }

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.6 {
    /^private void synchronize$allowlistForLimbo(Packet<?> packet, ChannelFutureListener callbacks, CallbackInfo ci) {
    ^///?} else
    private void synchronize$allowlistForLimbo(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        Object self = this;
        if (self instanceof ServerPlayNetworkHandler handler) {
            if (LimboManager.shouldSuppressWorldData(handler.player)
                    && !LimboPacketFilter.isAllowedInLimbo(handler.player, packet)) {
                ci.cancel();
            }
        } else if (self instanceof ServerConfigurationNetworkHandler handler
                && LimboPacketFilter.isPendingLimbo(handler.connection)
                && LimboPacketFilter.filterConfigurationPacket(packet) == null) {
            ci.cancel();
        }
    }
    *///?}
}
