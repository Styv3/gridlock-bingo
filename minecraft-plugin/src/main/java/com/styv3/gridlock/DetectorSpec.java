package com.styv3.gridlock;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

record DetectorSpec(TriggerType type, Material material, EntityType entityType, String source) {
    boolean matches(Trigger trigger) {
        if (trigger.type() != type) return false;
        if (material != null && trigger.material() != material) return false;
        return entityType == null || trigger.entityType() == entityType;
    }

    String display() {
        String target = material != null ? material.name() : entityType != null ? entityType.name() : "*";
        return type.name().toLowerCase() + ":" + target + " (" + source + ")";
    }
}
