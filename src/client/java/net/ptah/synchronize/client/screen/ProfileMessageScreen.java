package net.ptah.synchronize.client.screen;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public final class ProfileMessageScreen extends Screen {
    private final Screen parent;
    private final Text body;
    private final Text primary;
    private final Runnable primaryAction;
    private final Text secondary;
    private final Runnable secondaryAction;
    private List<OrderedText> lines = List.of();

    public ProfileMessageScreen(Screen parent, Text title, Text body, Text primary, Runnable primaryAction, Text secondary, Runnable secondaryAction) {
        super(title); this.parent = parent; this.body = body; this.primary = primary; this.primaryAction = primaryAction;
        this.secondary = secondary; this.secondaryAction = secondaryAction;
    }

    @Override
    protected void init() {
        lines = textRenderer.wrapLines(body, Math.max(80, Math.min(360, width - 48)));
        int y = Math.min(height - 28, height / 2 + lines.size() * 6 + 18);
        int w = Math.max(70, Math.min(155, (width - 30) / 2));
        addDrawableChild(ButtonWidget.builder(primary, b -> primaryAction.run()).dimensions(secondary == null ? width / 2 - w / 2 : width / 2 - w - 4, y, w, 20).build());
        if (secondary != null) addDrawableChild(ButtonWidget.builder(secondary, b -> secondaryAction.run()).dimensions(width / 2 + 4, y, w, 20).build());
    }

    @Override
    //? if <1.20 {
    /*public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
    *///?} else
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        //? if <1.20 {
        /*renderBackground(context.getMatrices());
        *///?} else if <1.20.2
        renderBackground(context);
        //? if <1.20 {
        /*super.render(context.getMatrices(), mouseX, mouseY, delta);
        *///?} else
        super.render(context, mouseX, mouseY, delta);
        int y = Math.max(16, height / 2 - lines.size() * 6 - 26);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, y, 0xFFFFFFFF);
        y += 26;
        for (OrderedText line : lines) { context.drawText(textRenderer, line, width / 2 - textRenderer.getWidth(line) / 2, y, 0xFFCCCCCC, true); y += 12; }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
