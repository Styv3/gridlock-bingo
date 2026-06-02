package com.styv3.gridlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

final class GridlockGame {
    private final GridlockPlugin plugin;
    private final Map<String, GridlockTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeams = new LinkedHashMap<>();
    private final Map<String, Completion> completions = new LinkedHashMap<>();

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
        teams.values().forEach(GridlockTeam::resetScore);
        running = plugin.getConfig().getBoolean("auto-start-after-import", false);
    }

    boolean hasBoard() {
        return !grid.isEmpty() && !objectives.isEmpty();
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        running = true;
    }

    void stop() {
        running = false;
    }

    void resetProgress() {
        completions.clear();
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

    boolean tryComplete(Player player, Trigger trigger) {
        if (!running || !hasBoard()) return false;
        for (BingoSlot slot : grid) {
            if (!slot.isObjective() || completions.containsKey(slot.objectiveId())) continue;
            Objective objective = objectives.get(slot.objectiveId());
            if (objective == null || objective.detector() == null) continue;
            if (objective.detector().matches(trigger)) {
                complete(player, objective);
                return true;
            }
        }
        return false;
    }

    boolean completeManual(Player player, String query) {
        Optional<Objective> objective = findObjective(query);
        if (objective.isEmpty() || completions.containsKey(objective.get().id())) return false;
        complete(player, objective.get());
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
                    Completion completion = completions.get(objective.id());
                    String mark = completion == null ? ChatColor.GRAY + "[ ] " : ChatColor.GREEN + "[x] ";
                    String category = categoryName(objective.categoryId());
                    String owner = completion == null ? "" : ChatColor.DARK_GRAY + " -> " + completion.teamName();
                    lines.add(ChatColor.DARK_GRAY + String.format(Locale.ROOT, "%02d. ", index) + mark + ChatColor.WHITE + objective.name() + ChatColor.DARK_GRAY + " (" + category + ")" + owner);
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
        lines.add(ChatColor.GOLD + "Gridlock scores");
        for (GridlockTeam team : sorted) {
            lines.add(ChatColor.WHITE + team.name() + ChatColor.GRAY + ": " + ChatColor.GREEN + team.score());
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

    private Optional<Objective> findObjective(String query) {
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        Objective byId = objectives.get(query);
        if (byId != null && isOnBoard(byId.id())) return Optional.of(byId);
        return objectives.values().stream()
            .filter(objective -> isOnBoard(objective.id()))
            .filter(objective -> objective.name().toLowerCase(Locale.ROOT).contains(normalized)
                || objective.description().toLowerCase(Locale.ROOT).contains(normalized))
            .findFirst();
    }

    private boolean isOnBoard(String objectiveId) {
        return grid.stream().anyMatch(slot -> objectiveId.equals(slot.objectiveId()));
    }

    private void complete(Player player, Objective objective) {
        String teamName = teamFor(player);
        GridlockTeam team = teams.computeIfAbsent(teamName, GridlockTeam::new);
        team.addPoint();
        Completion completion = new Completion(objective.id(), teamName, player.getUniqueId(), player.getName(), Instant.now());
        completions.put(objective.id(), completion);

        if (plugin.getConfig().getBoolean("broadcast-completions", true)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.WHITE + teamName
                + ChatColor.GRAY + " completed " + ChatColor.GREEN + objective.name()
                + ChatColor.GRAY + " (+" + 1 + ")");
        }
    }

    private String teamFor(Player player) {
        String assigned = playerTeams.get(player.getUniqueId());
        return assigned == null || assigned.isBlank() ? player.getName() : assigned;
    }

    private String categoryName(String categoryId) {
        Category category = categories.get(categoryId);
        return category == null ? categoryId : category.name();
    }
}
