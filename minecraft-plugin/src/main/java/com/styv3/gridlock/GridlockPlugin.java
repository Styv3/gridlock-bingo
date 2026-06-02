package com.styv3.gridlock;

import java.io.File;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class GridlockPlugin extends JavaPlugin implements Listener {
    private GridlockGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDataFolder();

        game = new GridlockGame(this);
        GridlockCommand gridlockCommand = new GridlockCommand(this, game);
        PluginCommand command = getCommand("gridlock");
        if (command != null) {
            command.setExecutor(gridlockCommand);
            command.setTabCompleter(gridlockCommand);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("GridlockSimulator enabled. Use /gridlock import after copying gridlock-bingo.json into the plugin folder.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Material material = event.getRecipe().getResult().getType();
        game.tryComplete(player, Trigger.material(TriggerType.CRAFT_ITEM, material));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.PLACE_BLOCK, event.getBlockPlaced().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.BREAK_BLOCK, event.getBlock().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.CONSUME_ITEM, event.getItem().getType()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        game.tryComplete(killer, Trigger.entity(TriggerType.KILL_ENTITY, event.getEntityType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        EntityType type = event.getRightClicked().getType();
        if (type != EntityType.ITEM_FRAME && type != EntityType.GLOW_ITEM_FRAME) return;
        ItemStack held = heldItem(event.getPlayer(), event.getHand());
        if (held == null || held.getType().isAir()) return;
        game.tryComplete(event.getPlayer(), Trigger.itemFrame(held.getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        game.tryComplete(player, Trigger.entity(TriggerType.DAMAGE_FROM_ENTITY, event.getDamager().getType()));
    }

    private static ItemStack heldItem(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) return player.getInventory().getItemInOffHand();
        return player.getInventory().getItemInMainHand();
    }

    private void ensureDataFolder() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("Could not create plugin data folder: " + folder);
        }
    }
}
