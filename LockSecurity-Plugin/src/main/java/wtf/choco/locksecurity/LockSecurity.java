package wtf.choco.locksecurity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import wtf.choco.locksecurity.api.LockSecurityAPI;
import wtf.choco.locksecurity.api.impl.LockSecurityWrapper;
import wtf.choco.locksecurity.block.LockedBlockManager;
import wtf.choco.locksecurity.command.CommandEditKey;
import wtf.choco.locksecurity.command.CommandGiveKey;
import wtf.choco.locksecurity.command.CommandIgnoreLocks;
import wtf.choco.locksecurity.command.CommandLockList;
import wtf.choco.locksecurity.command.CommandLockNotify;
import wtf.choco.locksecurity.command.CommandLockSecurity;
import wtf.choco.locksecurity.command.CommandRefreshKeys;
import wtf.choco.locksecurity.key.KeyFactory;
import wtf.choco.locksecurity.listener.KeyItemListener;
import wtf.choco.locksecurity.listener.LockedBlockInteractionListener;
import wtf.choco.locksecurity.listener.LockedBlockProtectionListener;
import wtf.choco.locksecurity.listener.PlayerWrapperStateListener;
import wtf.choco.locksecurity.metrics.StatHandler;
import wtf.choco.locksecurity.player.LockSecurityPlayer;
import wtf.choco.locksecurity.util.LSConstants;
import wtf.choco.locksecurity.util.UpdateChecker;
import wtf.choco.locksecurity.util.UpdateChecker.UpdateReason;

public final class LockSecurity extends JavaPlugin {

    public static final Gson GSON = new Gson();

    private static LockSecurity instance;

    private File blocksFile, playerDataDirectory;
    private BukkitTask updateTask;

    private final LockedBlockManager lockedBlockManager = new LockedBlockManager();
    private final Map<UUID, LockSecurityPlayer> players = new HashMap<>();

    private final Set<Material> lockableBlocks = EnumSet.noneOf(Material.class);

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        if (!Files.exists(getDataFolder().toPath().resolve(LSConstants.PATH_RESOURCE_PACK))) {
            this.saveResource(LSConstants.PATH_RESOURCE_PACK, false);
        }

        Logger logger = getLogger();

        // Load lockable blocks into memory
        this.reloadLockableBlocks();

        // Create the blocks.json file
        this.blocksFile = new File(getDataFolder(), "blocks.json");
        if (blocksFile.exists()) {
            logger.info("Loading blocks...");

            try (BufferedReader reader = new BufferedReader(new FileReader(blocksFile))) {
                this.lockedBlockManager.read(GSON.fromJson(reader, JsonObject.class), true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            logger.info("Done!");
        }

        this.playerDataDirectory = new File(getDataFolder(), "playerData/");
        this.playerDataDirectory.mkdirs();

        // Read from file all online players (support for /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            LockSecurityPlayer playerWrapper = getPlayer(player);
            UUID uuid = player.getUniqueId();

            File playerDataFile = new File(playerDataDirectory, uuid.toString() + ".json");
            if (!playerDataFile.exists()) {
                return;
            }

            // We can do this synchronously on startup
            try (BufferedReader reader = new BufferedReader(new FileReader(playerDataFile))) {
                JsonObject object = LockSecurity.GSON.fromJson(reader, JsonObject.class);
                playerWrapper.read(object);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JsonSyntaxException | JsonIOException e) {
                logger.warning("Could not load player data for player \"" + player.getName() + "\" (" + uuid + "). Deleting...");
                playerDataFile.delete();
            }
        }

        // Register listeners
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new KeyItemListener(this), this);
        pluginManager.registerEvents(new LockedBlockInteractionListener(this), this);
        pluginManager.registerEvents(new LockedBlockProtectionListener(this), this);
        pluginManager.registerEvents(new PlayerWrapperStateListener(this), this);

        // Register commands
        this.registerCommandSafely("locksecurity", new CommandLockSecurity(this));
        this.registerCommandSafely("editkey", new CommandEditKey());
        this.registerCommandSafely("givekey", new CommandGiveKey(this));
        this.registerCommandSafely("ignorelocks", new CommandIgnoreLocks(this));
        this.registerCommandSafely("locklist", new CommandLockList(this));
        this.registerCommandSafely("locknotify", new CommandLockNotify(this));
        this.registerCommandSafely("refreshkeys", new CommandRefreshKeys());

        // Register recipes
        this.registerKeyRecipe("n  ", " i ", "  w", KeyFactory.RECIPE_UNSMITHED_KEY_UP_LEFT);
        this.registerKeyRecipe(" n ", " i ", " w ", KeyFactory.RECIPE_UNSMITHED_KEY_UP);
        this.registerKeyRecipe("  n", " i ", "w  ", KeyFactory.RECIPE_UNSMITHED_KEY_UP_RIGHT);
        this.registerKeyRecipe("   ", "niw", "   ", KeyFactory.RECIPE_UNSMITHED_KEY_LEFT);
        this.registerKeyRecipe("   ", "win", "   ", KeyFactory.RECIPE_UNSMITHED_KEY_RIGHT);
        this.registerKeyRecipe("  w", " i ", "n  ", KeyFactory.RECIPE_UNSMITHED_KEY_DOWN_RIGHT);
        this.registerKeyRecipe(" w ", " i ", " n ", KeyFactory.RECIPE_UNSMITHED_KEY_DOWN);
        this.registerKeyRecipe("w  ", " i ", "  n", KeyFactory.RECIPE_UNSMITHED_KEY_DOWN_LEFT);

