package com.styv3.gridlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

final class GridlockGame {
    private static final int GRID_SIZE = 5;
    private static final int[][] GRIDLINES = {
        {0, 1, 2, 3, 4},
        {5, 6, 7, 8, 9},
        {10, 11, 12, 13, 14},
        {15, 16, 17, 18, 19},
        {20, 21, 22, 23, 24},
        {0, 5, 10, 15, 20},
        {1, 6, 11, 16, 21},
        {2, 7, 12, 17, 22},
        {3, 8, 13, 18, 23},
        {4, 9, 14, 19, 24},
        {0, 6, 12, 18, 24},
        {4, 8, 12, 16, 20}
    };

    private final GridlockPlugin plugin;
    private final Map<String, GridlockTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeams = new LinkedHashMap<>();
    private final Map<String, Completion> completions = new LinkedHashMap<>();
    private final Set<Integer> awardedGridlines = new LinkedHashSet<>();

    private Map<String, Category> categories = new LinkedHashMap<>();
    private Map<String, Objective> objectives = new LinkedHashMap<>();
    private List<BingoSlot> grid = List.of();
    private boolean running;

    GridlockGame(GridlockPlugin plugin) {
        this.plugin = plugin;
    }

    void load(GridlockData data) {
        categories = new LinkedHashMap<>(data.categories());
        objectives = new LinkedHashMap<>(data.objectives());
        grid = List.copyOf(data.grid());
        completions.clear();
        awardedGridlines.clear();
        teams.values().forEach(GridlockTeam::resetScore);
        running = plugin.getConfig().getBoolean("auto-start-after-import", false);
        if (running) plugin.applyGridlockWorldRules();
    }

    boolean hasBoard() {
        return !grid.isEmpty() && !objectives.isEmpty();
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        running = true;
        plugin.applyGridlockWorldRules();
    }

    void stop() {
        running = false;
    }

    void resetProgress() {
        completions.clear();
        awardedGridlines.clear();
        teams.values().forEach(GridlockTeam::resetScore);
    }

    void createTeam(String name) {
        teams.computeIfAbsent(name, GridlockTeam::new);
    }

    void joinTeam(Player player, String teamName) {
        GridlockTeam team = teams.computeIfAbsent(teamName, GridlockTeam::new);
        playerTeams.put(player.getUniqueId(), team.name());
        team.members().add(player.getUniqueId());
    }

    void leaveTeam(Player player) {
        String current = playerTeams.remove(player.getUniqueId());
        if (current == null) return;
        GridlockTeam team = teams.get(current);
        if (team != null) team.members().remove(player.getUniqueId());
    }

    boolean isOpponent(Player player, Player target) {
        if (player.getUniqueId().equals(target.getUniqueId())) return false;
        String playerTeam = playerTeams.get(player.getUniqueId());
        String targetTeam = playerTeams.get(target.getUniqueId());
        return playerTeam != null && targetTeam != null && !playerTeam.equals(targetTeam);
    }

