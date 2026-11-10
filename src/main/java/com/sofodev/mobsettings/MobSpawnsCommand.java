package com.sofodev.mobsettings;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * {@code /mobsettings spawns <entity>} lists the biomes of the current dimension a mob naturally spawns in, and
 * {@code /mobsettings spawns here} lists what can spawn where the command runs, both with every rule applied.
 */
public final class MobSpawnsCommand {
    private MobSpawnsCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MobSettings.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawns")
                        .then(Commands.literal("here").executes(MobSpawnsCommand::here))
                        .then(Commands.argument("entity", ResourceArgument.resource(event.getBuildContext(), Registries.ENTITY_TYPE))
                                .executes(MobSpawnsCommand::entity))));
    }

    /** Biome lists only, plus this dimension's rules. Structure spawns (fortresses, witch huts...) show up in "here". */
    private static int entity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        EntityType<?> type = ResourceArgument.getResource(context, "entity", Registries.ENTITY_TYPE).value();
        ServerLevel level = source.getLevel();
        ResourceLocation dimension = level.dimension().location();
        MobCategory category = type.getCategory();
        List<Component> lines = new ArrayList<>();
        List<Holder<Biome>> biomes = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .sorted((a, b) -> name(a).compareTo(name(b))).toList();
        for (Holder<Biome> biome : biomes) {
            LevelEvent.PotentialSpawns spawns = new LevelEvent.PotentialSpawns(level, category, BlockPos.ZERO,
                    biome.value().getMobSettings().getMobs(category));
            MobSpawns.applyDimensionRules(spawns, dimension, () -> biome);
            int total = total(spawns.getSpawnerDataList());
            for (MobSpawnSettings.SpawnerData entry : spawns.getSpawnerDataList()) {
                if (entry.type == type) lines.add(line(name(biome), entry, total, dimension, biome));
            }
        }
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal(entityId + " doesn't spawn naturally in any biome of " + dimension), false);
        } else {
            source.sendSuccess(() -> Component.literal(entityId + " (" + category.getName() + ") in " + dimension + ":")
                    .withStyle(ChatFormatting.GOLD), false);
            for (Component line : lines) source.sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    private static int here(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Holder<Biome> biome = level.getBiome(pos);
        StructureManager structures = level.structureManager();
        source.sendSuccess(() -> Component.literal("Spawns at " + pos.toShortString() + " in " + name(biome) + ":")
                .withStyle(ChatFormatting.GOLD), false);
        int count = 0;
        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MISC) continue;
            // The same list natural spawning uses, including structure spawns and every mod's spawn events.
            List<MobSpawnSettings.SpawnerData> list = EventHooks.getPotentialSpawns(level, category, pos,
                    level.getChunkSource().getGenerator().getMobsAt(biome, structures, category, pos)).unwrap();
            if (list.isEmpty()) continue;
            source.sendSuccess(() -> Component.literal(category.getName() + ":").withStyle(ChatFormatting.YELLOW), false);
            int total = total(list);
            for (MobSpawnSettings.SpawnerData entry : list) {
                Component line = line(BuiltInRegistries.ENTITY_TYPE.getKey(entry.type).toString(), entry, total, level.dimension().location(), biome);
                source.sendSuccess(() -> line, false);
                count++;
            }
        }
        if (count == 0) source.sendSuccess(() -> Component.literal("Nothing can spawn naturally here."), false);
        return count;
    }

    /** Shows the min and max, who places the group, and the safety limit when it cuts the max. */
    private static Component line(String name, MobSpawnSettings.SpawnerData data, int total, ResourceLocation dimension, Holder<Biome> biome) {
        int weight = data.getWeight().asInt();
        String share = String.format("%.1f%%", 100.0 * weight / Math.max(1, total));
        String group = "min " + data.minCount + ", max " + data.maxCount;
        String placer;
        if (MobSpawns.placesGroups(data.type, dimension, biome)) {
            int limit = MSConfig.MAX_GROUP_PLACEMENT.get();
            placer = data.maxCount > limit ? "placed by Mob Settings, capped at " + limit : "placed by Mob Settings";
        } else {
            placer = data.maxCount > 4 ? "vanilla, usually cut short above 4" : "vanilla";
        }
        return Component.literal("  " + name).append(Component.literal(
                " weight " + weight + " (" + share + " of category), group " + group + " (" + placer + ")")
                .withStyle(ChatFormatting.GRAY));
    }

    private static int total(List<MobSpawnSettings.SpawnerData> list) {
        int total = 0;
        for (MobSpawnSettings.SpawnerData entry : list) total += entry.getWeight().asInt();
        return total;
    }

    private static String name(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }
}
