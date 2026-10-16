package net.ptah.synchronize.client.screen;

import net.ptah.synchronize.client.compat.GuiCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;
import net.minecraft.util.math.MathHelper;

public class PromptScreen extends SynchronizeScreen {
    private static final Identifier MAIN_ICON_TEXTURE = Identifiers.mod("textures/gui/main_icon.png");
    private static final String ELLIPSIS = "...";
    private static final String REQUIRED_SECTION = "section:required";
    private static final String OPTIONAL_SECTION = "section:optional";

    private static final int ROW_HEIGHT = 14;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int MAX_VISIBLE_ROWS = 18;
    private static final int MAX_PANEL_WIDTH = 360;
    private static final int MAX_TEXT_WIDTH = 400;
    private static final int LINE_HEIGHT = 10;
    private static final int IMAGE_WIDTH = 34;
    private static final int IMAGE_HEIGHT = 20;
    private static final int TOOLBAR_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MAX_BUTTON_WIDTH = 120;
    private static final int INDENT = 10;

    private final Screen parent;
    private final Consumer<List<String>> onConfirm;
    private final Runnable onDecline;
    private final List<String> mandatoryFiles;
    private final List<String> optionalKeys;
    private final Map<String, Boolean> optionalSelection = new LinkedHashMap<>();

    private final TreeNode requiredRoot = new TreeNode("", "", false);
    private final TreeNode optionalRoot = new TreeNode("", "", false);
    private final Set<String> expanded = new HashSet<>();
    private final List<Row> displayRows = new ArrayList<>();

    private int scrollOffset;
    private boolean draggingScrollbar;

    private ButtonWidget actionButton;
    private ButtonWidget selectAllButton;

    private List<OrderedText> headerLines = List.of();
    private List<OrderedText> infoLines = List.of();
    private boolean showImage;
    private int imageY;
    private int headerY;
    private int infoY;
    private int toolbarY;
    private int panelLeft;
    private int panelWidth;
    private int panelTop;
    private int panelHeight;
    private int visibleRows;
    private int statusY;
    private int buttonY;

    private int animationTick = 0;
    private int animationFrame = 0;

    public PromptScreen(Screen parent, List<String> mandatoryFiles, Map<String, String> optionalDisplayNames, Consumer<List<String>> onConfirm, Runnable onDecline) {
        super(Text.translatable("synchronize.prompt.title"));
        this.parent = parent;
        this.mandatoryFiles = new ArrayList<>(mandatoryFiles);
        this.optionalKeys = new ArrayList<>(optionalDisplayNames.keySet());
        this.onConfirm = onConfirm;
        this.onDecline = onDecline;
        for (String key : optionalKeys) {
            this.optionalSelection.put(key, Boolean.FALSE);
        }
        buildTree(requiredRoot, this.mandatoryFiles, "r:");
        buildTree(optionalRoot, this.optionalKeys, "o:");
        expanded.add(REQUIRED_SECTION);
        expanded.add(OPTIONAL_SECTION);
    }

