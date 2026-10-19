package net.ptah.synchronize.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.ptah.synchronize.client.profile.ProfileManager;
import net.ptah.synchronize.client.screen.ProfilesScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerProfileMixin extends Screen {
    protected MultiplayerProfileMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void synchronize$profiles(CallbackInfo ci) {
        String label = Text.translatable("synchronize.profiles.button", ProfileManager.activeName()).getString();
        String pending = ProfileManager.pendingName();
        Text tooltip = Text.literal(label + "\n" + Text.translatable("synchronize.profiles.open_hint").getString()
                + (pending.isEmpty() ? "" : "\n" + Text.translatable("synchronize.profiles.pending", pending).getString()));
        int buttonWidth = Math.max(60, Math.min(128, (width - textRenderer.getWidth(title)) / 2 - 16));
        String fitted = textRenderer.getWidth(label) <= buttonWidth - 12 ? label
                : textRenderer.trimToWidth(label, Math.max(0, buttonWidth - 12 - textRenderer.getWidth("..."))) + "...";
        addDrawableChild(ButtonWidget.builder(Text.literal(fitted), b -> client.setScreen(new ProfilesScreen((Screen) (Object) this)))
                .dimensions(8, 7, buttonWidth, 18).tooltip(Tooltip.of(tooltip)).build());
    }
}
