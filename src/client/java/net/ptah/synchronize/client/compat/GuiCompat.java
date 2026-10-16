package net.ptah.synchronize.client.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

//? if <1.21.5
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.6 {
/*import net.minecraft.client.gl.RenderPipelines;
*///?} elif >=1.21.2
/*import net.minecraft.client.render.RenderLayer;*/

 









public final class GuiCompat {
    private GuiCompat() {
    }

     
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} elif >=1.21.2 {
        /*context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else
        context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

     
    public static void drawTextureScaled(DrawContext context, Identifier texture, int x, int y, int width, int height,
                                         float u, float v, int regionWidth, int regionHeight,
                                         int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        *///?} elif >=1.21.2 {
        /*context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        *///?} else
        context.drawTexture(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

     
    public static boolean hasShiftDown() {
        //? if >=1.21.9 {
        /*net.minecraft.client.util.Window window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        return net.minecraft.client.util.InputUtil.isKeyPressed(window, net.minecraft.client.util.InputUtil.GLFW_KEY_LEFT_SHIFT)
            || net.minecraft.client.util.InputUtil.isKeyPressed(window, net.minecraft.client.util.InputUtil.GLFW_KEY_RIGHT_SHIFT);
        *///?} else
        return net.minecraft.client.gui.screen.Screen.hasShiftDown();
    }

    public static void enableBlend() {
        //? if <1.21.5 {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        //?}
    }

    public static void disableBlend() {
        //? if <1.21.5
        RenderSystem.disableBlend();
    }

     

    public static int getX(net.minecraft.client.gui.widget.ClickableWidget widget) {
        //? if <1.19.3 {
        /*return widget.x;
        *///?} else
        return widget.getX();
    }

    public static int getY(net.minecraft.client.gui.widget.ClickableWidget widget) {
        //? if <1.19.3 {
        /*return widget.y;
        *///?} else
        return widget.getY();
    }

    public static void setX(net.minecraft.client.gui.widget.ClickableWidget widget, int x) {
        //? if <1.19.3 {
        /*widget.x = x;
        *///?} else
        widget.setX(x);
    }

    public static void setY(net.minecraft.client.gui.widget.ClickableWidget widget, int y) {
        //? if <1.19.3 {
        /*widget.y = y;
        *///?} else
        widget.setY(y);
    }
}
