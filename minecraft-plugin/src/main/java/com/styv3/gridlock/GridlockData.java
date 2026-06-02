package com.styv3.gridlock;

import java.util.List;
import java.util.Map;

record GridlockData(Map<String, Category> categories, Map<String, Objective> objectives, List<BingoSlot> grid) {
}
