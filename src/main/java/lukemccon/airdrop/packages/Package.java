package lukemccon.airdrop.packages;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.earth2me.essentials.User;
import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.helpers.ChatHandler;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Package {

	private static Economy econ = Airdrop.AIRDROP_ECONOMY;
	private ArrayList<ItemStack> items;
	private double price;
	private String name;
	
	public Package() {
		
	}
	
	Package(String name, double price, ArrayList<ItemStack> items) {
		this.name = name;
		this.price = price;

		if (items != null && !items.isEmpty()) {
			this.items = items;
		} else {
			this.items = new ArrayList<ItemStack>();
		}

	}
	
	public double getPrice() { 
		return this.price;
	}

	public String getName() { return this.name; }

	public Boolean canAfford(Player player) {
		double balance = econ.getBalance(player);
		int comparison = Double.compare(balance, this.price);

        return comparison >= 0;
    }

	public void chargeUser(Player player) {
		econ.withdrawPlayer(player, this.price);
		ChatHandler.sendMessage(player, ChatColor.AQUA + "$" + this.price + ChatColor.BLUE + " has been taken from your account");
	}

	public String toString() {
		return this.items.stream().map(ItemStack::toString).collect(Collectors.joining("\n")) + "\nprice: " + this.price + "\n";
	}
	public ArrayList<ItemStack> getItems() {
		return this.items;
	}

	public void setItems(ArrayList<ItemStack> items) { this.items = items; }
	
}
