package com.sofodev.mobsettings;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Applies spawn rules in two layers, each removing before adding:
 * rules for every dimension edit the biomes themselves (so chunk-generation spawns and other mods see them),
 * then dimension-specific rules edit the spawn list at runtime, since a biome doesn't know which dimension it's in.
 * Added mobs also skip their own spawn rules (ground, light) where a rule covers them, or they'd rarely spawn outside
 * their usual habitat, unless the rule asks to keep those rules.
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
    private static final Map<Rules.GroupSize, Map<MobSpawnSettings.SpawnerData, MobSpawnSettings.SpawnerData>> RESIZED =
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
            for (Rules.GroupSize rule : Rules.GROUP_SIZES.get()) {
                if (rule.dimension() != null || !rule.biome().matches(biome)) continue;
                for (MobCategory category : MobCategory.values()) resize(spawns.getSpawner(category), rule);
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
        for (Rules.GroupSize rule : Rules.GROUP_SIZES.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (biome == null) biome = level.getBiome(event.getPos());
            if (!rule.biome().matches(biome)) continue;
            for (MobSpawnSettings.SpawnerData entry : List.copyOf(event.getSpawnerDataList())) {
                if (!Rules.matches(rule.entity(), entry.type)) continue;
                event.removeSpawnerData(entry);
                event.addSpawnerData(RESIZED
                        .computeIfAbsent(rule, r -> Collections.synchronizedMap(new WeakHashMap<>()))
                        .computeIfAbsent(entry, e -> resized(e, rule)));
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

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getDefaultResult() || event.getResult() != MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT) return;
        if (isNaturalSpawn(event.getSpawnType()) && ignoresSpawnRules(event.getEntityType(), event.getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
        }
    }

    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT || !isNaturalSpawn(event.getSpawnType())) return;
        Mob mob = event.getEntity();
        if (ignoresSpawnRules(mob.getType(), event.getLevel(), mob.blockPosition())) {
            // Only the mob's own rules are skipped, so it still can't spawn inside liquid or other mobs.
            event.setResult(mob.checkSpawnObstruction(event.getLevel())
                    ? MobSpawnEvent.PositionCheck.Result.SUCCEED
                    : MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /**
     * Phantoms, cats, patrols, the wandering trader, sieges and raids are spawned by code instead of a biome's spawn
     * list, so removal rules reach them here. A partial percent only rolls for patrols and events: natural spawns
     * already had their weight cut, and rolling again would cut it twice.
     */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType reason = event.getSpawnType();
        boolean rolls = reason == MobSpawnType.EVENT || reason == MobSpawnType.PATROL;
        if (!rolls && reason != MobSpawnType.NATURAL) return;
        EntityType<?> type = event.getEntity().getType();
        ServerLevelAccessor level = event.getLevel();
        ResourceLocation dimension = level.getLevel().dimension().location();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        Holder<Biome> biome = null;
        double keep = 1;
        boolean matched = false;
        for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
            if (!Rules.matches(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            if (rule.keep() > 0 && !rolls) continue;
            if (biome == null) biome = level.getBiome(pos);
            if (!rule.biome().matches(biome)) continue;
            keep *= rule.keep();
            matched = true;
        }
        if (!matched || isAdded(type, dimension, biome)) return;
        if (keep <= 0 || event.getEntity().getRandom().nextDouble() >= keep) {
            event.setSpawnCancelled(true);
            // A raider joins its wave before finalizing, and one that never enters the world would hold the raid open.
            if (event.getEntity() instanceof Raider raider && raider.getCurrentRaid() != null) raider.getCurrentRaid().removeFromRaid(raider, true);
        }
    }

    /** Whether an addSpawns line covers the mob here, since added spawns are never removed. */
    private static boolean isAdded(EntityType<?> type, ResourceLocation dimension, Holder<Biome> biome) {
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.entity() == type && Rules.matches(rule.dimension(), dimension) && rule.biome().matches(biome)) return true;
        }
        return false;
    }

    private static boolean isNaturalSpawn(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    private static boolean ignoresSpawnRules(EntityType<?> type, ServerLevelAccessor level, BlockPos pos) {
        ResourceLocation dimension = null;
        Holder<Biome> biome = null;
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.useSpawnRules() || rule.entity() != type) continue;
            if (dimension == null) dimension = level.getLevel().dimension().location();
            if (!Rules.matches(rule.dimension(), dimension)) continue;
            if (biome == null) biome = level.getBiome(pos);
            if (rule.biome().matches(biome)) return true;
        }
        return false;
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

    private static void resize(List<MobSpawnSettings.SpawnerData> spawner, Rules.GroupSize rule) {
        spawner.replaceAll(entry -> Rules.matches(rule.entity(), entry.type) ? resized(entry, rule) : entry);
    }

    private static MobSpawnSettings.SpawnerData resized(MobSpawnSettings.SpawnerData data, Rules.GroupSize rule) {
        return new MobSpawnSettings.SpawnerData(data.type, data.getWeight(), rule.min(), rule.max());
    }

    /** The entry with its weight cut by the rule's percent, or null only when the rule removes all of it. */
    private static MobSpawnSettings.@Nullable SpawnerData reduced(MobSpawnSettings.SpawnerData entry, Rules.RemoveSpawn rule) {
        if (rule.keep() <= 0) return null;
        // A partial cut never rounds a small weight down to nothing.
        int weight = Math.max(1, (int) Math.round(entry.getWeight().asInt() * rule.keep()));
        return new MobSpawnSettings.SpawnerData(entry.type, weight, entry.minCount, entry.maxCount);
    }

    private static MobSpawnSettings.SpawnerData spawnerData(Rules.AddSpawn rule) {
        return new MobSpawnSettings.SpawnerData(rule.entity(), rule.weight(), rule.min(), rule.max());
    }
}
