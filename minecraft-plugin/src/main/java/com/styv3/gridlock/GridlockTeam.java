package com.styv3.gridlock;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class GridlockTeam {
    private final String name;
    private final Set<UUID> members = new LinkedHashSet<>();
    private int score;

    GridlockTeam(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    Set<UUID> members() {
        return members;
    }

    int score() {
        return score;
    }

    void addPoints(int points) {
        score += points;
    }

    void resetScore() {
        score = 0;
    }
}
