package com.styv3.gridlock;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

final class DetectorCompiler {
    private static final Map<String, String> MATERIAL_ALIASES = Map.ofEntries(
        Map.entry("totem of undying", "TOTEM_OF_UNDYING"),
        Map.entry("diamond", "DIAMOND"),
        Map.entry("gold block", "GOLD_BLOCK"),
        Map.entry("iron block", "IRON_BLOCK"),
        Map.entry("diamond block", "DIAMOND_BLOCK"),
        Map.entry("kelp block", "DRIED_KELP_BLOCK"),
        Map.entry("raw iron", "RAW_IRON"),
        Map.entry("iron spear", "IRON_SWORD"),
        Map.entry("gold spear", "GOLDEN_SWORD"),
        Map.entry("gold pickaxe", "GOLDEN_PICKAXE"),
        Map.entry("gold axe", "GOLDEN_AXE"),
        Map.entry("gold shovel", "GOLDEN_SHOVEL"),
        Map.entry("gold hoe", "GOLDEN_HOE"),
        Map.entry("gold sword", "GOLDEN_SWORD"),
        Map.entry("gold chestplate", "GOLDEN_CHESTPLATE"),
        Map.entry("gold helmet", "GOLDEN_HELMET"),
        Map.entry("gold pants", "GOLDEN_LEGGINGS"),
        Map.entry("gold boots", "GOLDEN_BOOTS"),
        Map.entry("iron pants", "IRON_LEGGINGS"),
        Map.entry("jack o lantern", "JACK_O_LANTERN"),
        Map.entry("jack olantern", "JACK_O_LANTERN"),
        Map.entry("ominous banner", "WHITE_BANNER"),
        Map.entry("target block", "TARGET"),
        Map.entry("chest minecart", "CHEST_MINECART"),
        Map.entry("item frame", "ITEM_FRAME"),
        Map.entry("glow item frame", "GLOW_ITEM_FRAME"),
        Map.entry("bottle of honey", "HONEY_BOTTLE"),
        Map.entry("honey bottle", "HONEY_BOTTLE"),
        Map.entry("beetroot stew", "BEETROOT_SOUP"),
        Map.entry("wither skull", "WITHER_SKELETON_SKULL"),
        Map.entry("sniffer egg", "SNIFFER_EGG"),
        Map.entry("rabbit foot", "RABBIT_FOOT"),
        Map.entry("large fern", "LARGE_FERN"),
        Map.entry("end rod", "END_ROD"),
        Map.entry("shulker shell", "SHULKER_SHELL"),
        Map.entry("nautilus shell", "NAUTILUS_SHELL"),
        Map.entry("heart of the sea", "HEART_OF_THE_SEA"),
        Map.entry("amethyst shard", "AMETHYST_SHARD")
    );

    private static final Map<String, String> ENTITY_ALIASES = Map.ofEntries(
        Map.entry("ender dragon", "ENDER_DRAGON"),
        Map.entry("enderdragon", "ENDER_DRAGON"),
        Map.entry("enderman", "ENDERMAN"),
        Map.entry("zombie", "ZOMBIE"),
        Map.entry("creeper", "CREEPER"),
        Map.entry("pillager", "PILLAGER"),
        Map.entry("guardian", "GUARDIAN"),
        Map.entry("blaze", "BLAZE"),
        Map.entry("cow", "COW"),
        Map.entry("pig", "PIG"),
        Map.entry("chicken", "CHICKEN"),
        Map.entry("parrot", "PARROT"),
        Map.entry("silverfish", "SILVERFISH"),
        Map.entry("snow golem", "SNOW_GOLEM"),
        Map.entry("iron golem", "IRON_GOLEM"),
        Map.entry("donkey", "DONKEY"),
        Map.entry("chest minecart", "CHEST_MINECART")
    );

    private DetectorCompiler() {
    }

    static DetectorSpec compile(String description, JsonObject explicitDetector) {
        DetectorSpec explicit = compileExplicit(explicitDetector);
        if (explicit != null) return explicit;
        return compileHeuristic(description == null ? "" : description);
    }

