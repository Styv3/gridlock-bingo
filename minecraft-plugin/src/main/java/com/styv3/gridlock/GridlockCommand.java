package com.styv3.gridlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class GridlockCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of("import", "start", "stop", "reset", "board", "score", "team", "complete", "detectors");
    private static final List<String> TEAM = List.of("create", "join", "leave");

    private final GridlockPlugin plugin;
    private final GridlockGame game;
    private final BingoDataLoader loader = new BingoDataLoader();

    GridlockCommand(GridlockPlugin plugin, GridlockGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "import" -> importBoard(sender, args);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reset" -> reset(sender);
            case "board" -> game.boardLines().forEach(sender::sendMessage);
            case "score" -> game.scoreLines().forEach(sender::sendMessage);
            case "team" -> team(sender, args);
            case "complete" -> complete(sender, args);
            case "detectors" -> sender.sendMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.GRAY + game.detectorSummary());
            default -> help(sender);
        }
        return true;
    }

    private void importBoard(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gridlock.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: gridlock.admin");
            return;
        }
        String fileName = args.length >= 2 ? args[1] : plugin.getConfig().getString("default-import-file", "gridlock-bingo.json");
        Path file = plugin.getDataFolder().toPath().resolve(fileName);
        if (!Files.exists(file)) {
            sender.sendMessage(ChatColor.RED + "File not found: " + file);
            sender.sendMessage(ChatColor.GRAY + "Copy your web export into the plugin data folder first.");
            return;
        }
        try {
            GridlockData data = loader.load(file);
            game.load(data);
            sender.sendMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.GREEN + "Loaded " + data.grid().size()
                + " grid slots and " + data.objectives().size() + " objectives.");
            sender.sendMessage(ChatColor.GRAY + game.detectorSummary());
        } catch (IOException | RuntimeException e) {
            sender.sendMessage(ChatColor.RED + "Import failed: " + e.getMessage());
            plugin.getLogger().warning("Gridlock import failed: " + e.getMessage());
        }
    }

    private void start(CommandSender sender) {
        if (!sender.hasPermission("gridlock.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: gridlock.admin");
            return;
        }
        if (!game.hasBoard()) {
            sender.sendMessage(ChatColor.RED + "Import a board first with /gridlock import.");
            return;
        }
        game.start();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.GREEN + "Game started.");
    }

    private void stop(CommandSender sender) {
        if (!sender.hasPermission("gridlock.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: gridlock.admin");
            return;
        }
        game.stop();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.YELLOW + "Game paused.");
    }

    private void reset(CommandSender sender) {
        if (!sender.hasPermission("gridlock.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: gridlock.admin");
            return;
        }
        game.resetProgress();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Gridlock] " + ChatColor.YELLOW + "Scores and completions reset.");
    }

    private void team(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gridlock.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: gridlock.admin");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /gridlock team <create|join|leave> ...");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GRAY + "Usage: /gridlock team create <name>");
                    return;
                }
                game.createTeam(args[2]);
                sender.sendMessage(ChatColor.GREEN + "Team created: " + args[2]);
            }
            case "join" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GRAY + "Usage: /gridlock team join <team> [player]");
                    return;
                }
                Player player = resolvePlayer(sender, args.length >= 4 ? args[3] : null);
                if (player == null) return;
                game.joinTeam(player, args[2]);
                sender.sendMessage(ChatColor.GREEN + player.getName() + " joined " + args[2]);
            }
            case "leave" -> {
                Player player = resolvePlayer(sender, args.length >= 3 ? args[2] : null);
                if (player == null) return;
                game.leaveTeam(player);
                sender.sendMessage(ChatColor.YELLOW + player.getName() + " left their Gridlock team.");
            }
            default -> sender.sendMessage(ChatColor.GRAY + "Usage: /gridlock team <create|join|leave> ...");
        }
    }

    private void complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can manually complete objectives in this MVP.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /gridlock complete <objective id or name>");
            return;
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (game.completeManual(player, query)) {
            sender.sendMessage(ChatColor.GREEN + "Objective completed: " + query);
        } else {
            sender.sendMessage(ChatColor.RED + "No incomplete board objective matched: " + query);
        }
    }

    private Player resolvePlayer(CommandSender sender, String name) {
        if (name == null) {
            if (sender instanceof Player player) return player;
            sender.sendMessage(ChatColor.RED + "Console must specify a player.");
            return null;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) sender.sendMessage(ChatColor.RED + "Player not online: " + name);
        return player;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Gridlock commands");
        sender.sendMessage(ChatColor.GRAY + "/gridlock import [file] " + ChatColor.DARK_GRAY + "- load web export JSON");
        sender.sendMessage(ChatColor.GRAY + "/gridlock start|stop|reset");
        sender.sendMessage(ChatColor.GRAY + "/gridlock board|score|detectors");
        sender.sendMessage(ChatColor.GRAY + "/gridlock team create <name>");
        sender.sendMessage(ChatColor.GRAY + "/gridlock team join <team> [player]");
        sender.sendMessage(ChatColor.GRAY + "/gridlock complete <objective>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(ROOT, args[0]);
        if (args.length == 2 && "team".equalsIgnoreCase(args[0])) return prefix(TEAM, args[1]);
        return new ArrayList<>();
    }

    private static List<String> prefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }
}
