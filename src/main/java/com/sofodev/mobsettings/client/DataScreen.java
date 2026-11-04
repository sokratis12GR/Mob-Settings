package com.sofodev.mobsettings.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shared layout of the data editors: scrolling rows of inputs on the left, a searchable list for whichever ID input was
 * last selected on the right, and buttons to copy the whole thing as text and paste it back.
 */
abstract class DataScreen extends Screen {
    private static final int LABEL_WIDTH = 76;
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 2;
    private static final int TOP = 36;
    private static final int FOOTER = 66;
    private static final int MAX_LENGTH = 32500;

    protected final Screen parent;
    private final Map<EditBox, Supplier<List<Suggestions.Option>>> pickers = new IdentityHashMap<>();
    private @Nullable EditBox activePicker;
    private @Nullable Rows rows;
    private @Nullable PickerList picker;
    private @Nullable Button doneButton;
    private @Nullable Button copyButton;
    private @Nullable String error;
    private @Nullable Component notice;

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
        copyButton = addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.copyAll"), b -> {
            minecraft.keyboardHandler.setClipboard(text());
            notice = Component.translatable("mobsettings.ui.data.copied");
        }).bounds(width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.pasteAll"), b -> applyPaste())
                .bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.data.clearAll"), b -> {
            clear();
            rebuild();
        }).bounds(width / 2 + 54, y, 100, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            if (problem() != null) return;
            finish();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
        revalidate();
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
    }

    protected void setNotice(Component notice) {
        this.notice = notice;
    }

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
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable(activePicker != null ? "mobsettings.ui.data.matches" : "mobsettings.ui.pickHint"),
                width * 2 / 3 + 6, TOP, 0xFFA0A0A0);
        Component message = error != null ? Component.literal(error).withStyle(ChatFormatting.RED) : notice;
        if (message != null) {
            graphics.drawString(font, font.plainSubstrByWidth(message.getString(), width - 20), 10, height - FOOTER + 2,
                    error != null || message.getStyle().getColor() != null ? 0xFFFF5555 : 0xFF55FF55);
        }
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
