package com.sofodev.mobsettings;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Applies spawn rules in two layers, each removing before adding:
 * rules for every dimension edit the biomes themselves (so chunk-generation spawns and other mods see them),
 * then dimension-specific rules edit the spawn list at runtime, since a biome doesn't know which dimension it's in.
 */
public final class MobSpawns implements BiomeModifier {
    private static final MobSpawns INSTANCE = new MobSpawns();
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MobSettings.MODID);
    private static final Supplier<MapCodec<MobSpawns>> CODEC =
            SERIALIZERS.register("configured_spawns", () -> MapCodec.unit(INSTANCE));

    // SpawnerData is compared by identity, and vanilla re-fires PotentialSpawns to check the picked entry is still
    // in the list. Runtime entries must therefore be the same instance every time, or they would never spawn.
    private static final Map<Rules.AddSpawn, MobSpawnSettings.SpawnerData> ADDED = new ConcurrentHashMap<>();
    private static final Map<Rules.RemoveSpawn, Map<MobSpawnSettings.SpawnerData, MobSpawnSettings.SpawnerData>> REDUCED =
            new ConcurrentHashMap<>();

    static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC.get(); }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        MobSpawnSettingsBuilder spawns = builder.getMobSpawnSettings();
        // REMOVE runs after every mod's ADD phase, and our additions wait until MODIFY so removals never undo them.
        if (phase == Phase.REMOVE) {
            for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
                if (rule.dimension() != null || !rule.biome().matches(biome)) continue;
                // Removing from all spawn categories also handles modded categories.
                for (MobCategory category : MobCategory.values()) reduce(spawns.getSpawner(category), rule);
            }
        } else if (phase == Phase.MODIFY) {
            for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
                if (rule.dimension() != null || !rule.biome().matches(biome)) continue;
                spawns.addSpawn(rule.entity().getCategory(), spawnerData(rule));
            }
        }
    }

    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof Level level)) return;
        ResourceLocation dimension = level.dimension().location();
        Holder<Biome> biome = null;
        for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (biome == null) biome = level.getBiome(event.getPos());
            if (!rule.biome().matches(biome)) continue;
            for (MobSpawnSettings.SpawnerData entry : List.copyOf(event.getSpawnerDataList())) {
                if (!Rules.matches(rule.entity(), entry.type)) continue;
                event.removeSpawnerData(entry);
                MobSpawnSettings.SpawnerData reduced = REDUCED
                        .computeIfAbsent(rule, r -> Collections.synchronizedMap(new WeakHashMap<>()))
                        .computeIfAbsent(entry, e -> reduced(e, rule));
                if (reduced != null) event.addSpawnerData(reduced);
            }
        }
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (rule.entity().getCategory() != event.getMobCategory()) continue;
            if (biome == null) biome = level.getBiome(event.getPos());
            if (!rule.biome().matches(biome)) continue;
            event.addSpawnerData(ADDED.computeIfAbsent(rule, MobSpawns::spawnerData));
        }
    }

    private static void reduce(List<MobSpawnSettings.SpawnerData> spawner, Rules.RemoveSpawn rule) {
        Predicate<MobSpawnSettings.SpawnerData> isTarget = entry -> Rules.matches(rule.entity(), entry.type);
        List<MobSpawnSettings.SpawnerData> matched = spawner.stream().filter(isTarget).toList();
        spawner.removeIf(isTarget);
        for (MobSpawnSettings.SpawnerData entry : matched) {
            MobSpawnSettings.SpawnerData reduced = reduced(entry, rule);
            if (reduced != null) spawner.add(reduced);
        }
    }

    /** The entry with its weight cut by the rule's percent, or null once nothing is left. */
    private static MobSpawnSettings.@Nullable SpawnerData reduced(MobSpawnSettings.SpawnerData entry, Rules.RemoveSpawn rule) {
        int weight = (int) Math.round(entry.getWeight().asInt() * rule.keep());
        return weight > 0 ? new MobSpawnSettings.SpawnerData(entry.type, weight, entry.minCount, entry.maxCount) : null;
    }

    private static MobSpawnSettings.SpawnerData spawnerData(Rules.AddSpawn rule) {
        return new MobSpawnSettings.SpawnerData(rule.entity(), rule.weight(), rule.min(), rule.max());
    }
}
