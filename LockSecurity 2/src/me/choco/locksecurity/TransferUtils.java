package me.choco.locksecurity;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import me.choco.locksecurity.api.LockedBlock;
import me.choco.locksecurity.registration.PlayerRegistry;
import me.choco.locksecurity.utils.LSPlayer;
import me.choco.locksecurity.utils.general.ConfigAccessor;

/**
 * Contains a few methods to assist in the transfering of information from one
 * data source to the new JSON data source in LockSecurity 2.0.0+
 * 
 * @author Parker Hawke - 2008Choco
 */
public final class TransferUtils {
	
	protected static final void fromDatabase(LockSecurity plugin) {
		plugin.getLogger().info("Commencing transfer process for Data Support of LockSecurity 1.7.0 - 1.8.2");
		// TODO: Transfer data from "lockinfo.db" (SQLite)
	}
	
	protected static final void fromFile(LockSecurity plugin) {
		plugin.getLogger().info("Commencing transfer process for Data Support of LockSecurity 1.0.0 - 1.6.3");
		
		PlayerRegistry playerRegistry = plugin.getPlayerRegistry();
		
		ConfigAccessor lockedFile = new ConfigAccessor(plugin, "locked.yml");
		if (!plugin.infoFile.exists()) {
			try {
				plugin.infoFile.createNewFile();
				FileUtils.write(plugin.infoFile, 
						"nextLockID=" + lockedFile.getConfig().getInt("NextLockID")
						+ "\nnextKeyID=" + lockedFile.getConfig().getInt("NextKeyID"), 
					Charset.defaultCharset());
			} catch (IOException e) {
				plugin.getLogger().info("Could not load key/lock ID to file");
				plugin.infoFile.delete();
			}
		}
		
		Set<String> keys = lockedFile.getConfig().getKeys(false);
		keys.remove("NextLockID"); keys.remove("NextKeyID");
		
		for (String key : keys) {
			int lockID = Integer.parseInt(key);
			int keyID = lockedFile.getConfig().getInt(key + ".KeyID");
			UUID ownerUUID = UUID.fromString(lockedFile.getConfig().getString(key + ".OwnerUUID"));
			
			World world = Bukkit.getWorld(lockedFile.getConfig().getString(key + ".Location.World"));
			double x = lockedFile.getConfig().getDouble(key + ".Location.X");
			double y = lockedFile.getConfig().getDouble(key + ".Location.Y");
			double z = lockedFile.getConfig().getDouble(key + ".Location.Z");
			Location location = new Location(world, x, y, z);
			
			LSPlayer player = playerRegistry.registerPlayer(Bukkit.getOfflinePlayer(ownerUUID));
			if (player == null) { // Player has never existed on the server
				plugin.getLogger().warning("Missing player with UUID \"" + ownerUUID + "\". Ignoring...");
				continue;
			}
			
			LockedBlock block = new LockedBlock(player, location, lockID, keyID);
			player.addBlockToOwnership(block);
			
			plugin.getLogger().info("Loaded block at " + location.getWorld().getName() + " " 
					+ location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
					+ " for user " + player.getPlayer().getName() + " with LockID " + lockID + " and KeyID " + keyID);
		}
		
		plugin.getLogger().info("Transfer process completed! You may now delete the \"locked.yml\", or (recommended) keep as a backup"
				+ "in case anything had went awry during the transfer process (i.e. missing data).");
		plugin.getLogger().info("Thank you for using LockSecurity " + plugin.getDescription().getVersion() + ". Enjoy!");
	}
}