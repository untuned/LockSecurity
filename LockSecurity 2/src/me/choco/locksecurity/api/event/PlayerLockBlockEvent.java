package me.choco.locksecurity.api.event;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import me.choco.locksecurity.api.event.variant.LSPlayerEvent;
import me.choco.locksecurity.utils.LSPlayer;

/** Called when a player successfully locks a block
 * @author Parker Hawke - 2008Choco
 */
public class PlayerLockBlockEvent extends LSPlayerEvent implements Cancellable {
	
	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	
	private final int lockID, keyID;
	private final Block block;
	public PlayerLockBlockEvent(LSPlayer player, Block block, int lockID, int keyID) {
		super(player);
		this.block = block;
		this.lockID = lockID;
		this.keyID = keyID;
	}
	
	/** Get the block involved with this event
	 * @return the block that will be locked
	 */
	public Block getBlock() {
		return block;
	}
	
	/** Get the lock ID which will be assigned to the block
	 * @return the assigned lock ID
	 */
	public int getLockID() {
		return lockID;
	}
	
	/** Get the key ID which will be assiged to the block
	 * @return the assigned lock ID
	 */
	public int getKeyID() {
		return keyID;
	}
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	public static HandlerList getHandlerList(){
		return handlers;
	}
	
	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}
}