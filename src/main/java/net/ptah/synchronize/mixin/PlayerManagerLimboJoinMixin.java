package net.ptah.synchronize.mixin;

import net.ptah.synchronize.server.LimboManager;
import net.ptah.synchronize.util.NetworkUtil;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.20.2 {
import net.ptah.synchronize.server.VanillaRegistryFilter;
import net.minecraft.registry.DynamicRegistryManager;
//?} else {
/*import net.minecraft.network.packet.s2c.play.CommonPlayerSpawnInfo;
import net.minecraft.server.network.ConnectedClientData;
*///?}
//? if <1.21.9 {
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?} else {
/*import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.ptah.synchronize.compat.ServerCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}

 



@Mixin(PlayerManager.class)
public abstract class PlayerManagerLimboJoinMixin {

    @Unique
    private static final ThreadLocal<Boolean> synchronize$pendingLimbo =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    //? if >=1.21.9 {
    /*@Shadow @Final private MinecraftServer server;
    *///?}

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    //? if >=1.20.2 {
    /*private void synchronize$computePendingLimboFlag(ClientConnection connection, ServerPlayerEntity player,
                                                     ConnectedClientData clientData, CallbackInfo ci) {
    *///?} else
    private void synchronize$computePendingLimboFlag(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        String ip = NetworkUtil.getIp(connection.getAddress());
        boolean pending = LimboManager.isPendingLimbo(ip);
        synchronize$pendingLimbo.set(pending);

        //? if >=1.21.9 {
        /* 
         
        if (pending) {
            ServerWorld current = ServerCompat.world(player);
            LimboManager.ORIGINAL_DIMENSION.set(current.getRegistryKey());
            ServerWorld overworld = this.server.getOverworld();
            if (overworld != null && current != overworld) {
                player.setServerWorld(overworld);
            }
        }
        *///?}
    }

    //? if <1.21.6 {
    @ModifyVariable(method = "onPlayerConnect", at = @At("STORE"), ordinal = 0)
    private RegistryKey<World> synchronize$forceOverworldForPendingLimbo(RegistryKey<World> registryKey) {
        if (synchronize$pendingLimbo.get()) {
            LimboManager.ORIGINAL_DIMENSION.set(registryKey);
            return World.OVERWORLD;
        }
        return registryKey;
    }
    //?} elif <1.21.9 {
    /* 
     
    @ModifyArg(method = "onPlayerConnect",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getWorld(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/server/world/ServerWorld;"))
    private RegistryKey<World> synchronize$forceOverworldForPendingLimbo(RegistryKey<World> registryKey) {
        if (synchronize$pendingLimbo.get()) {
            LimboManager.ORIGINAL_DIMENSION.set(registryKey);
            return World.OVERWORLD;
        }
        return registryKey;
    }
    *///?}

     
    //? if <1.19.3 {
    /*@Unique private static final String GAME_JOIN_INIT = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;<init>(IZLnet/minecraft/world/GameMode;Lnet/minecraft/world/GameMode;Ljava/util/Set;Lnet/minecraft/util/registry/DynamicRegistryManager$Immutable;Lnet/minecraft/util/registry/RegistryKey;Lnet/minecraft/util/registry/RegistryKey;JIIIZZZZLjava/util/Optional;)V";
    *///?} elif <1.20 {
    /*@Unique private static final String GAME_JOIN_INIT = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;<init>(IZLnet/minecraft/world/GameMode;Lnet/minecraft/world/GameMode;Ljava/util/Set;Lnet/minecraft/registry/DynamicRegistryManager$Immutable;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/RegistryKey;JIIIZZZZLjava/util/Optional;)V";
    *///?} elif <1.20.2 {
    @Unique private static final String GAME_JOIN_INIT = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;<init>(IZLnet/minecraft/world/GameMode;Lnet/minecraft/world/GameMode;Ljava/util/Set;Lnet/minecraft/registry/DynamicRegistryManager$Immutable;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/RegistryKey;JIIIZZZZLjava/util/Optional;I)V";
    //?} elif <1.20.5 {
    /*@Unique private static final String GAME_JOIN_INIT = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;<init>(IZLjava/util/Set;IIIZZZLnet/minecraft/network/packet/s2c/play/CommonPlayerSpawnInfo;)V";
    *///?} else {
    /*@Unique private static final String GAME_JOIN_INIT = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;<init>(IZLjava/util/Set;IIIZZZLnet/minecraft/network/packet/s2c/play/CommonPlayerSpawnInfo;Z)V";
    *///?}

    //? if <1.20.2 {
    @ModifyArg(method = "onPlayerConnect",
        at = @At(value = "INVOKE", target = GAME_JOIN_INIT),
        index = 2)
    private GameMode synchronize$forceSpectatorForPendingLimbo(GameMode original) {
        return synchronize$pendingLimbo.get() ? GameMode.SPECTATOR : original;
    }

    @ModifyArg(method = "onPlayerConnect",
        at = @At(value = "INVOKE", target = GAME_JOIN_INIT),
        index = 5)
    private DynamicRegistryManager.Immutable synchronize$maybeVanillaSafeRegistries(DynamicRegistryManager.Immutable original) {
        return synchronize$pendingLimbo.get() ? VanillaRegistryFilter.getOrBuild(original) : original;
    }
    //?} elif <1.21.2 {
    /* 
     
    @ModifyArg(method = "onPlayerConnect",
        at = @At(value = "INVOKE", target = GAME_JOIN_INIT),
        index = 9)
    private CommonPlayerSpawnInfo synchronize$forceSpectatorForPendingLimbo(CommonPlayerSpawnInfo info) {
        if (!synchronize$pendingLimbo.get()) {
            return info;
        }
        return new CommonPlayerSpawnInfo(info.dimensionType(), info.dimension(), info.seed(), GameMode.SPECTATOR,
            info.prevGameMode(), info.isDebug(), info.isFlat(), info.lastDeathLocation(), info.portalCooldown());
    }
    *///?} else {
    /* 
    @ModifyArg(method = "onPlayerConnect",
        at = @At(value = "INVOKE", target = GAME_JOIN_INIT),
        index = 9)
    private CommonPlayerSpawnInfo synchronize$forceSpectatorForPendingLimbo(CommonPlayerSpawnInfo info) {
        if (!synchronize$pendingLimbo.get()) {
            return info;
        }
         
        //? if >=1.21.5 {
        /^GameMode previous = info.lastGameMode();
        ^///?} else
        GameMode previous = info.prevGameMode();
        return new CommonPlayerSpawnInfo(info.dimensionType(), info.dimension(), info.seed(), GameMode.SPECTATOR,
            previous, info.isDebug(), info.isFlat(), info.lastDeathLocation(), info.portalCooldown(), info.seaLevel());
    }
    *///?}

    @Inject(method = "onPlayerConnect", at = @At("RETURN"))
    //? if >=1.20.2 {
    /*private void synchronize$clearPendingLimboFlag(ClientConnection connection, ServerPlayerEntity player,
                                                   ConnectedClientData clientData, CallbackInfo ci) {
    *///?} else
    private void synchronize$clearPendingLimboFlag(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        synchronize$pendingLimbo.remove();
        LimboManager.ORIGINAL_DIMENSION.remove();
    }
}
