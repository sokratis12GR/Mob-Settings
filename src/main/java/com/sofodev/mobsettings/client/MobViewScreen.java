package com.sofodev.mobsettings.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sofodev.mobsettings.MobNetwork;
import com.sofodev.mobsettings.SpawnLines;
import com.sofodev.thedragonlib.client.config.RuleType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * EXPERIMENTAL. Shows a mob in 3D with its equipment slots. Each version of the mob gets its own client-only copy, made
 * from the NBT the server sent and never added to the world, so edits survive switching versions. Every slot remembers
 * what it started with: changed slots are marked, and can be reset one at a time or all together.
 */
final class MobViewScreen extends Screen {
    private static final int SLOT = 18;
    private static final int GAP = 4;
    private static final int CHANGED = 0xFFFFD34D;
    private static final List<EquipmentSlot> LEFT = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<EquipmentSlot> RIGHT = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.BODY);
    private static final Map<EquipmentSlot, Identifier> EMPTY_ICONS = Map.of(
            EquipmentSlot.HEAD, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            EquipmentSlot.CHEST, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            EquipmentSlot.LEGS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            EquipmentSlot.FEET, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            EquipmentSlot.OFFHAND, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);

    private final MobNetwork.ViewData data;
    private final List<Version> versions = new ArrayList<>();
    private int variant;
    private EquipmentSlot selected = EquipmentSlot.HEAD;
    private final List<SlotBox> slots = new ArrayList<>();
    private int boxX, boxY, boxW, boxH, panelX, panelW;

    /** One version of the mob: its preview and the gear each slot started with. */
    private static final class Version {
        @Nullable LivingEntity preview;
        @Nullable Component error;
        final Map<EquipmentSlot, ItemStack> original = new EnumMap<>(EquipmentSlot.class);
        /** The addSpawns line this version is saved as, and its index; empty and -1 until it is one. */
        String line = "";
        int index = -1;
        String label = "";
    }

    private record SlotBox(EquipmentSlot slot, int x, int y) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
        }
    }

    MobViewScreen(MobNetwork.ViewData data) {
        super(Component.translatableWithFallback("mobsettings.inspect.title", "Inspect (Experimental)"));
        this.data = data;
    }

    // ---- Tabs ----

    private enum Tab {
        OVERVIEW("Overview"), EQUIPMENT("Equipment"), SPAWNS("Spawns"), DROPS("Drops"), RULES("Rules");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final java.util.Set<String> RESTART_LISTS = java.util.Set.of("addSpawns", "removeSpawns", "spawnGroupSizes");

    private Tab tab = Tab.OVERVIEW;
    private List<Component> infoLines = List.of();
    private int scroll;

    /** The text of a read-only tab, one component per line before wrapping. */
    private List<Component> infoLines() {
        MobNetwork.ViewInfo info = data.info();
        List<Component> lines = new ArrayList<>();
        switch (tab) {
            case OVERVIEW -> lines.addAll(info.overview());
            case SPAWNS -> {
                if (info.rules().stream().anyMatch(r -> RESTART_LISTS.contains(r.list()) && r.state() != MobNetwork.ACTIVE)) {
                    lines.add(Component.literal("⚠ Spawn rules were changed since the world loaded. The list below is what's active now; "
                            + "the changes take effect after a world restart (see Rules).").withStyle(ChatFormatting.GOLD));
                }
                lines.add(Component.literal("Where it spawns naturally, after rules (" + info.spawns().size() + "):").withStyle(ChatFormatting.GOLD));
                if (info.spawns().isEmpty()) lines.add(Component.literal("  Nowhere. Structure spawns aren't listed.").withStyle(ChatFormatting.GRAY));
                String dimension = null;
                for (MobNetwork.SpawnRow row : info.spawns()) {
                    // One heading per dimension instead of repeating it on every row.
                    if (!row.dimension().equals(dimension)) {
                        dimension = row.dimension();
                        lines.add(Component.literal(shortId(dimension) + ":").withStyle(ChatFormatting.YELLOW));
                    }
                    var line = Component.literal("  " + shortId(row.biome())).withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("  weight " + row.weight() + ", group " + row.min()
                                    + (row.max() != row.min() ? "-" + row.max() : "")).withStyle(ChatFormatting.GRAY));
                    if (!row.tags().isEmpty()) line.append(Component.literal("  [" + row.tags() + "]").withStyle(ChatFormatting.AQUA));
                    lines.add(line);
                }
            }
            case DROPS -> {
                lines.add(Component.literal("Loot table drops in " + shortId(data.dimension()) + ":").withStyle(ChatFormatting.GOLD));
                boolean any = false;
                for (MobNetwork.DropRow row : info.drops()) {
                    if (row.extra()) continue;
                    any = true;
                    Component name = itemName(row.item());
                    if (row.removedPercent() >= 100) {
                        lines.add(Component.literal("  ").append(name.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH))
                                .append(Component.literal("  ✖ Removed").withStyle(ChatFormatting.RED)));
                    } else if (row.removedPercent() > 0) {
                        lines.add(Component.literal("  ").append(name.copy().withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("  ◐ Removed " + row.removedPercent() + "% of the time").withStyle(ChatFormatting.GOLD)));
                    } else {
                        lines.add(Component.literal("  ").append(name.copy().withStyle(ChatFormatting.WHITE)));
                    }
                }
                if (!any) lines.add(Component.literal("  Its loot table drops nothing.").withStyle(ChatFormatting.GRAY));
                List<MobNetwork.DropRow> extras = info.drops().stream().filter(MobNetwork.DropRow::extra).toList();
                if (!extras.isEmpty()) {
                    lines.add(Component.literal("Added by extraDrops:").withStyle(ChatFormatting.GOLD));
                    for (MobNetwork.DropRow row : extras) {
                        lines.add(Component.literal("  + ").withStyle(ChatFormatting.GREEN).append(itemName(row.item()).copy().withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("  " + row.detail()).withStyle(ChatFormatting.GRAY)));
                    }
                }
                lines.add(Component.literal("Drop rules apply at once, no restart needed.").withStyle(ChatFormatting.DARK_GRAY));
            }
            case RULES -> {
                if (info.rules().isEmpty()) lines.add(Component.literal("No config lines cover this mob.").withStyle(ChatFormatting.GRAY));
                String list = null;
                for (MobNetwork.RuleRow row : info.rules()) {
                    if (!row.list().equals(list)) {
                        list = row.list();
                        lines.add(Component.literal(list + (RESTART_LISTS.contains(list) ? " (needs a world restart)" : "")).withStyle(ChatFormatting.GOLD));
                    }
                    lines.add(switch (row.state()) {
                        case MobNetwork.PENDING_RESTART -> Component.literal("  ◔ ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(row.line()).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("  saved, takes effect after restart").withStyle(ChatFormatting.YELLOW));
                        case MobNetwork.REMOVED_PENDING_RESTART -> Component.literal("  ✖ ").withStyle(ChatFormatting.RED)
                                .append(Component.literal(row.line()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH))
                                .append(Component.literal("  removed, active until restart").withStyle(ChatFormatting.RED));
                        case MobNetwork.INVALID -> Component.literal("  ⚠ ").withStyle(ChatFormatting.RED)
                                .append(Component.literal(row.line()).withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  invalid, ignored").withStyle(ChatFormatting.RED));
                        default -> Component.literal("  ● ").withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(row.line()).withStyle(ChatFormatting.WHITE));
                    });
                }
                lines.add(Component.literal("Lines for every dimension are shown. On a dedicated server these are the server's "
                        + "lists; your own config screen shows your local file.").withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> {}
        }
        return lines;
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static Component itemName(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null ? Component.literal(id) : Component.empty().append(Component.translatable(item.getDescriptionId()))
                .append(Component.literal(" (" + shortId(id) + ")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void printToChat() {
        if (minecraft.player == null) return;
        String target = "";
        if (data.live() && minecraft.level != null) {
            var entity = minecraft.level.getEntity(data.networkId());
            if (entity != null) target = " " + entity.getStringUUID();
            minecraft.player.connection.sendCommand("mobsettings inspect chat" + target);
        } else {
            minecraft.player.connection.sendCommand("mobsettings inspect chat type " + data.entity());
        }
        onClose(); // chat isn't drawn behind a screen; still asks first if there are unsaved gear changes
    }

    private static final int PAD = 5, ROW = 10, INDENT = 14;

    /** A wrapped line and how far it is indented: lines after the first of a row hang under its text. */
    private record Wrapped(FormattedCharSequence text, int indent) {}

    /** The read-only tab's text in a dark box the same style as the preview, with a scrollbar when it doesn't fit. */
    private void renderInfo(GuiGraphicsExtractor graphics) {
        int x0 = panelX - PAD, y0 = boxY, x1 = panelX + panelW, y1 = height - 36;
        graphics.fill(x0, y0, x1, y1, 0xC0101010);
        graphics.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFF5A5A5A);
        int textW = panelW - PAD - 6; // room for the scrollbar
        List<Wrapped> wrapped = new ArrayList<>();
        for (Component line : infoLines) {
            // Wrapped narrower so the indented continuation lines still fit.
            List<FormattedCharSequence> parts = font.split(line, textW - INDENT);
            for (int i = 0; i < parts.size(); i++) wrapped.add(new Wrapped(parts.get(i), i == 0 ? 0 : INDENT));
        }
        int top = y0 + PAD;
        int visible = Math.max(1, (y1 - PAD - top) / ROW);
        scroll = Math.max(0, Math.min(scroll, wrapped.size() - visible));
        graphics.enableScissor(x0, y0, x1, y1);
        for (int i = 0; i < visible && scroll + i < wrapped.size(); i++) {
            Wrapped line = wrapped.get(scroll + i);
            graphics.text(font, line.text, panelX + line.indent, top + i * ROW, 0xFFFFFFFF);
        }
        graphics.disableScissor();
        if (wrapped.size() > visible) {
            int trackTop = y0 + 2, trackH = y1 - y0 - 4;
            int thumbH = Math.max(12, trackH * visible / wrapped.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, wrapped.size() - visible);
            graphics.fill(x1 - 5, trackTop, x1 - 2, trackTop + trackH, 0xFF2A2A2A);
            graphics.fill(x1 - 5, thumbY, x1 - 2, thumbY + thumbH, 0xFF9A9A9A);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab != Tab.EQUIPMENT && mouseX >= panelX) {
            scroll -= (int) Math.signum(scrollY) * 3;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void init() {
        if (versions.isEmpty()) {
            for (MobNetwork.ViewVariant v : data.variants()) versions.add(load(v));
        }
        int content = Math.min(width - 20, 460);
        int left = (width - content) / 2;
        boxH = Math.max(80, Math.min(height - 158, 190));
        boxW = Math.max(70, Math.min(content / 2 - 2 * (SLOT + GAP), 130));
        boxX = left + SLOT + GAP;
        boxY = 62;
        panelX = boxX + boxW + SLOT + 2 * GAP + 8;
        panelW = left + content - panelX;

        slots.clear();
        for (int i = 0; i < LEFT.size(); i++) slots.add(new SlotBox(LEFT.get(i), boxX - SLOT - GAP, boxY + i * (SLOT + 2)));
        int row = 0;
        for (EquipmentSlot slot : RIGHT) {
            // Only horses, wolves and the like have a body slot.
            if (slot == EquipmentSlot.BODY && !(preview() instanceof Mob mob && mob.canUseSlot(EquipmentSlot.BODY))) continue;
            slots.add(new SlotBox(slot, boxX + boxW + GAP, boxY + row++ * (SLOT + 2)));
        }
        if (slots.stream().noneMatch(b -> b.slot == selected)) selected = EquipmentSlot.HEAD;

        int tabW = (content - 4 * GAP) / 5;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            Button button = Button.builder(Component.literal(t.label), b -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + i * (tabW + GAP), 34, tabW, 20).build();
            button.active = t != tab;
            addRenderableWidget(button);
        }
        if (tab != Tab.EQUIPMENT) {
            infoLines = infoLines();
            int each = (Math.min(width - 20, 320) - GAP) / 2;
            int bx = (width - (2 * each + GAP)) / 2;
            addSlotButton("Print to chat", "Print the chat version of inspect, for copying or sharing.", bx, height - 28, each,
                    minecraft.player != null, this::printToChat);
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(bx + each + GAP, height - 28, each, 20).build());
            return;
        }

        int y = boxY;
        if (versions.size() > 1) {
            Button cycle = Button.builder(Component.literal("◀ " + (variant + 1) + "/" + versions.size() + ": "
                    + versions.get(variant).label + " ▶"), b -> {
                variant = (variant + 1) % versions.size();
                rebuildWidgets();
            }).bounds(panelX, y, panelW, 20).build();
            cycle.setTooltip(Tooltip.create(Component.literal("Switch between the plain mob and each addSpawns line for it. Edits are kept per version.")));
            addRenderableWidget(cycle);
            y += 24;
        }
        y += 24; // the selected slot's name and item are drawn here
        int half = (panelW - GAP) / 2;
        boolean canEdit = preview() != null;
        addSlotButton("Edit item…", "Open the item editor for this slot.", panelX, y, half, canEdit, this::editSelected);
        addSlotButton("Use held item", "Put a copy of the item in your main hand into this slot.", panelX + half + GAP, y, half,
                canEdit && minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty(),
                () -> set(selected, minecraft.player.getMainHandItem().copy()));
        y += 24;
        addSlotButton("Clear", "Empty this slot. Reset brings the old item back.", panelX, y, half,
                canEdit && !stack(selected).isEmpty(), () -> set(selected, ItemStack.EMPTY));
        addSlotButton("Reset", "Put back what this slot had when the screen opened.", panelX + half + GAP, y, half,
                canEdit && changed(selected), () -> set(selected, original(selected).copy()));

        int actions = (Math.min(width - 20, 320) - GAP) / 2;
        int ax = (width - (2 * actions + GAP)) / 2;
        Version version = versions.get(variant);
        addSlotButton(version.index >= 0 ? "Save to config" : "Add to config",
                version.index >= 0 ? "Replace addSpawns line " + (version.index + 1) + " with this gear. Shows the line first."
                        : "Add this mob as a new addSpawns line (weight 10 in this dimension). Shows the line first.",
                ax, height - 52, actions, preview() != null && !waiting, this::saveToConfig);
        addSlotButton("Apply to mob", data.live() ? "Give the live mob exactly this gear. It also stops it despawning."
                        : "Only a live mob can be changed: run /mobsettings inspect while looking at one.",
                ax + actions + GAP, height - 52, actions, data.live() && preview() != null && !waiting && changed(), this::applyToMob);

        int bottom = height - 28;
        int each = (Math.min(width - 20, 460) - 4 * GAP) / 5;
        int x = (width - (5 * each + 4 * GAP)) / 2;
        boolean has = preview() != null;
        addSlotButton("Copy line", "Copy this mob as an addSpawns line, ready to paste into the config or a rule list.", x, bottom, each, has, this::copyLine);
        x += each + GAP;
        addSlotButton("Copy data", "Copy only the {...} data, for an addSpawns line's entity field.", x, bottom, each, has, this::copyData);
        x += each + GAP;
        addSlotButton("Paste", "Load the equipment from an addSpawns line, {...} data or a /summon command on the clipboard.", x, bottom, each, has, this::paste);
        x += each + GAP;
        addSlotButton("Reset all", "Undo every change to this version.", x, bottom, each, changed(), this::resetAll);
        x += each + GAP;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x, bottom, each, 20).build());
    }

    /** Whether the current version has any changed slot. */
    private boolean changed() {
        for (SlotBox box : slots) {
            if (changed(box.slot)) return true;
        }
        return false;
    }

    private void addSlotButton(String label, String tooltip, int x, int y, int w, boolean active, Runnable action) {
        Button button = Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build();
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        button.active = active;
        addRenderableWidget(button);
    }

    /** Makes one version's preview. Some modded mobs need server state to exist; those show their data without a model. */
    private Version load(MobNetwork.ViewVariant v) {
        Version version = new Version();
        version.line = v.line();
        version.index = v.index();
        version.label = v.label();
        EntityType<?> type = EntityType.byString(data.entity()).orElse(null);
        if (type == null || minecraft.level == null) {
            version.error = Component.literal("Unknown entity on this client: " + data.entity());
            return version;
        }
        try {
            if (!(type.create(minecraft.level, EntitySpawnReason.LOAD) instanceof LivingEntity living)) {
                version.error = Component.literal(data.entity() + " can't be previewed");
                return version;
            }
            living.load(TagValueInput.create(ProblemReporter.DISCARDING, registries(), v.nbt().copy()));
            living.setYRot(0);
            living.yBodyRot = 0;
            living.yHeadRot = 0;
            version.preview = living;
            for (EquipmentSlot slot : EquipmentSlot.values()) version.original.put(slot, living.getItemBySlot(slot).copy());
        } catch (RuntimeException ex) {
            version.error = Component.literal("Preview failed: " + ex.getClass().getSimpleName());
        }
        return version;
    }

    private @Nullable LivingEntity preview() {
        return versions.get(variant).preview;
    }

    private ItemStack stack(EquipmentSlot slot) {
        LivingEntity preview = preview();
        return preview == null ? ItemStack.EMPTY : preview.getItemBySlot(slot);
    }

    private ItemStack original(EquipmentSlot slot) {
        return versions.get(variant).original.getOrDefault(slot, ItemStack.EMPTY);
    }

    private boolean changed(EquipmentSlot slot) {
        return !ItemStack.matches(stack(slot), original(slot));
    }

    private int changedCount() {
        int count = 0;
        for (Version version : versions) {
            if (version.preview == null) continue;
            for (Map.Entry<EquipmentSlot, ItemStack> entry : version.original.entrySet()) {
                if (!ItemStack.matches(version.preview.getItemBySlot(entry.getKey()), entry.getValue())) count++;
            }
        }
        return count;
    }

    private void set(EquipmentSlot slot, ItemStack stack) {
        LivingEntity preview = preview();
        if (preview == null) return;
        preview.setItemSlot(slot, stack);
        rebuildWidgets();
    }

    private void resetAll() {
        Version version = versions.get(variant);
        if (version.preview == null) return;
        version.original.forEach((slot, stack) -> version.preview.setItemSlot(slot, stack.copy()));
        rebuildWidgets();
    }

    // ---- Copy and paste ----

    private @Nullable Component notice;
    private boolean noticeError;

    private void notice(String text, boolean error) {
        notice = Component.literal(text);
        noticeError = error;
    }

    /**
     * The current version as entity data in the addSpawns format. A version from an addSpawns line keeps that line's
     * drop chances, name and other data; a live or plain mob gets its drop chances where they differ from vanilla's.
     * Empty slots are left out: a rule can't empty a slot, so the mob keeps its own gear there.
     */
    private @Nullable EntityLine currentLine() {
        if (preview() == null) return null;
        Version version = versions.get(variant);
        EntityLine line = version.line.isEmpty() ? null : EntityLine.parse(SpawnLines.fieldData(SpawnLines.split(version.line)[0]));
        if (line == null) {
            line = new EntityLine();
            readDropChances(data.variants().get(variant).nbt(), line);
        }
        line.items.clear();
        line.counts.clear();
        for (SlotBox box : slots) {
            ItemStack stack = stack(box.slot);
            if (stack.isEmpty()) continue;
            line.items.put(box.slot.getName(), itemText(stack));
            if (stack.getCount() > 1) line.counts.put(box.slot.getName(), stack.getCount());
        }
        return line;
    }

    private static void readDropChances(CompoundTag nbt, EntityLine line) {
        // Mobs save their drop chances per slot name, the same as the rules use.
        nbt.getCompound("drop_chances").ifPresent(chances -> {
            for (String slot : chances.keySet()) chances.getFloat(slot).ifPresent(chance -> chance(line, slot, chance));
        });
    }

    /** Vanilla's default chance is left out, so the line stays short. */
    private static void chance(EntityLine line, String slot, float chance) {
        if (Math.abs(chance - 0.085F) > 1e-6) line.chances.put(slot, RuleType.format(chance));
    }

    private @Nullable String dataText() {
        EntityLine line = currentLine();
        if (line == null) return null;
        try {
            return line.text();
        } catch (CommandSyntaxException ex) {
            notice("Couldn't write the data: " + ex.getMessage(), true);
            return null;
        }
    }

    private boolean anyEmpty() {
        return slots.stream().anyMatch(b -> stack(b.slot).isEmpty());
    }

    private void copyData() {
        String text = dataText();
        if (text == null) return;
        minecraft.keyboardHandler.setClipboard(text);
        notice(text.isEmpty() ? "Copied: no data, every slot is empty" : "Copied the entity data", false);
    }

    /** The version's own addSpawns line with the new data, or a new line for this dimension with weight 10. */
    private @Nullable String lineText() {
        String text = dataText();
        if (text == null) return null;
        Version version = versions.get(variant);
        if (version.line.isEmpty()) return data.entity() + text + "|" + data.dimension() + "|?|10";
        String[] fields = SpawnLines.split(version.line);
        fields[0] = SpawnLines.fieldId(fields[0]) + text;
        return String.join("|", fields);
    }

    private void copyLine() {
        String line = lineText();
        if (line == null) return;
        minecraft.keyboardHandler.setClipboard(line);
        notice("Copied an addSpawns line" + (anyEmpty() ? " (empty slots keep the mob's own gear)" : ""), false);
    }

    // ---- Saving and applying ----

    private boolean waiting;

    /** Replaces the version's addSpawns line, or adds a new one, after showing the line and asking. */
    private void saveToConfig() {
        String line = lineText();
        if (line == null) return;
        Version version = versions.get(variant);
        boolean replace = version.index >= 0;
        if (replace && line.equals(version.line)) {
            notice("addSpawns line " + (version.index + 1) + " already has this gear", false);
            return;
        }
        Component question = Component.literal(replace ? "Replace addSpawns line " + (version.index + 1) + "?" : "Add this addSpawns line?");
        Component message = Component.literal(line + (anyEmpty() ? "\n\nEmpty slots aren't written: the mob keeps its own gear there." : ""));
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(this);
            if (!yes) return;
            waiting = true;
            notice("Saving…", false);
            ClientView.send(new MobNetwork.ViewEdit(false, version.index, version.line, line, -1, Map.of()));
        }, question, message, Component.literal(replace ? "Replace" : "Add"), net.minecraft.network.chat.CommonComponents.GUI_CANCEL));
    }

    /** Sends every slot, so the live mob ends up with exactly the gear shown. */
    private void applyToMob() {
        LivingEntity preview = preview();
        if (preview == null || !data.live()) return;
        Map<String, CompoundTag> equipment = new java.util.HashMap<>();
        for (SlotBox box : slots) {
            ItemStack stack = stack(box.slot);
            equipment.put(box.slot.getName(), stack.isEmpty() ? new CompoundTag() : (CompoundTag) saveStack(stack));
        }
        waiting = true;
        notice("Applying…", false);
        ClientView.send(new MobNetwork.ViewEdit(true, -1, "", "", data.networkId(), equipment));
    }

    void onEditResult(MobNetwork.ViewEditResult result) {
        waiting = false;
        notice(result.message(), !result.ok());
        if (result.ok()) {
            Version version = versions.get(variant);
            if (!result.apply()) {
                version.line = result.line();
                version.index = result.index();
                version.label = "addSpawns line " + (result.index() + 1);
            }
            // For spawn data the saved line is the new starting point; a live mob only counts as saved once applied.
            if ((result.apply() || !data.live()) && version.preview != null) {
                for (EquipmentSlot slot : EquipmentSlot.values()) version.original.put(slot, version.preview.getItemBySlot(slot).copy());
            }
        }
        if (minecraft.screen == this) rebuildWidgets();
    }

    /** Takes the equipment from an addSpawns line, {...} data or a /summon command. Slots it doesn't name stay as they are. */
    private void paste() {
        String clipboard = minecraft.keyboardHandler.getClipboard().trim();
        // A line copied straight from the .toml file: "line", with escaped quotes.
        if (clipboard.startsWith("\"")) {
            clipboard = clipboard.replaceAll("^\"|\",?$", "").replace("\\\"", "\"");
        }
        String field = clipboard.contains("|") ? SpawnLines.split(clipboard)[0] : clipboard;
        EntityLine line = clipboard.isEmpty() ? null : EntityLine.parse(EntityLine.fromClipboard(field));
        if (line == null) {
            notice("The clipboard has no entity data to paste", true);
            return;
        }
        String problem = line.problem();
        if (problem != null) {
            notice("Can't paste: " + problem, true);
            return;
        }
        int count = 0;
        for (SlotBox box : slots) {
            String text = line.items.get(box.slot.getName());
            if (text == null || text.isBlank()) continue;
            setFromText(box.slot, text);
            count++;
        }
        if (count == 0) {
            notice("The pasted data has no equipment for this mob's slots", true);
            return;
        }
        String id = SpawnLines.fieldId(field);
        boolean otherMob = !id.isEmpty() && !id.startsWith("{") && !id.contains(" ") && !ItemLine.fullId(id).equals(data.entity());
        notice("Pasted " + count + " slot" + (count == 1 ? "" : "s") + (otherMob ? " from " + id : ""), false);
        rebuildWidgets();
    }

    private void editSelected() {
        EquipmentSlot slot = selected;
        minecraft.setScreen(new ItemDataScreen(this, slotName(slot), itemText(stack(slot)), false, text -> setFromText(slot, text)));
    }

    // ---- Rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        Component sub = Component.literal(data.entity() + (data.live() ? " · live mob" : " · spawn data") + " · " + data.dimension());
        graphics.centeredText(font, sub.copy().withStyle(ChatFormatting.GRAY), width / 2, 22, 0xFFFFFFFF);
        int changes = changedCount();
        if (changes > 0) {
            graphics.centeredText(font, Component.literal("● " + changes + " unsaved change" + (changes == 1 ? "" : "s")
                    + (data.live() ? " (apply or save them)" : " (save them to keep them)")), width / 2, height - 64, CHANGED);
        }
        if (notice != null) graphics.centeredText(font, notice, width / 2, height - 76, noticeError ? 0xFFFF6B6B : 0xFF7CFC7C);

        renderPreview(graphics, mouseX, mouseY);
        SlotBox hovered = null;
        for (SlotBox box : slots) {
            renderSlot(graphics, box);
            if (box.contains(mouseX, mouseY)) hovered = box;
        }
        if (tab == Tab.EQUIPMENT) renderPanel(graphics);
        else renderInfo(graphics);
        if (hovered != null) graphics.setComponentTooltipForNextFrame(font, slotTooltip(hovered.slot), mouseX, mouseY);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xC0101010);
        graphics.outline(boxX - 1, boxY - 1, boxW + 2, boxH + 2, 0xFF5A5A5A);
        Version version = versions.get(variant);
        if (version.preview != null) {
            LivingEntity preview = version.preview;
            float size = Math.max(preview.getBbHeight(), preview.getBbWidth() * 0.8F);
            int scale = (int) Math.max(4, Math.min(70, boxH * 0.62F / Math.max(size, 0.3F)));
            // Look at the mouse only while it's over the preview; otherwise face forward.
            boolean inside = mouseX >= boxX && mouseX < boxX + boxW && mouseY >= boxY && mouseY < boxY + boxH;
            float lookX = inside ? mouseX : boxX + boxW / 2F;
            float lookY = inside ? mouseY : boxY + boxH / 2F - preview.getBbHeight() * scale / 2F;
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, boxX, boxY, boxX + boxW, boxY + boxH, scale, 0.0625F,
                    lookX, lookY, preview);
        } else if (version.error != null) {
            graphics.textWithWordWrap(font, version.error, boxX + 6, boxY + boxH / 2 - 10, boxW - 12, 0xFFFF6B6B);
        }
    }

    /** A slot drawn like an inventory slot: dark top-left edge, light bottom-right edge. */
    private void renderSlot(GuiGraphicsExtractor graphics, SlotBox box) {
        int x = box.x, y = box.y;
        graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + SLOT, y + SLOT, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF8B8B8B);
        ItemStack stack = stack(box.slot);
        if (!stack.isEmpty()) {
            graphics.item(stack, x + 1, y + 1);
            graphics.itemDecorations(font, stack, x + 1, y + 1);
        } else if (EMPTY_ICONS.containsKey(box.slot)) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_ICONS.get(box.slot), x + 1, y + 1, 16, 16);
        }
        if (box.slot == selected) graphics.outline(x - 1, y - 1, SLOT + 2, SLOT + 2, 0xFFFFFFFF);
        if (changed(box.slot)) graphics.fill(x + SLOT - 4, y + 1, x + SLOT - 1, y + 4, CHANGED);
    }

    private void renderPanel(GuiGraphicsExtractor graphics) {
        int y = boxY + (versions.size() > 1 ? 24 : 0);
        ItemStack stack = stack(selected);
        Component name = Component.literal("Selected: ").withStyle(ChatFormatting.GRAY).append(slotName(selected).copy().withStyle(ChatFormatting.YELLOW));
        if (changed(selected)) name = name.copy().append(Component.literal("  ● changed").withStyle(ChatFormatting.GOLD));
        graphics.text(font, name, panelX, y + 2, 0xFFFFFFFF);
        Component item = stack.isEmpty() ? Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY) : stack.getHoverName();
        graphics.text(font, font.substrByWidth(item, panelW).getString(), panelX, y + 13, stack.isEmpty() ? 0xFF777777 : 0xFFFFFFFF);
        if (changed(selected)) {
            ItemStack old = original(selected);
            String was = "was: " + (old.isEmpty() ? "empty" : old.getHoverName().getString());
            graphics.text(font, font.plainSubstrByWidth(was, panelW), panelX + panelW - Math.min(panelW, font.width(was)), y + 13, 0xFF999999);
        }

        // Below the slot buttons: the addSpawns line this version comes from.
        y += 24 + 48 + 6;
        Version version = versions.get(variant);
        if (!version.line.isEmpty()) {
            graphics.text(font, Component.literal("Saved as addSpawns line " + (version.index + 1) + ":").withStyle(ChatFormatting.GRAY), panelX, y, 0xFFFFFFFF);
            y += 11;
            List<FormattedCharSequence> lines = font.split(Component.literal(version.line), panelW);
            int max = Math.max(1, (height - 82 - y) / 10);
            for (int i = 0; i < Math.min(lines.size(), max); i++) {
                graphics.text(font, lines.get(i), panelX, y, 0xFFAAAAAA);
                y += 10;
            }
        }
        graphics.text(font, Component.literal("Click a slot to select it.").withStyle(ChatFormatting.DARK_GRAY),
                boxX - SLOT - GAP, boxY + boxH + 6, 0xFFFFFFFF);
    }

    private List<Component> slotTooltip(EquipmentSlot slot) {
        List<Component> lines = new ArrayList<>();
        lines.add(slotName(slot).copy().withStyle(ChatFormatting.GOLD));
        ItemStack stack = stack(slot);
        if (stack.isEmpty()) {
            lines.add(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.addAll(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.Default.NORMAL));
        }
        if (changed(slot)) {
            ItemStack old = original(slot);
            lines.add(Component.literal("Changed, was: " + (old.isEmpty() ? "empty" : old.getHoverName().getString())).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        for (SlotBox box : slots) {
            if (!box.contains(mouseX, mouseY)) continue;
            if (tab != Tab.EQUIPMENT) {
                // Clicking gear from another tab goes to the editor with that slot selected.
                tab = Tab.EQUIPMENT;
                selected = box.slot;
                rebuildWidgets();
            } else if (selected == box.slot && button == 0 && preview() != null) {
                editSelected(); // a second click on the selected slot opens its editor
            } else {
                selected = box.slot;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (changedCount() == 0) {
            ClientView.closed(this);
            super.onClose();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                ClientView.closed(this);
                super.onClose();
            }
            else minecraft.setScreen(this);
        }, Component.translatable("mobsettings.ui.data.confirmDiscard"), Component.translatable("mobsettings.ui.data.confirmDiscard.message"),
                Component.translatable("mobsettings.ui.data.discard"), CommonComponents.GUI_CANCEL));
    }

    // ---- Item text ----

    private static Component slotName(EquipmentSlot slot) {
        return Component.translatable("mobsettings.ui.slot." + slot.getName());
    }

    private HolderLookup.Provider registries() {
        return minecraft.level.registryAccess();
    }

    private Tag saveStack(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(registries().createSerializationContext(NbtOps.INSTANCE), stack).result().orElseGet(CompoundTag::new);
    }

    /** The stack in the same text form the item editor and the config use, or empty. */
    String itemText(ItemStack stack) {
        if (stack.isEmpty()) return "";
        Tag saved = saveStack(stack);
        ItemLine line = saved instanceof CompoundTag tag ? ItemLine.fromTag(tag) : null;
        return line == null ? "" : line.text();
    }

    /** Applies the item editor's result. Unchanged text leaves the stack alone, so nothing it can't show is lost. */
    private void setFromText(EquipmentSlot slot, String text) {
        LivingEntity preview = preview();
        if (preview == null || text.equals(itemText(stack(slot)))) return;
        if (text.isBlank()) {
            preview.setItemSlot(slot, ItemStack.EMPTY);
            return;
        }
        ItemLine line = ItemLine.parse(text);
        if (line == null) return;
        try {
            int count = Math.max(1, stack(slot).getCount());
            preview.setItemSlot(slot, ItemStack.CODEC.parse(registries().createSerializationContext(NbtOps.INSTANCE), line.toTag(count)).result().orElse(ItemStack.EMPTY));
        } catch (CommandSyntaxException ex) {
            // The item editor only finishes with valid text, so this shouldn't happen; keep the old stack.
        }
    }

    /** The current version's equipment as item text per slot name, for copying and saving in the next steps. */
    Map<String, String> equipmentText() {
        Map<String, String> items = new LinkedHashMap<>();
        for (SlotBox box : slots) items.put(box.slot.getName(), itemText(stack(box.slot)));
        return items;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
