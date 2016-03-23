package me.choco.locks.api.event;

import java.util.List;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class PlayerCombineKeyEvent extends Event{
	private static final HandlerList handlers = new HandlerList();
	
	private List<Integer> keyIDs;
	private ItemStack smithedKey1, smithedKey2;
	public PlayerCombineKeyEvent(List<Integer> keyIDs, ItemStack smithedKey1, ItemStack smithedKey2){
		this.keyIDs = keyIDs;
		this.smithedKey1 = smithedKey1;
		this.smithedKey2 = smithedKey2;
	}
	
	@Override
	public HandlerList getHandlers(){
	    return handlers;
	}
	
	public static HandlerList getHandlerList(){
	    return handlers;
	}
	
	/** Get the KeyID's that are being combined in the result
	 * @return List - A list of key ID's 
	 */
	public List<Integer> getFinalKeyIDs(){
		return keyIDs;
	}
	
	/** Get the first smithed key from the crafting recipe
	 * @return ItemStack - The first key component to the recipe
	 */
	public ItemStack getSmithedKey1(){
		return smithedKey1;
	}
	
	/** Get the second smithed key from the crafting recipe
	 * @return ItemStack - The second key component to the recipe
	 */
	public ItemStack getSmithedKey2(){
		return smithedKey2;
	}
}