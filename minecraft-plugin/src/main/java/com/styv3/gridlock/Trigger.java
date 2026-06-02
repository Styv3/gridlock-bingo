package com.styv3.gridlock;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

record Trigger(TriggerType type, Material material, EntityType entityType) {
    static Trigger material(TriggerType type, Material material) {
        return new Trigger(type, material, null);
    }

    static Trigger entity(TriggerType type, EntityType entityType) {
        return new Trigger(type, null, entityType);
    }

    static Trigger itemFrame(Material material) {
        return new Trigger(TriggerType.ITEM_FRAME_ITEM, material, EntityType.ITEM_FRAME);
    }
}