    private void buildTree(TreeNode root, List<String> keys, String expandPrefix) {
        for (String key : keys) {
            String[] parts = key.split("/");
            TreeNode current = root;
            String currentPath = "";
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLast = i == parts.length - 1;
                currentPath = currentPath.isEmpty() ? part : currentPath + "/" + part;

                TreeNode next = null;
                for (TreeNode child : current.children) {
                    if (child.name.equals(part) && child.isFile == isLast) {
                        next = child;
                        break;
                    }
                }
                if (next == null) {
                    next = new TreeNode(part, isLast ? key : currentPath, isLast);
                    current.children.add(next);
                    if (!isLast) expanded.add(expandPrefix + currentPath);
                }
                current = next;
            }
        }
    }

    private static class TreeNode {
        final String name;
        final String path;
        final boolean isFile;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(String name, String path, boolean isFile) {
            this.name = name;
            this.path = path;
            this.isFile = isFile;
        }

        List<String> getAllFileKeys() {
            List<String> keys = new ArrayList<>();
            if (isFile) {
                keys.add(path);
            } else {
                for (TreeNode c : children) {
                    keys.addAll(c.getAllFileKeys());
                }
            }
            return keys;
        }
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

    private void rebuildRows() {
        displayRows.clear();
        if (!mandatoryFiles.isEmpty()) {
            boolean open = expanded.contains(REQUIRED_SECTION);
            displayRows.add(new SectionRow(true, open));
            if (open) {
                for (TreeNode child : requiredRoot.children) buildRows(child, 1, true);
            }
        }
        if (!optionalKeys.isEmpty()) {
            boolean open = expanded.contains(OPTIONAL_SECTION);
            displayRows.add(new SectionRow(false, open));
            if (open) {
                for (TreeNode child : optionalRoot.children) buildRows(child, 1, false);
            }
        }
    }

    private void buildRows(TreeNode node, int depth, boolean required) {
        if (node.isFile) {
            displayRows.add(new FileRow(node.path, node.name, depth, required));
            return;
        }
        String expandKey = (required ? "r:" : "o:") + node.path;
        boolean open = expanded.contains(expandKey);
        displayRows.add(new CategoryRow(node.name, expandKey, node.getAllFileKeys(), open, depth, required));
        if (open) {
            for (TreeNode child : node.children) buildRows(child, depth + 1, required);
        }
    }

    private void calculateLayout() {
        int margin = this.height < 300 ? 4 : 10;
        int gap = this.height < 300 ? 3 : 6;

        panelWidth = Math.max(120, Math.min(MAX_PANEL_WIDTH, this.width - margin * 2 - 8));
        panelLeft = (this.width - panelWidth) / 2;
        int textWidth = Math.max(120, Math.min(MAX_TEXT_WIDTH, this.width - margin * 2 - 8));
        headerLines = this.textRenderer.wrapLines(Text.translatable("synchronize.prompt.header"), textWidth);
        infoLines = this.textRenderer.wrapLines(Text.translatable("synchronize.prompt.info"), textWidth);

        int textBlock = headerLines.size() * LINE_HEIGHT + gap / 2 + infoLines.size() * LINE_HEIGHT;
        int toolbar = optionalKeys.isEmpty() ? 0 : TOOLBAR_HEIGHT + 2;
        int status = LINE_HEIGHT + gap;
        int baseFixed = margin + textBlock + gap + toolbar + 4 + status + BUTTON_HEIGHT + margin;
        int imageBlock = IMAGE_HEIGHT + gap;

        int rowsAvailable = (this.height - baseFixed - imageBlock - 4) / ROW_HEIGHT;
        showImage = rowsAvailable >= Math.min(MIN_VISIBLE_ROWS + 2, displayRows.size());
        if (!showImage) {
            rowsAvailable = (this.height - baseFixed - 4) / ROW_HEIGHT;
        }

        visibleRows = MathHelper.clamp(displayRows.size(), Math.min(MIN_VISIBLE_ROWS, displayRows.size()), Math.max(1, Math.min(MAX_VISIBLE_ROWS, rowsAvailable)));
        panelHeight = visibleRows * ROW_HEIGHT + 4;
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll());

        int total = baseFixed - margin * 2 + (showImage ? imageBlock : 0) + panelHeight;
        int y = Math.max(margin, (this.height - total) / 2);

        if (showImage) {
            imageY = y;
            y += imageBlock;
        }
        headerY = y;
        y += headerLines.size() * LINE_HEIGHT + gap / 2;
        infoY = y;
        y += infoLines.size() * LINE_HEIGHT + gap;
        if (toolbar > 0) {
            toolbarY = y;
            y += toolbar;
        }
        panelTop = y;
        y += panelHeight + 4;
        statusY = y;
        y += status;
        buttonY = Math.min(y, this.height - BUTTON_HEIGHT - 2);
    }

    @Override
    protected void init() {
        rebuildRows();
        calculateLayout();

        int midX = this.width / 2;

        if (!optionalKeys.isEmpty()) {
            Text label = selectAllLabel();
            int labelWidth = Math.max(this.textRenderer.getWidth(Text.translatable("synchronize.button.select_all")),
                    this.textRenderer.getWidth(Text.translatable("synchronize.button.clear")));
            int width = Math.min(panelWidth / 2, labelWidth + 12);
            selectAllButton = ButtonWidget.builder(label, button -> {
                boolean newState = !allSelected();
                for (String key : optionalKeys) {
                    optionalSelection.put(key, newState);
                }
                refreshSelectAllLabel();
                updateActionButtonLabel();
            }).dimensions(panelLeft + panelWidth - width, toolbarY, width, TOOLBAR_HEIGHT).build();
            this.addDrawableChild(selectAllButton);
        } else {
            selectAllButton = null;
        }

        int buttonWidth = Math.max(40, Math.min(MAX_BUTTON_WIDTH, (panelWidth - 6) / 2));
        actionButton = ButtonWidget.builder(Text.translatable("synchronize.button.download"), button -> this.onConfirm.accept(buildRequestedList()))
            .dimensions(midX - buttonWidth - 3, buttonY, buttonWidth, BUTTON_HEIGHT)
            .build();
        this.addDrawableChild(actionButton);

        ButtonWidget disconnectButton = ButtonWidget.builder(Text.translatable("synchronize.button.disconnect"), button -> {
            this.onDecline.run();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(midX + 3, buttonY, buttonWidth, BUTTON_HEIGHT).build();
        this.addDrawableChild(disconnectButton);

        updateActionButtonLabel();
    }

    private void refreshLayout() {
        this.clearAndInit();
    }

    private void updateActionButtonLabel() {
        if (actionButton == null) return;
        boolean anythingOptionalSelected = optionalSelection.values().stream().anyMatch(Boolean::booleanValue);
        boolean nothingToFetch = mandatoryFiles.isEmpty() && !anythingOptionalSelected;
        actionButton.setMessage(Text.translatable(nothingToFetch ? "synchronize.button.join" : "synchronize.button.download"));
    }

    private boolean allSelected() {
        return !optionalSelection.isEmpty() && optionalSelection.values().stream().allMatch(Boolean::booleanValue);
    }

    private Text selectAllLabel() {
        return Text.translatable(allSelected() ? "synchronize.button.clear" : "synchronize.button.select_all").styled(style -> style.withColor(Formatting.YELLOW));
    }

    private void refreshSelectAllLabel() {
        if (selectAllButton != null) {
            selectAllButton.setMessage(selectAllLabel());
        }
    }

    private List<String> buildRequestedList() {
        List<String> requested = new ArrayList<>(mandatoryFiles);
        for (Map.Entry<String, Boolean> e : optionalSelection.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                requested.add(e.getKey());
            }
        }
        return requested;
    }

    private int maxScroll() {
        return Math.max(0, displayRows.size() - visibleRows);
    }

    private boolean hasScrollbar() {
        return displayRows.size() > visibleRows;
    }

    private int listWidth() {
        return panelWidth - (hasScrollbar() ? SCROLLBAR_WIDTH : 0);
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= panelLeft && mouseX < panelLeft + listWidth() && mouseY >= panelTop + 2 && mouseY < panelTop + panelHeight - 2;
    }

    private int rowIndexAt(double mouseY) {
        return scrollOffset + (int) ((mouseY - (panelTop + 2)) / ROW_HEIGHT);
    }

    @Override
     
    //? if >=1.20.2 {
    /*public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
    *///?} else
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll() > 0) {
            if (amount < 0) {
                scrollOffset = Math.min(maxScroll(), scrollOffset + 1);
            } else if (amount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            }
            return true;
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
        if (button == 0 && !displayRows.isEmpty()) {
            int panelRight = panelLeft + panelWidth;
            if (hasScrollbar() && mouseX >= panelRight - SCROLLBAR_WIDTH && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelTop + panelHeight) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
            if (isInsideList(mouseX, mouseY)) {
                int rowIdx = rowIndexAt(mouseY);
                if (rowIdx >= 0 && rowIdx < displayRows.size()) {
                    displayRows.get(rowIdx).onClick((int) (mouseX - panelLeft), listWidth());
                    return true;
                }
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
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
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
            draggingScrollbar = false;
        }
        //? if >=1.21.9 {
        /*return super.mouseReleased(click);
        *///?} else
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int thumbHeight() {
        return Math.max(8, panelHeight * visibleRows / Math.max(1, displayRows.size()));
    }

    private void updateScrollFromMouse(double mouseY) {
        int thumbHeight = thumbHeight();
        float fraction = (float) (mouseY - panelTop - thumbHeight / 2.0) / (float) Math.max(1, panelHeight - thumbHeight);
        fraction = MathHelper.clamp(fraction, 0.0F, 1.0F);
        scrollOffset = Math.round(fraction * maxScroll());
    }

    private Text buildStatusLine() {
        int total = mandatoryFiles.size() + optionalKeys.size();
        int selectedCount = (int) optionalSelection.values().stream().filter(Boolean::booleanValue).count();
        MutableText status = Text.literal("");
        boolean hasContent = false;
        if (hasScrollbar()) {
            status.append(Text.translatable("synchronize.prompt.total_scroll", total).styled(style -> style.withColor(Formatting.GRAY)));
            hasContent = true;
        }
        if (selectedCount > 0) {
            if (hasContent) {
                status.append(Text.literal("  |  ").styled(style -> style.withColor(Formatting.DARK_GRAY)));
            }
            status.append(Text.translatable("synchronize.prompt.selected", selectedCount).styled(style -> style.withColor(Formatting.GREEN)));
            hasContent = true;
        }
        return hasContent ? status : null;
    }

    private String fitText(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        int ellipsisWidth = textRenderer.getWidth(ELLIPSIS);
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - ellipsisWidth)) + ELLIPSIS;
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

        for (int i = 0; i < headerLines.size(); i++) {
            context.drawCenteredTextWithShadow(this.textRenderer, headerLines.get(i), midX, headerY + i * LINE_HEIGHT, 0xFFFFFFFF);
        }
        for (int i = 0; i < infoLines.size(); i++) {
            context.drawCenteredTextWithShadow(this.textRenderer, infoLines.get(i), midX, infoY + i * LINE_HEIGHT, 0xFFFFAA00);
        }

        if (selectAllButton != null) {
            int labelMax = panelWidth - selectAllButton.getWidth() - 6;
            String label = fitText(Text.translatable("synchronize.prompt.choose_optional").getString(), labelMax);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), panelLeft + 2, toolbarY + (TOOLBAR_HEIGHT - 8) / 2, 0xFFFFFFFF);
        }

        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xB0101010);
        context.fill(panelLeft, panelTop - 1, panelRight, panelTop, 0xFF555555);
        context.fill(panelLeft, panelBottom, panelRight, panelBottom + 1, 0xFF555555);
        context.fill(panelLeft - 1, panelTop - 1, panelLeft, panelBottom + 1, 0xFF555555);
        context.fill(panelRight, panelTop - 1, panelRight + 1, panelBottom + 1, 0xFF555555);

        int listWidth = listWidth();
        context.enableScissor(panelLeft, panelTop + 2, panelLeft + listWidth, panelBottom - 2);
        for (int i = 0; i <= visibleRows; i++) {
            int rowIdx = scrollOffset + i;
            if (rowIdx >= displayRows.size()) break;
            int rowY = panelTop + 2 + i * ROW_HEIGHT;
            displayRows.get(rowIdx).render(context, panelLeft, rowY, listWidth, mouseX, mouseY);
        }
        context.disableScissor();

        if (hasScrollbar()) {
            int thumbHeight = thumbHeight();
            int maxOffset = maxScroll();
            int thumbY = panelTop + (maxOffset == 0 ? 0 : (int) ((float) scrollOffset / maxOffset * (panelHeight - thumbHeight)));
            context.fill(panelRight - SCROLLBAR_WIDTH, panelTop, panelRight, panelBottom, 0xFF333333);
            context.fill(panelRight - SCROLLBAR_WIDTH, thumbY, panelRight, thumbY + thumbHeight, draggingScrollbar ? 0xFFAAAAAA : 0xFF888888);
        }

        Text statusLine = buildStatusLine();
        if (statusLine != null) {
            String fitted = fitText(statusLine.getString(), panelWidth - 4);
            if (fitted.equals(statusLine.getString())) {
                context.drawTextWithShadow(this.textRenderer, statusLine, panelLeft + 2, statusY, 0xFFFFFFFF);
            } else {
                context.drawTextWithShadow(this.textRenderer, Text.literal(fitted), panelLeft + 2, statusY, 0xFFAAAAAA);
            }
        }

        if (!draggingScrollbar && isInsideList(mouseX, mouseY)) {
            int rowIdx = rowIndexAt(mouseY);
            if (rowIdx >= 0 && rowIdx < displayRows.size()) {
                displayRows.get(rowIdx).renderTooltip(context, mouseX, mouseY, listWidth);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private abstract class Row {
        final int depth;

        Row(int depth) {
            this.depth = depth;
        }

        abstract void render(DrawContext context, int x, int y, int width, int mouseX, int mouseY);

        abstract void onClick(int x, int width);

        void renderTooltip(DrawContext context, int mouseX, int mouseY, int width) {
        }

        boolean isHovered(int x, int y, int width, int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
        }
    }

    private class SectionRow extends Row {
        final boolean required;
        final boolean open;

        SectionRow(boolean required, boolean open) {
            super(0);
            this.required = required;
            this.open = open;
        }

        private Text label() {
            int count = required ? mandatoryFiles.size() : optionalKeys.size();
            return Text.translatable(required ? "synchronize.prompt.required_section" : "synchronize.prompt.optional_section", count);
        }

        @Override
        void render(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
            context.fill(x, y, x + width, y + ROW_HEIGHT, 0x60000000);
            if (isHovered(x, y, width, mouseX, mouseY)) {
                context.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);
            }
            String prefix = open ? "▼ " : "▶ ";
            int color = required ? 0xFF55FFFF : 0xFFFFFF55;
            String text = fitText(prefix + label().getString(), width - 8);
            context.drawTextWithShadow(textRenderer, Text.literal(text), x + 4, y + 3, color);
        }

        @Override
        void onClick(int x, int width) {
            String key = required ? REQUIRED_SECTION : OPTIONAL_SECTION;
            if (!expanded.remove(key)) expanded.add(key);
            refreshLayout();
        }
    }

    private class CategoryRow extends Row {
        final String name;
        final String expandKey;
        final List<String> keys;
        final boolean open;
        final boolean required;

        CategoryRow(String name, String expandKey, List<String> keys, boolean open, int depth, boolean required) {
            super(depth);
            this.name = name;
            this.expandKey = expandKey;
            this.keys = keys;
            this.open = open;
            this.required = required;
        }

        private int indent(int width) {
            return Math.min(depth * INDENT, width / 3);
        }

        @Override
        void render(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
            context.fill(x, y, x + width, y + ROW_HEIGHT, 0x30000000);
            if (isHovered(x, y, width, mouseX, mouseY)) {
                context.fill(x, y, x + width, y + ROW_HEIGHT, 0x20FFFFFF);
            }
            int indent = indent(width);
            String prefix = open ? "▼ " : "▶ ";
            int boxSpace = 0;
            if (!required) {
                boolean all = keys.stream().allMatch(k -> Boolean.TRUE.equals(optionalSelection.get(k)));
                boolean some = keys.stream().anyMatch(k -> Boolean.TRUE.equals(optionalSelection.get(k)));
                String box = all ? "[x]" : (some ? "[-]" : "[ ]");
                int boxColor = all ? 0xFF55FF55 : (some ? 0xFFFFFF55 : 0xFFAAAAAA);
                int boxWidth = textRenderer.getWidth(box);
                boxSpace = boxWidth + 8;
                context.drawTextWithShadow(textRenderer, Text.literal(box), x + width - boxWidth - 4, y + 3, boxColor);
            }
            String text = fitText(prefix + name, width - 4 - indent - boxSpace - 4);
            context.drawTextWithShadow(textRenderer, Text.literal(text).styled(s -> s.withColor(Formatting.GOLD)), x + 4 + indent, y + 3, 0xFFFFFFFF);
        }

        @Override
        void onClick(int clickX, int width) {
            int boxWidth = textRenderer.getWidth("[x]");
            if (!required && clickX >= width - boxWidth - 8) {
                boolean all = keys.stream().allMatch(k -> Boolean.TRUE.equals(optionalSelection.get(k)));
                for (String k : keys) {
                    optionalSelection.put(k, !all);
                }
                refreshSelectAllLabel();
                updateActionButtonLabel();
                return;
            }
            if (!expanded.remove(expandKey)) expanded.add(expandKey);
            refreshLayout();
        }
    }

    private class FileRow extends Row {
        final String key;
        final String name;
        final boolean required;

        FileRow(String key, String name, int depth, boolean required) {
            super(depth);
            this.key = key;
            this.name = name;
            this.required = required;
        }

        private int indent(int width) {
            return Math.min(depth * INDENT, width / 3);
        }

        private String prefix() {
            if (required) return "• ";
            return Boolean.TRUE.equals(optionalSelection.get(key)) ? "[x] " : "[ ] ";
        }

        private int nameMaxWidth(int width) {
            return Math.max(0, width - 8 - indent(width) - textRenderer.getWidth(prefix()) - 4);
        }

        @Override
        void render(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
            if (isHovered(x, y, width, mouseX, mouseY)) {
                context.fill(x, y, x + width, y + ROW_HEIGHT, 0x10FFFFFF);
            }
            int color;
            if (required) {
                color = 0xFFDDDDDD;
            } else {
                color = Boolean.TRUE.equals(optionalSelection.get(key)) ? 0xFF55FF55 : 0xFFAAAAAA;
            }
            String shown = prefix() + fitText(name, nameMaxWidth(width));
            context.drawTextWithShadow(textRenderer, Text.literal(shown), x + 8 + indent(width), y + 3, color);
        }

        @Override
        void onClick(int clickX, int width) {
            if (required) return;
            optionalSelection.put(key, !Boolean.TRUE.equals(optionalSelection.get(key)));
            refreshSelectAllLabel();
            updateActionButtonLabel();
        }

        @Override
        void renderTooltip(DrawContext context, int mouseX, int mouseY, int width) {
            if (textRenderer.getWidth(name) > nameMaxWidth(width)) {
                context.drawTooltip(textRenderer, Text.literal(key), mouseX, mouseY);
            }
        }
    }
}