    boolean tryComplete(Player player, Trigger trigger) {
        if (!running || !hasBoard()) return false;
        for (BingoSlot slot : grid) {
            if (!slot.isObjective() || completions.containsKey(slot.slotId())) continue;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective == null || objective.detector() == null) continue;
            if (objective.detector().matches(trigger)) {
                complete(player, slot, objective);
                return true;
            }
        }
        return false;
    }

    boolean completeManual(Player player, String query) {
        Optional<BoardObjective> match = findBoardObjective(query);
        if (match.isEmpty()) return false;
        complete(player, match.get().slot(), match.get().objective());
        return true;
    }

    List<String> boardLines() {
        List<String> lines = new ArrayList<>();
        if (!hasBoard()) {
            lines.add(ChatColor.GRAY + "No board loaded.");
            return lines;
        }
        lines.add(ChatColor.GOLD + "Gridlock board " + ChatColor.GRAY + "(" + completions.size() + "/" + objectiveSlots().size() + ")");
        int index = 1;
        for (BingoSlot slot : grid) {
            if (slot.isQuestPlaceholder()) {
                lines.add(ChatColor.DARK_GRAY + String.format(Locale.ROOT, "%02d. ", index) + ChatColor.GOLD + "[Quest placeholder]");
            } else {
                Objective objective = objectives.get(slot.objectiveId());
                if (objective == null) {
                    lines.add(ChatColor.DARK_GRAY + String.format(Locale.ROOT, "%02d. ", index) + ChatColor.RED + "Missing objective " + slot.objectiveId());
                } else {
                    Completion completion = completions.get(slot.slotId());
                    int points = pointValue(slot, objective);
                    String mark = completion == null ? ChatColor.GRAY + "[ ] " : ChatColor.GREEN + "[x] ";
                    String category = categoryName(objective.categoryId());
                    String owner = completion == null ? "" : ChatColor.DARK_GRAY + " -> " + completion.teamName();
                    lines.add(ChatColor.DARK_GRAY + String.format(Locale.ROOT, "%02d. ", index) + mark
                        + ChatColor.WHITE + objective.name()
                        + ChatColor.DARK_GRAY + " (" + category + ", " + points + "pt)" + owner);
                }
            }
            index++;
        }
        return lines;
    }

    List<String> scoreLines() {
        List<GridlockTeam> sorted = new ArrayList<>(teams.values());
        sorted.sort(Comparator.comparingInt(GridlockTeam::score).reversed().thenComparing(GridlockTeam::name));
        if (sorted.isEmpty()) {
            return List.of(ChatColor.GRAY + "No teams yet. Players without a team score under their own name.");
        }
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "Gridlock scores" + ChatColor.DARK_GRAY + " / total available: " + totalAvailablePoints());
        for (GridlockTeam team : sorted) {
            long squares = completions.values().stream().filter(completion -> completion.teamName().equals(team.name())).count();
            lines.add(ChatColor.WHITE + team.name() + ChatColor.GRAY + ": " + ChatColor.GREEN + team.score()
                + ChatColor.DARK_GRAY + " (" + squares + " squares)");
        }
        return lines;
    }

    String detectorSummary() {
        long objectiveSlots = objectiveSlots().size();
        long automated = objectiveSlots().stream()
            .map(BingoSlot::objectiveId)
            .map(objectives::get)
            .filter(objective -> objective != null && objective.hasDetector())
            .count();
        return automated + "/" + objectiveSlots + " grid objectives have automatic detectors.";
    }

    private List<BingoSlot> objectiveSlots() {
        return grid.stream().filter(BingoSlot::isObjective).toList();
    }

    private Optional<BoardObjective> findBoardObjective(String query) {
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        Optional<BoardObjective> byNumber = findBySlotNumber(normalized);
        if (byNumber.isPresent()) return byNumber;

        for (int i = 0; i < grid.size(); i++) {
            BingoSlot slot = grid.get(i);
            if (!slot.isObjective() || completions.containsKey(slot.slotId())) continue;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective == null) continue;
            if (objective.id().equals(query)
                || objective.name().toLowerCase(Locale.ROOT).contains(normalized)
                || objective.description().toLowerCase(Locale.ROOT).contains(normalized)) {
                return Optional.of(new BoardObjective(slot, objective));
            }
        }
        return Optional.empty();
    }

    private Optional<BoardObjective> findBySlotNumber(String normalized) {
        try {
            int slotNumber = Integer.parseInt(normalized);
            int index = slotNumber - 1;
            if (index < 0 || index >= grid.size()) return Optional.empty();
            BingoSlot slot = grid.get(index);
            if (!slot.isObjective() || completions.containsKey(slot.slotId())) return Optional.empty();
            Objective objective = objectives.get(slot.objectiveId());
            return objective == null ? Optional.empty() : Optional.of(new BoardObjective(slot, objective));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private void complete(Player player, BingoSlot slot, Objective objective) {
        String teamName = teamFor(player);
        GridlockTeam team = teams.computeIfAbsent(teamName, GridlockTeam::new);
        int points = pointValue(slot, objective);
        team.addPoints(points);
        Completion completion = new Completion(slot.slotId(), objective.id(), teamName, player.getUniqueId(), player.getName(), points, Instant.now());
        completions.put(slot.slotId(), completion);

        if (plugin.getConfig().getBoolean("broadcast-completions", true)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.WHITE + teamName
                + ChatColor.GRAY + " completed " + ChatColor.GREEN + objective.name()
                + ChatColor.GRAY + " (+" + points + ")");
        }

        for (GridlineBonus bonus : awardGridlineBonuses(team)) {
            team.addPoints(bonus.points());
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.WHITE + teamName
                + ChatColor.GRAY + " completed " + ChatColor.AQUA + bonus.name()
                + ChatColor.GRAY + " (+" + bonus.points() + " Gridline bonus)");
        }
        maybeEndByTotalGridlock(team);
    }

    private List<GridlineBonus> awardGridlineBonuses(GridlockTeam team) {
        List<GridlineBonus> bonuses = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < GRIDLINES.length; lineIndex++) {
            if (awardedGridlines.contains(lineIndex)) continue;
            int lineValue = lineValueForTeam(GRIDLINES[lineIndex], team.name());
            if (lineValue <= 0) continue;
            awardedGridlines.add(lineIndex);
            bonuses.add(new GridlineBonus(gridlineName(lineIndex), lineValue));
        }
        return bonuses;
    }

    private int lineValueForTeam(int[] positions, String teamName) {
        int lineValue = 0;
        for (int position : positions) {
            if (position >= grid.size()) return 0;
            BingoSlot slot = grid.get(position);
            if (!slot.isObjective()) return 0;
            Completion completion = completions.get(slot.slotId());
            if (completion == null || !completion.teamName().equals(teamName)) return 0;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective == null) return 0;
            lineValue += pointValue(slot, objective);
        }
        return lineValue;
    }

    private void maybeEndByTotalGridlock(GridlockTeam team) {
        if (!plugin.getConfig().getBoolean("enable-total-gridlock", true)) return;
        int total = totalAvailablePoints();
        if (total <= 0 || team.score() <= total / 2.0) return;
        running = false;
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.GREEN + "Total Gridlock! "
            + ChatColor.WHITE + team.name() + ChatColor.GRAY + " has an insurmountable lead.");
    }

    private int totalAvailablePoints() {
        int total = 0;
        for (BingoSlot slot : grid) {
            if (!slot.isObjective()) continue;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective != null) total += pointValue(slot, objective);
        }
        for (int[] gridline : GRIDLINES) {
            int lineValue = lineValue(gridline);
            if (lineValue > 0) total += lineValue;
        }
        return total;
    }

    private int lineValue(int[] positions) {
        int lineValue = 0;
        for (int position : positions) {
            if (position >= grid.size()) return 0;
            BingoSlot slot = grid.get(position);
            if (!slot.isObjective()) return 0;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective == null) return 0;
            lineValue += pointValue(slot, objective);
        }
        return lineValue;
    }

    private int pointValue(BingoSlot slot, Objective objective) {
        String category = normalizedCategory(objective);
        return switch (category) {
            case "complex", "team" -> 2;
            case "opponent" -> 3;
            case "quest" -> questValue(slot);
            default -> 1;
        };
    }

    private int questValue(BingoSlot targetSlot) {
        int questNumber = 0;
        for (BingoSlot slot : grid) {
            if (!slot.isObjective()) continue;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective == null || !"quest".equals(normalizedCategory(objective))) continue;
            questNumber++;
            if (slot.slotId().equals(targetSlot.slotId())) {
                if (questNumber <= 2) return 3;
                if (questNumber <= 4) return 4;
                return 5;
            }
        }
        return 3;
    }

    private String normalizedCategory(Objective objective) {
        String categoryId = objective.categoryId() == null ? "" : objective.categoryId();
        Category category = categories.get(categoryId);
        String value = category == null ? categoryId : category.name();
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String gridlineName(int index) {
        if (index < GRID_SIZE) return "row " + (index + 1);
        if (index < GRID_SIZE * 2) return "column " + (index - GRID_SIZE + 1);
        return index == GRID_SIZE * 2 ? "diagonal 1" : "diagonal 2";
    }

    private String teamFor(Player player) {
        String assigned = playerTeams.get(player.getUniqueId());
        return assigned == null || assigned.isBlank() ? player.getName() : assigned;
    }

    private String categoryName(String categoryId) {
        Category category = categories.get(categoryId);
        return category == null ? categoryId : category.name();
    }

    private record BoardObjective(BingoSlot slot, Objective objective) {
    }

    private record GridlineBonus(String name, int points) {
    }
}
