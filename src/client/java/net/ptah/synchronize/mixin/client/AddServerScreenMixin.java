package net.ptah.synchronize.mixin.client;

import net.ptah.synchronize.client.compat.GuiCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;
import net.ptah.synchronize.client.handshake.HandshakeServerEntry;
import net.ptah.synchronize.util.HandshakeCrypto;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AddServerScreen.class)
public abstract class AddServerScreenMixin extends Screen {
    @Unique private static final int SYNCHRONIZE_TOGGLE_WIDTH = 25;
    @Unique private static final int SYNCHRONIZE_LABEL_GAP = 6;
    @Unique private static final Identifier SYNCHRONIZE_EYE_ICON = Identifiers.mod("textures/gui/eye_icon.png");
    @Unique private static final Identifier SYNCHRONIZE_INVISIBLE_ICON = Identifiers.mod("textures/gui/invisible_icon.png");
    @Unique private static final int SYNCHRONIZE_ICON_TEXTURE_SIZE = 512;
    @Unique private static final int SYNCHRONIZE_ICON_SIZE = 16;

    @Shadow @Final private ServerInfo server;
    @Shadow private TextFieldWidget addressField;

    @Unique private TextFieldWidget synchronize$handshakeField;
    @Unique private ButtonWidget synchronize$toggleButton;
    @Unique private String synchronize$pendingCode;
    @Unique private boolean synchronize$revealed;
    //? if >=1.21.9
    /*@Unique private boolean synchronize$formatterInstalled;*/
    @Unique private int synchronize$labelY;

    protected AddServerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void synchronize$addHandshakeField(CallbackInfo ci) {
        if (synchronize$pendingCode == null) {
            synchronize$pendingCode = server instanceof HandshakeServerEntry entry ? entry.synchronize$getHandshakeCode() : "";
        }
        int left = this.width / 2 - 100;
        int addressBottom = GuiCompat.getY(addressField) + addressField.getHeight();
        synchronize$labelY = addressBottom + SYNCHRONIZE_LABEL_GAP;
        int fieldY = synchronize$labelY + 12;

        List<ClickableWidget> below = new ArrayList<>();
        for (Element child : this.children()) {
            if (child instanceof ClickableWidget widget && !(widget instanceof TextFieldWidget) && GuiCompat.getY(widget) >= addressBottom) {
                below.add(widget);
            }
        }
        below.sort(Comparator.comparingInt(GuiCompat::getY));

        synchronize$handshakeField = new TextFieldWidget(this.textRenderer, left, fieldY, 200 - SYNCHRONIZE_TOGGLE_WIDTH - 4, 20,
                Text.translatable("synchronize.handshake.field"));
        synchronize$handshakeField.setMaxLength(HandshakeCrypto.MAX_CODE_LENGTH);
        synchronize$handshakeField.setText(synchronize$pendingCode);
         
        //? if >=1.19.3
        synchronize$handshakeField.setPlaceholder(Text.translatable("synchronize.handshake.placeholder").formatted(Formatting.DARK_GRAY));
        synchronize$handshakeField.setChangedListener(text -> synchronize$pendingCode = text);
        synchronize$applyMask();
        this.addDrawableChild(synchronize$handshakeField);

        synchronize$toggleButton = ButtonWidget.builder(Text.empty(), b -> {
            synchronize$revealed = !synchronize$revealed;
            synchronize$applyMask();
            //? if >=1.19.3
            b.setTooltip(Tooltip.of(synchronize$toggleText()));
        }).dimensions(left + 200 - SYNCHRONIZE_TOGGLE_WIDTH, fieldY, SYNCHRONIZE_TOGGLE_WIDTH, 20)
                //? if <1.19.3 {
                /*.tooltip(Tooltip.dynamic(this::synchronize$toggleText)).build();
                *///?} else
                .tooltip(Tooltip.of(synchronize$toggleText())).build();
        this.addDrawableChild(synchronize$toggleButton);

        if (!below.isEmpty()) {
            int start = Math.max(GuiCompat.getY(below.get(0)), fieldY + 20 + 8);
            int originalFirst = GuiCompat.getY(below.get(0));
            int originalSpan = GuiCompat.getY(below.get(below.size() - 1)) - originalFirst;
            ClickableWidget last = below.get(below.size() - 1);
            boolean compact = start + originalSpan + last.getHeight() > this.height - 4;
            int y = start;
            int previousOriginal = originalFirst;
            for (int i = 0; i < below.size(); i++) {
                ClickableWidget widget = below.get(i);
                if (i > 0) {
                    int gap = GuiCompat.getY(widget) - previousOriginal;
                    y += compact ? Math.min(gap, 24) : gap;
                }
                previousOriginal = GuiCompat.getY(widget);
                GuiCompat.setY(widget, y);
            }
        }
    }

