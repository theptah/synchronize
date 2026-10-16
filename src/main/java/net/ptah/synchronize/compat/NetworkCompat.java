package net.ptah.synchronize.compat;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

 



public final class NetworkCompat {
    private NetworkCompat() {
    }

    public static void sendLogin(PacketSender sender, Identifier channel, PacketByteBuf buf) {
        //? if >=1.20.5 {
        /*((net.fabricmc.fabric.api.networking.v1.LoginPacketSender) sender).sendPacket(channel, buf);
        *///?} else
        sender.sendPacket(channel, buf);
    }
}
