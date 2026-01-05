package com.airdropmc.packages;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.ChatTheme;
import com.airdropmc.Airdrop;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a package within the airdrop
 * Which includes the name, price, and items
 */
public class Package {

	private static final Economy econ = Airdrop.getAirdropEconomy();
	private List<ItemStack> items;
	private double price;
	private String name;

	public Package(String name, double price, List<ItemStack> items) {
		this.name = name;
		this.price = price;
		this.setItems(items);
	}

	public double getPrice() {
		return this.price;
	}

	public String getName() {
		return this.name;
	}

	public boolean canAfford(Player player) {
		return Double.compare(econ.getBalance(player), this.price) >= 0;
	}

	public void chargeUser(Player player) throws CannotAffordException {
		if (!econ.withdrawPlayer(player, this.price).transactionSuccess()) {
			// Handle transaction failure
			throw new CannotAffordException(player.getName() + " cannot afford " + this.price);
		}
		ChatHandler.sendMessage(player,
				ChatTheme.accent() + "$" + this.price + ChatTheme.primary() + " has been taken from your account");
	}

	public String toString() {
		return this.items.stream().map(ItemStack::toString).collect(Collectors.joining("\n")) + "\nprice: " + this.price
				+ "\n";
	}

	public List<ItemStack> getItems() {
		return this.items;
	}

	public void setItems(List<ItemStack> items) {
		if (items != null && !items.isEmpty()) {
			this.items = items;
		} else {
			this.items = new ArrayList<>();
		}
	}

}
