package net.ptah.synchronize.server;

//? if >=1.19.4 {
import net.minecraft.network.packet.Packet;
//?} else
/*import net.minecraft.network.Packet;*/
import net.minecraft.network.packet.s2c.play.ChunkLoadDistanceS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
//? if >=1.19.3 && <1.20.2
import net.minecraft.network.packet.s2c.play.FeaturesS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
//? if >=1.20.2 {
/*import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
*///?} else
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
//? if >=1.20.2 {
/*import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.common.SynchronizeTagsS2CPacket;
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.TagPacketSerializer;
import net.ptah.synchronize.util.NetworkUtil;
*///?}

 
public final class LimboPacketFilter {
    private LimboPacketFilter() {
    }

     
    public static boolean isAllowedInLimbo(ServerPlayerEntity player, Packet<?> packet) {
        if (packet instanceof EntityTrackerUpdateS2CPacket trackerUpdate) {
            return trackerUpdate.id() == player.getId();
        }
        return packet instanceof GameJoinS2CPacket
            //? if >=1.19.3 && <1.20.2
            || packet instanceof FeaturesS2CPacket
            || packet instanceof PlayerAbilitiesS2CPacket
            || packet instanceof UpdateSelectedSlotS2CPacket
            || packet instanceof DifficultyS2CPacket
            || packet instanceof PlayerPositionLookS2CPacket
            || packet instanceof KeepAliveS2CPacket
            || packet instanceof ChunkRenderDistanceCenterS2CPacket
            || packet instanceof ChunkLoadDistanceS2CPacket
            //? if >=1.20.2 {
            /*|| packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
            *///?} else
            || packet instanceof net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
    }

    //? if >=1.20.2 {
    /*public static boolean isPendingLimbo(ClientConnection connection) {
        return LimboManager.isPendingLimbo(NetworkUtil.getIp(connection.getAddress()));
    }

    private static boolean isVanilla(RegistryKey<?> key) {
        return "minecraft".equals(key.getValue().getNamespace());
    }

     




    public static Packet<?> filterConfigurationPacket(Packet<?> packet) {
        if (packet instanceof DynamicRegistriesS2CPacket registries) {
            //? if >=1.20.5 {
            /^if (!isVanilla(registries.registry())) {
                return null;
            }
            return new DynamicRegistriesS2CPacket(registries.registry(), registries.entries().stream()
                .filter(entry -> "minecraft".equals(entry.id().getNamespace()))
                .toList());
            ^///?} else
            return new DynamicRegistriesS2CPacket(VanillaRegistryFilter.getOrBuild(registries.registryManager()));
        }
        if (packet instanceof SynchronizeTagsS2CPacket tags) {
            Map<RegistryKey<? extends Registry<?>>, TagPacketSerializer.Serialized> vanilla = new HashMap<>();
            tags.getGroups().forEach((key, value) -> {
                if (isVanilla(key)) {
                    vanilla.put(key, value);
                }
            });
            return new SynchronizeTagsS2CPacket(vanilla);
        }
        return packet;
    }
    *///?}
}