    private static DetectorSpec compileExplicit(JsonObject detector) {
        if (detector == null || detector.isJsonNull()) return null;
        TriggerType type = parseType(getString(detector, "type"));
        if (type == null) return null;

        Material material = parseMaterial(firstString(detector, "material", "item", "block"));
        EntityType entityType = parseEntity(firstString(detector, "entity", "entityType", "mob"));
        String detail = firstString(detector, "detail", "qualifier", "color");
        if (requiresMaterial(type) && material == null) return null;
        if (requiresEntity(type) && entityType == null) return null;
        return new DetectorSpec(type, material, entityType, detail, "explicit");
    }

    private static DetectorSpec compileHeuristic(String description) {
        String lower = normalizeSpaces(description).toLowerCase(Locale.ROOT);
        if (lower.isBlank() || lower.contains("[") || lower.contains("]")) return null;

        if (lower.equals("avoid death with a totem of undying")) {
            return new DetectorSpec(TriggerType.TOTEM_RESURRECT, null, null, null, "heuristic");
        }
        if (lower.startsWith("craft ")) {
            return materialDetector(TriggerType.CRAFT_ITEM, targetAfterVerb(lower, "craft"), "heuristic");
        }
        if (lower.startsWith("place ")) {
            String target = targetAfterVerb(lower, "place");
            if (lower.contains(" item frame")) {
                Material material = parseMaterial(cutAt(target, " in ", " on ", " near ", " with "));
                return material == null ? null : new DetectorSpec(TriggerType.ITEM_FRAME_ITEM, material, EntityType.ITEM_FRAME, null, "heuristic");
            }
            return materialDetector(TriggerType.PLACE_BLOCK, target, "heuristic");
        }
        if (lower.startsWith("build up to the height limit")) {
            return new DetectorSpec(TriggerType.PLACE_AT_HEIGHT_LIMIT, null, null, null, "heuristic");
        }
        if (lower.startsWith("break ")) {
            return materialDetector(TriggerType.BREAK_BLOCK, targetAfterVerb(lower, "break"), "heuristic");
        }
        if (lower.startsWith("smelt ")) {
            return materialDetector(TriggerType.SMELT_ITEM, targetAfterVerb(lower, "smelt"), "heuristic");
        }
        if (lower.startsWith("obtain ")) {
            return materialDetector(TriggerType.OBTAIN_ITEM, targetAfterVerb(lower, "obtain"), "heuristic");
        }
        if (lower.startsWith("eat ")) {
            return materialDetector(TriggerType.CONSUME_ITEM, targetAfterVerb(lower, "eat"), "heuristic");
        }
        if (lower.startsWith("drink ")) {
            return materialDetector(TriggerType.CONSUME_ITEM, targetAfterVerb(lower, "drink"), "heuristic");
        }
        if (lower.startsWith("shear ")) {
            return shearDetector(targetAfterVerb(lower, "shear"));
        }
        if (lower.startsWith("put ")) {
            DetectorSpec detector = useItemOnEntityDetector(targetAfterVerb(lower, "put"));
            if (detector != null) return detector;
        }
        if (lower.startsWith("kill ")) {
            if (lower.equals("kill an opponent")) {
                return new DetectorSpec(TriggerType.KILL_OPPONENT, null, null, null, "heuristic");
            }
            if (containsAny(lower, " with ", " using ", " by ", " while ", " from ") || lower.contains("baby ")) return null;
            EntityType entityType = parseEntity(targetAfterVerb(lower, "kill"));
            return entityType == null ? null : new DetectorSpec(TriggerType.KILL_ENTITY, null, entityType, null, "heuristic");
        }
        if (lower.startsWith("take melee damage from ")) {
            EntityType entityType = parseEntity(targetAfterVerb(lower, "take melee damage from"));
            return entityType == null ? null : new DetectorSpec(TriggerType.DAMAGE_FROM_ENTITY, null, entityType, null, "heuristic");
        }
        if (lower.startsWith("hit a ") && lower.contains(" with a wind charge")) {
            EntityType entityType = parseEntity(cutAt(targetAfterVerb(lower, "hit"), " with "));
            Material material = Material.matchMaterial("WIND_CHARGE");
            return entityType == null || material == null ? null : new DetectorSpec(TriggerType.PROJECTILE_HIT_ENTITY, material, entityType, null, "heuristic");
        }
        if (lower.startsWith("hit an opponent with ") || lower.startsWith("hit your opponent with ")) {
            String target = lower.startsWith("hit an opponent with ")
                ? targetAfterVerb(lower, "hit an opponent with")
                : targetAfterVerb(lower, "hit your opponent with");
            return opponentHitDetector(target);
        }
        if (lower.equals("fishing rod your opponent")) {
            return new DetectorSpec(TriggerType.FISHING_ROD_OPPONENT, null, null, null, "heuristic");
        }
        if (lower.equals("light your opponent on fire")) {
            return new DetectorSpec(TriggerType.IGNITE_OPPONENT, null, null, null, "heuristic");
        }
        return null;
    }

