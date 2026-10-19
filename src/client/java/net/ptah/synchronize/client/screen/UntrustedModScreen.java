package net.ptah.synchronize.client.screen;

import net.ptah.synchronize.client.compat.GuiCompat;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;
import net.minecraft.util.Util;
import net.ptah.synchronize.client.ModUpdater;
import net.ptah.synchronize.client.handshake.HandshakeClient;
import net.ptah.synchronize.util.ModJar;

public class UntrustedModScreen extends SynchronizeScreen {
    private static final int MAX_TEXT_WIDTH = 340;
    private static final int LINE_HEIGHT = 10;
    private static final int VERSION_LINE_HEIGHT = 11;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_BUTTON_WIDTH = 100;
    private static final int MAX_BUTTON_WIDTH = 150;
    private static final int BUTTON_GAP = 10;
    private static final Identifier WARN_ICON_TEXTURE = Identifiers.mod("textures/gui/warn_icon.png");
    private static final int WARN_ICON_TEXTURE_SIZE = 512;
    private static final int WARN_ICON_SIZE = 28;
    public static final String LEARN_MORE_URL = "https://github.com/theptah/synchronize";

    private final Screen parent;
    private final ModUpdater.Outcome outcome;
    private final String serverVersion;
    private final String serverHash;
    private final String pageUrl;
    private final String error;

    private static final int HANDSHAKE_DELAY_TICKS = 60;

    private final HandshakeClient.Context handshake;
    private final Runnable handshakeAction;
    private ButtonWidget handshakeButton;
    private int handshakeTicks;

    private List<OrderedText> reasonLines = List.of();
    private List<OrderedText> blockedLines = List.of();
    private List<OrderedText> hintLines = List.of();
    private int hintColor;
    private boolean showIcon;
    private int iconY, titleY, reasonY, blockedY, versionY, hintY, buttonY;

    public UntrustedModScreen(Screen parent, ModUpdater.Outcome outcome, String serverVersion, String serverHash, String pageUrl, String error) {
        this(parent, outcome, serverVersion, serverHash, pageUrl, error,
                outcome == ModUpdater.Outcome.CHECK_FAILED ? HandshakeClient.lastContext() : null, null);
    }

    public UntrustedModScreen(Screen parent, ModUpdater.Outcome outcome, String serverVersion, String serverHash, String pageUrl, String error,
            HandshakeClient.Context handshake, Runnable handshakeAction) {
        super(Text.translatable(switch (outcome) {
            case CHECK_FAILED -> "synchronize.untrusted.title_unverified";
            case SERVER_NOT_OFFICIAL -> "synchronize.untrusted.title_server";
            case CLIENT_MODIFIED -> "synchronize.untrusted.title_client";
            default -> "synchronize.untrusted.title";
        }));
        this.parent = parent;
        this.outcome = outcome;
        this.serverVersion = serverVersion;
        this.serverHash = serverHash;
        this.pageUrl = pageUrl;
        this.error = error;
        this.handshake = outcome == ModUpdater.Outcome.CHECK_FAILED ? handshake : null;
        this.handshakeAction = handshakeAction;
    }

    private boolean handshakeReady() {
        return handshake != null && handshake.serverOffersHandshake() && handshake.hasCode();
    }

