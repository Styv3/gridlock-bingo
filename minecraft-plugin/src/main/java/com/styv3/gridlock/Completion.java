package com.styv3.gridlock;

import java.time.Instant;
import java.util.UUID;

record Completion(String slotId, String objectiveId, String teamName, UUID playerId, String playerName, int points, Instant completedAt) {
}
