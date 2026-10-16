package net.ptah.synchronize.client.compat.legacy;

//? if <1.19.3 {
/*import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

 
 
public final class ButtonBuilder {
    private final Text message;
    private final ButtonWidget.PressAction onPress;
    private int x;
    private int y;
    private int width = 150;
    private int height = 20;
    private Tooltip tooltip;

    private ButtonBuilder(Text message, ButtonWidget.PressAction onPress) {
        this.message = message;
        this.onPress = onPress;
    }

    public static ButtonBuilder builder(Text message, ButtonWidget.PressAction onPress) {
        return new ButtonBuilder(message, onPress);
    }

    public ButtonBuilder dimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public ButtonBuilder position(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public ButtonBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public ButtonBuilder width(int width) {
        this.width = width;
        return this;
    }

    public ButtonBuilder tooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public ButtonWidget build() {
        if (this.tooltip == null) {
            return new ButtonWidget(this.x, this.y, this.width, this.height, this.message, this.onPress);
        }
        Tooltip tip = this.tooltip;
        return new ButtonWidget(this.x, this.y, this.width, this.height, this.message, this.onPress,
                (button, matrices, mouseX, mouseY) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.currentScreen != null) {
                        client.currentScreen.renderTooltip(matrices, tip.text(), mouseX, mouseY);
                    }
                });
    }
}
*///?}
