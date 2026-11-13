package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.MSConfig;
import com.sofodev.mobsettings.MobNetwork;
import com.sofodev.mobsettings.SpawnLines;
import com.sofodev.thedragonlib.client.config.EntryPicker;
import com.sofodev.thedragonlib.client.config.EntryPickerScreen;
import com.sofodev.thedragonlib.client.config.EntryPickerScreen.Candidate;
import com.sofodev.thedragonlib.client.config.EntryPickerScreen.Category;
import com.sofodev.thedragonlib.client.config.RuleListScreen;
import com.sofodev.thedragonlib.client.config.RuleType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

/**
 * "Add from list" for extraDrops, removeDrops, addSpawns and removeSpawns: pick a mob, then pick lines made from the
 * opposite list (removeDrops from extraDrops and so on) and, for operators, from what the server says the mob drops
 * or where it spawns.
 */
final class LookupPicker implements EntryPicker {
    private static final String ANY = "*";
    private static final String FROM_CONFIG = "config";
    private static final String FROM_SERVER = "server";
    /** addSpawns needs a weight and group size; these are vanilla's usual ones and can be changed through Edit. */
    private static final String[] DEFAULT_SPAWN = {"10", "1", "4"};

    private final String key;

    private LookupPicker(String key) {
        this.key = key;
    }

    static @Nullable EntryPicker forKey(String key) {
        return switch (key) {
            case "extraDrops", "removeDrops", "addSpawns", "removeSpawns" -> new LookupPicker(key);
            default -> null;
        };
    }

    private boolean spawns() {
        return key.endsWith("Spawns");
    }

    private RuleType type() {
        return MobSettingsRules.TYPES.get(key);
    }

    /** e.g. "Remove Spawns › Add from list › Pick a mob". */
    private Component crumb(Component step) {
        return RuleListScreen.crumbs(Component.translatable(MobSettingsRules.CONFIG_PREFIX + key),
                Component.translatable(MobSettingsRules.UI_PREFIX + "pick"), step);
    }

    private static Component ui(String path, Object... args) {
        return Component.translatable(MobSettingsRules.UI_PREFIX + "lookup." + path, args);
    }

