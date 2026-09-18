package com.airdropmc.lightkeeper.economy;

import com.airdropmc.integration.support.BlockExplosionCommand;
import com.airdropmc.integration.support.InventoryReplacementCommand;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class LightkeeperEconomyPlugin extends JavaPlugin {

	private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private final EconomyLedger ledger = new EconomyLedger();
	private ExecutorService executor;
	private Economy provider;

	@Override
	public void onEnable() {
		Objects.requireNonNull(getCommand("airdrop-lightkeeper-block-explode"), "block explosion command")
				.setExecutor(new BlockExplosionCommand());
		Objects.requireNonNull(getCommand("airdrop-lightkeeper-inventory-replacement"), "inventory replacement command")
				.setExecutor(new InventoryReplacementCommand());
		executor = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "lightkeeper-economy");
			thread.setDaemon(true);
			return thread;
		});
		Objects.requireNonNull(getCommand("lkeconomy"), "lkeconomy command")
				.setExecutor(this::onEconomyCommand);
	}

	@Override
	public void onDisable() {
		getServer().getServicesManager().unregisterAll(this);
		provider = null;
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	private boolean onEconomyCommand(
			CommandSender sender,
			Command command,
			String label,
			String[] arguments
	) {
		if (arguments.length == 1 && "enable".equalsIgnoreCase(arguments[0])) {
			return enableProvider(sender);
		}
		if (arguments.length != 3) {
			return false;
		}

		try {
			return switch (arguments[0].toLowerCase(Locale.ROOT)) {
				case "reset" -> resetAccount(sender, arguments[1], arguments[2]);
				case "report" -> reportAccount(sender, arguments[1], arguments[2]);
				default -> false;
			};
		} catch (IllegalArgumentException failure) {
			sender.sendMessage("Invalid LightKeeper economy command: " + failure.getMessage());
			return false;
		}
	}

	private boolean enableProvider(CommandSender sender) {
		if (provider == null) {
			provider = VaultUnlockedEconomyService.create(
					ledger, executor, operation -> getServer().getPluginManager()
							.callEvent(new EconomyOperationEvent(operation)));
			getServer().getServicesManager().register(
					Economy.class, provider, this, ServicePriority.Normal);
		}
		sender.sendMessage("Enabled LightKeeper economy provider");
		return true;
	}

	private boolean resetAccount(CommandSender sender, String rawPlayerId, String rawBalance) {
		UUID playerId = UUID.fromString(rawPlayerId);
		BigDecimal balance = new BigDecimal(rawBalance);
		if (balance.signum() < 0) {
			throw new IllegalArgumentException("balance must be non-negative");
		}
		ledger.reset(playerId, balance);
		sender.sendMessage("Reset LightKeeper economy account " + playerId);
		return true;
	}

	private boolean reportAccount(CommandSender sender, String rawPlayerId, String correlationId) {
		UUID playerId = UUID.fromString(rawPlayerId);
		if (!CORRELATION_ID.matcher(correlationId).matches()) {
			throw new IllegalArgumentException("invalid correlation id");
		}
		getServer().getPluginManager().callEvent(
				new EconomyStateEvent(correlationId, playerId, ledger.snapshot(playerId)));
		sender.sendMessage("Reported LightKeeper economy account " + playerId);
		return true;
	}
}
