package com.styv3.gridlock;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

final class DetectorCompiler {
    private static final Map<String, String> MATERIAL_ALIASES = Map.ofEntries(
        Map.entry("gold block", "GOLD_BLOCK"),
        Map.entry("iron block", "IRON_BLOCK"),
        Map.entry("diamond block", "DIAMOND_BLOCK"),
        Map.entry("kelp block", "DRIED_KELP_BLOCK"),
        Map.entry("raw iron", "RAW_IRON"),
        Map.entry("jack o lantern", "JACK_O_LANTERN"),
        Map.entry("jack olantern", "JACK_O_LANTERN"),
        Map.entry("ominous banner", "WHITE_BANNER"),
        Map.entry("target block", "TARGET"),
        Map.entry("chest minecart", "CHEST_MINECART"),
        Map.entry("item frame", "ITEM_FRAME")
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
        Map.entry("silverfish", "SILVERFISH")
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
        if (requiresMaterial(type) && material == null) return null;
        if (requiresEntity(type) && entityType == null) return null;
        return new DetectorSpec(type, material, entityType, "explicit");
    }

    private static DetectorSpec compileHeuristic(String description) {
        String lower = normalizeSpaces(description).toLowerCase(Locale.ROOT);
        if (lower.isBlank() || lower.contains("[") || lower.contains("]")) return null;

        if (lower.startsWith("craft ")) {
            return materialDetector(TriggerType.CRAFT_ITEM, targetAfterVerb(lower, "craft"), "heuristic");
        }
        if (lower.startsWith("place ")) {
            String target = targetAfterVerb(lower, "place");
            if (lower.contains(" item frame")) {
                Material material = parseMaterial(cutAt(target, " in ", " on ", " near ", " with "));
                return material == null ? null : new DetectorSpec(TriggerType.ITEM_FRAME_ITEM, material, EntityType.ITEM_FRAME, "heuristic");
            }
            return materialDetector(TriggerType.PLACE_BLOCK, target, "heuristic");
        }
        if (lower.startsWith("break ")) {
            return materialDetector(TriggerType.BREAK_BLOCK, targetAfterVerb(lower, "break"), "heuristic");
        }
        if (lower.startsWith("eat ")) {
            return materialDetector(TriggerType.CONSUME_ITEM, targetAfterVerb(lower, "eat"), "heuristic");
        }
        if (lower.startsWith("drink ")) {
            return materialDetector(TriggerType.CONSUME_ITEM, targetAfterVerb(lower, "drink"), "heuristic");
        }
        if (lower.startsWith("kill ")) {
            if (containsAny(lower, " with ", " using ", " by ", " while ", " from ") || lower.contains("baby ")) return null;
            EntityType entityType = parseEntity(targetAfterVerb(lower, "kill"));
            return entityType == null ? null : new DetectorSpec(TriggerType.KILL_ENTITY, null, entityType, "heuristic");
        }
        if (lower.startsWith("take melee damage from ")) {
            EntityType entityType = parseEntity(targetAfterVerb(lower, "take melee damage from"));
            return entityType == null ? null : new DetectorSpec(TriggerType.DAMAGE_FROM_ENTITY, null, entityType, "heuristic");
        }
        return null;
    }

    private static DetectorSpec materialDetector(TriggerType type, String target, String source) {
        Material material = parseMaterial(cutAt(target, " in ", " on ", " near ", " with ", " from ", " inside ", " at "));
        return material == null ? null : new DetectorSpec(type, material, null, source);
    }

    private static TriggerType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "CRAFT", "CRAFT_ITEM" -> TriggerType.CRAFT_ITEM;
            case "PLACE", "PLACE_BLOCK" -> TriggerType.PLACE_BLOCK;
            case "BREAK", "BREAK_BLOCK" -> TriggerType.BREAK_BLOCK;
            case "CONSUME", "CONSUME_ITEM", "EAT", "DRINK" -> TriggerType.CONSUME_ITEM;
            case "KILL", "KILL_ENTITY" -> TriggerType.KILL_ENTITY;
            case "ITEM_FRAME", "ITEM_FRAME_ITEM" -> TriggerType.ITEM_FRAME_ITEM;
            case "DAMAGE_FROM", "DAMAGE_FROM_ENTITY" -> TriggerType.DAMAGE_FROM_ENTITY;
            default -> null;
        };
    }

    private static boolean requiresMaterial(TriggerType type) {
        return type == TriggerType.CRAFT_ITEM
            || type == TriggerType.PLACE_BLOCK
            || type == TriggerType.BREAK_BLOCK
            || type == TriggerType.CONSUME_ITEM
            || type == TriggerType.ITEM_FRAME_ITEM;
    }

    private static boolean requiresEntity(TriggerType type) {
        return type == TriggerType.KILL_ENTITY || type == TriggerType.DAMAGE_FROM_ENTITY;
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String human = cleanTarget(raw);
        String alias = MATERIAL_ALIASES.get(human);
        if (alias != null) return Material.matchMaterial(alias);

        Material direct = Material.matchMaterial(toEnumName(human));
        if (direct != null) return direct;

        if (human.endsWith("s") && human.length() > 3) {
            Material singular = Material.matchMaterial(toEnumName(human.substring(0, human.length() - 1)));
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
            .replace("’", " ")
            .replaceAll("[^a-z0-9 ]", " "));
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
