package me.choco.locksecurity.utils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import me.choco.locksecurity.LockSecurity;
import me.choco.locksecurity.api.LockedBlock;
import me.choco.locksecurity.api.utils.LSMode;
import me.choco.locksecurity.registration.LockedBlockManager;
import me.choco.locksecurity.utils.json.JSONSerializable;
import me.choco.locksecurity.utils.json.JSONUtils;

/** A wrapper class for the OfflinePlayer interface containing information about a player's
 * LockSecurity details such as (but not limited to); their data file, their owned blocks, their
 * active modes ({@link LSMode}), etc.
 * <br>
 * <br><b>NOTE: </b>
 * <br>Information regarding owned locked blocks is a record of what locked blocks currently exist.
 * Not all owned blocks are registered in the {@link LockedBlockManager}, meaning
 * unregistered blocks will be ignored in a locked block lookup / protection listener
 * @author Parker Hawke - 2008Choco
 */
public class LSPlayer implements JSONSerializable {
	
	private static final LockSecurity plugin = LockSecurity.getPlugin();
	
	private final File jsonDataFile;
	
	private final Set<LockedBlock> ownedBlocks = new HashSet<>();
	private final Set<LSMode> activeModes = new HashSet<>();
	
	private LSPlayer toTransferTo;
	
	private OfflinePlayer player;
	public LSPlayer(OfflinePlayer player) {
		this.player = player;
		
		this.jsonDataFile = new File(plugin.playerdataDir + File.separator + player.getUniqueId() + ".json");
		if (!jsonDataFile.exists()){
			try{
				jsonDataFile.createNewFile();
				JSONUtils.writeJSON(jsonDataFile, this.write(new JSONObject()));
			}catch(IOException e){};
		}
	}
	
	/** Get the player this object represents
	 * @return the player
	 */
	public OfflinePlayer getPlayer() {
		return player;
	}
	
	/** Get a set of all blocks owned by this player
	 * @return a set of all owned blocks
	 * @see {@link LockedBlockManager}
	 */
	public Set<LockedBlock> getOwnedBlocks() {
		return ownedBlocks;
	}
	
	/** Add a block to this player's ownership. This does not register the block
	 * @param block - The block to add to ownership
	 * @see {@link LockedBlockManager#registerBlock(LockedBlock)}
	 */
	public void addBlockToOwnership(LockedBlock block){
		if (!block.getOwner().equals(this))
			throw new IllegalStateException("Unable to register a locked block to a user that does not own it");
		
		if (ownedBlocks.contains(block)) return;
		this.ownedBlocks.add(block);
	}
	
	/** Remove a block from this players ownership
	 * @param block - The block to remove
	 */
	public void removeBlockFromOwnership(LockedBlock block){
		ownedBlocks.remove(block);
	}
	
	/** Check if the player owns the specified block or not
	 * @param block - The block to check
	 * @return true if the player owns this block
	 * @see {@link LockedBlock#isOwner(LSPlayer)}
	 */
	public boolean ownsBlock(LockedBlock block){
		return ownedBlocks.contains(block);
	}
	
	/** Enable a mode for the player
	 * @param mode - the mode to enable
	 */
	public void enableMode(LSMode mode){
		this.activeModes.add(mode);
	}
	
	/** Disable a mode for the player
	 * @param mode - The mode to disable
	 */
	public void disableMode(LSMode mode){
		this.activeModes.remove(mode);
	}
	
	/** Toggle the mode either enabled or disabled, depending on
	 * it's current state
	 * @param mode - The mode to toggle
	 * @return true if set enabled, false if set disabled
	 */
	public boolean toggleMode(LSMode mode){
		if (activeModes.contains(mode)) this.activeModes.remove(mode);
		else this.activeModes.add(mode);
		
		return this.isModeActive(mode);
	}
	
	/** Check if a mode is currently active for this player
	 * @param mode - The mode to check
	 * @return true if the specified mode is active
	 */
	public boolean isModeActive(LSMode mode){
		return this.activeModes.contains(mode);
	}
	
	/** Get a set of all currently active modes for this player
	 * @return a set of active modes
	 */
	public Set<LSMode> getActiveModes() {
		return activeModes;
	}
	
	public void setToTransferTo(LSPlayer toTransferTo) {
		this.toTransferTo = toTransferTo;
	}
	
	public LSPlayer getToTransferTo() {
		return toTransferTo;
	}
	
	/** Get the JSON data file that keeps track of offline information for this user
	 * @return the player's JSON data file
	 */
	public File getJSONDataFile() {
		return jsonDataFile;
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject write(JSONObject data) {
		data.put("uuid", player.getUniqueId().toString());
		
		JSONArray activeModesData = new JSONArray();
		for (LSMode mode : this.activeModes){
			activeModesData.add(mode.getName());
		}
		
		data.put("activeModes", activeModesData);
		
		JSONArray ownedBlocksData = new JSONArray();
		for (LockedBlock block : this.ownedBlocks){
			ownedBlocksData.add(block.write(new JSONObject()));
		}
		
		data.put("ownedBlocks", ownedBlocksData);
		return data;
	}

	@Override
	public boolean read(JSONObject data) {
		this.player = Bukkit.getOfflinePlayer(UUID.fromString((String) data.get("uuid")));
		
		JSONArray activeModesData = (JSONArray) data.get("activeModes");
		for (int i = 0; i < activeModesData.size(); i++){
			LSMode mode = LSMode.getByName((String) activeModesData.get(i));
			if (mode == null) continue;
			
			this.activeModes.add(mode);
		}
		
		JSONArray ownedBlocksData = (JSONArray) data.get("ownedBlocks");
		for (int i = 0; i < ownedBlocksData.size(); i++){
			JSONObject blockData = (JSONObject) ownedBlocksData.get(i);
			LockedBlock block = new LockedBlock(blockData);
			
			this.ownedBlocks.add(block);
		}
		return true;
	}
}