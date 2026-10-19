package net.ptah.synchronize.client.screen;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.ptah.synchronize.compat.Identifiers;
import net.ptah.synchronize.client.compat.GuiCompat;
import net.ptah.synchronize.client.profile.ProfileManager;
import net.ptah.synchronize.client.profile.ProfileStorage;

public final class ProfilesScreen extends Screen {
    private static final Identifier DELETE_ICON = Identifiers.mod("textures/gui/delete_icon.png");
    private final Screen parent;
    private String selected;
    private String active = "default";
    private String pending = "";
    private String activeName = "Default";
    private String error;
    private ProfileList profileList;
    private ButtonWidget applyButton;
    private ButtonWidget applyCloseButton;

    public ProfilesScreen(Screen parent) { super(Text.translatable("synchronize.profiles.title")); this.parent = parent; }

    @Override
    protected void init() {
        applyButton = null;
        applyCloseButton = null;
        profileList = null;
        try {
            List<String> ids = ProfileManager.profiles();
            active = ProfileManager.active();
            activeName = ProfileManager.name(active);
            pending = ProfileStorage.state(ProfileManager.root()).getProperty("pending", "");
            if (selected == null || !ids.contains(selected)) selected = active;
            profileList = new ProfileList();
            for (String id : ids) {
                ProfileRow row = new ProfileRow(id, ProfileStorage.metadata(ProfileManager.root(), id));
                profileList.append(row);
                if (id.equals(selected)) profileList.setSelected(row);
            }
        } catch (IOException e) { error = e.getMessage(); }
        if (profileList == null) profileList = new ProfileList();
        addDrawableChild(profileList);
        profileList.revealSelected();
        Text saveText = Text.translatable("synchronize.profiles.apply_close");
        Text selectText = Text.translatable("synchronize.profiles.apply");
        Text backText = Text.translatable("synchronize.profiles.back");
        int saveWidth = 140;
        int selectWidth = 90;
        int backWidth = 64;
        int x = (width - saveWidth - selectWidth - backWidth - 12) / 2;
        applyCloseButton = addDrawableChild(ButtonWidget.builder(saveText, b -> apply(true))
                .dimensions(x, height - 29, saveWidth, 20).tooltip(Tooltip.of(Text.translatable("synchronize.profiles.apply_close_hint"))).build());
        applyButton = addDrawableChild(ButtonWidget.builder(selectText, b -> apply(false))
                .dimensions(x + saveWidth + 6, height - 29, selectWidth, 20).tooltip(Tooltip.of(Text.translatable("synchronize.profiles.apply_hint"))).build());
        addDrawableChild(ButtonWidget.builder(backText, b -> close())
                .dimensions(x + saveWidth + selectWidth + 12, height - 29, backWidth, 20).build());
        if (!pending.isEmpty()) {
            Text cancelText = Text.translatable("synchronize.profiles.cancel_pending");
            int cancelWidth = pendingCancelWidth();
            int boxWidth = pendingBoxWidth();
            //? if <1.20 {
            /*addDrawable((matrices, mouseX, mouseY, delta) -> renderPending(new DrawContext(matrices)));
            *///?} else
            addDrawable((context, mouseX, mouseY, delta) -> renderPending(context));
            addDrawableChild(ButtonWidget.builder(cancelText, b -> {
                try { ProfileStorage.cancel(ProfileManager.root()); error = null; clearAndInit(); }
                catch (IOException e) { error = e.getMessage(); }
            }).dimensions((width + boxWidth) / 2 - cancelWidth - 4, height - 67, cancelWidth, 20).build());
        }
        updateButtons();
    }

    private int pendingCancelWidth() {
        return textRenderer.getWidth(Text.translatable("synchronize.profiles.cancel_pending")) + 16;
    }

    private int pendingBoxWidth() {
        return Math.min(width - 24, textRenderer.getWidth(Text.translatable("synchronize.profiles.pending")) + pendingCancelWidth() + 30);
    }

