package com.styv3.gridlock;

record Objective(
    String id,
    String name,
    String description,
    String categoryId,
    boolean anywhere,
    DetectorSpec detector
) {
    boolean hasDetector() {
        return detector != null;
    }
}
