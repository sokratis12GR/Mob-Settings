package com.sofodev.mobsettings;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.SpawnClusterSizeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

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
            for (Rules.GroupSize rule : Rules.GROUP_SIZES.get()) {
                if (rule.dimension() != null || !rule.biome().matches(biome)) continue;
                for (MobCategory category : spawns.getSpawnerTypes()) resize(spawns.getSpawner(category), rule);
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
        applyDimensionRules(event, level.dimension().identifier(), () -> level.getBiome(event.getPos()));
    }

    /** Applies the dimension-specific rules to a spawn list. The biome is only looked up if a rule needs it. */
    static void applyDimensionRules(LevelEvent.PotentialSpawns event, Identifier dimension, Supplier<Holder<Biome>> biomeAt) {
        Holder<Biome> biome = null;
        for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (biome == null) biome = biomeAt.get();
            if (!rule.biome().matches(biome)) continue;
            for (Weighted<MobSpawnSettings.SpawnerData> entry : List.copyOf(event.getSpawnerDataList())) {
                if (!Rules.matches(rule.entity(), entry.value().type())) continue;
                event.removeSpawnerData(entry);
                Weighted<MobSpawnSettings.SpawnerData> reduced = reduced(entry, rule);
                if (reduced != null) event.addSpawnerData(reduced);
            }
        }
        for (Rules.GroupSize rule : Rules.GROUP_SIZES.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (biome == null) biome = biomeAt.get();
            if (!rule.biome().matches(biome)) continue;
            for (Weighted<MobSpawnSettings.SpawnerData> entry : List.copyOf(event.getSpawnerDataList())) {
                if (!Rules.matches(rule.entity(), entry.value().type())) continue;
                event.removeSpawnerData(entry);
                event.addSpawnerData(new Weighted<>(resized(entry.value(), rule), entry.weight()));
            }
        }
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.dimension() == null || !rule.dimension().equals(dimension)) continue;
            if (rule.entity().getCategory() != event.getMobCategory()) continue;
            if (biome == null) biome = biomeAt.get();
            if (!rule.biome().matches(biome)) continue;
            event.addSpawnerData(new Weighted<>(spawnerData(rule), rule.weight()));
        }
    }

    /**
     * Vanilla places a group one try per member, all at the first spot's height and each step up to 5 blocks from the
     * last, and stops after getMaxSpawnClusterSize() mobs, so large configured groups end up around 10 to 20.
     * This fires right after vanilla adds a mob: for mobs a rule sizes, the rest of the group is placed here and vanilla
     * is told to stop, so a group is never placed twice.
     */
    public static void onSpawnClusterSize(SpawnClusterSizeEvent event) {
        Mob leader = event.getEntity();
        // Only a mob vanilla has just added, not some other caller asking for the size.
        if (!(leader.level() instanceof ServerLevel level) || !leader.isAddedToLevel() || leader.tickCount > 0) return;
        int[] group = groupSize(leader, level);
        if (group == null) return;
        int size = group[0] + leader.getRandom().nextInt(1 + group[1] - group[0]);
        placeGroup(level, leader, Math.min(size, MSConfig.MAX_GROUP_PLACEMENT.get()) - 1);
        event.setSize(1);
    }

    /**
     * The {min, max} for this mob here, or null when no rule sizes it. A spawn can't be traced back to its entry, so an
     * addSpawns line is chosen by its share of the weight, as for spawn data. The rest of the weight uses the
     * spawnGroupSizes line that applies last, or stays vanilla's when there is none.
     */
    private static int @Nullable [] groupSize(Mob mob, ServerLevel level) {
        EntityType<?> type = mob.getType();
        Identifier dimension = level.dimension().identifier();
        Holder<Biome> biome = level.getBiome(mob.blockPosition());
        List<Rules.AddSpawn> adds = new java.util.ArrayList<>();
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.entity() == type && Rules.matches(rule.dimension(), dimension) && rule.biome().matches(biome)) adds.add(rule);
        }
        Rules.GroupSize resize = groupRule(type, dimension, biome);
        if (adds.isEmpty() && resize == null) return null;
        Rules.AddSpawn chosen = null;
        if (!adds.isEmpty()) {
            MobSpawnSettings spawns = level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, mob.blockPosition());
            chosen = MobSpawnData.roll(adds, type, dimension, biome, spawns, mob);
        }
        if (chosen != null) return new int[] {chosen.min(), chosen.max()};
        return resize == null ? null : new int[] {resize.min(), resize.max()};
    }

    /** The spawnGroupSizes line that wins here: dimension lines are applied after every-dimension ones, later lines after earlier. */
    private static Rules.@Nullable GroupSize groupRule(EntityType<?> type, Identifier dimension, Holder<Biome> biome) {
        Rules.GroupSize result = null;
        for (boolean dimensional : new boolean[] {false, true}) {
            for (Rules.GroupSize rule : Rules.GROUP_SIZES.get()) {
                if ((rule.dimension() != null) != dimensional || !Rules.matches(rule.entity(), type)) continue;
                if (Rules.matches(rule.dimension(), dimension) && rule.biome().matches(biome)) result = rule;
            }
        }
        return result;
    }

    /** Whether this mod places the mob's groups here, for the spawns command. */
    static boolean placesGroups(EntityType<?> type, Identifier dimension, Holder<Biome> biome) {
        return isAdded(type, dimension, biome) || groupRule(type, dimension, biome) != null;
    }

    /**
     * Places the rest of a group around its first mob, with the checks vanilla makes: the spawn rules (skipped where an
     * addSpawns line says so), room to stand, NeoForge's position check and the finalize event, so removals, light
     * restrictions and spawn data still apply to each member. Unlike vanilla it searches a few blocks up and down and
     * widens with the group, so the min is reached unless the area is really full.
     */
    private static void placeGroup(ServerLevel level, Mob leader, int count) {
        if (count <= 0) return;
        EntityType<?> type = leader.getType();
        RandomSource random = leader.getRandom();
        BlockPos origin = leader.blockPosition();
        int radius = 4 + (int) Math.ceil(Math.sqrt(count));
        int placed = 0;
        for (int attempt = 0; attempt < count * 8 && placed < count; attempt++) {
            int x = origin.getX() + random.nextInt(2 * radius + 1) - radius;
            int z = origin.getZ() + random.nextInt(2 * radius + 1) - radius;
            BlockPos pos = standingSpot(level, type, x, origin.getY(), z, random);
            // Vanilla never spawns within 24 blocks of a player.
            if (pos == null || level.getNearestPlayer(x + 0.5, pos.getY(), z + 0.5, 24, false) != null) continue;
            if (!(type.create(level, EntitySpawnReason.NATURAL) instanceof Mob mob)) return;
            mob.snapTo(x + 0.5, pos.getY(), z + 0.5, random.nextFloat() * 360, 0);
            if (!EventHooks.checkSpawnPosition(mob, level, EntitySpawnReason.NATURAL)) {
                mob.discard();
                continue;
            }
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            // Cancelled while finalizing, e.g. by a light restriction: that spot doesn't count.
            if (mob.isSpawnCancelled()) continue;
            level.addFreshEntityWithPassengers(mob);
            placed++;
        }
    }

    private static @Nullable BlockPos standingSpot(ServerLevel level, EntityType<?> type, int x, int y, int z, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= 8; i++) {
            // 0, +1, -1, +2, -2... so the leader's own height is tried first.
            int dy = (i + 1) / 2 * (i % 2 == 1 ? 1 : -1);
            pos.set(x, y + dy, z);
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) continue;
            if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) continue;
            if (!SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)) continue;
            if (level.noCollision(type.getSpawnAABB(x + 0.5, pos.getY(), z + 0.5))) return pos.immutable();
        }
        return null;
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
        EntitySpawnReason reason = event.getSpawnType();
        boolean rolls = reason == EntitySpawnReason.EVENT || reason == EntitySpawnReason.PATROL;
        // Chunk generation reads the biome's list directly, so only dimension rules need catching here.
        boolean generating = reason == EntitySpawnReason.CHUNK_GENERATION;
        if (!rolls && !generating && reason != EntitySpawnReason.NATURAL) return;
        EntityType<?> type = event.getEntity().getType();
        ServerLevelAccessor level = event.getLevel();
        Identifier dimension = level.getLevel().dimension().identifier();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        Holder<Biome> biome = null;
        double keep = 1;
        boolean matched = false;
        for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
            if (!Rules.matches(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            if (rule.keep() > 0 && !rolls) continue;
            if (generating && rule.dimension() == null) continue;
            if (biome == null) biome = level.getBiome(pos);
            if (!rule.biome().matches(biome)) continue;
            keep *= rule.keep();
            matched = true;
        }
        if (!matched || isAdded(type, dimension, biome)) return;
        if (keep <= 0 || event.getEntity().getRandom().nextDouble() >= keep) event.setSpawnCancelled(true);
    }

    /** Whether an addSpawns line covers the mob here, since added spawns are never removed. */
    private static boolean isAdded(EntityType<?> type, Identifier dimension, Holder<Biome> biome) {
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.entity() == type && Rules.matches(rule.dimension(), dimension) && rule.biome().matches(biome)) return true;
        }
        return false;
    }

    private static boolean isNaturalSpawn(EntitySpawnReason reason) {
        return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
    }

    private static boolean ignoresSpawnRules(EntityType<?> type, ServerLevelAccessor level, BlockPos pos) {
        Identifier dimension = null;
        Holder<Biome> biome = null;
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.useSpawnRules() || rule.entity() != type) continue;
            if (dimension == null) dimension = level.getLevel().dimension().identifier();
            if (!Rules.matches(rule.dimension(), dimension)) continue;
            if (biome == null) biome = level.getBiome(pos);
            if (rule.biome().matches(biome)) return true;
        }
        return false;
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

    private static void resize(WeightedList.Builder<MobSpawnSettings.SpawnerData> spawner, Rules.GroupSize rule) {
        Predicate<Weighted<MobSpawnSettings.SpawnerData>> isTarget = entry -> Rules.matches(rule.entity(), entry.value().type());
        List<Weighted<MobSpawnSettings.SpawnerData>> matched = spawner.getList().stream().filter(isTarget).toList();
        spawner.removeIf(isTarget);
        for (Weighted<MobSpawnSettings.SpawnerData> entry : matched) spawner.add(resized(entry.value(), rule), entry.weight());
    }

    private static MobSpawnSettings.SpawnerData resized(MobSpawnSettings.SpawnerData data, Rules.GroupSize rule) {
        return new MobSpawnSettings.SpawnerData(data.type(), UniformInt.of(rule.min(), rule.max()));
    }

    /** The entry with its weight cut by the rule's percent, or null only when the rule removes all of it. */
    private static @Nullable Weighted<MobSpawnSettings.SpawnerData> reduced(Weighted<MobSpawnSettings.SpawnerData> entry, Rules.RemoveSpawn rule) {
        if (rule.keep() <= 0) return null;
        // A partial cut never rounds a small weight down to nothing.
        int weight = Math.max(1, (int) Math.round(entry.weight() * rule.keep()));
        return new Weighted<>(entry.value(), weight);
    }

    private static MobSpawnSettings.SpawnerData spawnerData(Rules.AddSpawn rule) {
        return new MobSpawnSettings.SpawnerData(rule.entity(), UniformInt.of(rule.min(), rule.max()));
    }
}
