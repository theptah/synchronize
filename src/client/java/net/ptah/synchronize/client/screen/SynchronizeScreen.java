package net.ptah.synchronize.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

 
public abstract class SynchronizeScreen extends Screen {
    protected SynchronizeScreen(Text title) { super(title); }

    protected final void renderSyncBackground(DrawContext context) {
        context.fill(0, 0, this.width, this.height, 0xFF10151F);
    }

     
     
     
    //? if >=1.21.6 {
    /*@Override
    protected void applyBlur(DrawContext context) {}
    *///?} elif >=1.21.2 {
    /*@Override
    protected void applyBlur() {}
    *///?} elif >=1.20.5 {
    /*@Override
    protected void applyBlur(float delta) {}
    *///?}

     
     
    @Override
    //? if >=1.20.2 {
    /*public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    *///?} elif >=1.20 {
    public void renderBackground(DrawContext context) {}
    //?} else {
    /*public void renderBackground(net.minecraft.client.util.math.MatrixStack matrices) {}
    *///?}
}
