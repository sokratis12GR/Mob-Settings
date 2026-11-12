package com.sofodev.mobsettings;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Formatting and chat-link helpers shared by the {@code /mobsettings} commands. */
final class CommandText {
    private CommandText() {}

    static final String ROOT = "/" + MobSettings.MODID;

    /** Aqua text that runs {@code command} when clicked and shows it on hover. */
    static MutableComponent link(String label, String command) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** Like {@link #link} but puts the command in the chat box instead of running it. */
    static MutableComponent suggest(String label, String command, String description) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(description))));
    }

    static MutableComponent url(String label, String url) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
    }

    static Component pageButton(String label, String command, boolean active) {
        return active ? link(label, command) : Component.literal(label).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** {@code [Spawns] [Drops]} buttons for a mob. */
    static MutableComponent mobButtons(String entityId) {
        return Component.empty()
                .append(link("[Spawns]", ROOT + " spawns " + entityId)).append(" ")
                .append(link("[Drops]", ROOT + " drops " + entityId));
    }

    /** Vanilla IDs without their namespace, to keep lines short. */
    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    static String shortId(ResourceLocation id) {
        return shortId(id.toString());
    }

    static String chance(double chance) {
        return chance >= 1 ? "" : String.format(" (%.1f%% chance)", chance * 100);
    }

    static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e9 ? String.valueOf((long) value)
                : String.format("%.3f", value).replaceAll("0+$", "");
    }

    /** One entry per drop rule that applies to the mob in the dimension. */
    record DropRule(String list, Rules.EntityRule entity, String text, boolean removes) {}

    static List<DropRule> dropRules(EntityType<?> type, ResourceLocation dimension) {
        List<DropRule> rules = new ArrayList<>();
        for (Rules.RemoveDrop rule : Rules.REMOVE_DROPS.get()) {
            if (!Rules.matches(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            String item = rule.item() == null ? "every item" : shortId(BuiltInRegistries.ITEM.getKey(rule.item()));
            rules.add(new DropRule("removeDrops", rule.entity(), item + chance(rule.chance()), true));
        }
        for (Rules.ExtraDrop rule : Rules.EXTRA_DROPS.get()) {
            if (!Rules.matchesMob(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            rules.add(new DropRule("extraDrops", rule.entity(), rule.count() + "x " + shortId(BuiltInRegistries.ITEM.getKey(rule.item()))
                    + chance(rule.chance()) + (rule.looting() ? ", more with Looting" : ""), false));
        }
        return rules;
    }
}