        /* Unique recipes - Handled programmatically in KeyItemListener */
        Bukkit.addRecipe(new ShapelessRecipe(KeyFactory.RECIPE_KEY_MERGE, KeyItemListener.IMPOSSIBLE_RECIPE_RESULT).addIngredient(2, Material.TRIPWIRE_HOOK));
        Bukkit.addRecipe(new ShapelessRecipe(KeyFactory.RECIPE_KEY_RESET, KeyItemListener.IMPOSSIBLE_RECIPE_RESULT).addIngredient(1, Material.TRIPWIRE_HOOK));

        // Enable metrics
        if (getConfig().getBoolean(LSConstants.METRICS, true)) {
            StatHandler.init(this, 7892); // https://bstats.org/what-is-my-plugin-id
            logger.info("Successfully enabled metrics. Thanks for keeping these enabled!");
        }

        // Update check
        UpdateChecker.init(this, 12650);
        if (getConfig().getBoolean(LSConstants.PERFORM_UPDATE_CHECKS, true)) {
            this.updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                UpdateChecker.get().requestUpdateCheck().whenComplete((result, exception) -> {
                    if (result.requiresUpdate()) {
                        logger.info(String.format("An update is available! LockSecurity %s may be downloaded on SpigotMC", result.getNewestVersion()));
                        Bukkit.broadcast(ChatColor.GRAY + "[" + ChatColor.YELLOW + "LockSecurity" + ChatColor.GRAY + "] A " + ChatColor.GREEN + "new version " + ChatColor.GRAY + "is available for download (Version " + ChatColor.YELLOW + result.getNewestVersion() + ")", "locksecurity.notifyupdate");
                        return;
                    }

                    UpdateReason reason = result.getReason();
                    if (reason == UpdateReason.UP_TO_DATE) {
                        logger.info(String.format("Your version of LockSecurity (%s) is up to date!", result.getNewestVersion()));
                    }
                    else if (reason == UpdateReason.UNRELEASED_VERSION) {
                        logger.info(String.format("Your version of LockSecurity (%s) is more recent than the one publicly available. Are you on a development build?", result.getNewestVersion()));
                    }
                    else {
                        logger.warning("Could not check for a new version of LockSecurity. Reason: " + reason);
                    }
                });
            }, 0L, 432000); // 6 hours
        }

        // Register the LockSecurity API
        LockSecurityAPI.setPlugin(new LockSecurityWrapper(this));
    }

    @Override
    public void onDisable() {
        try (JsonWriter writer = GSON.newJsonWriter(new BufferedWriter(new FileWriter(blocksFile)))) {
            this.blocksFile.createNewFile();
            GSON.toJson(lockedBlockManager.write(new JsonObject()), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Write to file all online players that have not yet left
        this.playerDataDirectory.mkdirs();
        for (Player player : Bukkit.getOnlinePlayers()) {
            LockSecurityPlayer playerWrapper = getPlayer(player);
            UUID uuid = player.getUniqueId();

            File playerDataFile = new File(playerDataDirectory, uuid.toString() + ".json");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(playerDataFile))) {
                writer.write(LockSecurity.GSON.toJson(playerWrapper.write(new JsonObject())));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.lockedBlockManager.clear();
        this.players.clear();
        this.lockableBlocks.clear();

        if (updateTask != null) {
            this.updateTask.cancel();
        }
    }

    public LockedBlockManager getLockedBlockManager() {
        return lockedBlockManager;
    }

    public LockSecurityPlayer getPlayer(OfflinePlayer player) {
        Preconditions.checkArgument(player != null, "Cannot get LockSecurityPlayer wrapper for null player");
        return players.computeIfAbsent(player.getUniqueId(), LockSecurityPlayer::new);
    }

    public File getPlayerDataDirectory() {
        return playerDataDirectory;
    }

    public boolean isLockable(Material material) {
        return lockableBlocks.contains(material);
    }

    public boolean isLockableBlock(Block block) {
        return block != null && isLockable(block.getType());
    }

    public void reloadLockableBlocks() {
        this.lockableBlocks.clear();
        this.getConfig().getStringList("LockableBlocks").forEach(m -> {
            Material material = Material.matchMaterial(m);
            if (material == null || !material.isBlock()) {
                this.getLogger().warning("Failed to load material \"" + m + "\". Is it a block?");
                return;
            }

            this.lockableBlocks.add(material);
        });
    }

    private void registerCommandSafely(String commandName, TabExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            return;
        }

        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerKeyRecipe(String top, String middle, String bottom, NamespacedKey key) {
        ShapedRecipe recipe = new ShapedRecipe(key, KeyFactory.createUnsmithedKey(getConfig().getInt(LSConstants.KEYS_UNSMITHED_RECIPE_YIELD, 1)));
        recipe.shape(top, middle, bottom);
        recipe.setIngredient('w', new MaterialChoice(Tag.PLANKS));
        recipe.setIngredient('i', Material.IRON_INGOT);
        recipe.setIngredient('n', Material.IRON_NUGGET);
        recipe.setGroup(LSConstants.RECIPE_CATEGORY_UNSMITHED_KEY);
        Bukkit.addRecipe(recipe);
    }

    public static NamespacedKey key(String key) {
        return new NamespacedKey(instance, key);
    }

    public static LockSecurity getInstance() {
        return instance;
    }

}