    private static DetectorSpec materialDetector(TriggerType type, String target, String source) {
        Material material = parseMaterial(cutAt(target, " in ", " on ", " near ", " with ", " from ", " inside ", " at "));
        return material == null ? null : new DetectorSpec(type, material, null, null, source);
    }

    private static DetectorSpec shearDetector(String target) {
        String detail = target.contains("pink sheep") ? "pink" : null;
        EntityType entityType = parseEntity(target.replace("pink ", ""));
        return entityType == null ? null : new DetectorSpec(TriggerType.SHEAR_ENTITY, null, entityType, detail, "heuristic");
    }

    private static DetectorSpec useItemOnEntityDetector(String target) {
        String delimiter = target.contains(" in ") ? " in " : target.contains(" on ") ? " on " : null;
        if (delimiter == null) return null;
        Material material = parseMaterial(cutAt(target, delimiter));
        EntityType entityType = parseEntity(target.substring(target.indexOf(delimiter) + delimiter.length()));
        if (material == null || entityType == null) return null;
        return new DetectorSpec(TriggerType.USE_ITEM_ON_ENTITY, material, entityType, null, "heuristic");
    }

    private static DetectorSpec opponentHitDetector(String target) {
        Material material = parseMaterial(target);
        if (material == null) return null;
        TriggerType type = isProjectileMaterial(material)
            ? TriggerType.PROJECTILE_HIT_OPPONENT
            : TriggerType.HIT_OPPONENT_WITH_ITEM;
        return new DetectorSpec(type, material, null, null, "heuristic");
    }

    private static boolean isProjectileMaterial(Material material) {
        return material == Material.SNOWBALL
            || material == Material.EGG
            || material == Material.SPLASH_POTION
            || material == Material.TRIDENT
            || "WIND_CHARGE".equals(material.name());
    }

