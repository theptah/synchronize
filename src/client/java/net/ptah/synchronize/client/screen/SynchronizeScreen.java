package net.ptah.synchronize.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class SynchronizeScreen extends Screen {
    protected SynchronizeScreen(Text title) { super(title); }

    protected final void renderSyncBackground(DrawContext context) {
        //? if <1.20 {
        /*renderBackground(context.getMatrices());
        *///?} else if <1.20.2
        renderBackground(context);
    }
}
