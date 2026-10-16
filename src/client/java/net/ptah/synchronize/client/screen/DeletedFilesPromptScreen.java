package net.ptah.synchronize.client.screen;

import net.ptah.synchronize.client.compat.GuiCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;

public class DeletedFilesPromptScreen extends SynchronizeScreen {
    private static final Identifier MAIN_ICON_TEXTURE = Identifiers.mod("textures/gui/main_icon.png");
    private static final int PANEL_HALF_WIDTH = 145;

    private final Screen parent;
    private final List<String> garbageFiles;
    private final boolean requiresRestart;
    private final Consumer<List<String>> onUninstall;
    private final Runnable onJoin;
    private final Runnable onDisconnect;

    private CheckboxWidget approvalCheckbox;
    private ButtonWidget actionButton;
    private ButtonWidget disconnectButton;

    private int panelHeight;
    private int imageY, titleY, subtitleY, panelTop, restartWarningY, checkboxY, buttonY;

	private int animationTick = 0;
	private int animationFrame = 0;

    public DeletedFilesPromptScreen(Screen parent, List<String> garbageFiles, boolean requiresRestart,
                                    Consumer<List<String>> onUninstall, Runnable onJoin, Runnable onDisconnect) {
        super(Text.translatable("synchronize.deleted.title"));
        this.parent = parent;
        this.garbageFiles = new ArrayList<>(garbageFiles);
        this.requiresRestart = requiresRestart;
        this.onUninstall = onUninstall;
        this.onJoin = onJoin;
        this.onDisconnect = onDisconnect;
    }

    private void calculateLayout() {
        int visibleRows = Math.min(6, garbageFiles.size());
        panelHeight = visibleRows * 14 + 8;

        int topFixed = 23 + 8 + 10 + 4 + 10 + 10;
        int panelSpace = panelHeight + 4;
        int bottomFixed = 0;
        if (requiresRestart) bottomFixed += 10 + 10;
        bottomFixed += 20 + 8;
        bottomFixed += 20;

        int totalLayoutHeight = topFixed + panelSpace + bottomFixed;
        int currentY = Math.max(10, (this.height - totalLayoutHeight) / 2);

        imageY = currentY; currentY += 23 + 8;
        titleY = currentY; currentY += 10 + 4;
        subtitleY = currentY; currentY += 10 + 10;

        panelTop = currentY;
        currentY += panelHeight + 4;

        if (requiresRestart) {
            currentY += 10;
            restartWarningY = currentY;
            currentY += 10 + 10;
        } else {
            currentY += 10;
        }

        checkboxY = currentY;
        currentY += 20 + 8;

        buttonY = currentY;
    }

    @Override
    protected void init() {
        super.init();
        calculateLayout();
        int midX = this.width / 2;

         
        //? if >=1.20.3 {
        /*this.approvalCheckbox = CheckboxWidget.builder(
                Text.translatable("synchronize.deleted.approve"), this.textRenderer)
                .pos(midX - PANEL_HALF_WIDTH, checkboxY)
                .checked(true)
                .build();
        *///?} else {
        this.approvalCheckbox = new CheckboxWidget(
                midX - PANEL_HALF_WIDTH, checkboxY, PANEL_HALF_WIDTH * 2, 20,
                Text.translatable("synchronize.deleted.approve"), true);
        //?}
        this.addDrawableChild(this.approvalCheckbox);

        this.actionButton = ButtonWidget.builder(
                Text.translatable("synchronize.button.uninstall"),
                button -> {
                    if (approvalCheckbox.isChecked()) {
                        onUninstall.accept(List.copyOf(garbageFiles));
                    } else {
                        onJoin.run();
                    }
                })
            .dimensions(midX - 105, buttonY, 100, 20)
            .build();

        this.disconnectButton = ButtonWidget.builder(
                Text.translatable("synchronize.button.disconnect"),
                button -> onDisconnect.run())
            .dimensions(midX + 5, buttonY, 100, 20)
            .build();

        this.addDrawableChild(this.actionButton);
        this.addDrawableChild(this.disconnectButton);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.approvalCheckbox != null && this.actionButton != null) {
            this.actionButton.setMessage(Text.translatable(this.approvalCheckbox.isChecked() ? "synchronize.button.uninstall" : "synchronize.button.join"));
        }

		animationTick++;

		if (animationTick >= 4) {
			animationTick = 0;
			animationFrame++;

			if (animationFrame >= 6) {
				animationFrame = 0;
			}
		}
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

		int frameWidth = 34;
		int frameHeight = 20;

		int v = animationFrame * frameHeight;

		GuiCompat.drawTexture(context, 
				MAIN_ICON_TEXTURE,
				midX - 17,
				imageY,
				0,
				v,
				frameWidth,
				frameHeight,
				34,
				120
		);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("synchronize.deleted.header"), midX, titleY, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("synchronize.deleted.subheader"), midX, subtitleY, 0xFFAAAAAA);

        int panelLeft = midX - PANEL_HALF_WIDTH;
        int panelRight = midX + PANEL_HALF_WIDTH;
        int panelBottom = panelTop + panelHeight;

        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xB0101010);
        context.fill(panelLeft, panelTop - 1, panelRight, panelTop, 0xFF555555);
        context.fill(panelLeft, panelBottom, panelRight, panelBottom + 1, 0xFF555555);
        context.fill(panelLeft - 1, panelTop, panelLeft, panelBottom, 0xFF555555);
        context.fill(panelRight, panelTop, panelRight + 1, panelBottom, 0xFF555555);

        int visible = Math.min(6, garbageFiles.size());
        int start = Math.max(0, garbageFiles.size() - visible);
        int maxTextWidth = (PANEL_HALF_WIDTH * 2) - 14;

        for (int i = 0; i < visible; i++) {
            String path = garbageFiles.get(start + i);
            String shownName = path;

            if (this.textRenderer.getWidth(path) > maxTextWidth) {
                int ellipsisWidth = this.textRenderer.getWidth("...");
                shownName = this.textRenderer.trimToWidth(path, Math.max(0, maxTextWidth - ellipsisWidth)) + "...";
            }

            context.drawText(this.textRenderer, Text.literal(shownName), panelLeft + 7, panelTop + 4 + i * 14, 0xFFAAAAAA, false);
        }

        if (requiresRestart) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("synchronize.deleted.restart_warning"),
                    midX, restartWarningY, 0xFFFFFF55);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}