    private void renderPending(DrawContext context) {
        int boxWidth = pendingBoxWidth();
        int x = (width - boxWidth) / 2;
        int y = height - 70;
        int cancelX = (width + boxWidth) / 2 - pendingCancelWidth() - 4;
        int warningX = cancelX - 14;
        int textWidth = warningX - x - 10;
        context.fill(x, y, x + boxWidth, y + 26, 0xFF786E46);
        context.fill(x + 1, y + 1, x + boxWidth - 1, y + 25, 0xFF2D2A1C);
        context.fill(x + 2, y + 2, x + boxWidth - 2, y + 24, 0xFF1C1B15);
        context.drawTextWithShadow(textRenderer, Text.literal(fit(Text.translatable("synchronize.profiles.pending").getString(), textWidth)), x + 6, y + 9, 0xFFFFFF55);
        for (int row = 0; row <= 8; row++) {
            int halfWidth = row / 2;
            context.fill(warningX + 4 - halfWidth, y + 8 + row, warningX + 5 + halfWidth, y + 9 + row, 0xFFFFFF55);
        }
        context.fill(warningX + 4, y + 11, warningX + 5, y + 14, 0xFF41370F);
        context.fill(warningX + 4, y + 15, warningX + 5, y + 16, 0xFF41370F);
    }

    private int footerHeight() { return pending.isEmpty() ? 64 : 78; }

    private void updateButtons() {
        ProfileRow row = profileList == null ? null : profileList.getSelectedOrNull();
        boolean changed = row != null && (!row.id.equals(active) || row.hasChanges);
        if (applyButton != null) applyButton.active = changed && !row.id.equals(pending);
        if (applyCloseButton != null) applyCloseButton.active = changed;
    }

    private void apply(boolean close) {
        if (selected == null) return;
        try { ProfileManager.queue(selected, close); error = null; if (!close) clearAndInit(); }
        catch (IOException e) { error = e.getMessage(); }
    }

    private void confirmDelete(String id) {
        boolean deletingActive = id.equals(active);
        Text body = Text.translatable(deletingActive ? "synchronize.profiles.delete_active_body" : "synchronize.profiles.delete_body");
        client.setScreen(new ProfileMessageScreen(this, Text.translatable("synchronize.profiles.delete_title"), body,
                Text.translatable(deletingActive ? "synchronize.button.delete_quit" : "synchronize.profiles.delete"), () -> {
            try { ProfileManager.delete(id); if (!deletingActive) client.setScreen(this); }
            catch (IOException e) { error = e.getMessage(); client.setScreen(this); }
        }, Text.translatable("synchronize.button.cancel"), () -> client.setScreen(this)));
    }

