package net.ptah.synchronize.client.screen;

import net.ptah.synchronize.client.compat.GuiCompat;

import net.ptah.synchronize.util.SyncLogger;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;
import net.minecraft.util.math.MathHelper;

public class DownloadProgressScreen extends SynchronizeScreen {
	private static final Identifier MAIN_ICON_TEXTURE = Identifiers.mod("textures/gui/main_icon.png");
	private static final Identifier PROGRESS_BAR_BG = Identifiers.mod("textures/gui/progress_bar_bg.png");
	private static final Identifier PROGRESS_BAR_FG = Identifiers.mod("textures/gui/progress_bar_fg.png");
	private static final int BAR_CORNER_RADIUS = 1;
	private static final int BAR_WIDTH = 204;
	private static final int BAR_HEIGHT = 13;
	private static final int PROGRESS_BAR_FG_WIDTH = 198;
	private static final int PROGRESS_BAR_FG_HEIGHT = 7;
	private static final int IMAGE_WIDTH = 34;
	private static final int IMAGE_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MAX_BOX_WIDTH = 300;
	private static final int LOG_LINE_HEIGHT = 10;
	private static final int MAX_LOG_ROWS = 6;
	private static final int MIN_LOG_ROWS = 2;
	private static final int LOG_COLOR_ERROR = 0xFFFF5555;

	private record LogLine(String text, int color) {
	}

	private State currentState;
	private Text statusText;
	private float progress;
	private ButtonWidget actionButton;
	private final List<LogLine> logMessages;
	private int scrollIndex;
	private int horizontalScrollIndex;
	private boolean draggingVertical;
	private boolean draggingHorizontal;
	private boolean hasMissingFiles;
	private boolean requiresRestart;
	private Runnable onApplyAction;
	private final Runnable onAbortAction;
	private Runnable onContinueAction;
	private Runnable onProfileSwitch;

	private boolean showImage;
	private int imageY;
	private int statusY;
	private int barY;
	private int logRows = MAX_LOG_ROWS;
	private int boxLeft;
	private int boxRight;
	private int boxTop;
	private int boxHeight;
	private int buttonY;

	private int animationTick = 0;
	private int animationFrame = 0;

	public DownloadProgressScreen(Runnable onAbortAction, Runnable onContinueAction) {
		super(Text.translatable("synchronize.download.title"));
		this.currentState = State.DOWNLOADING;
		this.logMessages = new CopyOnWriteArrayList<>();
		this.statusText = Text.translatable("synchronize.download.starting");
		this.progress = 0.0F;
		this.onAbortAction = onAbortAction;
		this.onContinueAction = onContinueAction;
	}

	@Override
	public void tick() {
		super.tick();
		animationTick++;
		if (animationTick >= 4) {
			animationTick = 0;
			animationFrame = (animationFrame + 1) % 6;
		}
	}

	private void calculateLayout() {
		int margin = this.height < 300 ? 4 : 10;
		int gap = this.height < 300 ? 5 : 8;
		int imageBlock = IMAGE_HEIGHT + gap;
		int fixed = LOG_LINE_HEIGHT + gap + BAR_HEIGHT + gap + 6 + gap + BUTTON_HEIGHT;

		int rowsWithImage = (this.height - margin * 2 - fixed - imageBlock) / LOG_LINE_HEIGHT;
		showImage = rowsWithImage >= MIN_LOG_ROWS + 1;
		int rows = showImage ? rowsWithImage : (this.height - margin * 2 - fixed) / LOG_LINE_HEIGHT;
		logRows = MathHelper.clamp(rows, 1, MAX_LOG_ROWS);

		int boxWidth = Math.max(120, Math.min(MAX_BOX_WIDTH, this.width - margin * 2 - 8));
		boxLeft = (this.width - boxWidth) / 2;
		boxRight = boxLeft + boxWidth;
		boxHeight = logRows * LOG_LINE_HEIGHT + 6;

		int total = fixed - 6 + boxHeight + (showImage ? imageBlock : 0);
		int y = Math.max(margin, (this.height - total) / 2);
		if (showImage) {
			imageY = y;
			y += imageBlock;
		}
		statusY = y;
		y += LOG_LINE_HEIGHT + gap;
		barY = y;
		y += BAR_HEIGHT + gap;
		boxTop = y;
		y += boxHeight + gap;
		buttonY = Math.min(y, this.height - BUTTON_HEIGHT - 2);
		clampScroll();
	}

