package dev.airdropmc.fixture;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.event.AirdropLandedEvent;
import com.airdropmc.api.event.AirdropLandingAttemptEvent;
import com.airdropmc.api.event.AirdropOutcomeEvent;
import com.airdropmc.api.event.AirdropRecoveredEvent;
import com.airdropmc.api.event.AirdropRequestEvent;
import com.airdropmc.api.event.AirdropRetiredEvent;
import com.airdropmc.api.event.AirdropSpawnedEvent;
import com.airdropmc.api.event.PackageRegistryChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal external consumer used to verify Airdrop's staged Maven publication
 * and supported runtime integration contract.
 */
public final class AirdropConsumerFixture extends JavaPlugin implements Listener {
	private final AtomicLong sequence = new AtomicLong();

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		AirdropApi api = getServer().getServicesManager().load(AirdropApi.class);
		if (api == null) {
			getLogger().severe("Airdrop service is unavailable despite depend: [Airdrop]");
			return;
		}

		api.readiness().whenComplete((readyApi, failure) -> onPrimaryThread(() -> {
			if (failure != null) {
				getLogger().warning(
						"Airdrop readiness failed: " + failure.getClass().getSimpleName());
				return;
			}
			log(ConsumerMarkerFormatter.ready(nextSequence(), Bukkit.isPrimaryThread()));
		}));
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("airdrop-consumer")) {
			return false;
		}
		if (!(sender instanceof ConsoleCommandSender)) {
			sender.sendMessage("This fixture command is console-only.");
			return true;
		}
		String token = args.length > 1 ? args[1] : "NONE";
		try {
			AirdropApi api = getServer().getServicesManager().load(AirdropApi.class);
			if (api == null) {
				commandFailure(token, "API_UNAVAILABLE");
			} else if (args.length == 3 && args[0].equals("snapshot")) {
				AirdropPackage definition = api.findPackage(args[2]).orElse(null);
				if (definition == null) {
					commandFailure(token, "PACKAGE_NOT_FOUND");
				} else {
					log(ConsumerMarkerFormatter.snapshot(token, nextSequence(), Bukkit.isPrimaryThread(),
							api.packageRevision(), definition.name(), definition.price().toPlainString(),
							definition.items().stream()
									.map(item -> item.getType().name() + ":" + item.getAmount()).toList()));
				}
			} else if (args.length == 4 && args[0].equals("request")) {
				Player player = getServer().getPlayer(UUID.fromString(args[2]));
				if (player == null) {
					commandFailure(token, "PLAYER_NOT_FOUND");
				} else {
					observeHandle(token, api.requestPlayerDrop(player, args[3], DropRequestOptions.defaults()));
				}
			} else {
				commandFailure(token, "INVALID_ARGUMENTS");
			}
		} catch (RuntimeException failure) {
			commandFailure(token, failure.getClass().getSimpleName());
		}
		return true;
	}

	private void observeHandle(String token, DropHandle handle) {
		var descriptor = handle.descriptor();
		log(ConsumerMarkerFormatter.handle(token, handle.requestId(), nextSequence(),
				Bukkit.isPrimaryThread(), descriptor.requestId(), descriptor.playerId().orElseThrow(),
				descriptor.requestedPackageName(), descriptor.source().name()));
		// Observe the real completion thread. Never schedule these callbacks onto another thread.
		handle.spawn().whenComplete((result, failure) -> {
			if (failure != null) {
				log(ConsumerMarkerFormatter.failure("HANDLE_SPAWN", token, nextSequence(),
						Bukkit.isPrimaryThread(), failure.getClass().getSimpleName()));
				return;
			}
			String details = switch (result) {
				case DropSpawnResult.Spawned spawned -> " spawned=true crateId=" + spawned.airdrop().crateId()
						+ " viewRequestId=" + spawned.airdrop().requestId().orElseThrow()
						+ " payment=" + spawned.payment().name();
				case DropSpawnResult.NotSpawned notSpawned -> " spawned=false delivery="
						+ notSpawned.outcome().delivery().name()
						+ " payment=" + notSpawned.outcome().payment().name();
			};
			log(ConsumerMarkerFormatter.handleResult("HANDLE_SPAWN", token, handle.requestId(),
					result.requestId(), nextSequence(), Bukkit.isPrimaryThread(), details));
		});
		handle.outcome().whenComplete((result, failure) -> {
			if (failure != null) {
				log(ConsumerMarkerFormatter.failure("HANDLE_OUTCOME", token, nextSequence(),
						Bukkit.isPrimaryThread(), failure.getClass().getSimpleName()));
				return;
			}
			String details = " delivery=" + result.delivery().name() + " payment=" + result.payment().name()
					+ " reason=" + outcomeReason(result);
			if (result instanceof DropOutcome.Landed landed) {
				details += " crateId=" + landed.airdrop().crateId()
						+ " viewRequestId=" + landed.airdrop().requestId().orElseThrow();
			}
			log(ConsumerMarkerFormatter.handleResult("HANDLE_OUTCOME", token, handle.requestId(),
					result.requestId(), nextSequence(), Bukkit.isPrimaryThread(), details));
		});
	}

	private void commandFailure(String token, String reason) {
		log(ConsumerMarkerFormatter.failure("COMMAND", token, nextSequence(), Bukkit.isPrimaryThread(), reason));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRequest(AirdropRequestEvent event) {
		logRequest("REQUEST", event.requestId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpawned(AirdropSpawnedEvent event) {
		logCrate("SPAWNED", event.requestId(), event.airdrop().crateId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLandingAttempt(AirdropLandingAttemptEvent event) {
		logCrate("LANDING_ATTEMPT", event.requestId(), event.airdrop().crateId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLanded(AirdropLandedEvent event) {
		logCrate("LANDED", event.requestId(), event.airdrop().crateId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onOutcome(AirdropOutcomeEvent event) {
		DropOutcome outcome = event.outcome();
		String reason = outcomeReason(outcome);
		log(ConsumerMarkerFormatter.outcome(
				event.requestId(),
				nextSequence(),
				Bukkit.isPrimaryThread(),
				outcome.delivery().name(),
				outcome.payment().name(),
				reason));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPackageRegistryChanged(PackageRegistryChangedEvent event) {
		log(ConsumerMarkerFormatter.nonRequest(
				"PACKAGE_REGISTRY",
				nextSequence(),
				Bukkit.isPrimaryThread(),
				" revision=" + event.revision()
						+ " cause=" + event.cause().name()
						+ " created=" + event.change().created().size()
						+ " updated=" + event.change().updated().size()
						+ " deleted=" + event.change().deleted().size()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRecovered(AirdropRecoveredEvent event) {
		logOptionalRequest(
				"RECOVERED",
				event.airdrop().requestId().orElse(null),
				" crateId=" + event.airdrop().crateId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRetired(AirdropRetiredEvent event) {
		logOptionalRequest(
				"RETIRED",
				event.airdrop().requestId().orElse(null),
				" crateId=" + event.airdrop().crateId()
						+ " reason=" + event.reason().name());
	}

	private static String outcomeReason(DropOutcome outcome) {
		return switch (outcome) {
			case DropOutcome.Rejected rejected -> rejected.rejection().reason().name();
			case DropOutcome.Landed ignored -> "NONE";
			case DropOutcome.Failed failed -> failed.delivery().name();
		};
	}

	private void logCrate(String type, UUID requestId, UUID crateId) {
		log(ConsumerMarkerFormatter.request(type, requestId, nextSequence(),
				Bukkit.isPrimaryThread(), " crateId=" + crateId));
	}

	private void logRequest(String type, UUID requestId) {
		log(ConsumerMarkerFormatter.request(
				type, requestId, nextSequence(), Bukkit.isPrimaryThread()));
	}

	private void logOptionalRequest(String type, UUID requestId, String details) {
		if (requestId == null) {
			log(ConsumerMarkerFormatter.nonRequest(
					type, nextSequence(), Bukkit.isPrimaryThread(), " requestId=NONE" + details));
			return;
		}
		log(ConsumerMarkerFormatter.request(
				type, requestId, nextSequence(), Bukkit.isPrimaryThread(), details));
	}

	private void onPrimaryThread(Runnable action) {
		if (!isEnabled()) {
			return;
		}
		if (Bukkit.isPrimaryThread()) {
			action.run();
			return;
		}
		getServer().getScheduler().runTask(this, action);
	}

	private long nextSequence() {
		return sequence.incrementAndGet();
	}

	private void log(String marker) {
		getLogger().info(marker);
	}
}
