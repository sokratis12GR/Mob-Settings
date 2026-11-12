package com.sofodev.mobsettings;

import static com.sofodev.mobsettings.CommandText.shortId;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /mobsettings spawns <entity>} lists the biomes of the current dimension a mob naturally spawns in, and
 * {@code /mobsettings spawns here} lists what can spawn where the command runs, both with every rule applied.
 * {@code /mobsettings drops <entity>} lists the items a mob's loot table can drop.
 */
public final class MobSpawnsCommand {
    private MobSpawnsCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MobSettings.MODID)
                .then(Commands.literal("spawns")
                        .requires(MobSettingsCommand::isOp)
                        .then(Commands.literal("here").executes(MobSpawnsCommand::here))
                        .then(Commands.argument("entity", ResourceArgument.resource(event.getBuildContext(), Registries.ENTITY_TYPE))
                                .executes(c -> entity(c, List.of(c.getSource().getLevel()), c.getSource().getLevel().dimension().location().toString(), 1))
                                .then(Commands.literal("all")
                                        .executes(c -> entity(c, levels(c.getSource()), "all", 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(c -> entity(c, levels(c.getSource()), "all", IntegerArgumentType.getInteger(c, "page")))))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(c -> dimension(c, 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(c -> dimension(c, IntegerArgumentType.getInteger(c, "page")))))))
                .then(Commands.literal("drops")
                        .requires(MobSettingsCommand::isOp)
                        .then(Commands.argument("entity", ResourceArgument.resource(event.getBuildContext(), Registries.ENTITY_TYPE))
                                .executes(c -> drops(c, c.getSource().getLevel()))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(c -> drops(c, DimensionArgument.getDimension(c, "dimension")))))));
    }

    /** Biome lines per page of {@code /mobsettings spawns}. */
    private static final int PAGE_SIZE = 8;

    /**
     * Biome lists only, plus each dimension's rules. Structure spawns (fortresses, witch huts...) show up in "here".
     * Page 1 starts with a summary of the distinct weights and group sizes; every page lists {@link #PAGE_SIZE}
     * biomes and ends with clickable page buttons.
     *
     * @param scope "all" or the dimension ID, for the page buttons' command
     */
    private static int entity(CommandContext<CommandSourceStack> context, List<ServerLevel> levels, String scope, int page)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        EntityType<?> type = ResourceArgument.getResource(context, "entity", Registries.ENTITY_TYPE).value();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        List<MobLookups.Spawn> spawns = new ArrayList<>();
        for (ServerLevel level : levels) spawns.addAll(MobLookups.spawns(level, type));
        String where = levels.size() == 1 ? shortId(levels.getFirst().dimension().location()) : "every dimension";
        if (spawns.isEmpty()) {
            source.sendFailure(Component.literal(shortId(entityId) + " doesn't spawn naturally in " + where));
            return 0;
        }
        int pages = (spawns.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.clamp(page, 1, pages);
        source.sendSuccess(() -> Component.literal(shortId(entityId) + " (" + type.getCategory().getName() + ") in " + where + ": "
                + spawns.size() + (spawns.size() == 1 ? " biome " : " biomes ")).withStyle(ChatFormatting.GOLD)
                .append(CommandText.link("[Drops]", CommandText.ROOT + " drops " + entityId)), false);
        if (current == 1) {
            for (Component line : groups(spawns)) source.sendSuccess(() -> line, false);
        }
        boolean showDimension = levels.size() > 1;
        for (MobLookups.Spawn spawn : spawns.subList((current - 1) * PAGE_SIZE, Math.min(spawns.size(), current * PAGE_SIZE))) {
            MobSpawnSettings.SpawnerData data = spawn.data();
            String name = (showDimension ? shortId(spawn.dimension()) + " / " : "") + shortId(spawn.biomeId());
            Component line = Component.literal("  " + name).append(Component.literal(
                    "  " + compact(data.getWeight().asInt(), data.minCount, data.maxCount, share(spawn))).withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
        if (pages > 1) {
            String command = CommandText.ROOT + " spawns " + entityId + " " + scope + " ";
            MutableComponent nav = Component.literal("  ");
            nav.append(CommandText.pageButton("[◀ Prev]", command + (current - 1), current > 1));
            nav.append(Component.literal("  Page " + current + "/" + pages + "  ").withStyle(ChatFormatting.YELLOW));
            nav.append(CommandText.pageButton("[Next ▶]", command + (current + 1), current < pages));
            source.sendSuccess(() -> nav, false);
        }
        return spawns.size();
    }

    /** The shared {@code weight 10 · 1–4 · 12.5%} format of every spawn line. */
    private static String compact(int weight, int min, int max, double share) {
        return "weight " + weight + " · " + min + "–" + max + String.format(" · %.1f%%", share);
    }

    /** One line per distinct weight, group size and placer, with how many biomes share it. */
    private static List<Component> groups(List<MobLookups.Spawn> spawns) {
        Map<String, List<MobLookups.Spawn>> groups = new LinkedHashMap<>();
        for (MobLookups.Spawn spawn : spawns) {
            MobSpawnSettings.SpawnerData data = spawn.data();
            String key = "weight " + data.getWeight().asInt() + ", group " + data.minCount + "–" + data.maxCount
                    + " (" + placer(data, spawn.dimension(), spawn.biome()) + ")";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(spawn);
        }
        List<Component> lines = new ArrayList<>();
        groups.forEach((key, members) -> {
            double low = Double.MAX_VALUE, high = 0;
            for (MobLookups.Spawn spawn : members) {
                low = Math.min(low, share(spawn));
                high = Math.max(high, share(spawn));
            }
            String shares = low == high ? String.format("%.1f%%", low) : String.format("%.1f–%.1f%%", low, high);
            lines.add(Component.literal("  " + key + ": ").withStyle(ChatFormatting.YELLOW).append(Component.literal(
                    members.size() + (members.size() == 1 ? " biome, " : " biomes, ") + shares + " of category")
                    .withStyle(ChatFormatting.GRAY)));
        });
        return lines;
    }

    /** The items the mob's own loot table can drop, then the drop rules that apply to it in the dimension. */
    private static int drops(CommandContext<CommandSourceStack> context, ServerLevel level) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        EntityType<?> type = ResourceArgument.getResource(context, "entity", Registries.ENTITY_TYPE).value();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        ResourceLocation dimension = level.dimension().location();
        List<ResourceLocation> items = MobLookups.drops(source.getServer(), type);
        List<CommandText.DropRule> rules = CommandText.dropRules(type, dimension);
        if (items.isEmpty() && rules.isEmpty()) {
            source.sendFailure(Component.literal(shortId(entityId) + "'s loot table drops no items and no drop rules apply in " + shortId(dimension)));
            return 0;
        }
        Component note = Component.literal(" [?]").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
                        "Equipment, picked-up items and heads from charged creepers can drop too; they aren't in the loot table."))));
        if (items.isEmpty()) {
            source.sendSuccess(() -> Component.literal(shortId(entityId) + "'s loot table drops no items ").withStyle(ChatFormatting.GOLD)
                    .append(CommandText.link("[Spawns]", CommandText.ROOT + " spawns " + entityId)).append(note), false);
        } else {
            source.sendSuccess(() -> Component.literal(shortId(entityId) + " can drop " + items.size() + (items.size() == 1 ? " item" : " items")
                    + " from its loot table ").withStyle(ChatFormatting.GOLD)
                    .append(CommandText.link("[Spawns]", CommandText.ROOT + " spawns " + entityId)).append(note), false);
            for (ResourceLocation item : items) {
                Component line = Component.literal("  " + shortId(item)).withStyle(ChatFormatting.GRAY);
                source.sendSuccess(() -> line, false);
            }
        }
        if (!rules.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Drop rules in " + shortId(dimension) + ":").withStyle(ChatFormatting.GOLD), false);
            for (CommandText.DropRule rule : rules) {
                Component line = Component.literal("  " + (rule.removes() ? "removed: " : "extra: ") + rule.text())
                        .withStyle(rule.removes() ? ChatFormatting.RED : ChatFormatting.GREEN);
                source.sendSuccess(() -> line, false);
            }
        }
        return items.size();
    }

    private static int dimension(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        return entity(context, List.of(level), level.dimension().location().toString(), page);
    }

    private static List<ServerLevel> levels(CommandSourceStack source) {
        List<ServerLevel> levels = new ArrayList<>();
        source.getServer().getAllLevels().forEach(levels::add);
        return levels;
    }

    private static int here(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Holder<Biome> biome = level.getBiome(pos);
        StructureManager structures = level.structureManager();
        ResourceLocation dimension = level.dimension().location();
        List<Component> lines = new ArrayList<>();
        int count = 0;
        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MISC) continue;
            // The same list natural spawning uses, including structure spawns and every mod's spawn events.
            List<MobSpawnSettings.SpawnerData> list = EventHooks.getPotentialSpawns(level, category, pos,
                    level.getChunkSource().getGenerator().getMobsAt(biome, structures, category, pos)).unwrap();
            if (list.isEmpty()) continue;
            lines.add(Component.literal(category.getName() + ":").withStyle(ChatFormatting.YELLOW));
            int total = total(list);
            for (MobSpawnSettings.SpawnerData entry : list) lines.add(line(entry, total, dimension, biome));
            count += list.size();
        }
        if (count == 0) {
            source.sendFailure(Component.literal("Nothing can spawn naturally at " + pos.toShortString() + " in " + name(biome)));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawns at " + pos.toShortString() + " in " + name(biome) + ":")
                .withStyle(ChatFormatting.GOLD), false);
        for (Component line : lines) source.sendSuccess(() -> line, false);
        return count;
    }

    private static double share(MobLookups.Spawn spawn) {
        return 100.0 * spawn.data().getWeight().asInt() / Math.max(1, spawn.total());
    }

    private static String placer(MobSpawnSettings.SpawnerData data, ResourceLocation dimension, Holder<Biome> biome) {
        if (MobSpawns.placesGroups(data.type, dimension, biome)) {
            int limit = MSConfig.MAX_GROUP_PLACEMENT.get();
            return data.maxCount > limit ? "placed by Mob Settings, capped at " + limit : "placed by Mob Settings";
        }
        return data.maxCount > 4 ? "vanilla, usually cut short above 4" : "vanilla";
    }

    /** A clickable mob name in the compact format; the placer goes in the hover. */
    private static Component line(MobSpawnSettings.SpawnerData data, int total, ResourceLocation dimension, Holder<Biome> biome) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(data.type).toString();
        int weight = data.getWeight().asInt();
        String command = CommandText.ROOT + " spawns " + id;
        Component hover = Component.literal(placer(data, dimension, biome)).append("\nClick: " + command);
        Component name = Component.literal(shortId(id)).withStyle(style -> style.withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        return Component.literal("  ").append(name).append(Component.literal(
                "  " + compact(weight, data.minCount, data.maxCount, 100.0 * weight / Math.max(1, total))).withStyle(ChatFormatting.GRAY));
    }

    private static int total(List<MobSpawnSettings.SpawnerData> list) {
        int total = 0;
        for (MobSpawnSettings.SpawnerData entry : list) total += entry.getWeight().asInt();
        return total;
    }

    private static String name(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> shortId(key.location())).orElse("unknown");
    }
}