	@Override
	protected void init() {
		calculateLayout();
		int midX = this.width / 2;
		this.actionButton = ButtonWidget.builder(Text.translatable("synchronize.button.exit"), button -> {
			if (this.currentState == State.DOWNLOADING) {
				this.onAbortAction.run();
				if (this.client != null) {
					this.client.setScreen(null);
				}
			} else if (this.currentState == State.DONE) {
				if (this.requiresRestart) {
					if (this.onProfileSwitch != null) this.onProfileSwitch.run();
                    else MinecraftClient.getInstance().scheduleStop();
				} else {
					if (this.onContinueAction != null) {
						this.onContinueAction.run();
					}
					if (this.client != null) {
						this.client.setScreen(null);
					}
				}
			}
		}).dimensions(midX - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		this.updateButtonVisuals();
		this.addDrawableChild(this.actionButton);
	}

	public void updateProgress(float progress, Text statusText) {
		this.progress = MathHelper.clamp(progress, 0.0F, 1.0F);
		this.statusText = statusText;
	}

	public void setHasMissingFiles() {
		this.hasMissingFiles = true;
	}

	public void setNetworkFinished(Runnable onApplyAction) {
		this.currentState = State.APPLYING;
		this.progress = 0.0F;
		this.statusText = Text.translatable("synchronize.download.preparing_move");
		this.onApplyAction = onApplyAction;
		this.updateButtonVisuals();
		if (this.onApplyAction != null) {
			this.onApplyAction.run();
		}
	}

	public void setFullyDone(boolean requiresRestart) {
		this.currentState = State.DONE;
		this.progress = 1.0F;
		this.requiresRestart = requiresRestart;
		if (requiresRestart) {
			this.statusText = this.onProfileSwitch != null ? Text.translatable("synchronize.profiles.prepared") : this.hasMissingFiles
				? Text.translatable("synchronize.download.restart_required_missing")
				: Text.translatable("synchronize.download.restart_required");
		} else if (this.hasMissingFiles) {
			this.statusText = Text.translatable("synchronize.download.applied_missing");
		} else {
			this.statusText = Text.translatable("synchronize.download.applied");
		}
		this.updateButtonVisuals();
	}

	private void updateButtonVisuals() {
		if (this.actionButton != null) {
			this.actionButton.active = this.currentState != State.APPLYING;
			switch (this.currentState) {
				case DOWNLOADING -> this.actionButton.setMessage(Text.translatable("synchronize.button.exit"));
				case APPLYING -> this.actionButton.setMessage(Text.translatable("synchronize.button.applying"));
				case DONE -> this.actionButton.setMessage(Text.translatable(this.onProfileSwitch != null ? "synchronize.profiles.switch" : this.requiresRestart ? "synchronize.button.quit_game" : "synchronize.button.join"));
			}
		}
	}

	public void addLog(String message) {
		SyncLogger.info(message);
	}

	public void addError(String message) {
		SyncLogger.error(message);
		this.logMessages.add(new LogLine(message, LOG_COLOR_ERROR));
		if (!this.draggingVertical) {
			this.scrollIndex = Math.max(0, this.logMessages.size() - this.logRows);
		}
	}

	private int maxVerticalScroll() {
		return Math.max(0, this.logMessages.size() - this.logRows);
	}

	private void clampScroll() {
		this.scrollIndex = MathHelper.clamp(this.scrollIndex, 0, maxVerticalScroll());
	}

	private int boxContentWidth() {
		return boxRight - boxLeft - 8;
	}

	private int maxHorizontalScroll() {
		return Math.max(0, this.getMaxLineWidth() - boxContentWidth() + 10);
	}

	@Override
	 
	//? if >=1.20.2 {
	/*public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
	*///?} else
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (GuiCompat.hasShiftDown()) {
			int scrollAmount = (int) (amount * 15.0);
			this.horizontalScrollIndex = MathHelper.clamp(this.horizontalScrollIndex - scrollAmount, 0, maxHorizontalScroll());
		} else if (maxVerticalScroll() > 0) {
			if (amount > 0.0) {
				this.scrollIndex = Math.max(0, this.scrollIndex - 1);
			} else if (amount < 0.0) {
				this.scrollIndex = Math.min(maxVerticalScroll(), this.scrollIndex + 1);
			}
		}
		//? if >=1.20.2 {
		/*return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
		*///?} else
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	 
	//? if >=1.21.9 {
	/*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();
	*///?} else {
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
	//?}
		if (button == 0) {
			int boxBottom = boxTop + boxHeight;
			if (maxVerticalScroll() > 0 && mouseX >= boxRight - 4 && mouseX <= boxRight && mouseY >= boxTop && mouseY <= boxBottom) {
				this.draggingVertical = true;
				this.updateVerticalScroll(mouseY);
				return true;
			}
			if (maxHorizontalScroll() > 0 && mouseX >= boxLeft && mouseX <= boxRight - 4 && mouseY >= boxBottom - 4 && mouseY <= boxBottom) {
				this.draggingHorizontal = true;
				this.updateHorizontalScroll(mouseX);
				return true;
			}
		}
		//? if >=1.21.9 {
		/*return super.mouseClicked(click, doubled);
		*///?} else
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	//? if >=1.21.9 {
	/*public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
		double mouseX = click.x();
		double mouseY = click.y();
	*///?} else {
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
	//?}
		if (this.draggingVertical) {
			this.updateVerticalScroll(mouseY);
			return true;
		} else if (this.draggingHorizontal) {
			this.updateHorizontalScroll(mouseX);
			return true;
		}
		//? if >=1.21.9 {
		/*return super.mouseDragged(click, deltaX, deltaY);
		*///?} else
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	//? if >=1.21.9 {
	/*public boolean mouseReleased(net.minecraft.client.gui.Click click) {
		int button = click.button();
	*///?} else {
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
	//?}
		if (button == 0) {
			this.draggingVertical = false;
			this.draggingHorizontal = false;
		}
		//? if >=1.21.9 {
		/*return super.mouseReleased(click);
		*///?} else
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private int verticalThumbHeight() {
		return Math.max(8, (int) ((float) boxHeight * (float) logRows / (float) Math.max(1, this.logMessages.size())));
	}

	private int horizontalThumbWidth() {
		int contentWidth = boxContentWidth();
		return Math.max(8, (int) ((float) contentWidth * (float) contentWidth / (float) Math.max(1, this.getMaxLineWidth())));
	}

	private void updateVerticalScroll(double mouseY) {
		int thumbHeight = verticalThumbHeight();
		float fraction = (float) (mouseY - boxTop - thumbHeight / 2.0) / (float) Math.max(1, boxHeight - thumbHeight);
		fraction = MathHelper.clamp(fraction, 0.0F, 1.0F);
		this.scrollIndex = Math.round(fraction * maxVerticalScroll());
	}

	private void updateHorizontalScroll(double mouseX) {
		int thumbWidth = horizontalThumbWidth();
		float fraction = (float) (mouseX - boxLeft - thumbWidth / 2.0) / (float) Math.max(1, boxContentWidth() - thumbWidth);
		fraction = MathHelper.clamp(fraction, 0.0F, 1.0F);
		this.horizontalScrollIndex = (int) (fraction * maxHorizontalScroll());
	}

	private int getMaxLineWidth() {
		int max = 0;
		for (LogLine line : this.logMessages) {
			max = Math.max(max, this.textRenderer.getWidth(line.text()));
		}
		return max;
	}

	private String fitText(String text, int maxWidth) {
		if (this.textRenderer.getWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		return this.textRenderer.trimToWidth(text, Math.max(0, maxWidth - this.textRenderer.getWidth(ellipsis))) + ellipsis;
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

		if (showImage) {
			GuiCompat.drawTexture(context, MAIN_ICON_TEXTURE, midX - IMAGE_WIDTH / 2, imageY, 0, animationFrame * IMAGE_HEIGHT,
					IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT * 6);
		}

		int statusColor = this.hasMissingFiles ? 0xFFFF5555 : (this.requiresRestart ? 0xFFFFFF55 : 0xFFFFFFFF);
		String status = fitText(this.statusText.getString(), this.width - 16);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), midX, statusY, statusColor);

		int barWidth = Math.min(BAR_WIDTH, this.width - 16);
		int left = midX - barWidth / 2;
		this.drawRoundedTexture(context, PROGRESS_BAR_BG, left, barY, barWidth, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT, BAR_CORNER_RADIUS, true, true);
		int fgMaxWidth = Math.max(0, barWidth - (BAR_WIDTH - PROGRESS_BAR_FG_WIDTH));
		int fgLeft = left + (BAR_WIDTH - PROGRESS_BAR_FG_WIDTH) / 2;
		int fgTop = barY + (BAR_HEIGHT - PROGRESS_BAR_FG_HEIGHT) / 2;
		int fillWidth = (int) ((float) fgMaxWidth * this.progress);
		if (fillWidth > 0) {
			this.drawRoundedTexture(context, PROGRESS_BAR_FG, fgLeft, fgTop, fillWidth, PROGRESS_BAR_FG_HEIGHT, PROGRESS_BAR_FG_WIDTH, PROGRESS_BAR_FG_HEIGHT, BAR_CORNER_RADIUS, true, true);
		}

		int boxBottom = boxTop + boxHeight;
		context.fill(boxLeft, boxTop, boxRight, boxBottom, 0xAA000000);
		context.fill(boxLeft, boxTop - 1, boxRight, boxTop, 0xFF555555);
		context.fill(boxLeft, boxBottom, boxRight, boxBottom + 1, 0xFF555555);

		int maxHScroll = maxHorizontalScroll();
		this.horizontalScrollIndex = MathHelper.clamp(this.horizontalScrollIndex, 0, maxHScroll);
		clampScroll();
		context.enableScissor(boxLeft + 2, boxTop + 2, boxRight - 6, boxBottom - 5);
		int textY = boxTop + 3;
		for (int i = 0; i < logRows; ++i) {
			int currentLogPos = this.scrollIndex + i;
			if (currentLogPos >= this.logMessages.size()) {
				break;
			}
			LogLine line = this.logMessages.get(currentLogPos);
			context.drawText(this.textRenderer, Text.literal(line.text()), boxLeft + 5 - this.horizontalScrollIndex, textY, line.color(), false);
			textY += LOG_LINE_HEIGHT;
		}
		context.disableScissor();

		int maxVScroll = maxVerticalScroll();
		if (maxVScroll > 0) {
			int thumbHeight = verticalThumbHeight();
			int thumbY = boxTop + (int) ((float) this.scrollIndex / (float) maxVScroll * (float) (boxHeight - thumbHeight));
			context.fill(boxRight - 4, boxTop, boxRight, boxBottom, 0xFF333333);
			context.fill(boxRight - 4, thumbY, boxRight, thumbY + thumbHeight, this.draggingVertical ? 0xFFAAAAAA : 0xFF888888);
		}

		if (maxHScroll > 0) {
			int contentWidth = boxContentWidth();
			int thumbWidth = horizontalThumbWidth();
			int thumbX = boxLeft + (int) ((float) this.horizontalScrollIndex / (float) maxHScroll * (float) (contentWidth - thumbWidth));
			context.fill(boxLeft, boxBottom - 4, boxRight - 4, boxBottom, 0xFF333333);
			context.fill(thumbX, boxBottom - 4, thumbX + thumbWidth, boxBottom, this.draggingHorizontal ? 0xFFAAAAAA : 0xFF888888);
		}
	}

