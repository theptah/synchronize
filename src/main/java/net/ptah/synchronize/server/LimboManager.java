package net.ptah.synchronize.server;

import net.ptah.synchronize.util.Translations;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.compat.ServerCompat;
import net.ptah.synchronize.util.NetworkUtil;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class LimboManager {
    private static final double POSITION_DRIFT_TOLERANCE_SQUARED = 0.0001;
    private static final Map<String, Long> PENDING_BY_IP = new ConcurrentHashMap<>();
    private static final long PENDING_TTL_MILLIS = 30_000L;
    private static final Map<UUID, LimboEntry> ACTIVE_LIMBO = new ConcurrentHashMap<>();
    public static final ThreadLocal<RegistryKey<World>> ORIGINAL_DIMENSION = new ThreadLocal<>();

    private LimboManager() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            String ip = NetworkUtil.getIp(player.networkHandler.connection.getAddress());
            Long pendingSince = PENDING_BY_IP.remove(ip);
            if (pendingSince == null) {
                return;
            }
            if (System.currentTimeMillis() - pendingSince > PENDING_TTL_MILLIS) {
                return;
            }
            enterLimbo(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LimboEntry entry = ACTIVE_LIMBO.remove(handler.player.getUuid());
            if (entry != null) {
                restoreOriginalState(handler.player, entry);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(LimboManager::tick);

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
            isLimbo(player) ? ActionResult.FAIL : ActionResult.PASS);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
            isLimbo(player) ? ActionResult.FAIL : ActionResult.PASS);

         
        //? if >=1.21.2 {
        /*UseItemCallback.EVENT.register((player, world, hand) ->
            isLimbo(player) ? ActionResult.FAIL : ActionResult.PASS);
        *///?} else {
        UseItemCallback.EVENT.register((player, world, hand) ->
            isLimbo(player)
                ? net.minecraft.util.TypedActionResult.fail(player.getStackInHand(hand))
                : net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand)));
        //?}

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            isLimbo(player) ? ActionResult.FAIL : ActionResult.PASS);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            isLimbo(player) ? ActionResult.FAIL : ActionResult.PASS);

        PlayerBlockBreakEvents.BEFORE.register(
            (world, player, pos, state, blockEntity) -> !isLimbo(player)
        );

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            (message, sender, params) -> !isLimbo(sender)
        );

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register(
            (message, source, params) ->
                source.getPlayer() == null || !isLimbo(source.getPlayer())
        );
    }

    public static void shutdown(MinecraftServer server) {
        if (server.getPlayerManager() != null) {
            for (Map.Entry<UUID, LimboEntry> entry : ACTIVE_LIMBO.entrySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    restoreOriginalState(player, entry.getValue());
                }
            }
        }
        PENDING_BY_IP.clear();
        ACTIVE_LIMBO.clear();
        ORIGINAL_DIMENSION.remove();
    }

    public static void markPendingLimbo(String ip) {
        long t = System.currentTimeMillis();
        PENDING_BY_IP.put(ip, t);
    }

    private static void enterLimbo(ServerPlayerEntity player) {
        ServerWorld currentWorld = ServerCompat.world(player);
        double joinX = player.getX();
        double joinY = player.getY();
        double joinZ = player.getZ();
        float joinYaw = player.getYaw();
        float joinPitch = player.getPitch();

        RegistryKey<World> origWorldKey = ORIGINAL_DIMENSION.get();
        ORIGINAL_DIMENSION.remove();

        ServerWorld origWorld =
            origWorldKey != null
                ? ServerCompat.server(player).getWorld(origWorldKey)
                : null;
        if (origWorld == null) {
            origWorld = currentWorld;
        }

        var config = Synchronize.getConfig();
        int durationSeconds =
            config != null
                ? Math.max(1, config.limboDurationSeconds)
                : 60;

        long deadline =
            System.currentTimeMillis() + durationSeconds * 1000L;

        boolean wasInvisible = player.isInvisible();

        ACTIVE_LIMBO.put(
            player.getUuid(),
            new LimboEntry(
                currentWorld,
                deadline,
                joinX,
                joinY,
                joinZ,
                joinYaw,
                joinPitch,
                wasInvisible
            )
        );

        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setNoGravity(true);
        player.setVelocity(Vec3d.ZERO);
        applySafetyNet(player);

        PlayerAbilities limboAbilities = new PlayerAbilities();
        limboAbilities.invulnerable = true;
        limboAbilities.flying = true;
        limboAbilities.allowFlying = true;
        limboAbilities.setFlySpeed(0.0F);
        limboAbilities.setWalkSpeed(0.0F);

        player.networkHandler.sendPacket(
            new PlayerAbilitiesS2CPacket(limboAbilities)
        );

        player.networkHandler.connection.send(
            new GameStateChangeS2CPacket(
                GameStateChangeS2CPacket.GAME_MODE_CHANGED,
                (float) ServerCompat.gameModeId(net.minecraft.world.GameMode.SPECTATOR)
            ),
            null
        );

        LimboBootstrapChunk.send(player);

        LimboSpawnPositionPacket.send(
            player,
            joinX,
            joinY,
            joinZ,
            0.0F
        );

        sendClientZeroRotation(player);

        if (config != null) {
            Text message =
                ServerSyncHandler.buildModRequiredMessage(config);
            player.networkHandler.connection.send(
                new GameMessageS2CPacket(message, false),
                null
            );
        } else {
            player.networkHandler.connection.send(
                new GameMessageS2CPacket(
                    Translations.player("synchronize.limbo.mod_required"),
                    false
                ),
                null
            );
        }

        Synchronize.LOGGER.info(
            "[Synchronize] Player {} joined without the required mod; " +
            "holding in limbo in place for {}s.",
            ServerCompat.profileName(player),
            durationSeconds
        );
    }

    public static void sendClientZeroRotation(ServerPlayerEntity player) {
        LimboEntry entry = ACTIVE_LIMBO.get(player.getUuid());
        if (entry != null) {
            player.networkHandler.requestTeleport(entry.anchorX, entry.anchorY, entry.anchorZ, 0.0F, 0.0F);

            player.setYaw(entry.anchorYaw);
            player.setPitch(entry.anchorPitch);
            player.setPosition(entry.anchorX, entry.anchorY, entry.anchorZ);
        }
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE_LIMBO.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, LimboEntry>> it =
            ACTIVE_LIMBO.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, LimboEntry> mapEntry = it.next();
            ServerPlayerEntity player =
                server.getPlayerManager().getPlayer(mapEntry.getKey());

            if (player == null) {
                it.remove();
                continue;
            }

            LimboEntry entry = mapEntry.getValue();

            if (now >= entry.deadlineMillis) {
                if (player.networkHandler.connection.isOpen()) {
                    Text kickMessage = Translations.player("synchronize.limbo.session_expired");
                    player.networkHandler.disconnect(kickMessage);
                }
                continue;
            }

            double dx = player.getX() - entry.anchorX;
            double dy = player.getY() - entry.anchorY;
            double dz = player.getZ() - entry.anchorZ;

            if (dx * dx + dy * dy + dz * dz
                    > POSITION_DRIFT_TOLERANCE_SQUARED) {
                Synchronize.LOGGER.warn(
                    "[Synchronize] Limbo player {} drifted from anchor " +
                    "({}, {}, {}) to ({}, {}, {}) - " +
                    "investigate the movement source; NOT correcting with a teleport.",
                    ServerCompat.profileName(player),
                    entry.anchorX,
                    entry.anchorY,
                    entry.anchorZ,
                    player.getX(),
                    player.getY(),
                    player.getZ()
                );
            }

            player.setVelocity(Vec3d.ZERO);

            if (!player.isInvisible()) {
                player.setInvisible(true);
            }

            applySafetyNet(player);
        }
    }

    private static void applySafetyNet(ServerPlayerEntity player) {
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0F);
        player.clearStatusEffects();
        player.fallDistance = 0f;
        player.setFireTicks(0);
        player.extinguish();
    }

    private static void restoreOriginalState(
        ServerPlayerEntity player,
        LimboEntry entry
    ) {
        player.setInvulnerable(false);
        player.setInvisible(entry.wasInvisible);
        player.setNoGravity(false);

        player.setYaw(entry.anchorYaw);
        player.setPitch(entry.anchorPitch);
        player.setPosition(entry.anchorX, entry.anchorY, entry.anchorZ);
    }

    public static boolean isLimbo(PlayerEntity player) {
        return player instanceof ServerPlayerEntity
            && ACTIVE_LIMBO.containsKey(player.getUuid());
    }

    private static final class LimboEntry {
        final ServerWorld limboWorld;
        final long deadlineMillis;
        final double anchorX;
        final double anchorY;
        final double anchorZ;
        final float anchorYaw;
        final float anchorPitch;
        final boolean wasInvisible;

        LimboEntry(
            ServerWorld limboWorld,
            long deadlineMillis,
            double anchorX,
            double anchorY,
            double anchorZ,
            float anchorYaw,
            float anchorPitch,
            boolean wasInvisible
        ) {
            this.limboWorld = limboWorld;
            this.deadlineMillis = deadlineMillis;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.anchorYaw = anchorYaw;
            this.anchorPitch = anchorPitch;
            this.wasInvisible = wasInvisible;
        }
    }

    public static boolean isPendingLimbo(String ip) {
        Long pendingSince = PENDING_BY_IP.get(ip);
        return pendingSince != null
            && System.currentTimeMillis() - pendingSince <= PENDING_TTL_MILLIS;
    }

    public static boolean shouldSuppressWorldData(
        ServerPlayerEntity player
    ) {
        if (ACTIVE_LIMBO.containsKey(player.getUuid())) {
            return true;
        }
        String ip =
            NetworkUtil.getIp(
                player.networkHandler.connection.getAddress()
            );
        return isPendingLimbo(ip);
    }
}