    private String fit(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        return textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth("..."))) + "...";
    }

    @Override
    //? if <1.20 {
    /*public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
    *///?} else {
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        //? if <1.20.2
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
    //?}
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        Text subtitle = error == null ? Text.translatable("synchronize.profiles.active", activeName) : Text.literal(error);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(fit(subtitle.getString(), width - 24)), width / 2, 30, error == null ? 0xFFAAAAAA : 0xFFFF5555);
        if (pending.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(fit(Text.translatable("synchronize.profiles.restart_hint").getString(), width - 24)), width / 2, height - 51, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    //? if >=1.21.9 {
    /*public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
    *///?} else
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 261 && getFocused() == profileList && selected != null && !selected.equals("default")) {
            confirmDelete(selected);
            return true;
        }
        //? if >=1.21.9 {
        /*return super.keyPressed(input);
        *///?} else
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private final class ProfileList extends AlwaysSelectedEntryListWidget<ProfileRow> {
        ProfileList() {
            //? if >=1.20.3 {
            /*super(ProfilesScreen.this.client, ProfilesScreen.this.width, Math.max(44, ProfilesScreen.this.height - 48 - footerHeight()), 48, 44);
            *///?} else
            super(ProfilesScreen.this.client, ProfilesScreen.this.width, ProfilesScreen.this.height, 48, Math.max(92, ProfilesScreen.this.height - footerHeight()), 44);
        }

        void append(ProfileRow row) { addEntry(row); }
        void revealSelected() { if (getSelectedOrNull() != null) centerScrollOn(getSelectedOrNull()); }

        @Override
        public int getRowWidth() { return Math.min(420, ProfilesScreen.this.width - 48); }

        @Override
        //? if >=1.20.5 {
        /*protected int getScrollbarX() {
        *///?} else
        protected int getScrollbarPositionX() {
            return ProfilesScreen.this.width / 2 + getRowWidth() / 2 + 6;
        }

        @Override
        //? if >=1.21.9 {
        /*protected boolean isEntrySelectionAllowed() { return true; }
        *///?} else
        protected boolean isSelectedEntry(int index) { return children().get(index) == getSelectedOrNull(); }

        @Override
        public void setSelected(ProfileRow row) {
            super.setSelected(row);
            if (row != null) selected = row.id;
            updateButtons();
        }
    }

    private final class ProfileRow extends AlwaysSelectedEntryListWidget.Entry<ProfileRow> {
        private final String id;
        private final String name;
        private final String address;
        private final Text status;
        private final boolean hasChanges;
        private final ButtonWidget deleteButton;

        ProfileRow(String id, Properties metadata) throws IOException {
            this.id = id;
            name = metadata.getProperty("name", id);
            address = id.equals("default") ? Text.translatable("synchronize.profiles.default_hint").getString() : metadata.getProperty("server", "");
            boolean isActive = id.equals(active);
            boolean isPending = id.equals(pending);
            Text activeStatus = Text.translatable("synchronize.profiles.status_active").formatted(Formatting.GREEN);
            Text pendingStatus = Text.translatable("synchronize.profiles.status_pending").formatted(Formatting.YELLOW, Formatting.ITALIC);
            status = isActive && isPending ? Text.empty().append(activeStatus).append(" / ").append(pendingStatus)
                    : isPending ? pendingStatus : isActive ? activeStatus : Text.empty();
            hasChanges = !ProfileStorage.profileData(ProfileManager.root(), id, "delta").isEmpty();
            deleteButton = id.equals("default") ? null : ButtonWidget.builder(Text.empty(), b -> confirmDelete(id))
                    .dimensions(0, 0, 24, 24).tooltip(Tooltip.of(Text.translatable("synchronize.profiles.delete_hint", name))).build();
        }

        @Override
        //? if >=1.21.9 {
        /*public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX();
            int y = getContentY();
            int entryWidth = getContentWidth();
            int entryHeight = getContentHeight();
        *///?} else if <1.20 {
        /*public void render(net.minecraft.client.util.math.MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            DrawContext context = new DrawContext(matrices);
        *///?} else
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            int buttonX = x + entryWidth - 28;
            int textWidth = Math.max(0, buttonX - x - 12);
            Text suffix = status.getString().isEmpty() ? Text.empty() : Text.literal(" - ").append(status);
            int suffixWidth = textRenderer.getWidth(suffix) + (id.equals(pending) ? 2 : 0);
            String visibleName = fit(name, Math.max(0, textWidth - suffixWidth));
            if (id.equals(active)) context.fill(x - 2, y - 2, x, y + entryHeight + 2, 0xFF55FF55);
            context.drawTextWithShadow(textRenderer, Text.literal(visibleName).append(suffix), x + 4, y + 6, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal(fit(address, textWidth)), x + 4, y + 21, 0xFFAAAAAA);
            if (deleteButton != null) {
                int buttonY = y + (entryHeight - 24) / 2;
                GuiCompat.setX(deleteButton, buttonX);
                GuiCompat.setY(deleteButton, buttonY);
                //? if <1.20 {
                /*deleteButton.render(context.getMatrices(), mouseX, mouseY, delta);
                *///?} else
                deleteButton.render(context, mouseX, mouseY, delta);
                GuiCompat.enableBlend();
                GuiCompat.drawTextureScaled(context, DELETE_ICON, buttonX + 4, buttonY + 4, 16, 16, 0, 0, 512, 512, 512, 512);
                GuiCompat.disableBlend();
            }
        }

        @Override
        //? if >=1.21.9 {
        /*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
            if (click.button() != 0) return false;
            profileList.setSelected(this);
            if (deleteButton != null) deleteButton.mouseClicked(click, doubled);
        *///?} else {
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            profileList.setSelected(this);
            if (deleteButton != null) deleteButton.mouseClicked(mouseX, mouseY, button);
        //?}
            return true;
        }

        @Override
        public Text getNarration() {
            return Text.literal(name + ". " + address + (status.getString().isEmpty() ? "" : ". " + status.getString()));
        }
    }
}
