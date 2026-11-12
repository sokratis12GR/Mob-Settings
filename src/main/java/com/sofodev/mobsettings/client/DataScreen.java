package com.sofodev.mobsettings.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Shared layout of the data editors: a breadcrumb title, scrolling rows of inputs on the left, a searchable list for
 * whichever ID input was last selected on the right, a preview of the resulting text, and buttons to copy, paste,
 * clear, apply and finish. Tracks unsaved changes and keeps an undo history of every valid state.
 */
abstract class DataScreen extends Screen {
    private static final int LABEL_WIDTH = 76;
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 2;
    private static final int TOP = 36;
    private static final int FOOTER = 80;
    private static final int MAX_LENGTH = 32500;
    private static final int HISTORY_LIMIT = 100;
    private static final String SEPARATOR = " › ";

    protected final Screen parent;
    private final Map<EditBox, Supplier<List<Suggestions.Option>>> pickers = new IdentityHashMap<>();
    private @Nullable EditBox activePicker;
    private @Nullable Rows rows;
    private @Nullable PickerList picker;
    private @Nullable Button doneButton;
    private @Nullable Button applyButton;
    private @Nullable Button copyButton;
    private @Nullable String error;
    private @Nullable Component notice;

    /** The text last handed back to whoever opened the screen; null until the screen first opens. */
    private @Nullable String saved;
    private boolean opened;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    /** Clickable breadcrumb parts laid out on the last render: x start, x end, target. */
    private final List<Crumb> crumbAreas = new ArrayList<>();

    DataScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    /** Adds this screen's rows with {@link #row}. Called again whenever rows are added or removed. */
    protected abstract void buildRows();

    /** The current data as text. Only called while {@link #problem()} finds nothing. */
    protected abstract String text();

    /** What's wrong with the current data, ready to show, or null. */
    protected abstract @Nullable String problem();

    /** Applies copied text, or returns what's wrong with it, leaving the data as it was. */
    protected abstract @Nullable String paste(String clipboard);

    /** Replaces the data with text this screen produced itself, for undo and redo. */
    protected abstract void load(String text);

    protected abstract void clear();

    /** Hands the finished data to whoever opened the screen. */
    protected abstract void finish();

