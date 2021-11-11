package me.lukemccon.airdrop.packages;

import java.util.ArrayList;

import org.bukkit.inventory.ItemStack;

public class Package {

	private ArrayList<ItemStack> items;
	private Double price;
	private String name;
	
	public Package() {
		
	}
	
	Package(String name, Double price, ArrayList<ItemStack> items) {
		this.name = name;
		this.price = price;
		this.items = items;
	}
	
	public String toString() {
		return this.name;
	}
	
}
