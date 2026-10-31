package com.sofodev.mobsettings;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
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
import org.jspecify.annotations.Nullable;

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

    static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC.get(); }

    @Override
    public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        MobSpawnSettingsBuilder spawns = builder.getMobSpawnSettings();
        // REMOVE runs after every mod's ADD phase, and our additions wait until MODIFY so removals never undo them.
        if (phase == Phase.REMOVE) {
            for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
                if (rule.dimension() != null || !rule.biome().matches(biome)) continue;
                // Every category the biome defines, which also covers modded categories. Undefined ones have no list (null).
                for (MobCategory category : spawns.getSpawnerTypes()) reduce(spawns.getSpawner(category), rule);
            }
        } else if (phase == Phase.MODIFY) {
            for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
                if (rule.dimension() != null || !rule.biome().matches(biome)) continue;
                spawns.addSpawn(rule.entity(), rule.weight(), rule.min(), rule.max());
            }
        }
    }

    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof Level level)) return;
        Identifier dimension = level.dimension().identifier();
        Holder<Biome> biome = null;
        for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (biome == null) biome = level.getBiome(event.getPos());
            if (!rule.biome().matches(biome)) continue;
            for (Weighted<MobSpawnSettings.SpawnerData> entry : List.copyOf(event.getSpawnerDataList())) {
                if (!Rules.matches(rule.entity(), entry.value().type())) continue;
                event.removeSpawnerData(entry);
                Weighted<MobSpawnSettings.SpawnerData> reduced = reduced(entry, rule);
                if (reduced != null) event.addSpawnerData(reduced);
            }
        }
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (rule.entity().getCategory() != event.getMobCategory()) continue;
            if (biome == null) biome = level.getBiome(event.getPos());
            if (!rule.biome().matches(biome)) continue;
            event.addSpawnerData(new Weighted<>(spawnerData(rule), rule.weight()));
        }
    }

    private static void reduce(WeightedList.Builder<MobSpawnSettings.SpawnerData> spawner, Rules.RemoveSpawn rule) {
        Predicate<Weighted<MobSpawnSettings.SpawnerData>> isTarget = entry -> Rules.matches(rule.entity(), entry.value().type());
        List<Weighted<MobSpawnSettings.SpawnerData>> matched = spawner.getList().stream().filter(isTarget).toList();
        spawner.removeIf(isTarget);
        for (Weighted<MobSpawnSettings.SpawnerData> entry : matched) {
            Weighted<MobSpawnSettings.SpawnerData> reduced = reduced(entry, rule);
            if (reduced != null) spawner.add(reduced.value(), reduced.weight());
        }
    }

    /** The entry with its weight cut by the rule's percent, or null once nothing is left. */
    private static @Nullable Weighted<MobSpawnSettings.SpawnerData> reduced(Weighted<MobSpawnSettings.SpawnerData> entry, Rules.RemoveSpawn rule) {
        int weight = (int) Math.round(entry.weight() * rule.keep());
        return weight > 0 ? new Weighted<>(entry.value(), weight) : null;
    }

    private static MobSpawnSettings.SpawnerData spawnerData(Rules.AddSpawn rule) {
        return new MobSpawnSettings.SpawnerData(rule.entity(), UniformInt.of(rule.min(), rule.max()));
    }
}
