package net.ptah.synchronize.client.handshake;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.ptah.synchronize.util.HandshakeCrypto;

public final class HandshakeClient {
    private static final long ARM_TIMEOUT_MS = 60_000L;

    private static volatile Context lastContext;
    private static volatile String armedAddress;
    private static volatile long armedAt;

    private HandshakeClient() {
    }

    public record Context(ServerInfo server, Screen returnScreen, boolean serverOffersHandshake) {
        public String code() {
            return codeOf(server);
        }

        public boolean hasCode() {
            return HandshakeCrypto.isUsableCode(code());
        }
    }

    public static String codeOf(ServerInfo server) {
        if (server instanceof HandshakeServerEntry entry) {
            return HandshakeCrypto.normalizeCode(entry.synchronize$getHandshakeCode());
        }
        return "";
    }

    public static void remember(ServerInfo server, Screen returnScreen, boolean serverOffersHandshake) {
        lastContext = new Context(server, returnScreen, serverOffersHandshake);
    }

    public static Context lastContext() {
        return lastContext;
    }

    public static boolean consumeArmed(ServerInfo server) {
        String armed = armedAddress;
        armedAddress = null;
        if (armed == null || server == null || System.currentTimeMillis() - armedAt > ARM_TIMEOUT_MS) {
            return false;
        }
        return armed.equals(key(server));
    }

    public static void connectWithHandshake(MinecraftClient client, Context context) {
        if (context == null || context.server() == null) return;
        armedAddress = key(context.server());
        armedAt = System.currentTimeMillis();
        Screen parent = context.returnScreen();
        if (parent == null) {
            parent = new MultiplayerScreen(new TitleScreen());
        }
         
        //? if >=1.20.5 {
        /*ConnectScreen.connect(parent, client, ServerAddress.parse(context.server().address), context.server(), false, null);
        *///?} elif >=1.20 {
        ConnectScreen.connect(parent, client, ServerAddress.parse(context.server().address), context.server(), false);
        //?} else
        /*ConnectScreen.connect(parent, client, ServerAddress.parse(context.server().address), context.server());*/
    }

    private static String key(ServerInfo server) {
        return server.address == null ? "" : server.address.trim().toLowerCase(Locale.ROOT);
    }
}
