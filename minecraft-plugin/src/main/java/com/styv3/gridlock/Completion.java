package com.styv3.gridlock;

import java.time.Instant;
import java.util.UUID;

record Completion(String objectiveId, String teamName, UUID playerId, String playerName, Instant completedAt) {
}
