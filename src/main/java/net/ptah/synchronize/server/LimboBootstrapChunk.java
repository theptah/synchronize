package net.ptah.synchronize.server;

import java.util.Collections;
import java.util.Iterator;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registry;
//? if >=1.19.3
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.ptah.synchronize.compat.ServerCompat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.tick.ChunkTickScheduler;
import net.minecraft.world.chunk.light.LightingProvider;

public final class LimboBootstrapChunk {
    //? if >=1.19.3 {
    private static final net.minecraft.registry.RegistryKey<Registry<Biome>> BIOME_REGISTRY_KEY = RegistryKeys.BIOME;
    //?} else
    /*private static final net.minecraft.util.registry.RegistryKey<Registry<Biome>> BIOME_REGISTRY_KEY = Registry.BIOME_KEY;*/

    private LimboBootstrapChunk() {
    }

    public static void send(ServerPlayerEntity player) {
        ServerWorld world = ServerCompat.world(player);
        ChunkPos center = player.getChunkPos();
        LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();

        Registry<Biome> biomeRegistry = ServerCompat.registry(world.getRegistryManager(), BIOME_REGISTRY_KEY);
        RegistryEntry<Biome> safeBiome = ServerCompat.entry(biomeRegistry, BiomeKeys.PLAINS);
        IndexedIterable<RegistryEntry<Biome>> safeBiomeIds = createSafeBiomeIds(safeBiome);
        ChunkSection[] sections = createEmptySections(world, safeBiomeIds, safeBiome);

         
         
        //? if >=1.20.3 {
        /*player.networkHandler.connection.send(new net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket(
                net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket.INITIAL_CHUNKS_COMING, 0.0F), null);
        *///?}
        //? if >=1.20.5 {
        /*player.networkHandler.connection.send(net.minecraft.network.packet.s2c.play.StartChunkSendS2CPacket.INSTANCE, null);
        *///?} elif >=1.20.2 {
        /*player.networkHandler.connection.send(new net.minecraft.network.packet.s2c.play.StartChunkSendS2CPacket(), null);
        *///?}

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                WorldChunk emptyChunk = new WorldChunk(
                    world,
                    pos,
                    UpgradeData.NO_UPGRADE_DATA,
                    new ChunkTickScheduler<>(),
                    new ChunkTickScheduler<>(),
                    0L,
                    sections.clone(),
                    null,
                    null
                );

                 
                //? if >=1.20 {
                ChunkDataS2CPacket packet =
                    new ChunkDataS2CPacket(emptyChunk, lightingProvider, null, null);
                //?} else {
                /*ChunkDataS2CPacket packet =
                    new ChunkDataS2CPacket(emptyChunk, lightingProvider, null, null, true);
                *///?}

                player.networkHandler.connection.send(packet, null);
            }
        }
        //? if >=1.20.2 {
        /*player.networkHandler.connection.send(new net.minecraft.network.packet.s2c.play.ChunkSentS2CPacket(9), null);
        *///?}
    }

    private static ChunkSection[] createEmptySections(
        ServerWorld world,
        IndexedIterable<RegistryEntry<Biome>> biomeIds,
        RegistryEntry<Biome> safeBiome
    ) {
        ChunkSection[] sections = new ChunkSection[world.getHeight() / 16];

        for (int i = 0; i < sections.length; i++) {
             
            //? if >=1.21.9 {
            /*PalettedContainer<BlockState> blocks = new PalettedContainer<>(
                Blocks.AIR.getDefaultState(),
                net.minecraft.world.chunk.PaletteProvider.forBlockStates(net.minecraft.block.Block.STATE_IDS)
            );

            PalettedContainer<RegistryEntry<Biome>> biomes = new PalettedContainer<>(
                safeBiome,
                net.minecraft.world.chunk.PaletteProvider.forBiomes(biomeIds)
            );
            *///?} else {
            PalettedContainer<BlockState> blocks = new PalettedContainer<>(
                net.minecraft.block.Block.STATE_IDS,
                Blocks.AIR.getDefaultState(),
                PalettedContainer.PaletteProvider.BLOCK_STATE
            );

            PalettedContainer<RegistryEntry<Biome>> biomes = new PalettedContainer<>(
                biomeIds,
                safeBiome,
                PalettedContainer.PaletteProvider.BIOME
            );
            //?}

             
            //? if >=1.20 {
            sections[i] = new ChunkSection(blocks, biomes);
            //?} else {
            /*sections[i] = new ChunkSection(world.getBottomSectionCoord() + i, blocks, biomes);
            *///?}
        }

        return sections;
    }

    private static IndexedIterable<RegistryEntry<Biome>> createSafeBiomeIds(
        RegistryEntry<Biome> safeBiome
    ) {
        return new IndexedIterable<RegistryEntry<Biome>>() {
            @Override
            public int getRawId(RegistryEntry<Biome> value) {
                return 0;
            }

            @Override
            public RegistryEntry<Biome> get(int index) {
                return index == 0 ? safeBiome : null;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public Iterator<RegistryEntry<Biome>> iterator() {
                return Collections.singletonList(safeBiome).iterator();
            }
        };
    }
}
