package com.sofodev.mobsettings.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Lists the entries of one rule-based config option; changes are only applied on Done. */
final class RuleListScreen extends Screen {
    private static final int LINE_HEIGHT = 11;

    private final Screen parent;
    private final RuleType type;
    private final List<String> rules;
    private final Consumer<List<String>> onDone;

    RuleListScreen(Screen parent, RuleType type, List<String> rules, Consumer<List<String>> onDone) {
        super(Component.translatable("mobsettings.configuration." + type.key()));
        this.parent = parent;
        this.type = type;
        this.rules = new ArrayList<>(rules);
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        // Ends above the Add button so the last rows and the scrollbar aren't covered.
        // All rows of a list have one height on this version, so it's what the longest rule needs.
        int rowHeight = 28;
        for (String rule : rules) rowHeight = Math.max(rowHeight, rowHeight(rule));
        RuleRows rows = addRenderableWidget(new RuleRows(minecraft, width, height - 36 - 60, 36, rowHeight));
        for (int i = 0; i < rules.size(); i++) rows.add(i);

        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("mobsettings.ui.add"),
                        b -> edit("", line -> rules.add(line)))
                .bounds(width / 2 - 154, buttonY - 24, 308, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
                    onDone.accept(List.copyOf(rules));
                    minecraft.setScreen(parent);
                })
                .bounds(width / 2 - 154, buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 + 4, buttonY, 150, 20).build());
    }

    private int rowWidth() {
        return Math.min(560, width - 40);
    }

    private Component summary(String rule, @Nullable String error) {
        Component description = type.describe(rule);
        return error != null
                ? Component.translatable("mobsettings.ui.invalid", Component.translatable(error))
                : description != null ? description : Component.literal(rule);
    }

    private List<FormattedCharSequence> wrap(Component text) {
        // Both buttons with their gap (104) and the gap before them (8).
        return font.split(text, rowWidth() - 112);
    }

    /** Tall enough for every wrapped line, and never shorter than the buttons need. */
    private int rowHeight(String rule) {
        int lines = wrap(summary(rule, type.validate(rule))).size() + wrap(Component.literal(rule)).size();
        return Math.max(28, lines * LINE_HEIGHT + 6);
    }

    private void edit(String line, Consumer<String> onSave) {
        minecraft.setScreen(new RuleEditScreen(this, type, line, onSave));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("mobsettings.configuration." + type.key() + ".tooltip"),
                width / 2, 22, 0xFFA0A0A0);
        if (rules.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("mobsettings.ui.empty"), width / 2, 50, 0xFFA0A0A0);
        }
    }

    private final class RuleRows extends ContainerObjectSelectionList<RuleRows.Row> {
        RuleRows(Minecraft minecraft, int width, int height, int y, int rowHeight) {
            super(minecraft, width, height, y, rowHeight);
        }

        void add(int index) {
            addEntry(new Row(index));
        }

        @Override
        public int getRowWidth() {
            return rowWidth();
        }

        final class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final @Nullable String error;
            private final List<FormattedCharSequence> summaryLines;
            private final List<FormattedCharSequence> rawLines;
            private final Button editButton;
            private final Button deleteButton;

            Row(int index) {
                this.error = type.validate(rules.get(index));
                this.summaryLines = wrap(summary(rules.get(index), error));
                this.rawLines = wrap(Component.literal(rules.get(index)));
                this.editButton = Button.builder(Component.translatable("mobsettings.ui.edit"),
                        b -> edit(rules.get(index), line -> rules.set(index, line))).size(50, 20).build();
                this.deleteButton = Button.builder(Component.translatable("mobsettings.ui.delete"), b -> {
                    rules.remove(index);
                    rebuildWidgets();
                }).size(50, 20).build();
                if (error != null) {
                    editButton.setTooltip(Tooltip.create(Component.translatable("mobsettings.ui.invalid",
                            Component.translatable(error))));
                }
            }

            @Override
            public void render(GuiGraphics graphics, int rowIndex, int top, int left, int rowWidth, int rowHeight,
                               int mouseX, int mouseY, boolean hovered, float a) {
                int buttonY = top + (rowHeight - 20) / 2;
                deleteButton.setPosition(left + rowWidth - deleteButton.getWidth(), buttonY);
                editButton.setPosition(deleteButton.getX() - 4 - editButton.getWidth(), buttonY);
                editButton.render(graphics, mouseX, mouseY, a);
                deleteButton.render(graphics, mouseX, mouseY, a);
                int textY = top + 2;
                for (FormattedCharSequence line : summaryLines) {
                    graphics.drawString(font, line, left, textY, error == null ? 0xFFFFFFFF : 0xFFFF5555);
                    textY += LINE_HEIGHT;
                }
                for (FormattedCharSequence line : rawLines) {
                    graphics.drawString(font, line, left, textY, 0xFF808080);
                    textY += LINE_HEIGHT;
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(editButton, deleteButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(editButton, deleteButton);
            }
        }
    }
}
