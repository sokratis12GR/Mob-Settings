package com.sofodev.mobsettings;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.predicates.NbtPredicate;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Gives naturally spawned mobs the data from their addSpawns line. Data is chosen when the mob finalizes, but only applied
 * once it joins the world: vanilla gear is rolled while finalizing and would overwrite it. Mobs placed during chunk
 * generation join when their chunk loads, so the choice is kept in the mob's saved data until then.
 */
public final class MobSpawnData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PENDING = MobSettings.MODID + ":pending_data";

    private MobSpawnData() {}

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        EntitySpawnReason reason = event.getSpawnType();
        if ((reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) || event.isSpawnCancelled()) return;
        Mob mob = event.getEntity();
        EntityType<?> type = mob.getType();
        List<Rules.AddSpawn> rules = new ArrayList<>();
        boolean hasData = false;
        Identifier dimension = event.getLevel().getLevel().dimension().identifier();
        Holder<Biome> biome = null;
        for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
            if (rule.entity() != type || !Rules.matches(rule.dimension(), dimension)) continue;
            if (biome == null) biome = event.getLevel().getBiome(mob.blockPosition());
            if (!rule.biome().matches(biome)) continue;
            rules.add(rule);
            hasData |= rule.template() != null;
        }
        if (!hasData) return;
        Rules.AddSpawn chosen = roll(rules, type, dimension, biome, mob);
        CompoundTag data = chosen == null || chosen.template() == null ? null : chosen.template().data(mob.registryAccess());
        if (data != null) mob.getPersistentData().put(PENDING, data.copy());
    }

    /**
     * A spawned mob doesn't say which spawn entry made it, so this picks a line by its share of the mob's weight here.
     * The share not covered by a line, such as the mob's existing spawns, stays plain and returns null.
     */
    static Rules.@Nullable AddSpawn roll(List<Rules.AddSpawn> rules, EntityType<?> type, Identifier dimension, Holder<Biome> biome, Mob mob) {
        // Lines for every dimension are already in the biome's list. Lines for one dimension are added to it as mobs spawn.
        double total = 0;
        for (Weighted<MobSpawnSettings.SpawnerData> entry : biome.value().getMobSettings().getMobs(type.getCategory()).unwrap()) {
            if (entry.value().type() == type) total += entry.weight();
        }
        for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
            if (rule.dimension() != null && rule.dimension().equals(dimension) && rule.biome().matches(biome) && Rules.matches(rule.entity(), type)) {
                total *= rule.keep();
            }
        }
        int lines = 0;
        for (Rules.AddSpawn rule : rules) {
            lines += rule.weight();
            if (rule.dimension() != null) total += rule.weight();
        }
        // The biome's list only updates when the world reloads, so it can be missing a line added since.
        double roll = mob.getRandom().nextDouble() * Math.max(total, lines);
        for (Rules.AddSpawn rule : rules) {
            roll -= rule.weight();
            if (roll < 0) return rule;
        }
        return null;
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        CompoundTag persistent = mob.getPersistentData();
        CompoundTag data = persistent.getCompound(PENDING).orElse(null);
        if (data == null) return;
        persistent.remove(PENDING);
        // The same merge as /data merge entity, so whatever that accepts works here too.
        CompoundTag merged = NbtPredicate.getEntityTagToCompare(mob).merge(data);
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mob.problemPath(), LOGGER)) {
            mob.load(TagValueInput.create(reporter, mob.registryAccess(), merged));
        }
    }
}
