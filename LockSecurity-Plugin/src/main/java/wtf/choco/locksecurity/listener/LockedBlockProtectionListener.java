package wtf.choco.locksecurity.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import wtf.choco.locksecurity.LockSecurity;
import wtf.choco.locksecurity.block.LockedBlock;
import wtf.choco.locksecurity.block.LockedBlockManager;

public final class LockedBlockProtectionListener implements Listener {

    private static final BlockFace[] POSSIBLE_DOUBLE_CHEST_FACES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

    private static final Map<UUID, WarningData> LAST_WARNING = new HashMap<>();
    private static final long WARNING_TIME_MILLIS = TimeUnit.SECONDS.toMillis(5);

    private final LockSecurity plugin;

    public LockedBlockProtectionListener(LockSecurity plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreakLockedBlock(BlockBreakEvent event) {
        LockedBlockManager manager = plugin.getLockedBlockManager();
        Block block = event.getBlock();
        LockedBlock lockedBlock = manager.getLockedBlock(block);

        if (lockedBlock == null) {
            return;
        }

        // Owner is allowed to break the block. It will unlock it
        Player player = event.getPlayer();
        if (lockedBlock.isOwner(player)) {
            manager.unregisterLockedBlock(lockedBlock);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1, 1.5F);
            return;
        }

        // Non-owners cannot break locked blocks
        String blockType = block.getType().getKey().getKey().toLowerCase().replace("_", " ");
        this.warnIfNecessary(player, block, LockSecurity.WARNING_PREFIX + "You cannot destroy a " + ChatColor.YELLOW + blockType + ChatColor.GRAY + " you do not own");
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlaceDoubleChest(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockData blockData = block.getBlockData();
        LockedBlockManager lockedBlockManager = plugin.getLockedBlockManager();

        // Have to make this check WITHOUT DoubleChest InventoryHolder because it's not set by this point...
        if (!player.isSneaking() && blockData instanceof Chest) {
            BlockFace facing = ((Chest) blockData).getFacing();

            for (BlockFace doubleChestFace : POSSIBLE_DOUBLE_CHEST_FACES) {
                Block doubleChest = block.getRelative(doubleChestFace);
                if (doubleChest.getType() != block.getType() || !lockedBlockManager.isLocked(doubleChest)) {
                    continue;
                }

                // We should let the owner do whatever they want, really
                if (lockedBlockManager.getLockedBlock(doubleChest).isOwner(player)) {
                    continue;
                }

                BlockData doubleChestBlockData = doubleChest.getBlockData();
                if (!(doubleChestBlockData instanceof Chest)) {
                    continue;
                }

                BlockFace doubleChestFacing = ((Chest) doubleChestBlockData).getFacing();
                if (facing != doubleChestFacing) {
                    continue;
                }

                // At this point, we're pretty confident it's a locked chest to become a double chest!
                String blockType = block.getType().getKey().getKey().toLowerCase().replace("_", " ");
                this.warnIfNecessary(player, block, LockSecurity.WARNING_PREFIX + "You cannot place a double chest against a " + ChatColor.YELLOW + blockType + ChatColor.GRAY + " you do not own");
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onExplodeLockedBlock(BlockExplodeEvent event) {
        LockedBlockManager manager = plugin.getLockedBlockManager();
        event.blockList().removeIf(manager::isLocked);
    }

    @EventHandler
    public void onExplodeLockedBlock(EntityExplodeEvent event) {
        LockedBlockManager manager = plugin.getLockedBlockManager();
        event.blockList().removeIf(manager::isLocked);
    }

    @EventHandler
    public void onLockedBlockBurn(BlockBurnEvent event) {
        if (plugin.getLockedBlockManager().isLocked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstoneActivateLockedBlock(BlockRedstoneEvent event) {
        if (plugin.getLockedBlockManager().isLocked(event.getBlock())) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler
    public void onZombieBreakLockedDoor(EntityBreakDoorEvent event) {
        if (plugin.getLockedBlockManager().isLocked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonMoveLockedBlock(BlockPistonExtendEvent event) {
        this.cancelPistonMovement(event, event.getBlock(), event.getBlocks(), Sound.BLOCK_PISTON_EXTEND);
    }

    @EventHandler
    public void onPistonMoveLockedBlock(BlockPistonRetractEvent event) {
        this.cancelPistonMovement(event, event.getBlock(), event.getBlocks(), Sound.BLOCK_PISTON_CONTRACT);
    }

    private void cancelPistonMovement(Cancellable event, Block piston, List<Block> blocks, Sound sound) {
        LockedBlockManager manager = plugin.getLockedBlockManager();

        for (Block block : blocks) {
            if (manager.isLocked(block)) {
                World world = piston.getWorld();
                Location pistonLocation = piston.getLocation();

                world.spawnParticle(Particle.SMOKE_NORMAL, block.getLocation().add(0.5, 1.2, 0.5), 5, 0.1F, 0.2F, 0.1F, 0.01F);
                world.playSound(pistonLocation, Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.25F);
                world.playSound(pistonLocation, sound, 1.0F, 1.75F);

                event.setCancelled(true);
                break;
            }
        }
    }

    private void warnIfNecessary(Player player, Block block, String message) {
        LAST_WARNING.computeIfAbsent(player.getUniqueId(), u -> new WarningData()).warnIfNecessary(player, block, message);
    }

    private final class WarningData {

        private final Map<Block, Long> lastWarning = new HashMap<>();

        private WarningData() { }

        public void warnIfNecessary(Player player, Block block, String warning) {
            long now = System.currentTimeMillis();
            if (now - lastWarning.computeIfAbsent(block, b -> 0L) >= WARNING_TIME_MILLIS) {
                this.lastWarning.put(block, now);
                player.sendMessage(warning);
            }
        }

    }

}