	private void drawRoundedTexture(DrawContext context, Identifier texture, int x, int y, int width, int height,
			int textureWidth, int textureHeight, int radius, boolean roundLeft, boolean roundRight) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int clampedRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		if (clampedRadius == 0 || (!roundLeft && !roundRight)) {
			GuiCompat.drawTexture(context, texture, x, y, 0, 0, width, height, textureWidth, textureHeight);
			return;
		}

		for (int row = 0; row < height; row++) {
			int inset = this.cornerInset(row, height, clampedRadius);
			int leftInset = roundLeft ? inset : 0;
			int rightInset = roundRight ? inset : 0;
			int rowWidth = width - leftInset - rightInset;
			if (rowWidth <= 0) {
				continue;
			}

			GuiCompat.drawTexture(context, texture, x + leftInset, y + row, leftInset, row, rowWidth, 1, textureWidth, textureHeight);
		}
	}

	private int cornerInset(int row, int height, int radius) {
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

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

    public void setProfileSwitch(Runnable action) { this.onProfileSwitch = action; }

    public void setPreparationFailed() {
        this.currentState = State.DOWNLOADING;
        this.hasMissingFiles = true;
        this.updateButtonVisuals();
    }

	public void setOnContinueAction(Runnable action) {
		this.onContinueAction = action;
	}

	private enum State {
		DOWNLOADING,
		APPLYING,
		DONE
	}
}
