package lukemccon.airdrop.packages;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.helpers.ChatHandler;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Package {

	private static final Economy econ = Airdrop.getAirdropEconomy();
	private List<ItemStack> items;
	private double price;
	private String name;
	
	public Package() {
		
	}
	
	Package(String name, double price, List<ItemStack> items) {
		this.name = name;
		this.price = price;

		if (items != null && !items.isEmpty()) {
			this.items = items;
		} else {
			this.items = new ArrayList<>();
		}

	}
	
	public double getPrice() { 
		return this.price;
	}

	public String getName() { return this.name; }

	public Boolean canAfford(Player player) {
        return Double.compare(econ.getBalance(player), this.price) >= 0;
    }

	public void chargeUser(Player player) {
		econ.withdrawPlayer(player, this.price);
		ChatHandler.sendMessage(player, ChatColor.AQUA + "$" + this.price + ChatColor.BLUE + " has been taken from your account");
	}

	public String toString() {
		return this.items.stream().map(ItemStack::toString).collect(Collectors.joining("\n")) + "\nprice: " + this.price + "\n";
	}
	public List<ItemStack> getItems() {
		return this.items;
	}

	public void setItems(List<ItemStack> items) { this.items = items; }
	
}
