package me.choco.locksecurity.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.choco.locksecurity.LockSecurityPlugin;
import me.choco.locksecurity.api.utils.KeyFactory;
import me.choco.locksecurity.api.utils.KeyFactory.KeyType;

public class ForgeKeyCmd implements CommandExecutor {
	
	private final LockSecurityPlugin plugin;
	
	public ForgeKeyCmd(LockSecurityPlugin plugin) {
		this.plugin = plugin;
	}
	
	/* Usage:
	 *   /forgekey <id>,[id],[id]...
	 */
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			this.plugin.sendMessage(sender, plugin.getLocale().getMessage("command.general.onlyplayers"));
			return true;
		}
		
		if (!sender.hasPermission("locks.forgekey")) {
			this.plugin.sendMessage(sender, plugin.getLocale().getMessage("command.general.nopermission"));
			return true;
		}
		
		if (args.length == 0) {
			this.plugin.sendMessage(sender, plugin.getLocale().getMessage("command.forgekey.noid"));
			return true;
		}
		
		Player player = (Player) sender;
		
		if (args.length >= 1) {
			String[] stringIDs = args[0].split(",");
			int[] IDs = new int[stringIDs.length];
			
			for (int i = 0; i < stringIDs.length; i++) {
				String ID = stringIDs[i];
				try {
					if (ID.equals("")) continue;
					IDs[i] = Integer.parseInt(ID);
				} catch (NumberFormatException e) {
					this.plugin.sendMessage(player, plugin.getLocale().getMessage("command.general.invalidinteger")
							.replace("%param%", ID));
					return true;
				}
			}
			
			player.getInventory().addItem(KeyFactory.buildKey(KeyType.SMITHED).withIDs(IDs).build());
			this.plugin.sendMessage(player, plugin.getLocale().getMessage("command.forgekey.givenkey").replace("%ID%", args[0]));
		}
		
		return true;
	}
	
}