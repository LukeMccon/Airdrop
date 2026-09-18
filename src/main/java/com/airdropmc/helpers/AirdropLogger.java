package com.airdropmc.helpers;

import com.airdropmc.Airdrop;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.EconomyState;
import com.airdropmc.api.PackageRegistryCause;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.internal.diagnostics.AirdropDiagnostics;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized logging helpers for Airdrop.
 */
public final class AirdropLogger {
	public enum Publication {
		CONFIGURATION,
		PACKAGE_REGISTRY
	}

	public enum AdmissionDecision {
		ACCEPTED,
		REJECTED
	}

	public enum RequestPhase {
		CREATED,
		RESOLVED,
		PAYMENT_PENDING,
		SPAWNED,
		LANDING_ATTEMPT,
		LANDED,
		TERMINAL
	}

	private AirdropLogger() {
		// Utility class
	}

	private static Logger getLogger() {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin != null) {
			return plugin.getLogger();
		}
		return Logger.getLogger(Airdrop.PLUGIN_NAME);
	}

	public static void info(String message) {
		getLogger().info(message);
	}

	public static void warning(String message) {
		getLogger().warning(message);
	}

	public static void severe(String message) {
		getLogger().severe(message);
	}

	public static void log(Level level, String message, Throwable throwable) {
		getLogger().log(level, message, throwable);
	}

	public static void debugReadiness(ReadinessState from, ReadinessState to) {
		debugLine("readiness from=" + Objects.requireNonNull(from, "from")
				+ " to=" + Objects.requireNonNull(to, "to"));
	}

	public static void debugEconomy(EconomyState state, String providerName) {
		EconomyState requiredState = Objects.requireNonNull(state, "state");
		String provider = requiredState == EconomyState.ACTIVE
				? AirdropDiagnostics.sanitizeLabel(providerName)
				: "none";
		debugLine("economy state=" + requiredState + " provider=" + provider);
	}

	public static void debugPublication(
			Publication publication,
			PackageRegistryCause cause,
			long revision,
			int count) {
		if (revision < 0L || count < 0) {
			throw new IllegalArgumentException("publication revision and count must be non-negative");
		}
		debugLine("publication kind=" + Objects.requireNonNull(publication, "publication")
				+ " cause=" + Objects.requireNonNull(cause, "cause")
				+ " revision=" + revision
				+ " count=" + count);
	}

	public static void debugAdmission(
			UUID requestId,
			AdmissionDecision decision,
			DropRejectionReason reason) {
		AdmissionDecision requiredDecision = Objects.requireNonNull(decision, "decision");
		if ((requiredDecision == AdmissionDecision.ACCEPTED) != (reason == null)) {
			throw new IllegalArgumentException(
					"accepted admission has no reason; rejected admission requires one");
		}
		String suffix = reason == null ? "" : " reason=" + reason;
		debugLine("admission request=" + Objects.requireNonNull(requestId, "requestId")
				+ " decision=" + requiredDecision + suffix);
	}

	public static void debugRequest(UUID requestId, RequestPhase phase) {
		debugRequest(requestId, phase, (Enum<?>) null);
	}

	public static void debugRequest(
			UUID requestId, RequestPhase phase, DeliveryStatus reason) {
		debugRequest(requestId, phase, (Enum<?>) reason);
	}

	public static void debugRequest(
			UUID requestId, RequestPhase phase, DropRejectionReason reason) {
		debugRequest(requestId, phase, (Enum<?>) reason);
	}

	private static void debugRequest(UUID requestId, RequestPhase phase, Enum<?> reason) {
		String suffix = reason == null ? "" : " reason=" + reason.name();
		debugLine("request request=" + Objects.requireNonNull(requestId, "requestId")
				+ " phase=" + Objects.requireNonNull(phase, "phase") + suffix);
	}

	private static void debugLine(String message) {
		if (!ConfigKeys.isDebugLoggingEnabled()) {
			return;
		}
		getLogger().info("[DEBUG] " + message);
	}
}
