package net.ptah.synchronize.client.screen;

import net.ptah.synchronize.client.compat.GuiCompat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.client.ModUpdater;
import net.ptah.synchronize.util.OfficialReleases;
import net.ptah.synchronize.util.ModJar;

public class ModUpdateScreen extends SynchronizeScreen {
    private static final Identifier MAIN_ICON_TEXTURE = Identifiers.mod("textures/gui/main_icon.png");
    private static final Identifier PROGRESS_BAR_BG = Identifiers.mod("textures/gui/progress_bar_bg.png");
    private static final Identifier PROGRESS_BAR_FG = Identifiers.mod("textures/gui/progress_bar_fg.png");
    private static final int BAR_WIDTH = 204;
    private static final int BAR_HEIGHT = 13;
    private static final int BAR_FG_WIDTH = 198;
    private static final int BAR_FG_HEIGHT = 7;
    private static final int BAR_CORNER_RADIUS = 1;
    private static final int PANEL_WIDTH = 300;
    private static final int MAX_TEXT_WIDTH = PANEL_WIDTH + 40;
    private static final int LINE_HEIGHT = 10;
    private static final int VERSION_LINE_HEIGHT = 11;
    private static final int IMAGE_WIDTH = 34;
    private static final int IMAGE_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int STATUS_LINES = 3;

    private enum State { CHECKING, DOWNLOADING, READY, FAILED }

    private final Screen parent;
    private final String serverVersion;
    private final String serverHash;

    private volatile State state = State.CHECKING;
    private volatile Text statusText = Text.translatable("synchronize.selfupdate.checking");
    private final AtomicLong downloadedBytes = new AtomicLong();
    private volatile long totalBytes = -1L;
    private volatile boolean cancelled;
    private Thread worker;

    private ButtonWidget closeGameButton;
    private ButtonWidget cancelButton;

    private int animationTick;
    private int animationFrame;

    private List<OrderedText> headerLines = List.of();
    private boolean showImage;
    private int imageY, titleY, headerY, versionY, barY, statusY, buttonY;

    public ModUpdateScreen(Screen parent, String serverVersion, String serverHash) {
        super(Text.translatable("synchronize.selfupdate.title"));
        this.parent = parent;
        this.serverVersion = serverVersion;
        this.serverHash = serverHash;
    }

    private void calculateLayout() {
        int margin = this.height < 300 ? 4 : 10;
        int gap = this.height < 300 ? 5 : 8;
        headerLines = wrap(Text.translatable("synchronize.selfupdate.header"));

        int content = LINE_HEIGHT + gap / 2
                + headerLines.size() * LINE_HEIGHT + gap - 2
                + VERSION_LINE_HEIGHT + LINE_HEIGHT + gap
                + BAR_HEIGHT + gap
                + STATUS_LINES * LINE_HEIGHT + gap / 2
                + BUTTON_HEIGHT;
        int imageBlock = IMAGE_HEIGHT + gap;
        showImage = this.height - margin * 2 - content >= imageBlock;
        int total = content + (showImage ? imageBlock : 0);

        int y = Math.max(margin, (this.height - total) / 2);
        if (showImage) {
            imageY = y;
            y += imageBlock;
        }
        titleY = y;
        y += LINE_HEIGHT + gap / 2;
        headerY = y;
        y += headerLines.size() * LINE_HEIGHT + gap - 2;
        versionY = y;
        y += VERSION_LINE_HEIGHT + LINE_HEIGHT + gap;
        barY = y;
        y += BAR_HEIGHT + gap;
        statusY = y;
        y += STATUS_LINES * LINE_HEIGHT + gap / 2;
        buttonY = Math.min(y, this.height - BUTTON_HEIGHT - 2);
    }

