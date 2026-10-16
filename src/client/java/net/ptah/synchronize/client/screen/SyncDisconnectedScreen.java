package net.ptah.synchronize.client.screen;

import net.ptah.synchronize.client.compat.GuiCompat;

import net.ptah.synchronize.client.ClientSyncHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;

public class SyncDisconnectedScreen extends SynchronizeScreen {
    private static final Identifier MAIN_ICON_TEXTURE = Identifiers.mod("textures/gui/main_icon.png");

    private final Screen parent;
    private ButtonWidget closeButton;

    private int imageY, titleY, message1Y, message2Y, buttonY;

	private int animationTick = 0;
	private int animationFrame = 0;

    public SyncDisconnectedScreen(Screen parent) {
        super(Text.translatable("synchronize.disconnected.title"));
        this.parent = parent;
    }

	@Override
	public void tick() {
		super.tick();

		animationTick++;

		if (animationTick >= 4) {
			animationTick = 0;
			animationFrame++;

			if (animationFrame >= 6) {
				animationFrame = 0;
			}
		}
	}

    private void calculateLayout() {
        int totalHeight = 23 + 8 + 10 + 4 + 10 + 2 + 10 + 16 + 20;
        int currentY = Math.max(10, (this.height - totalHeight) / 2);

        imageY = currentY;
        currentY += 23 + 8;

        titleY = currentY;
        currentY += 10 + 4;

        message1Y = currentY;
        currentY += 10 + 2;

        message2Y = currentY;
        currentY += 10 + 16;

        buttonY = currentY;
    }

    @Override
    protected void init() {
        calculateLayout();

        int midX = this.width / 2;

        this.closeButton = ButtonWidget.builder(Text.translatable("synchronize.button.close"), button -> {
            ClientSyncHandler.dismissDisconnectScreen();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(midX - 50, buttonY, 100, 20).build();

        this.addDrawableChild(this.closeButton);
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

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("synchronize.disconnected.title").formatted(Formatting.RED),
                midX,
                titleY,
                0xFFFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("synchronize.disconnected.message"),
                midX,
                message1Y,
                0xFFFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("synchronize.disconnected.no_files"),
                midX,
                message2Y,
                0xFFAAAAAA
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
