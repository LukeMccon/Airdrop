package lukemccon.airdrop.packages;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.earth2me.essentials.User;
import lukemccon.airdrop.Airdrop;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Package {

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
		User user = new User(player, Airdrop.ESSENTIALS);
		BigDecimal price = new BigDecimal(this.getPrice());

		System.out.println(user.getMoney());
		return user.canAfford(price);
	}

	public void chargeUser(Player player) {
		User user = new User(player, Airdrop.ESSENTIALS);
		BigDecimal price = new BigDecimal(this.getPrice());
		user.takeMoney(price);
	}

	public String toString() {
		return this.items.stream().map(ItemStack::toString).collect(Collectors.joining("\n")) + "\nprice: " + this.price + "\n";
	}
	public ArrayList<ItemStack> getItems() {
		return this.items;
	}
	
}
