package com.styv3.gridlock;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

record DetectorSpec(TriggerType type, Material material, EntityType entityType, String detail, String source) {
    boolean matches(Trigger trigger) {
        if (trigger.type() != type) return false;
        if (material != null && trigger.material() != material) return false;
        if (entityType != null && trigger.entityType() != entityType) return false;
        return detail == null || detail.equalsIgnoreCase(trigger.detail());
    }

    String display() {
        String target = material != null ? material.name() : entityType != null ? entityType.name() : "*";
        String suffix = detail == null ? "" : ":" + detail;
        return type.name().toLowerCase() + ":" + target + suffix + " (" + source + ")";
    }
}