    @Override
    protected void init() {
        calculateLayout();
        int midX = this.width / 2;
        int bw = buttonWidth();
        int leftX = midX - bw - BUTTON_GAP / 2;
        int rightX = midX + BUTTON_GAP / 2;
        if (outcome == ModUpdater.Outcome.SERVER_NOT_OFFICIAL) {
             
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("synchronize.button.back"), b -> {
                if (this.client != null) this.client.setScreen(parent);
            }).dimensions(midX - bw / 2, buttonY, bw, BUTTON_HEIGHT).build());
            return;
        }
        boolean checkFailed = outcome == ModUpdater.Outcome.CHECK_FAILED;
        if (checkFailed && handshakeReady()) {
            this.handshakeButton = ButtonWidget.builder(handshakeButtonText(), b -> connectWithHandshake())
                    .dimensions(leftX, buttonY, bw, BUTTON_HEIGHT).build();
            this.handshakeButton.active = handshakeTicks >= HANDSHAKE_DELAY_TICKS;
            this.addDrawableChild(this.handshakeButton);
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("synchronize.button.back"), b -> {
                if (this.client != null) this.client.setScreen(parent);
            }).dimensions(rightX, buttonY, bw, BUTTON_HEIGHT).build());
            return;
        }
        String linkUrl = checkFailed ? LEARN_MORE_URL : pageUrl;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable(checkFailed ? "synchronize.button.learn_more" : "synchronize.button.official_page"), b -> {
            if (this.client == null) return;
            this.client.setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) Util.getOperatingSystem().open(linkUrl);
                this.client.setScreen(this);
            }, linkUrl, true));
        }).dimensions(leftX, buttonY, bw, BUTTON_HEIGHT).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("synchronize.button.back"), b -> {
            if (this.client != null) this.client.setScreen(parent);
        }).dimensions(rightX, buttonY, bw, BUTTON_HEIGHT).build());
    }

    private int margin() {
        return this.height < 300 ? 4 : 10;
    }

    private int gap() {
        return this.height < 300 ? 5 : 8;
    }

    private int textWidth() {
        return Math.max(120, Math.min(MAX_TEXT_WIDTH, this.width - margin() * 2 - 8));
    }

    private int buttonWidth() {
        int widest = Math.max(this.textRenderer.getWidth(Text.translatable("synchronize.button.back")),
                this.textRenderer.getWidth(Text.translatable("synchronize.button.official_page")));
        widest = Math.max(widest, this.textRenderer.getWidth(Text.translatable("synchronize.button.learn_more")));
        widest = Math.max(widest, this.textRenderer.getWidth(Text.translatable("synchronize.button.connect_handshake_wait", 3)));
        int width = Math.max(MIN_BUTTON_WIDTH, Math.min(MAX_BUTTON_WIDTH, widest + 20));
        return Math.max(40, Math.min(width, (this.width - margin() * 2 - BUTTON_GAP) / 2));
    }

    private void calculateLayout() {
        int margin = margin();
        int gap = gap();
        int textWidth = textWidth();

        reasonLines = this.textRenderer.wrapLines(reason(), textWidth);
        blockedLines = this.textRenderer.wrapLines(Text.translatable("synchronize.untrusted.blocked").formatted(Formatting.RED), textWidth);
        Text hint = null;
        if (outcome == ModUpdater.Outcome.CLIENT_MODIFIED) {
            hint = Text.translatable("synchronize.untrusted.hint_client");
            hintColor = 0xFFFFFF55;
        } else if (handshake != null && handshake.serverOffersHandshake()) {
            if (handshake.hasCode()) {
                hint = Text.translatable("synchronize.untrusted.handshake_confirm");
                hintColor = 0xFFFFFF55;
            } else {
                hint = Text.translatable("synchronize.untrusted.handshake_hint");
                hintColor = 0xFFAAAAAA;
            }
        }
        hintLines = hint == null ? List.of() : this.textRenderer.wrapLines(hint, textWidth);

        int section = gap - 2;
        int content = LINE_HEIGHT + gap / 2                          
                + reasonLines.size() * LINE_HEIGHT + section         
                + blockedLines.size() * LINE_HEIGHT + section        
                + VERSION_LINE_HEIGHT + LINE_HEIGHT                  
                + (hintLines.isEmpty() ? 0 : section + hintLines.size() * LINE_HEIGHT)
                + gap * 2 + BUTTON_HEIGHT;
        int iconBlock = WARN_ICON_SIZE + gap;
        showIcon = this.height - margin * 2 - content >= iconBlock;
        int total = content + (showIcon ? iconBlock : 0);

        int y = Math.max(margin, (this.height - total) / 2);
        if (showIcon) {
            iconY = y;
            y += iconBlock;
        }
        titleY = y;
        y += LINE_HEIGHT + gap / 2;
        reasonY = y;
        y += reasonLines.size() * LINE_HEIGHT + section;
        blockedY = y;
        y += blockedLines.size() * LINE_HEIGHT + section;
        versionY = y;
        y += VERSION_LINE_HEIGHT + LINE_HEIGHT;
        if (!hintLines.isEmpty()) {
            y += section;
            hintY = y;
            y += hintLines.size() * LINE_HEIGHT;
        }
        y += gap * 2;
        buttonY = Math.min(y, this.height - BUTTON_HEIGHT - 2);
    }

    private Text handshakeButtonText() {
        int remaining = HANDSHAKE_DELAY_TICKS - handshakeTicks;
        if (remaining <= 0) {
            return Text.translatable("synchronize.button.connect_handshake");
        }
        return Text.translatable("synchronize.button.connect_handshake_wait", (remaining + 19) / 20);
    }

    private void connectWithHandshake() {
        if (handshakeTicks < HANDSHAKE_DELAY_TICKS || this.client == null) return;
        if (handshakeAction != null) {
            handshakeAction.run();
            return;
        }
        HandshakeClient.connectWithHandshake(this.client, handshake);
    }

    @Override
    public void tick() {
        super.tick();
        if (handshakeButton != null && handshakeTicks < HANDSHAKE_DELAY_TICKS) {
            handshakeTicks++;
            handshakeButton.active = handshakeTicks >= HANDSHAKE_DELAY_TICKS;
            handshakeButton.setMessage(handshakeButtonText());
        }
    }

    private Text reason() {
        return switch (outcome) {
            case CLIENT_MODIFIED -> Text.translatable("synchronize.untrusted.client_modified", ModJar.version());
            case SERVER_NOT_OFFICIAL -> Text.translatable("synchronize.untrusted.server_not_official", serverVersion);
            case CHECK_FAILED -> Text.translatable("synchronize.untrusted.check_failed", String.valueOf(error));
            default -> Text.empty();
        };
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isEmpty()) return "?";
        return hash.length() > 12 ? hash.substring(0, 12) + "…" : hash;
    }

    @Override
    //? if <1.20 {
    /*public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
    *///?} else
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderSyncBackground(context);
        //? if <1.20 {
        /*super.render(context.getMatrices(), mouseX, mouseY, delta);
        *///?} else
        super.render(context, mouseX, mouseY, delta);
        int midX = this.width / 2;

        if (showIcon) {
            GuiCompat.enableBlend();
            GuiCompat.drawTextureScaled(context, WARN_ICON_TEXTURE, midX - WARN_ICON_SIZE / 2, iconY, WARN_ICON_SIZE, WARN_ICON_SIZE, 0, 0,
                    WARN_ICON_TEXTURE_SIZE, WARN_ICON_TEXTURE_SIZE, WARN_ICON_TEXTURE_SIZE, WARN_ICON_TEXTURE_SIZE);
            GuiCompat.disableBlend();
        }
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.RED, Formatting.BOLD), midX, titleY, 0xFFFFFFFF);
        drawLines(context, reasonLines, midX, reasonY, 0xFFFFFFFF);
        drawLines(context, blockedLines, midX, blockedY, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("synchronize.untrusted.server_line", serverVersion, shortHash(serverHash)), midX, versionY, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("synchronize.untrusted.client_line", ModJar.version(), shortHash(ModJar.sha256())), midX, versionY + VERSION_LINE_HEIGHT, 0xFFAAAAAA);
        drawLines(context, hintLines, midX, hintY, hintColor);
    }

    private void drawLines(DrawContext context, List<OrderedText> lines, int midX, int y, int color) {
        for (OrderedText line : lines) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, midX, y, color);
            y += LINE_HEIGHT;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
