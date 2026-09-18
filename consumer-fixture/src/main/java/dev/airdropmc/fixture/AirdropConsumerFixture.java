package dev.airdropmc.fixture;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.event.AirdropLandedEvent;
import com.airdropmc.api.event.AirdropLandingAttemptEvent;
import com.airdropmc.api.event.AirdropOutcomeEvent;
import com.airdropmc.api.event.AirdropRecoveredEvent;
import com.airdropmc.api.event.AirdropRequestEvent;
import com.airdropmc.api.event.AirdropRetiredEvent;
import com.airdropmc.api.event.AirdropSpawnedEvent;
import com.airdropmc.api.event.PackageRegistryChangedEvent;
import org.bukkit.Bukkit;
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

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRequest(AirdropRequestEvent event) {
		logRequest("REQUEST", event.requestId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpawned(AirdropSpawnedEvent event) {
		logRequest("SPAWNED", event.requestId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLandingAttempt(AirdropLandingAttemptEvent event) {
		logRequest("LANDING_ATTEMPT", event.requestId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLanded(AirdropLandedEvent event) {
		logRequest("LANDED", event.requestId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onOutcome(AirdropOutcomeEvent event) {
		DropOutcome outcome = event.outcome();
		String reason = switch (outcome) {
			case DropOutcome.Rejected rejected -> rejected.rejection().reason().name();
			case DropOutcome.Landed ignored -> "NONE";
			case DropOutcome.Failed failed -> failed.delivery().name();
		};
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
