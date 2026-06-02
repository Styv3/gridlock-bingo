package com.styv3.gridlock;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class GridlockPlugin extends JavaPlugin implements Listener {
    private final Map<String, Deque<Material>> smeltedSources = new LinkedHashMap<>();
    private final Set<UUID> spawnRatesAdjusted = new HashSet<>();
    private final Random random = new Random();
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

    void applyGridlockWorldRules() {
        if (!getConfig().getBoolean("apply-gridlock-world-rules", true)) return;
        for (World world : getServer().getWorlds()) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setStorm(false);
            world.setThundering(false);
            applySpawnRates(world);
        }
    }

    private void applySpawnRates(World world) {
        if (!getConfig().getBoolean("apply-gridlock-spawn-rates", true)) return;
        if (!spawnRatesAdjusted.add(world.getUID())) return;

        long animalTicks = world.getTicksPerSpawns(SpawnCategory.ANIMAL);
        long monsterTicks = world.getTicksPerSpawns(SpawnCategory.MONSTER);
        if (animalTicks > 0) {
            world.setTicksPerSpawns(SpawnCategory.ANIMAL, (int) Math.max(1L, animalTicks / 10L));
        }
        if (monsterTicks > 0) {
            long adjustedMonsterTicks = Math.min(Integer.MAX_VALUE, monsterTicks * 5L);
            world.setTicksPerSpawns(SpawnCategory.MONSTER, (int) adjustedMonsterTicks);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (game != null && game.isRunning() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (game != null && game.isRunning() && event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTotemResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            game.tryComplete(player, Trigger.simple(TriggerType.TOTEM_RESURRECT));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Material material = event.getRecipe().getResult().getType();
        game.tryComplete(player, Trigger.material(TriggerType.CRAFT_ITEM, material));
        game.tryComplete(player, Trigger.material(TriggerType.OBTAIN_ITEM, material));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.PLACE_BLOCK, block.getType()));
        if (block.getY() >= block.getWorld().getMaxHeight() - 1) {
            game.tryComplete(event.getPlayer(), Trigger.simple(TriggerType.PLACE_AT_HEIGHT_LIMIT));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.BREAK_BLOCK, event.getBlock().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.CONSUME_ITEM, event.getItem().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            game.tryComplete(player, Trigger.material(TriggerType.OBTAIN_ITEM, event.getItem().getItemStack().getType()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        smeltedSources
            .computeIfAbsent(blockKey(event.getBlock()), ignored -> new ArrayDeque<>())
            .addLast(event.getSource().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        String key = blockKey(event.getBlock());
        Deque<Material> sources = smeltedSources.get(key);
        Material source = sources == null ? null : sources.pollFirst();
        if (sources != null && sources.isEmpty()) smeltedSources.remove(key);

        if (source != null) {
            game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.SMELT_ITEM, source));
        }
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.SMELT_ITEM, event.getItemType()));
        game.tryComplete(event.getPlayer(), Trigger.material(TriggerType.OBTAIN_ITEM, event.getItemType()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Player killer = victim.getKiller();
            if (killer != null && game.isOpponent(killer, victim)) {
                game.tryComplete(killer, Trigger.simple(TriggerType.KILL_OPPONENT));
            }
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            game.tryComplete(killer, Trigger.entity(TriggerType.KILL_ENTITY, event.getEntityType()));
        }
        applyLootChanges(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Entity entity = event.getEntity();
        String detail = entity instanceof Sheep sheep ? sheep.getColor().name().toLowerCase() : null;
        game.tryComplete(event.getPlayer(), Trigger.entity(TriggerType.SHEAR_ENTITY, entity.getType(), detail));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        EntityType type = event.getRightClicked().getType();
        ItemStack held = heldItem(event.getPlayer(), event.getHand());
        if (held == null || held.getType().isAir()) return;

        if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
            game.tryComplete(event.getPlayer(), Trigger.itemFrame(held.getType()));
        }
        game.tryComplete(event.getPlayer(), Trigger.materialAndEntity(TriggerType.USE_ITEM_ON_ENTITY, held.getType(), type));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        handleProjectileHitEntity(event);
        if (!(event.getEntity() instanceof Player victim)) return;

        game.tryComplete(victim, Trigger.entity(TriggerType.DAMAGE_FROM_ENTITY, event.getDamager().getType()));

        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || !game.isOpponent(attacker, victim)) return;

        Material projectile = projectileMaterial(event.getDamager());
        if (projectile != null) {
            game.tryComplete(attacker, Trigger.material(TriggerType.PROJECTILE_HIT_OPPONENT, projectile));
            return;
        }

        Material weapon = attacker.getInventory().getItemInMainHand().getType();
        if (!weapon.isAir()) {
            game.tryComplete(attacker, Trigger.material(TriggerType.HIT_OPPONENT_WITH_ITEM, weapon));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Player target && game.isOpponent(event.getPlayer(), target)) {
            game.tryComplete(event.getPlayer(), Trigger.simple(TriggerType.FISHING_ROD_OPPONENT));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player attacker = attackingPlayer(event.getCombuster());
        if (attacker != null && game.isOpponent(attacker, target)) {
            game.tryComplete(attacker, Trigger.simple(TriggerType.IGNITE_OPPONENT));
        }
    }

    private void handleProjectileHitEntity(EntityDamageByEntityEvent event) {
        Material material = projectileMaterial(event.getDamager());
        if (material == null) return;
        Player shooter = attackingPlayer(event.getDamager());
        if (shooter == null) return;
        game.tryComplete(shooter, Trigger.materialAndEntity(TriggerType.PROJECTILE_HIT_ENTITY, material, event.getEntityType()));
    }

    private void applyLootChanges(EntityDeathEvent event) {
        if (!game.isRunning() || !getConfig().getBoolean("apply-gridlock-loot", true)) return;

        if (event.getEntity() instanceof Enderman) {
            ensureDrop(event, Material.ENDER_PEARL);
            return;
        }
        if (event.getEntity() instanceof WitherSkeleton && random.nextDouble() < 0.20) {
            ensureDrop(event, Material.WITHER_SKELETON_SKULL);
            return;
        }
        if (event.getEntity() instanceof Drowned drowned && holds(drowned, Material.TRIDENT)) {
            ensureDrop(event, Material.TRIDENT);
        }
    }

    private static void ensureDrop(EntityDeathEvent event, Material material) {
        boolean alreadyDrops = event.getDrops().stream().anyMatch(drop -> drop.getType() == material);
        if (!alreadyDrops) event.getDrops().add(new ItemStack(material));
    }

    private static boolean holds(Drowned drowned, Material material) {
        EntityEquipment equipment = drowned.getEquipment();
        if (equipment == null) return false;
        return equipment.getItemInMainHand().getType() == material
            || equipment.getItemInOffHand().getType() == material;
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private static Material projectileMaterial(Entity damager) {
        if (!(damager instanceof Projectile)) return null;
        return switch (damager.getType().name()) {
            case "SNOWBALL" -> Material.SNOWBALL;
            case "EGG" -> Material.EGG;
            case "TRIDENT" -> Material.TRIDENT;
            case "SPLASH_POTION" -> Material.SPLASH_POTION;
            case "ARROW", "SPECTRAL_ARROW" -> Material.ARROW;
            case "WIND_CHARGE", "BREEZE_WIND_CHARGE" -> Material.matchMaterial("WIND_CHARGE");
            default -> null;
        };
    }

    private static ItemStack heldItem(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) return player.getInventory().getItemInOffHand();
        return player.getInventory().getItemInMainHand();
    }

    private static String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void ensureDataFolder() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning(ChatColor.stripColor("Could not create plugin data folder: " + folder));
        }
    }
}
