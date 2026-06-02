package com.styv3.gridlock;

record BingoSlot(String slotId, String objectiveId, String type, String label) {
    boolean isObjective() {
        return objectiveId != null && !objectiveId.isBlank();
    }

    boolean isQuestPlaceholder() {
        return "quest-placeholder".equals(type);
    }
}
