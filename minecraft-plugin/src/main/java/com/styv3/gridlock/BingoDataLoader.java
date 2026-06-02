package com.styv3.gridlock;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BingoDataLoader {
    private final Gson gson = new Gson();

    GridlockData load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Category> categories = readCategories(root);
            Map<String, Objective> objectives = readObjectives(root);
            List<BingoSlot> grid = readGrid(root);
            if (grid.isEmpty()) {
                grid = readActiveObjectiveIds(root);
            }
            return new GridlockData(categories, objectives, grid);
        }
    }

    private Map<String, Category> readCategories(JsonObject root) {
        Map<String, Category> categories = new LinkedHashMap<>();
        JsonArray array = root.has("categories") ? root.getAsJsonArray("categories") : new JsonArray();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", UUID.randomUUID().toString());
            categories.put(id, new Category(id, string(object, "name", id)));
        }
        return categories;
    }

    private Map<String, Objective> readObjectives(JsonObject root) {
        Map<String, Objective> objectives = new LinkedHashMap<>();
        JsonArray array = root.has("objectives") ? root.getAsJsonArray("objectives") : new JsonArray();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", UUID.randomUUID().toString());
            String name = string(object, "name", id);
            String description = string(object, "description", "");
            String categoryId = string(object, "categoryId", "simple");
            boolean anywhere = object.has("anywhere") && object.get("anywhere").getAsBoolean();
            JsonObject detectorJson = object.has("detector") && object.get("detector").isJsonObject()
                ? object.getAsJsonObject("detector")
                : null;
            DetectorSpec detector = DetectorCompiler.compile(description, detectorJson);
            objectives.put(id, new Objective(id, name, description, categoryId, anywhere, detector));
        }
        return objectives;
    }

    private List<BingoSlot> readGrid(JsonObject root) {
        List<BingoSlot> slots = new ArrayList<>();
        if (!root.has("bingoGrid") || !root.get("bingoGrid").isJsonArray()) return slots;
        for (JsonElement element : root.getAsJsonArray("bingoGrid")) {
            if (element.isJsonPrimitive()) {
                slots.add(new BingoSlot(UUID.randomUUID().toString(), element.getAsString(), null, null));
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                slots.add(new BingoSlot(
                    string(object, "slotId", UUID.randomUUID().toString()),
                    nullableString(object, "objectiveId"),
                    nullableString(object, "type"),
                    nullableString(object, "label")
                ));
            }
        }
        return slots.stream().limit(25).toList();
    }

    private List<BingoSlot> readActiveObjectiveIds(JsonObject root) {
        List<BingoSlot> slots = new ArrayList<>();
        if (!root.has("activeObjectiveIds") || !root.get("activeObjectiveIds").isJsonArray()) return slots;
        for (JsonElement element : root.getAsJsonArray("activeObjectiveIds")) {
            if (element.isJsonPrimitive()) {
                slots.add(new BingoSlot(UUID.randomUUID().toString(), element.getAsString(), null, null));
            }
        }
        return slots.stream().limit(25).toList();
    }

    @SuppressWarnings("unused")
    String toJson(Object value) {
        return gson.toJson(value);
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        String value = nullableString(object, key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
