package net.ptah.synchronize.client.compat.legacy;

//? if <1.20 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

 
 
 
 
public final class DrawContext {
    private final MatrixStack matrices;

    public DrawContext(MatrixStack matrices) {
        this.matrices = matrices;
    }

    public MatrixStack getMatrices() {
        return this.matrices;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(this.matrices, x1, y1, x2, y2, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer renderer, Text text, int centerX, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(this.matrices, renderer, text.asOrderedText(), centerX, y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer renderer, String text, int centerX, int y, int color) {
        renderer.drawWithShadow(this.matrices, text, (float) (centerX - renderer.getWidth(text) / 2), (float) y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer renderer, OrderedText text, int centerX, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(this.matrices, renderer, text, centerX, y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, Text text, int x, int y, int color) {
        return renderer.drawWithShadow(this.matrices, text, (float) x, (float) y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, String text, int x, int y, int color) {
        return renderer.drawWithShadow(this.matrices, text, (float) x, (float) y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, OrderedText text, int x, int y, int color) {
        return renderer.drawWithShadow(this.matrices, text, (float) x, (float) y, color);
    }

    public int drawText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow) {
        return shadow
                ? renderer.drawWithShadow(this.matrices, text, (float) x, (float) y, color)
                : renderer.draw(this.matrices, text, (float) x, (float) y, color);
    }

    public int drawText(TextRenderer renderer, String text, int x, int y, int color, boolean shadow) {
        return shadow
                ? renderer.drawWithShadow(this.matrices, text, (float) x, (float) y, color)
                : renderer.draw(this.matrices, text, (float) x, (float) y, color);
    }

    public int drawText(TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow) {
        return shadow
                ? renderer.drawWithShadow(this.matrices, text, (float) x, (float) y, color)
                : renderer.draw(this.matrices, text, (float) x, (float) y, color);
    }

     
    public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height,
                            int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(this.matrices, x, y, u, v, width, height, textureWidth, textureHeight);
    }

     
    public void drawTexture(Identifier texture, int x, int y, int width, int height, float u, float v,
                            int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(this.matrices, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public void drawTooltip(TextRenderer renderer, Text text, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) {
            client.currentScreen.renderTooltip(this.matrices, text, x, y);
        }
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        DrawableHelper.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        DrawableHelper.disableScissor();
    }
}
*///?}
