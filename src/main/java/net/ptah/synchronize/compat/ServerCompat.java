package net.ptah.synchronize.compat;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;


import java.util.function.Supplier;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

 


public final class ServerCompat {
    private ServerCompat() {
    }

     
    public static ServerWorld world(ServerPlayerEntity player) {
        //? if >=1.21.9 {
        /*return player.getEntityWorld();
        *///?} elif >=1.21.6 {
        /*return player.getWorld();
        *///?} elif >=1.20 {
        return player.getServerWorld();
        //?} else
        /*return player.getWorld();*/
    }

     
    public static void feedback(ServerCommandSource source, Supplier<Text> feedback, boolean broadcastToOps) {
        //? if >=1.20 {
        source.sendFeedback(feedback, broadcastToOps);
        //?} else
        /*source.sendFeedback(feedback.get(), broadcastToOps);*/
    }

     
    public static MinecraftServer server(ServerPlayerEntity player) {
        //? if >=1.21.9 {
        /*return world(player).getServer();
        *///?} else
        return player.getServer();
    }

     
    public static String profileName(ServerPlayerEntity player) {
        //? if >=1.21.9 {
        /*return player.getGameProfile().name();
        *///?} else
        return player.getGameProfile().getName();
    }

     
    public static int gameModeId(GameMode mode) {
        //? if >=1.21.5 {
        /*return mode.getIndex();
        *///?} else
        return mode.getId();
    }

     
    public static <T> Registry<T> registry(DynamicRegistryManager manager, RegistryKey<? extends Registry<? extends T>> key) {
        //? if >=1.21.2 {
        /*return manager.getOrThrow(key);
        *///?} else
        return manager.get(key);
    }

     
    public static <T> RegistryEntry<T> entry(Registry<T> registry, RegistryKey<T> key) {
        //? if >=1.21.2 {
        /*return registry.getOrThrow(key);
        *///?} else
        return registry.entryOf(key);
    }

     
    public static <T> void addEntry(SimpleRegistry<T> target, Registry<T> source, RegistryKey<T> key, T value) {
        //? if >=1.20.5 {
        /*target.add(key, value, net.minecraft.registry.entry.RegistryEntryInfo.DEFAULT);
        *///?} else
        target.add(key, value, source.getEntryLifecycle(value));
    }
}
