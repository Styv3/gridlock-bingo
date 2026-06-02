package com.styv3.gridlock;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

record Trigger(TriggerType type, Material material, EntityType entityType, String detail) {
    static Trigger simple(TriggerType type) {
        return new Trigger(type, null, null, null);
    }

    static Trigger material(TriggerType type, Material material) {
        return new Trigger(type, material, null, null);
    }

    static Trigger entity(TriggerType type, EntityType entityType) {
        return entity(type, entityType, null);
    }

    static Trigger entity(TriggerType type, EntityType entityType, String detail) {
        return new Trigger(type, null, entityType, detail);
    }

    static Trigger materialAndEntity(TriggerType type, Material material, EntityType entityType) {
        return new Trigger(type, material, entityType, null);
    }

    static Trigger itemFrame(Material material) {
        return materialAndEntity(TriggerType.ITEM_FRAME_ITEM, material, EntityType.ITEM_FRAME);
    }
}
