package lukemccon.airdrop.exceptions;

import org.bukkit.ChatColor;

@SuppressWarnings("serial")
public class PackageNotFoundException extends Exception {

	public PackageNotFoundException(String message) {
		super("Unable to find package with name: " + ChatColor.GREEN + message + "\nUse "+ ChatColor.YELLOW +"/airdrop packages" + ChatColor.GREEN + " to see all packages");
	}
}