    @Override
    protected void init() {
        pickers.clear();
        activePicker = null;
        int listWidth = width * 2 / 3;
        rows = addRenderableWidget(new Rows(minecraft, listWidth, height - TOP - FOOTER, TOP));
        buildRows();
        int pickerX = listWidth + 6;
        picker = addRenderableWidget(new PickerList(minecraft, width - pickerX - 6, height - TOP - 12 - FOOTER, TOP + 12));
        picker.setX(pickerX);

        int y = height - 52;
        copyButton = addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.copyAll"), b -> copyAll())
                .tooltip(Tooltip.create(Component.translatable("mobsettings.ui.data.help.copyAll")))
                .bounds(width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.pasteAll"), b -> applyPaste())
                .tooltip(Tooltip.create(Component.translatable("mobsettings.ui.data.help.pasteAll")))
                .bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.clearAll"), b -> confirmClear())
                .bounds(width / 2 + 54, y, 100, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done())
                .tooltip(Tooltip.create(Component.translatable("mobsettings.ui.data.help.done")))
                .bounds(width / 2 - 154, height - 28, 100, 20).build());
        applyButton = addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.apply"), b -> apply())
                .tooltip(Tooltip.create(Component.translatable("mobsettings.ui.data.help.apply")))
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 + 54, height - 28, 100, 20).build());
        if (!opened) {
            opened = true;
            saved = current();
            record();
        }
        revalidate();
    }

    /** The current text, or null while the data has a problem. */
    private @Nullable String current() {
        return problem() == null ? text() : null;
    }

    protected boolean dirty() {
        String now = current();
        return now == null || !now.equals(saved);
    }

    private void copyAll() {
        if (problem() != null) return;
        minecraft.keyboardHandler.setClipboard(text());
        notice = Component.translatable("mobsettings.ui.data.copied");
    }

    private void applyPaste() {
        String problem = paste(minecraft.keyboardHandler.getClipboard());
        if (problem != null) {
            notice = Component.literal(problem).withStyle(ChatFormatting.RED);
            return;
        }
        rebuild();
        notice = Component.translatable("mobsettings.ui.data.pasted");
    }

    private void confirmClear() {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) clear();
            minecraft.setScreen(this);
            if (yes) notice = Component.translatable("mobsettings.ui.data.cleared");
        }, Component.translatable("mobsettings.ui.data.confirmClear"), Component.translatable("mobsettings.ui.data.confirmClear.message")));
    }

    /** Hands the data back and stays open. */
    private void apply() {
        if (problem() != null) return;
        finish();
        saved = text();
        notice = Component.translatable("mobsettings.ui.data.applied");
        revalidate();
    }

    private void done() {
        if (problem() != null) return;
        finish();
        saved = text();
        minecraft.setScreen(parent);
    }

    /** Goes to {@code target}, asking first when there are changes that would be lost. */
    private void leave(Screen target) {
        // Jumping back past other editors drops their changes too.
        boolean losesChanges = false;
        for (Screen screen = this; screen instanceof DataScreen data && screen != target; screen = data.parent) {
            losesChanges |= data.dirty();
        }
        if (!losesChanges) {
            minecraft.setScreen(target);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> minecraft.setScreen(yes ? target : this),
                Component.translatable("mobsettings.ui.data.confirmDiscard"), Component.translatable("mobsettings.ui.data.confirmDiscard.message"),
                Component.translatable("mobsettings.ui.data.discard"), CommonComponents.GUI_CANCEL));
    }

    /** Lays the rows out again, staying at the same scroll position. */
    protected void rebuild() {
        double scroll = rows == null ? 0 : rows.getScrollAmount();
        rebuildWidgets();
        if (rows != null) rows.setScrollAmount(scroll);
    }

    protected void revalidate() {
        error = problem();
        if (doneButton != null) doneButton.active = error == null;
        if (copyButton != null) copyButton.active = error == null;
        if (applyButton != null) applyButton.active = error == null && dirty();
        if (opened) record();
    }

    protected void setNotice(Component notice) {
        this.notice = notice;
    }

    // ---- Undo history ----

    /** Adds the current state to the history when it's valid and new, dropping anything that was undone. */
    private void record() {
        String now = current();
        if (now == null || (historyIndex >= 0 && history.get(historyIndex).equals(now))) return;
        while (history.size() > historyIndex + 1) history.removeLast();
        history.add(now);
        if (history.size() > HISTORY_LIMIT) history.removeFirst();
        historyIndex = history.size() - 1;
    }

    private void undo() {
        String now = current();
        // An unfinished edit since the last valid state undoes back to that state.
        int target = now != null && historyIndex >= 0 && history.get(historyIndex).equals(now) ? historyIndex - 1 : historyIndex;
        if (target < 0) return;
        historyIndex = target;
        restore(Component.translatable("mobsettings.ui.data.undone"));
    }

    private void redo() {
        if (historyIndex + 1 >= history.size()) return;
        historyIndex++;
        restore(Component.translatable("mobsettings.ui.data.redone"));
    }

    private void restore(Component message) {
        load(history.get(historyIndex));
        rebuild();
        notice = message;
    }

    // ---- Rows ----

    /** Adds a row. A widget with a width of 0 shares whatever room the others leave. */
    protected void row(Component label, @Nullable Component help, AbstractWidget... widgets) {
        if (rows != null) rows.addRow(label, help, List.of(widgets));
    }

    protected EditBox box(Component label, @Nullable Component help, String value, int boxWidth, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, boxWidth, 20, label);
        box.setMaxLength(MAX_LENGTH);
        box.setValue(value);
        // A box without its width yet scrolls past all of its text, and getting the width later doesn't scroll it back.
        box.moveCursorToStart(false);
        if (help != null) box.setTooltip(Tooltip.create(help));
        box.setResponder(text -> {
            onChange.accept(text);
            notice = null;
            if (box == activePicker) refreshPicker();
            revalidate();
        });
        return box;
    }

    /** Makes selecting the box show these options in the list on the right. */
    protected EditBox picker(EditBox box, Supplier<List<Suggestions.Option>> options) {
        pickers.put(box, options);
        return box;
    }

    protected Button button(Component label, @Nullable Component help, int buttonWidth, Runnable onPress) {
        Button button = Button.builder(label, b -> onPress.run()).size(buttonWidth, 20).build();
        if (help != null) button.setTooltip(Tooltip.create(help));
        return button;
    }

    /** A 20x20 icon of whichever item {@code id} names, blank when it names none. */
    protected AbstractWidget itemIcon(Supplier<String> id) {
        return new ItemIcon(id);
    }

    protected static Component hint(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }

    private void refreshPicker() {
        if (picker == null) return;
        if (activePicker == null) {
            picker.show(List.of());
            return;
        }
        String query = com.sofodev.mobsettings.SpawnLines.fieldId(activePicker.getValue()).toLowerCase(Locale.ROOT);
        List<Suggestions.Option> matches = new ArrayList<>();
        for (Suggestions.Option option : pickers.get(activePicker).get()) {
            if (option.matches(query)) matches.add(option);
        }
        picker.show(matches);
    }

    /** Replaces only the ID, so an item's data stays when another item is picked. */
    private void pick(Suggestions.Option option) {
        if (activePicker != null) activePicker.setValue(option.id() + com.sofodev.mobsettings.SpawnLines.fieldData(activePicker.getValue()));
    }

    @Override
    public void tick() {
        // The list takes focus when clicked, so the active box only changes when another one is selected.
        for (EditBox box : pickers.keySet()) {
            if (box.isFocused() && box != activePicker) {
                activePicker = box;
                refreshPicker();
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Checked before the focused box, which would otherwise take Ctrl+Z and Enter.
        if (hasControlDown() && !hasAltDown()) {
            boolean shift = hasShiftDown();
            switch (key) {
                case GLFW.GLFW_KEY_S -> { apply(); return true; }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { done(); return true; }
                case GLFW.GLFW_KEY_Z -> { if (shift) redo(); else undo(); return true; }
                case GLFW.GLFW_KEY_Y -> { redo(); return true; }
                case GLFW.GLFW_KEY_C -> { if (shift) { copyAll(); return true; } }
                case GLFW.GLFW_KEY_V -> { if (shift) { applyPaste(); return true; } }
                default -> {}
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= 6 && mouseY < 18) {
            for (Crumb crumb : crumbAreas) {
                if (mouseX >= crumb.start && mouseX < crumb.end && crumb.target != null) {
                    leave(crumb.target);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        leave(parent);
    }

    // ---- Breadcrumbs ----

    private record Crumb(@Nullable Screen target, int start, int end) {}

    /** One breadcrumb: a title and the screen it goes back to, or null for the current screen. */
    protected record Step(Component label, @Nullable Screen target) {}

    /** The titles from the outermost screen to this one. */
    protected List<Step> breadcrumbs() {
        List<Step> crumbs;
        if (parent instanceof DataScreen data) {
            crumbs = new ArrayList<>(data.breadcrumbs());
            // The parent's own step was the current screen there; here it's somewhere to go back to.
            crumbs.add(new Step(crumbs.removeLast().label(), parent));
        } else {
            crumbs = new ArrayList<>();
            if (parent != null && !parent.getTitle().getString().isEmpty()) crumbs.add(new Step(parent.getTitle(), parent));
        }
        crumbs.add(new Step(title, null));
        return crumbs;
    }

    private void renderBreadcrumbs(GuiGraphics graphics, int mouseX, int mouseY) {
        crumbAreas.clear();
        List<Step> crumbs = breadcrumbs();
        String star = dirty() ? " *" : "";
        int total = font.width(star);
        for (int i = 0; i < crumbs.size(); i++) total += font.width(crumbs.get(i).label()) + (i > 0 ? font.width(SEPARATOR) : 0);
        // Too long for the screen: keep the end of the trail.
        int first = 0;
        while (total > width - 20 && first < crumbs.size() - 1) {
            total -= font.width(crumbs.get(first).label()) + font.width(SEPARATOR);
            first++;
        }
        int x = (width - total) / 2;
        if (first > 0) x -= font.width("…" + SEPARATOR) / 2;
        if (first > 0) x = graphics.drawString(font, "…" + SEPARATOR, x, 10, 0xFF808080);
        for (int i = first; i < crumbs.size(); i++) {
            if (i > first) x = graphics.drawString(font, SEPARATOR, x, 10, 0xFF808080);
            Component label = crumbs.get(i).label();
            Screen target = crumbs.get(i).target();
            int end = x + font.width(label);
            boolean hovered = target != null && mouseX >= x && mouseX < end && mouseY >= 6 && mouseY < 18;
            int color = target == null ? 0xFFFFFFFF : hovered ? 0xFFFFFF55 : 0xFFA0A0A0;
            graphics.drawString(font, label, x, 10, color);
            if (hovered) graphics.fill(x, 19, end, 20, color);
            crumbAreas.add(new Crumb(target, x, end));
            x = end;
        }
        if (!star.isEmpty()) graphics.drawString(font, star, x, 10, 0xFFFFAA00);
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        renderBreadcrumbs(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable(activePicker != null ? "mobsettings.ui.data.matches" : "mobsettings.ui.pickHint"),
                width * 2 / 3 + 6, TOP, 0xFFA0A0A0);

        int previewY = height - FOOTER + 2;
        String preview = error == null ? text() : null;
        String shown = preview == null ? "" : preview.isEmpty() || preview.equals("{}") ? Component.translatable("mobsettings.ui.data.previewEmpty").getString() : preview;
        if (preview != null) {
            String label = Component.translatable("mobsettings.ui.data.preview").getString() + " ";
            int x = graphics.drawString(font, label, 10, previewY, 0xFFA0A0A0);
            String cut = font.plainSubstrByWidth(shown, width - 10 - x);
            graphics.drawString(font, cut.length() < shown.length() ? font.plainSubstrByWidth(shown, width - 10 - x - font.width("…")) + "…" : cut,
                    x, previewY, 0xFFE0E0E0);
            if (mouseY >= previewY - 1 && mouseY < previewY + 9 && mouseX >= 10 && mouseX < width - 10 && cut.length() < shown.length()) {
                setTooltipForNextRenderPass(font.split(Component.literal(shown), Math.min(width - 20, 320)));
            }
        }

        Component message = error != null ? Component.literal(error).withStyle(ChatFormatting.RED) : notice;
        if (message != null) {
            graphics.drawString(font, font.plainSubstrByWidth(message.getString(), width - 20), 10, previewY + 12,
                    error != null || message.getStyle().getColor() != null ? 0xFFFF5555 : 0xFF55FF55);
        }
    }

    /** Draws the item named by the supplier; never takes clicks. */
    private final class ItemIcon extends AbstractWidget {
        private final Supplier<String> id;
        private String cachedId = "";
        private ItemStack cached = ItemStack.EMPTY;

        ItemIcon(Supplier<String> id) {
            super(0, 0, 20, 20, CommonComponents.EMPTY);
            this.id = id;
            this.active = false;
        }

        private ItemStack stack() {
            String now = com.sofodev.mobsettings.SpawnLines.fieldId(Objects.requireNonNullElse(id.get(), "")).trim();
            if (!now.equals(cachedId)) {
                cachedId = now;
                ResourceLocation key = ResourceLocation.tryParse(now);
                cached = ItemStack.EMPTY;
                if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                    try {
                        cached = new ItemStack(BuiltInRegistries.ITEM.get(key));
                    } catch (RuntimeException ignored) {
                        // Some items can't be made outside a world; they just get no icon.
                    }
                }
            }
            return cached;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
            graphics.fill(getX(), getY(), getX() + 20, getY() + 20, 0x40000000);
            ItemStack stack = stack();
            if (!stack.isEmpty()) graphics.renderItem(stack, getX() + 2, getY() + 2);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    private final class Rows extends ContainerObjectSelectionList<Rows.Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        void addRow(Component label, @Nullable Component help, List<AbstractWidget> widgets) {
            addEntry(new Row(label, help, widgets));
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - SCROLLBAR_WIDTH;
        }

        @Override
        public int getRowWidth() {
            return width - SCROLLBAR_WIDTH * 2 - 4;
        }

        final class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Component label;
            private final @Nullable Component help;
            private final List<AbstractWidget> widgets;
            private final List<AbstractWidget> flexible = new ArrayList<>();
            private final int fixedWidth;

            Row(Component label, @Nullable Component help, List<AbstractWidget> widgets) {
                this.label = label;
                this.help = help;
                this.widgets = widgets;
                int fixed = 0;
                for (AbstractWidget widget : widgets) {
                    if (widget.getWidth() == 0) flexible.add(widget);
                    fixed += widget.getWidth();
                }
                this.fixedWidth = fixed;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth, int rowHeight,
                               int mouseX, int mouseY, boolean hovered, float a) {
                int y = top + (rowHeight - 20) / 2;
                int x = left + LABEL_WIDTH;
                // Stops short of the row's edge, which on this version touches the scrollbar.
                int contentWidth = rowWidth - 4;
                int room = left + contentWidth - x - fixedWidth - GAP * (widgets.size() - 1);
                graphics.drawString(font, font.plainSubstrByWidth(label.getString(), widgets.isEmpty() ? contentWidth : LABEL_WIDTH - 2),
                        left, y + 6, widgets.isEmpty() ? 0xFFFFFF55 : 0xFFE0E0E0);
                for (AbstractWidget widget : widgets) {
                    if (flexible.contains(widget)) widget.setWidth(Math.max(10, room / flexible.size()));
                    widget.setPosition(x, y);
                    widget.render(graphics, mouseX, mouseY, a);
                    x += widget.getWidth() + GAP;
                }
                if (help != null && mouseX >= left && mouseX < left + LABEL_WIDTH && mouseY >= y && mouseY < y + 20) {
                    setTooltipForNextRenderPass(font.split(help, 200));
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return widgets;
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return widgets;
            }
        }
    }

    private final class PickerList extends ObjectSelectionList<PickerList.Row> {
        PickerList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 12);
        }

        void show(List<Suggestions.Option> options) {
            clearEntries();
            options.forEach(option -> addEntry(new Row(option)));
            setScrollAmount(0);
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - SCROLLBAR_WIDTH;
        }

        @Override
        public int getRowWidth() {
            return width - SCROLLBAR_WIDTH * 2 - 4;
        }

        final class Row extends ObjectSelectionList.Entry<Row> {
            private final Suggestions.Option option;

            Row(Suggestions.Option option) {
                this.option = option;
            }

            @Override
            public Component getNarration() {
                return Component.literal(option.name());
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth, int rowHeight,
                               int mouseX, int mouseY, boolean hovered, float a) {
                String name = font.plainSubstrByWidth(option.name(), rowWidth);
                graphics.drawString(font, name, left, top + 1, hovered ? 0xFFFFFF55 : 0xFFFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                pick(option);
                return true;
            }
        }
    }
}