    private static TriggerType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "CRAFT", "CRAFT_ITEM" -> TriggerType.CRAFT_ITEM;
            case "PLACE", "PLACE_BLOCK" -> TriggerType.PLACE_BLOCK;
            case "PLACE_AT_HEIGHT_LIMIT" -> TriggerType.PLACE_AT_HEIGHT_LIMIT;
            case "BREAK", "BREAK_BLOCK" -> TriggerType.BREAK_BLOCK;
            case "CONSUME", "CONSUME_ITEM", "EAT", "DRINK" -> TriggerType.CONSUME_ITEM;
            case "KILL", "KILL_ENTITY" -> TriggerType.KILL_ENTITY;
            case "KILL_OPPONENT" -> TriggerType.KILL_OPPONENT;
            case "ITEM_FRAME", "ITEM_FRAME_ITEM" -> TriggerType.ITEM_FRAME_ITEM;
            case "DAMAGE_FROM", "DAMAGE_FROM_ENTITY" -> TriggerType.DAMAGE_FROM_ENTITY;
            case "SMELT", "SMELT_ITEM" -> TriggerType.SMELT_ITEM;
            case "OBTAIN", "OBTAIN_ITEM" -> TriggerType.OBTAIN_ITEM;
            case "SHEAR", "SHEAR_ENTITY" -> TriggerType.SHEAR_ENTITY;
            case "USE_ITEM_ON_ENTITY", "INTERACT_ENTITY" -> TriggerType.USE_ITEM_ON_ENTITY;
            case "HIT_OPPONENT_WITH_ITEM" -> TriggerType.HIT_OPPONENT_WITH_ITEM;
            case "PROJECTILE_HIT_ENTITY" -> TriggerType.PROJECTILE_HIT_ENTITY;
            case "PROJECTILE_HIT_OPPONENT" -> TriggerType.PROJECTILE_HIT_OPPONENT;
            case "FISHING_ROD_OPPONENT" -> TriggerType.FISHING_ROD_OPPONENT;
            case "IGNITE_OPPONENT" -> TriggerType.IGNITE_OPPONENT;
            case "TOTEM", "TOTEM_RESURRECT" -> TriggerType.TOTEM_RESURRECT;
            default -> null;
        };
    }

    private static boolean requiresMaterial(TriggerType type) {
        return type == TriggerType.CRAFT_ITEM
            || type == TriggerType.PLACE_BLOCK
            || type == TriggerType.BREAK_BLOCK
            || type == TriggerType.CONSUME_ITEM
            || type == TriggerType.ITEM_FRAME_ITEM
            || type == TriggerType.SMELT_ITEM
            || type == TriggerType.OBTAIN_ITEM
            || type == TriggerType.USE_ITEM_ON_ENTITY
            || type == TriggerType.HIT_OPPONENT_WITH_ITEM
            || type == TriggerType.PROJECTILE_HIT_ENTITY
            || type == TriggerType.PROJECTILE_HIT_OPPONENT;
    }

    private static boolean requiresEntity(TriggerType type) {
        return type == TriggerType.KILL_ENTITY
            || type == TriggerType.DAMAGE_FROM_ENTITY
            || type == TriggerType.SHEAR_ENTITY
            || type == TriggerType.USE_ITEM_ON_ENTITY
            || type == TriggerType.PROJECTILE_HIT_ENTITY;
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String human = cleanTarget(raw);
        String alias = MATERIAL_ALIASES.get(human);
        if (alias != null) return Material.matchMaterial(alias);

        Material direct = Material.matchMaterial(toEnumName(normalizeMaterialName(human)));
        if (direct != null) return direct;

        if (human.endsWith("s") && human.length() > 3) {
            Material singular = Material.matchMaterial(toEnumName(normalizeMaterialName(human.substring(0, human.length() - 1))));
            if (singular != null) return singular;
        }
        return null;
    }

    private static EntityType parseEntity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String human = cleanTarget(raw);
        String alias = ENTITY_ALIASES.get(human);
        String enumName = alias != null ? alias : toEnumName(human);
        try {
            return EntityType.valueOf(enumName);
        } catch (IllegalArgumentException ignored) {
            if (human.endsWith("s") && human.length() > 3) {
                try {
                    return EntityType.valueOf(toEnumName(human.substring(0, human.length() - 1)));
                } catch (IllegalArgumentException ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private static String targetAfterVerb(String text, String verb) {
        return stripArticles(text.substring(verb.length()).trim());
    }

    private static String cutAt(String text, String... delimiters) {
        int end = text.length();
        for (String delimiter : delimiters) {
            int idx = text.indexOf(delimiter);
            if (idx >= 0) end = Math.min(end, idx);
        }
        return text.substring(0, end);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static String cleanTarget(String raw) {
        String withoutNamespace = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        return stripArticles(normalizeSpaces(withoutNamespace)
            .toLowerCase(Locale.ROOT)
            .replace("'", " ")
            .replace("\u2019", " ")
            .replace("\u2018", " ")
            .replaceAll("[^a-z0-9 ]", " "));
    }

    private static String normalizeMaterialName(String human) {
        return human
            .replace("grey", "gray")
            .replace(" pants", " leggings")
            .replace("gold ", "golden ");
    }

    private static String stripArticles(String text) {
        String cleaned = normalizeSpaces(text);
        return cleaned
            .replaceFirst("^(a|an|the|any|one|of) ", "")
            .replaceFirst("^(a|an|the|any|one|of) ", "")
            .trim();
    }

    private static String normalizeSpaces(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String toEnumName(String human) {
        return human.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = getString(object, key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
}