    @Unique
    private Text synchronize$toggleText() {
        return Text.translatable(synchronize$revealed ? "synchronize.handshake.hide" : "synchronize.handshake.show");
    }

    @Unique
    private void synchronize$applyMask() {
        if (synchronize$handshakeField == null) return;
         
        //? if >=1.21.9 {
        /*if (!synchronize$formatterInstalled) {
            synchronize$formatterInstalled = true;
            synchronize$handshakeField.addFormatter((text, firstIndex) -> OrderedText.styledForwardsVisitedString(
                    synchronize$revealed ? text : "*".repeat(text.length()), Style.EMPTY));
        }
        *///?} else {
        if (synchronize$revealed) {
            synchronize$handshakeField.setRenderTextProvider((text, firstIndex) -> OrderedText.styledForwardsVisitedString(text, Style.EMPTY));
        } else {
            synchronize$handshakeField.setRenderTextProvider((text, firstIndex) ->
                    OrderedText.styledForwardsVisitedString("*".repeat(text.length()), Style.EMPTY));
        }
        //?}
    }

     
    //? if <1.20.2 {
    @Inject(method = "tick", at = @At("TAIL"))
    private void synchronize$tickHandshakeField(CallbackInfo ci) {
        if (synchronize$handshakeField != null) synchronize$handshakeField.tick();
    }
    //?}

    //? if <1.20 {
    /*@Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V"))
    private void synchronize$renderHandshakeLabel(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        DrawContext context = new DrawContext(matrices);
    *///?} else {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void synchronize$renderHandshakeLabel(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    //?}
        if (synchronize$handshakeField != null) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("synchronize.handshake.field"),
                    this.width / 2 - 100, synchronize$labelY, 0xA0A0A0);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    //? if <1.20 {
    /*private void synchronize$renderToggleIcon(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        DrawContext context = new DrawContext(matrices);
    *///?} else
    private void synchronize$renderToggleIcon(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (synchronize$toggleButton == null || !synchronize$toggleButton.visible) return;
        Identifier icon = synchronize$revealed ? SYNCHRONIZE_EYE_ICON : SYNCHRONIZE_INVISIBLE_ICON;
        int x = GuiCompat.getX(synchronize$toggleButton) + (synchronize$toggleButton.getWidth() - SYNCHRONIZE_ICON_SIZE) / 2;
        int y = GuiCompat.getY(synchronize$toggleButton) + (synchronize$toggleButton.getHeight() - SYNCHRONIZE_ICON_SIZE) / 2;
        GuiCompat.enableBlend();
        GuiCompat.drawTextureScaled(context, icon, x, y, SYNCHRONIZE_ICON_SIZE, SYNCHRONIZE_ICON_SIZE, 0, 0,
                SYNCHRONIZE_ICON_TEXTURE_SIZE, SYNCHRONIZE_ICON_TEXTURE_SIZE, SYNCHRONIZE_ICON_TEXTURE_SIZE, SYNCHRONIZE_ICON_TEXTURE_SIZE);
        GuiCompat.disableBlend();
    }

    @Inject(method = "addAndClose", at = @At("HEAD"))
    private void synchronize$saveHandshake(CallbackInfo ci) {
        if (server instanceof HandshakeServerEntry entry && synchronize$handshakeField != null) {
            entry.synchronize$setHandshakeCode(synchronize$handshakeField.getText());
        }
    }

    @Inject(method = "resize", at = @At("HEAD"))
     
    //? if >=1.21.11 {
    /*private void synchronize$keepHandshakeOnResize(int width, int height, CallbackInfo ci) {
    *///?} else
    private void synchronize$keepHandshakeOnResize(MinecraftClient client, int width, int height, CallbackInfo ci) {
        if (synchronize$handshakeField != null) synchronize$pendingCode = synchronize$handshakeField.getText();
    }
}
