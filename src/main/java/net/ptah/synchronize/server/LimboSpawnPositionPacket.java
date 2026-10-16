package net.ptah.synchronize.server;

import net.ptah.synchronize.compat.ServerCompat;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class LimboSpawnPositionPacket {
    private LimboSpawnPositionPacket() {
    }

    public static void send(ServerPlayerEntity player, double joinX, double joinY, double joinZ, float joinYaw) {
         
        //? if >=1.19.4 {
        BlockPos joinPos = BlockPos.ofFloored(joinX, joinY, joinZ);
        //?} else
        /*BlockPos joinPos = new BlockPos(joinX, joinY, joinZ);*/

         
        //? if >=1.21.9 {
        /*PlayerSpawnPositionS2CPacket packet = new PlayerSpawnPositionS2CPacket(
            net.minecraft.world.WorldProperties.SpawnPoint.create(
                ServerCompat.world(player).getRegistryKey(), joinPos, joinYaw, 0.0F));
        *///?} else
        PlayerSpawnPositionS2CPacket packet = new PlayerSpawnPositionS2CPacket(joinPos, joinYaw);

        player.networkHandler.connection.send(packet, null);
    }
}
