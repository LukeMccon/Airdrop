package lukemccon.airdrop.packages;

import java.util.ArrayList;

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
		this.items = items;
	}
	
	public String toString() {
		return this.name;
	}
	
	public double getPrice() { 
		return this.price;
	}
	
	public ArrayList<ItemStack> getItems() {
		return this.items;
	}
	
}