    /** In a world only operators may use it, as it shows what mobs drop and where they spawn. The server checks again. */
    @Override
    public boolean isAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null || Commands.LEVEL_GAMEMASTERS.check(minecraft.player.permissions());
    }

    @Override
    public void open(Screen parent, List<?> current, Consumer<List<String>> add) {
        Component reason = ClientLookups.unavailable();
        if (reason != null) {
            pickMob(parent, Map.of(), reason, current, add);
            return;
        }
        // One request for every mob's count, so the list can say what each one has and hide the rest.
        ClientLookups.request(spawns(), MobNetwork.ALL, result -> {
            if (Minecraft.getInstance().screen != parent) return;
            if (result.statusValue() == MobNetwork.Status.OK) {
                pickMob(parent, result.counts(), null, current, add);
            } else {
                // Lost operator status since the button was drawn: show nothing rather than the config lines.
                Minecraft.getInstance().setScreen(new EntryPickerScreen(crumb(ui("pickMobWithProblem", ui("operatorsOnly"))), parent, List.of(),
                        List.of(new Category(ui("category.all"), c -> true)), Set.of(), true, labels(), picked -> {})
                        .withEmptyText(ui("empty.problem", ui("operatorsOnly"))));
            }
        });
    }

    /** The mobs that have something to add, each with how many drops/biomes the server knows and lines the configs give. */
    private void pickMob(Screen parent, Map<String, Integer> serverCounts, @Nullable Component problem,
                         List<?> current, Consumer<List<String>> add) {
        Map<String, Integer> configCounts = new HashMap<>();
        RuleType type = type();
        for (Candidate candidate : fromConfig(ANY)) {
            configCounts.merge(SpawnLines.fieldId(type.split(candidate.id())[0]), 1, Integer::sum);
        }
        Suggestions.Kind kind = spawns() ? Suggestions.Kind.SPAWNABLE_ENTITY : Suggestions.Kind.ENTITY;
        List<Candidate> mobs = new ArrayList<>();
        int allConfig = configCounts.values().stream().mapToInt(Integer::intValue).sum();
        mobs.add(new Candidate(ANY, new ItemStack(Items.BOOK), ui("anyMob"), ANY, ui("count.config", allConfig)));
        for (Suggestions.Option option : Suggestions.options(kind, null)) {
            if (Suggestions.isSelector(kind, option.id()) || option.id().startsWith("#")) continue;
            int server = serverCounts.getOrDefault(option.id(), 0);
            int config = configCounts.getOrDefault(option.id(), 0);
            if (server == 0 && config == 0) continue;
            Component detail = Component.literal(option.id());
            if (server > 0) detail = ui("count.join", detail, ui(spawns() ? "count.biomes" : "count.drops", server));
            if (config > 0) detail = ui("count.join", detail, ui("count.config", config));
            mobs.add(new Candidate(option.id(), egg(option.id()), Component.literal(option.name()), option.id(), detail));
        }
        Component title = problem == null ? ui("pickMob") : ui("pickMobWithProblem", problem);
        Minecraft.getInstance().setScreen(new EntryPickerScreen(crumb(title), parent, mobs,
                List.of(new Category(ui("category.all"), c -> true)), Set.of(), true, labels(),
                picked -> {
                    if (!picked.isEmpty()) lookup(parent, picked.getFirst(), current, add);
                }));
    }

    /** Asks the server when it can, then shows the lines; without an answer only the config lines are offered. */
    private void lookup(Screen parent, String entity, List<?> current, Consumer<List<String>> add) {
        List<Candidate> fromConfig = fromConfig(entity);
        Component reason = entity.equals(ANY) ? null : ClientLookups.unavailable();
        if (entity.equals(ANY) || reason != null) {
            show(parent, entity, fromConfig, reason, null, current, add);
            return;
        }
        ClientLookups.request(spawns(), entity, result -> {
            // The player may have left the list screen while waiting.
            if (Minecraft.getInstance().screen != parent) return;
            List<Candidate> all = new ArrayList<>(fromConfig);
            Component problem = switch (result.statusValue()) {
                case OK -> null;
                case NO_PERMISSION -> ui("operatorsOnly");
                case UNKNOWN_ENTITY -> ui("unknownEntity");
            };
            if (problem == null) all.addAll(fromServer(entity, result));
            show(parent, entity, all, problem, problem == null ? summary(result) : null, current, add);
        });
    }

    /** A short overview of the server's answer, e.g. "52 biomes in 1 dimension" or "4 items in its loot table". */
    private static Component summary(MobNetwork.Result result) {
        if (!result.spawns()) return ui("summary.drops", result.drops().size());
        if (result.spawnList().isEmpty()) return ui("summary.noSpawns");
        long dimensions = result.spawnList().stream().map(MobNetwork.SpawnEntry::dimension).distinct().count();
        return ui("summary.spawns", result.spawnList().size(), dimensions);
    }

    private void show(Screen parent, String entity, List<Candidate> candidates, @Nullable Component problem,
                      @Nullable Component summary, List<?> current, Consumer<List<String>> add) {
        Set<String> existing = new HashSet<>();
        for (Object line : current) existing.add(String.valueOf(line));
        if (!entity.equals(ANY) && key.startsWith("remove")) {
            // One wildcard line instead of a line per biome or item.
            String[] values = key.equals("removeSpawns") ? new String[]{entity, "", "", ""} : new String[]{entity, ANY, "", ""};
            String line = type().join(values);
            if (type().validate(line, MobSettingsRules.CONTEXT) == null) {
                List<Candidate> withAll = new ArrayList<>();
                withAll.add(new Candidate(line, new ItemStack(Items.BARRIER), ui("everything." + key), ANY,
                        ui("count.join", Component.literal(line), type().format().describe(type().split(line), type(), MobSettingsRules.CONTEXT))));
                withAll.addAll(candidates);
                candidates = withAll;
            }
        }
        String name = entity.equals(ANY) ? ui("anyMob").getString() : Suggestions.displayName(Suggestions.Kind.ENTITY, entity);
        Component title = ui("titleWithProblem", name, problem != null ? problem : summary != null ? summary : ui("summary.configOnly"));
        List<Category> categories = List.of(
                new Category(ui("category.all"), c -> true),
                new Category(ui("category.config"), c -> FROM_CONFIG.equals(c.entry())),
                new Category(ui("category.server"), c -> FROM_SERVER.equals(c.entry())));
        Component empty = problem != null ? ui("empty.problem", problem) : ui("empty");
        Minecraft.getInstance().setScreen(new EntryPickerScreen(crumb(title), parent, candidates, categories, existing, false, labels(), add)
                .withEmptyText(empty));
    }

    /** Lines of the opposite list, turned into lines of this one. */
    private List<Candidate> fromConfig(String entity) {
        String source = switch (key) {
            case "extraDrops" -> "removeDrops";
            case "removeDrops" -> "extraDrops";
            case "addSpawns" -> "removeSpawns";
            default -> "addSpawns";
        };
        RuleType sourceType = MobSettingsRules.TYPES.get(source);
        List<? extends String> lines = switch (source) {
            case "extraDrops" -> MSConfig.EXTRA_DROPS.getRaw();
            case "removeDrops" -> MSConfig.REMOVE_DROPS.getRaw();
            case "addSpawns" -> MSConfig.ADD_SPAWNS.getRaw();
            default -> MSConfig.REMOVE_SPAWNS.getRaw();
        };
        Map<String, Candidate> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (!sourceType.format().matches(line, sourceType)) continue;
            String[] v = sourceType.split(line);
            String mob = SpawnLines.fieldId(v[0]);
            if (!entity.equals(ANY) && !mob.equals(entity)) continue;
            String[] values = switch (key) {
                case "removeDrops" -> new String[]{mob, SpawnLines.fieldId(v[1]), "", v[5]};
                case "extraDrops" -> concrete(Suggestions.Kind.ITEM, v[1]) ? new String[]{mob, v[1], "", "", "", v[3]} : null;
                case "removeSpawns" -> new String[]{mob, v[1], v[2], ""};
                default -> concrete(Suggestions.Kind.SPAWNABLE_ENTITY, mob)
                        ? new String[]{mob, v[1], v[2], DEFAULT_SPAWN[0], DEFAULT_SPAWN[1], DEFAULT_SPAWN[2], ""} : null;
            };
            add(result, values, FROM_CONFIG);
        }
        return List.copyOf(result.values());
    }

    private List<Candidate> fromServer(String entity, MobNetwork.Result answer) {
        Map<String, Candidate> result = new LinkedHashMap<>();
        if (spawns()) {
            for (MobNetwork.SpawnEntry spawn : answer.spawnList()) {
                String[] values = key.equals("removeSpawns")
                        ? new String[]{entity, spawn.dimension(), spawn.biome(), ""}
                        : new String[]{entity, spawn.dimension(), spawn.biome(), String.valueOf(spawn.weight()),
                        String.valueOf(spawn.min()), String.valueOf(spawn.max()), ""};
                add(result, values, FROM_SERVER);
            }
        } else {
            for (String item : answer.drops()) {
                add(result, key.equals("removeDrops") ? new String[]{entity, item, "", ""} : new String[]{entity, item, "", "", "", ""}, FROM_SERVER);
            }
        }
        return List.copyOf(result.values());
    }

    /** Adds the joined line if it's valid; a line from both sources keeps the first. */
    private void add(Map<String, Candidate> result, String @Nullable [] values, String from) {
        if (values == null) return;
        RuleType type = type();
        String line = type.join(values);
        if (result.containsKey(line) || type.validate(line, MobSettingsRules.CONTEXT) != null) return;
        Component name = type.format().describe(type.split(line), type, MobSettingsRules.CONTEXT);
        ItemStack icon = spawns() ? egg(values[0]) : item(values[1]);
        result.put(line, new Candidate(line, icon, name, from));
    }

    /** Not empty, a wildcard, a tag or a namespace selector, so it names one thing. */
    private static boolean concrete(Suggestions.Kind kind, String id) {
        return !id.isEmpty() && !id.equals(ANY) && !id.startsWith("#") && !Suggestions.isSelector(kind, id);
    }

    private static ItemStack egg(String entity) {
        Identifier id = Identifier.tryParse(entity);
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).flatMap(SpawnEggItem::byId).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ItemStack item(String item) {
        Identifier id = Identifier.tryParse(SpawnLines.fieldId(item));
        return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static EntryPickerScreen.Labels labels() {
        return new EntryPickerScreen.Labels(ui("category"), ui("search"), ui("toggleShown"),
                count -> ui("addSelected", count), id -> ui("alreadyAdded"));
    }
}