    @Override
    protected void init() {
        calculateLayout();
        int midX = this.width / 2;

        this.closeGameButton = ButtonWidget.builder(Text.translatable("synchronize.button.close_game"), b -> closeGame())
                .dimensions(midX - BUTTON_WIDTH - 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.cancelButton = ButtonWidget.builder(Text.translatable("synchronize.button.cancel"), b -> cancel())
                .dimensions(midX + 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addDrawableChild(this.closeGameButton);
        this.addDrawableChild(this.cancelButton);
        updateButtons();

        if (worker == null) {
            worker = new Thread(this::runUpdate, "Synchronize-SelfUpdate");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void runUpdate() {
        ModUpdater.CheckResult result = ModUpdater.check(serverVersion, serverHash);
        if (cancelled) return;
        if (result.outcome() != ModUpdater.Outcome.UPDATE_AVAILABLE) {
            runOnClient(() -> {
                if (this.client != null && !cancelled && this.client.currentScreen == this) {
                    Screen next = new UntrustedModScreen(parent, result.outcome(), serverVersion, serverHash,
                            result.pageUrl(), result.error());
                    net.ptah.synchronize.client.ClientSyncHandler.updateVerificationScreen(this, next);
                    this.client.setScreen(next);
                }
            });
            return;
        }

        OfficialReleases.Asset asset = result.lookup().matchedAsset();
        totalBytes = asset.size();
        state = State.DOWNLOADING;
        statusText = Text.translatable("synchronize.selfupdate.downloading", serverVersion);
        try {
            ModUpdater.downloadAndStage(result, serverHash, downloadedBytes::set);
            if (cancelled) return;
            state = State.READY;
            statusText = Text.translatable("synchronize.selfupdate.ready", serverVersion);
        } catch (Exception e) {
            Synchronize.LOGGER.error("[Synchronize] Automatic Synchronize update failed", e);
            state = State.FAILED;
            statusText = Text.translatable("synchronize.selfupdate.failed", String.valueOf(e.getMessage())).formatted(Formatting.RED);
            ModUpdater.cleanupScratch();
        }
        runOnClient(this::updateButtons);
    }

    private void runOnClient(Runnable runnable) {
        if (this.client != null) {
            this.client.execute(runnable);
        }
    }

    private void updateButtons() {
        if (closeGameButton != null) {
            closeGameButton.active = state == State.READY;
            closeGameButton.visible = state == State.READY || state == State.DOWNLOADING;
        }
        if (cancelButton != null) {
            int midX = this.width / 2;
            boolean alone = closeGameButton == null || !closeGameButton.visible;
            GuiCompat.setX(cancelButton, alone ? midX - cancelButton.getWidth() / 2 : midX + 5);
            cancelButton.setMessage(Text.translatable(state == State.READY || state == State.FAILED
                    ? "synchronize.button.back" : "synchronize.button.cancel"));
        }
    }

    private void closeGame() {
        if (state != State.READY || this.client == null) return;
        this.client.scheduleStop();
    }

    private void cancel() {
        if (state != State.READY) {
            cancelled = true;
            if (worker != null) worker.interrupt();
            if (state != State.FAILED) ModUpdater.cleanupScratch();
        }
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
         
        cancelled = true;
        if (worker != null) worker.interrupt();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (++animationTick >= 4) {
            animationTick = 0;
            animationFrame = (animationFrame + 1) % 6;
        }
        updateButtons();
    }

    @Override
    //? if <1.20 {
    /*public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
    *///?} else
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderSyncBackground(context);
        int midX = this.width / 2;

        if (showImage) {
            GuiCompat.drawTexture(context, MAIN_ICON_TEXTURE, midX - IMAGE_WIDTH / 2, imageY, 0, animationFrame * IMAGE_HEIGHT,
                    IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT * 6);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.GOLD, Formatting.BOLD), midX, titleY, 0xFFFFFFFF);
        int y = headerY;
        for (OrderedText line : headerLines) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, midX, y, 0xFFAAAAAA);
            y += LINE_HEIGHT;
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("synchronize.selfupdate.server_version", serverVersion), midX, versionY, 0xFF55FF55);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("synchronize.selfupdate.client_version", ModJar.version()), midX, versionY + VERSION_LINE_HEIGHT, 0xFFFFFF55);
        y = barY;
        int barWidth = Math.min(BAR_WIDTH, this.width - 16);
        int barLeft = midX - barWidth / 2;
        drawRoundedTexture(context, PROGRESS_BAR_BG, barLeft, y, barWidth, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        int fgWidth = Math.max(0, barWidth - (BAR_WIDTH - BAR_FG_WIDTH));
        int fgLeft = barLeft + (BAR_WIDTH - BAR_FG_WIDTH) / 2;
        int fgTop = y + (BAR_HEIGHT - BAR_FG_HEIGHT) / 2;
        if (state == State.CHECKING || state == State.DOWNLOADING) {
            int span = Math.max(1, fgWidth / 4);
            int offset = (int) ((System.currentTimeMillis() / 8) % (fgWidth + span)) - span;
            int segStart = Math.max(0, offset);
            int segEnd = Math.min(fgWidth, offset + span);
            if (segEnd > segStart) {
                drawRoundedTexture(context, PROGRESS_BAR_FG, fgLeft + segStart, fgTop, segEnd - segStart, BAR_FG_HEIGHT, BAR_FG_WIDTH, BAR_FG_HEIGHT, segStart);
            }
        } else if (state == State.READY) {
            drawRoundedTexture(context, PROGRESS_BAR_FG, fgLeft, fgTop, fgWidth, BAR_FG_HEIGHT, BAR_FG_WIDTH, BAR_FG_HEIGHT);
        }

        List<OrderedText> statusLines = wrap(statusText);
        y = statusY;
        for (int i = 0; i < Math.min(STATUS_LINES, statusLines.size()); i++) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusLines.get(i), midX, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        //? if <1.20 {
        /*super.render(context.getMatrices(), mouseX, mouseY, delta);
        *///?} else
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRoundedTexture(DrawContext context, Identifier texture, int x, int y, int width, int height,
            int textureWidth, int textureHeight) {
        drawRoundedTexture(context, texture, x, y, width, height, textureWidth, textureHeight, 0);
    }

    private void drawRoundedTexture(DrawContext context, Identifier texture, int x, int y, int width, int height,
            int textureWidth, int textureHeight, int u) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.max(0, Math.min(BAR_CORNER_RADIUS, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int inset = cornerInset(row, height, radius);
            int rowWidth = width - inset * 2;
            if (rowWidth <= 0) {
                continue;
            }
            GuiCompat.drawTexture(context, texture, x + inset, y + row, u + inset, row, rowWidth, 1, textureWidth, textureHeight);
        }
    }

    private static int cornerInset(int row, int height, int radius) {
        if (radius == 0) {
            return 0;
        }
        int dy;
        if (row < radius) {
            dy = radius - row;
        } else if (row >= height - radius) {
            dy = radius - (height - 1 - row);
        } else {
            return 0;
        }
        return radius - (int) Math.floor(Math.sqrt((double) (radius * radius - dy * dy)) + 0.5);
    }

    private List<OrderedText> wrap(Text text) {
        int margin = this.height < 300 ? 4 : 10;
        return this.textRenderer.wrapLines(text, Math.max(120, Math.min(MAX_TEXT_WIDTH, this.width - margin * 2 - 8)));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
