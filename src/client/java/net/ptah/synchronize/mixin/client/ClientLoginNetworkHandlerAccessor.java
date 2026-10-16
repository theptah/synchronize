package net.ptah.synchronize.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLoginNetworkHandler.class)
public interface ClientLoginNetworkHandlerAccessor {
     
    //? if >=1.19.3 {
    @Accessor("serverInfo")
    ServerInfo synchronize$getServerInfo();
    //?}

    @Accessor("parentScreen")
    Screen synchronize$getParentScreen();
}
