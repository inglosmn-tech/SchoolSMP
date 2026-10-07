package com.school.smp;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class SchoolSMP extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Long> combatTag = new HashMap<>();
    private final Map<UUID, Location> homes = new HashMap<>();
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, Inventory> vaults = new HashMap<>();
    
    private final NamespacedKey villagerKey = new NamespacedKey(this, "villager_data");
    private final NamespacedKey artifactKey = new NamespacedKey(this, "artifact_id");
    
    private long serverStartTime;
    private final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private final long FOURTEEN_DAYS_MS = 14L * 24 * 60 * 60 * 1000;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Command Registrations
        Objects.requireNonNull(getCommand("smpstart")).setExecutor(this);
        Objects.requireNonNull(getCommand("dimensionstatus")).setExecutor(this);
        Objects.requireNonNull(getCommand("vault")).setExecutor(this);
        Objects.requireNonNull(getCommand("sethome")).setExecutor(this);
        Objects.requireNonNull(getCommand("home")).setExecutor(this);
        Objects.requireNonNull(getCommand("tpa")).setExecutor(this);
        Objects.requireNonNull(getCommand("tpaccept")).setExecutor(this);
        Objects.requireNonNull(getCommand("spawnartifact")).setExecutor(this);

        saveDefaultConfig();
        if (!getConfig().contains("server_start_time")) {
            serverStartTime = System.currentTimeMillis();
            getConfig().set("server_start_time", serverStartTime);
            saveConfig();
        } else {
            serverStartTime = getConfig().getLong("server_start_time");
        }

        registerEzGapRecipe();
        startArtifactTrackerRunnable();
        startArtifactBuffRunnable();
        getLogger().info("SchoolSMP Core Fully Operational!");
    }

    // --- 1. FIRST-JOIN WELCOME BOOK ---
    @EventHandler
    public void onFirstJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            
            if (meta != null) {
                meta.setTitle(ChatColor.GOLD + "SMP Guide & Rules");
                meta.setAuthor(ChatColor.YELLOW + "Server Admin");

                List<String> pages = new ArrayList<>();
                pages.add(ChatColor.BOLD + "Welcome to the SMP!\n\n" +
                          ChatColor.RESET + "This server features custom mechanics built for high-stakes survival and clean PvP.\n\n" +
                          "Read the following pages to understand the server twist and commands!");

                pages.add(ChatColor.BOLD + "THE ARTIFACT TWIST\n\n" +
                          ChatColor.DARK_PURPLE + "3 Artifacts" + ChatColor.RESET + " exist in the world:\n" +
                          "• " + ChatColor.YELLOW + "Crown of Speed\n" +
                          "• " + ChatColor.RED + "Heart of Steel\n" +
                          "• " + ChatColor.DARK_BLUE + "Void Blade\n\n" +
                          "Holding one gives massive passive perks, but your location pings every 15 mins!");

                pages.add(ChatColor.BOLD + "SERVER RULES\n\n" +
                          "• " + ChatColor.RED + "Max 1 Totem" + ChatColor.RESET + " per inventory.\n" +
                          "• " + ChatColor.RED + "No End Crystals" + ChatColor.RESET + " (Disabled).\n" +
                          "• Nether opens Day 7, End opens Day 14.\n" +
                          "• Combat Log = Instant Death.");

                pages.add(ChatColor.BOLD + "COMMANDS\n\n" +
                          "• " + ChatColor.GOLD + "/vault" + ChatColor.RESET + " - Personal storage.\n" +
                          "• " + ChatColor.GOLD + "/sethome" + ChatColor.RESET + " - Set home.\n" +
                          "• " + ChatColor.GOLD + "/home" + ChatColor.RESET + " - Teleport home.\n" +
                          "• " + ChatColor.GOLD + "/tpa <player>" + ChatColor.RESET + " - Request TP.\n\n" +
                          "Note: Commands are locked in combat!");

                meta.setPages(pages);
                book.setItemMeta(meta);
                p.getInventory().addItem(book);
            }
        }
    }

    // --- 2. ARTIFACT CREATION & ITEM FACTORY ---
    private ItemStack createArtifact(String type) {
        ItemStack item;
        ItemMeta meta;

        switch (type.toLowerCase()) {
            case "crown" -> {
                item = new ItemStack(Material.GOLDEN_HELMET);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + "Crown of Speed");
                meta.setLore(List.of(ChatColor.YELLOW + "Artifact", ChatColor.GRAY + "Grants Speed I & Jump Boost I while in inventory."));
                meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, "crown");
            }
            case "heart" -> {
                item = new ItemStack(Material.HEART_OF_THE_SEA);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.RED + "Heart of Steel");
                meta.setLore(List.of(ChatColor.RED + "Artifact", ChatColor.GRAY + "Grants Resistance I & +2 Hearts while in inventory."));
                meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, "heart");
            }
            case "blade" -> {
                item = new ItemStack(Material.NETHERITE_SWORD);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.DARK_PURPLE + "Void Blade");
                meta.setLore(List.of(ChatColor.DARK_PURPLE + "Artifact", ChatColor.GRAY + "Applies extra knockback & Slowness I on hit."));
                meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, "blade");
            }
            default -> { return null; }
        }
        item.setItemMeta(meta);
        return item;
    }

    // --- 3. ARTIFACT RUNNABLES (BUFFS & LOCATION PINGS) ---
    private void startArtifactBuffRunnable() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.hasItemMeta()) {
                            String artifact = item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
                            if ("crown".equals(artifact)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 0, false, false));
                            } else if ("heart".equals(artifact)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startArtifactTrackerRunnable() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.hasItemMeta()) {
                            String artifact = item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
                            if (artifact != null) {
                                Location loc = p.getLocation();
                                Bukkit.broadcastMessage(ChatColor.GOLD + "[Artifact Ping] " + ChatColor.YELLOW + p.getName() + 
                                        " is carrying an Artifact near X: " + (loc.getBlockX() / 100 * 100) + ", Z: " + (loc.getBlockZ() / 100 * 100));
                                break;
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 18000L); // Pings every 15 minutes (18000 ticks)
    }

    // --- 4. VOID BLADE HIT EVENT ---
    @EventHandler
    public void onVoidBladeHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player attacker && e.getEntity() instanceof Player victim) {
            ItemStack held = attacker.getInventory().getItemInMainHand();
            if (held.hasItemMeta()) {
                String artifact = held.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
                if ("blade".equals(artifact)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                    Vector knockback = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(0.4);
                    victim.setVelocity(victim.getVelocity().add(knockback));
                }
            }
        }
    }

    // --- 5. COMBAT CHECK HELPER ---
    private boolean isInCombat(Player p) {
        if (combatTag.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            p.sendMessage(ChatColor.RED + "You cannot use this command while in combat!");
            return true;
        }
        return false;
    }

    // --- 6. COMMAND HANDLING ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        String name = cmd.getName().toLowerCase();

        switch (name) {
            case "spawnartifact" -> {
                if (!p.hasPermission("smp.admin") || args.length < 1) {
                    p.sendMessage(ChatColor.RED + "Usage: /spawnartifact <crown|heart|blade>");
                    return true;
                }
                ItemStack artifact = createArtifact(args[0]);
                if (artifact != null) {
                    p.getInventory().addItem(artifact);
                    p.sendMessage(ChatColor.GREEN + "Spawned " + args[0] + " artifact!");
                }
            }
            case "vault" -> {
                if (isInCombat(p)) return true;
                Inventory vault = vaults.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createInventory(null, 9, ChatColor.DARK_PURPLE + "Personal Vault"));
                p.openInventory(vault);
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
            }
            case "sethome" -> {
                homes.put(p.getUniqueId(), p.getLocation());
                p.sendMessage(ChatColor.GREEN + "Home set!");
            }
            case "home" -> {
                if (isInCombat(p)) return true;
                Location home = homes.get(p.getUniqueId());
                if (home == null) {
                    p.sendMessage(ChatColor.RED + "No home set! Use /sethome first.");
                } else {
                    p.teleport(home);
                    p.sendMessage(ChatColor.GREEN + "Teleported home!");
                }
            }
            case "tpa" -> {
                if (isInCombat(p)) return true;
                if (args.length < 1) {
                    p.sendMessage(ChatColor.RED + "Usage: /tpa <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                tpaRequests.put(target.getUniqueId(), p.getUniqueId());
                p.sendMessage(ChatColor.GREEN + "Teleport request sent to " + target.getName() + "!");
                target.sendMessage(ChatColor.YELLOW + p.getName() + " wants to teleport to you. Type " + ChatColor.GOLD + "/tpaccept" + ChatColor.YELLOW + " to accept!");
            }
            case "tpaccept" -> {
                if (isInCombat(p)) return true;
                UUID requesterUUID = tpaRequests.remove(p.getUniqueId());
                if (requesterUUID == null) {
                    p.sendMessage(ChatColor.RED + "No active teleport requests!");
                    return true;
                }
                Player requester = Bukkit.getPlayer(requesterUUID);
                if (requester != null && requester.isOnline()) {
                    requester.teleport(p.getLocation());
                    requester.sendMessage(ChatColor.GREEN + "Teleported!");
                    p.sendMessage(ChatColor.GREEN + "Request accepted!");
                }
            }
            case "dimensionstatus" -> {
                long elapsed = System.currentTimeMillis() - serverStartTime;
                long netherHours = Math.max(0, (SEVEN_DAYS_MS - elapsed) / (1000 * 60 * 60));
                long endHours = Math.max(0, (FOURTEEN_DAYS_MS - elapsed) / (1000 * 60 * 60));

                p.sendMessage(ChatColor.GOLD + "--- Dimension Status ---");
                p.sendMessage(ChatColor.YELLOW + "Nether: " + (netherHours == 0 ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "Locked (" + netherHours + "h left)"));
                p.sendMessage(ChatColor.YELLOW + "End: " + (endHours == 0 ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "Locked (" + endHours + "h left)"));
            }
            case "smpstart" -> {
                if (!p.hasPermission("smp.admin")) return true;

                World world = Bukkit.getWorlds().get(0);
                WorldBorder border = world.getWorldBorder();
                border.setCenter(0, 0);
                border.setSize(100);

                for (Player pl : Bukkit.getOnlinePlayers()) {
                    pl.setGameMode(GameMode.SPECTATOR);
                    pl.teleport(new Location(world, 0, 120, 0));
                }

                new BukkitRunnable() {
                    int timer = 10;
                    double angle = 0;

                    @Override
                    public void run() {
                        if (timer > 0) {
                            for (Player pl : Bukkit.getOnlinePlayers()) {
                                angle += 0.2;
                                double x = 30 * Math.cos(angle);
                                double z = 30 * Math.sin(angle);
                                pl.teleport(new Location(world, x, 100, z, (float) Math.toDegrees(Math.atan2(-z, -x)) - 90, 20));
                                pl.sendTitle(ChatColor.GOLD + "" + timer, ChatColor.YELLOW + "Prepare for SMP Start!", 0, 25, 5);
                                pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                            timer--;
                        } else {
                            border.setSize(10000, 300);
                            for (Player pl : Bukkit.getOnlinePlayers()) {
                                pl.setGameMode(GameMode.SURVIVAL);
                                pl.teleport(world.getHighestBlockAt(0, 0).getLocation().add(0, 1, 0));
                                pl.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 4));
                                pl.sendTitle(ChatColor.RED + "GO!", ChatColor.GREEN + "The SMP Has Officially Begun!", 5, 40, 10);
                                pl.playSound(pl.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                            }
                            cancel();
                        }
                    }
                }.runTaskTimer(this, 0L, 20L);
            }
        }
        return true;
    }

    // --- 7. GRAVESTONES ---
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getDrops().isEmpty()) return;

        Location loc = p.getLocation();
        Block block = loc.getBlock();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest chest) {
            chest.setCustomName(ChatColor.DARK_RED + p.getName() + "'s Gravestone");
            chest.update();

            for (ItemStack item : new ArrayList<>(e.getDrops())) {
                if (item != null) chest.getInventory().addItem(item);
            }
            e.getDrops().clear();
        }

        ArmorStand holo = (ArmorStand) loc.getWorld().spawnEntity(loc.add(0.5, 1.2, 0.5), EntityType.ARMOR_STAND);
        holo.setVisible(false);
        holo.setCustomName(ChatColor.GOLD + p.getName() + "'s Gravestone");
        holo.setCustomNameVisible(true);
        holo.setGravity(false);
    }

    // --- 8. COMBAT TAG & LOGGING ---
    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Player attacker) {
            combatTag.put(victim.getUniqueId(), System.currentTimeMillis() + 15000);
            combatTag.put(attacker.getUniqueId(), System.currentTimeMillis() + 15000);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (combatTag.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            p.setHealth(0.0);
            Bukkit.broadcastMessage(ChatColor.RED + p.getName() + " combat logged and was slain!");
        }
    }

    // --- 9. DISABLE END CRYSTALS ---
    @EventHandler
    public void onCrystalPlace(EntityPlaceEvent e) {
        if (e.getEntityType() == EntityType.END_CRYSTAL) {
            e.setCancelled(true);
            if (e.getPlayer() != null) e.getPlayer().sendMessage(ChatColor.RED + "End Crystals are disabled!");
        }
    }

    @EventHandler
    public void onCrystalSpawn(EntitySpawnEvent e) {
        if (e.getEntityType() == EntityType.END_CRYSTAL) e.setCancelled(true);
    }

    // --- 10. MINEMEN WIND CHARGE TECH ---
    @EventHandler
    public void onWindChargeHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof WindCharge windCharge && windCharge.getShooter() instanceof Player) {
            Location loc = windCharge.getLocation();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 3.5, 3.5, 3.5)) {
                if (entity instanceof Player target) {
                    Vector direction = target.getLocation().toVector().subtract(loc.toVector()).normalize();
                    direction.multiply(1.45).setY(0.65);
                    target.setVelocity(direction);
                    target.playSound(target.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.2f);
                }
            }
        }
    }

    // --- 11. TIMED DIMENSION LOCKS ---
    @EventHandler
    public void onPortalUse(PlayerTeleportEvent e) {
        long elapsed = System.currentTimeMillis() - serverStartTime;

        if (e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && elapsed < SEVEN_DAYS_MS) {
            e.setCancelled(true);
            long hoursLeft = (SEVEN_DAYS_MS - elapsed) / (1000 * 60 * 60);
            e.getPlayer().sendMessage(ChatColor.RED + "The Nether is locked for " + hoursLeft + " more hours!");
        } else if (e.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && elapsed < FOURTEEN_DAYS_MS) {
            e.setCancelled(true);
            long hoursLeft = (FOURTEEN_DAYS_MS - elapsed) / (1000 * 60 * 60);
            e.getPlayer().sendMessage(ChatColor.RED + "The End is locked for " + hoursLeft + " more hours!");
        }
    }

    // --- 12. MAX 1 TOTEM PER PLAYER ---
    @EventHandler
    public void onTotemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && e.getItem().getItemStack().getType() == Material.TOTEM_OF_UNDYING) {
            if (hasTotem(p)) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "You can only carry 1 Totem of Undying at a time!");
            }
        }
    }

    private boolean hasTotem(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) return true;
        }
        return p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    // --- 13. EZGAPS RECIPE ---
    private void registerEzGapRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "ez_gap");
        ShapedRecipe ezGap = new ShapedRecipe(recipeKey, new ItemStack(Material.GOLDEN_APPLE, 1));
        ezGap.shape(" G ", "GAG", " G ");
        ezGap.setIngredient('G', Material.GOLD_INGOT);
        ezGap.setIngredient('A', Material.APPLE);
        Bukkit.addRecipe(ezGap);
    }

    // --- 14. VILLAGER BUCKET & SPAWN PROTECTION & STRING DUPE ---
    @EventHandler
    public void onVillagerBucket(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking() && e.getRightClicked() instanceof Villager v) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.getType() == Material.WATER_BUCKET) {
                e.setCancelled(true);
                ItemStack item = new ItemStack(Material.AXOLOTL_BUCKET);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.AQUA + "Villager in a Bucket");
                meta.getPersistentDataContainer().set(villagerKey, PersistentDataType.STRING, v.getProfession().name());
                item.setItemMeta(meta);

                v.remove();
                held.setAmount(held.getAmount() - 1);
                p.getInventory().addItem(item);
                p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onPlaceVillager(PlayerInteractEvent e) {
        ItemStack held = e.getItem();
        if (e.getAction().toString().contains("RIGHT_CLICK") && held != null && held.getType() == Material.AXOLOTL_BUCKET) {
            ItemMeta meta = held.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(villagerKey, PersistentDataType.STRING)) {
                e.setCancelled(true);
                Location loc = e.getInteractionPoint() != null ? e.getInteractionPoint() : e.getPlayer().getLocation();
                Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
                String prof = meta.getPersistentDataContainer().get(villagerKey, PersistentDataType.STRING);
                v.setProfession(Villager.Profession.valueOf(prof));
                held.setAmount(held.getAmount() - 1);
                e.getPlayer().playSound(loc, Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onBuildAtSpawn(BlockBreakEvent e) {
        if (e.getBlock().getLocation().distance(e.getBlock().getWorld().getSpawnLocation()) < 50 && !e.getPlayer().isOp()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "You cannot break blocks in spawn protection!");
        }
    }

    @EventHandler
    public void onStringBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.TRIPWIRE) {
            Tripwire tripwire = (Tripwire) b.getBlockData();
            if (tripwire.isDisarmed()) {
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.STRING, 2));
            }
        }
    }
}