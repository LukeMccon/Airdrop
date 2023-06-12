package lukemccon.airdrop.exceptions;

import org.bukkit.ChatColor;

@SuppressWarnings("serial")
public class PackageNotFoundException extends Exception {

	public PackageNotFoundException() {
		super("Unable to find package");
	}
	
	public PackageNotFoundException(String message) {
		super("Unable to find package with name: " + ChatColor.GREEN + message);
	}
}
