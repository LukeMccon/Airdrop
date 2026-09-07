package com.airdropmc.internal.recovery;

import com.airdropmc.api.DropSource;
import com.airdropmc.api.RecoveredDropDescriptor;
import com.airdropmc.api.ResolvedDropSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Schema-v1 boundary for the persistence-safe subset of resolved drop context. */
@ApiStatus.Internal
public final class DropContextPersistence {

	public static final int SCHEMA_VERSION = 1;
	public static final NamespacedKey SCHEMA_KEY = key("airdrop:context_schema");
	private static final NamespacedKey REQUEST_ID_KEY = key("airdrop:context_request_id");
	private static final NamespacedKey SOURCE_KEY = key("airdrop:context_source");
	private static final NamespacedKey PLAYER_ID_KEY = key("airdrop:context_player_id");
	private static final NamespacedKey PACKAGE_NAME_KEY = key("airdrop:context_package_name");
	private static final NamespacedKey PACKAGE_PRICE_KEY = key("airdrop:context_package_price");
	private static final NamespacedKey CHICKEN_COUNT_KEY = key("airdrop:context_chicken_count");
	private static final NamespacedKey FALLING_SPEED_KEY = key("airdrop:context_falling_speed");
	private static final NamespacedKey DROP_HEIGHT_KEY = key("airdrop:context_drop_height");
	private static final NamespacedKey LANDING_EFFECTS_KEY = key("airdrop:context_landing_effects");
	private static final NamespacedKey CONTINUOUS_EFFECTS_KEY = key("airdrop:context_continuous_effects");
	private static final NamespacedKey FLARE_EFFECTS_KEY = key("airdrop:context_flare_effects");
	private static final NamespacedKey SMOKE_ENABLED_KEY = key("airdrop:context_smoke_enabled");
	private static final NamespacedKey SMOKE_HEIGHT_KEY = key("airdrop:context_smoke_height");
	private static final NamespacedKey REQUEST_COOLDOWN_KEY = key("airdrop:context_request_cooldown_ms");
	private static final NamespacedKey MAX_FALLING_KEY = key("airdrop:context_max_falling");
	private static final NamespacedKey MAX_LANDED_KEY = key("airdrop:context_max_landed");
	private static final NamespacedKey LANDED_LIFETIME_KEY = key("airdrop:context_landed_lifetime_ms");
	private static final List<NamespacedKey> KEYS = List.of(
			SCHEMA_KEY, REQUEST_ID_KEY, SOURCE_KEY, PLAYER_ID_KEY, PACKAGE_NAME_KEY,
			PACKAGE_PRICE_KEY, CHICKEN_COUNT_KEY, FALLING_SPEED_KEY, DROP_HEIGHT_KEY,
			LANDING_EFFECTS_KEY, CONTINUOUS_EFFECTS_KEY, FLARE_EFFECTS_KEY,
			SMOKE_ENABLED_KEY, SMOKE_HEIGHT_KEY, REQUEST_COOLDOWN_KEY,
			MAX_FALLING_KEY, MAX_LANDED_KEY, LANDED_LIFETIME_KEY);

	private DropContextPersistence() {
	}

	public sealed interface ReadResult permits Legacy, Complete, Invalid {
	}

	/** Indicates a valid pre-schema paid crate. */
	public record Legacy() implements ReadResult {
	}

	/** Indicates a complete validated schema-v1 descriptor. */
	public record Complete(RecoveredDropDescriptor descriptor) implements ReadResult {
		public Complete {
			Objects.requireNonNull(descriptor, "descriptor");
		}
	}

	/** Indicates partial, unknown, or malformed context metadata. */
	public record Invalid(String diagnostic) implements ReadResult {
		public Invalid {
			if (diagnostic == null || diagnostic.isBlank()) {
				throw new IllegalArgumentException("diagnostic must not be blank");
			}
		}
	}

	/** Writes one complete schema-v1 descriptor, replacing any prior context keys. */
	public static void write(PersistentDataContainer data, RecoveredDropDescriptor descriptor) {
		PersistentDataContainer target = Objects.requireNonNull(data, "data");
		RecoveredDropDescriptor required = Objects.requireNonNull(descriptor, "descriptor");
		clear(target);
		target.set(SCHEMA_KEY, PersistentDataType.INTEGER, SCHEMA_VERSION);
		target.set(REQUEST_ID_KEY, PersistentDataType.STRING, required.requestId().toString());
		target.set(SOURCE_KEY, PersistentDataType.STRING, required.source().name());
		required.playerId().ifPresent(playerId ->
				target.set(PLAYER_ID_KEY, PersistentDataType.STRING, playerId.toString()));
		target.set(PACKAGE_NAME_KEY, PersistentDataType.STRING, required.packageName());
		// Descriptor equality includes the price scale, including negative scales.
		target.set(PACKAGE_PRICE_KEY, PersistentDataType.STRING, required.packagePrice().toString());
		ResolvedDropSettings settings = required.settings();
		target.set(CHICKEN_COUNT_KEY, PersistentDataType.INTEGER, settings.chickenCount());
		target.set(FALLING_SPEED_KEY, PersistentDataType.DOUBLE, settings.fallingSpeed());
		target.set(DROP_HEIGHT_KEY, PersistentDataType.INTEGER, settings.dropHeight());
		writeBoolean(target, LANDING_EFFECTS_KEY, settings.landingEffects());
		writeBoolean(target, CONTINUOUS_EFFECTS_KEY, settings.continuousEffects());
		writeBoolean(target, FLARE_EFFECTS_KEY, settings.flareEffects());
		writeBoolean(target, SMOKE_ENABLED_KEY, settings.smokeEnabled());
		target.set(SMOKE_HEIGHT_KEY, PersistentDataType.INTEGER, settings.smokeHeight());
		target.set(REQUEST_COOLDOWN_KEY, PersistentDataType.LONG, settings.requestCooldown().toMillis());
		target.set(MAX_FALLING_KEY, PersistentDataType.INTEGER, settings.maxFalling());
		target.set(MAX_LANDED_KEY, PersistentDataType.INTEGER, settings.maxLanded());
		target.set(LANDED_LIFETIME_KEY, PersistentDataType.LONG, settings.landedLifetime().toMillis());
	}

	/** Parses absent legacy, complete v1, and invalid context as distinct results. */
	public static ReadResult read(PersistentDataContainer data) {
		PersistentDataContainer source = Objects.requireNonNull(data, "data");
		List<NamespacedKey> contextKeys = source.getKeys().stream()
				.filter(DropContextPersistence::isContextKey)
				.toList();
		if (contextKeys.isEmpty()) {
			return new Legacy();
		}
		if (contextKeys.stream().anyMatch(key -> !KEYS.contains(key))) {
			return new Invalid("unknown-context-key");
		}
		Integer schema = source.get(SCHEMA_KEY, PersistentDataType.INTEGER);
		if (schema == null || schema != SCHEMA_VERSION) {
			return new Invalid(schema == null ? "partial-context" : "unknown-context-schema");
		}
		try {
			String request = required(source, REQUEST_ID_KEY, PersistentDataType.STRING);
			String sourceName = required(source, SOURCE_KEY, PersistentDataType.STRING);
			DropSource dropSource = DropSource.valueOf(sourceName);
			String rawPlayer = source.get(PLAYER_ID_KEY, PersistentDataType.STRING);
			if (contextKeys.contains(PLAYER_ID_KEY) && rawPlayer == null) {
				throw new IllegalArgumentException("Malformed player UUID");
			}
			UUID playerId = rawPlayer == null ? null : UUID.fromString(rawPlayer);
			ResolvedDropSettings settings = new ResolvedDropSettings(
					required(source, CHICKEN_COUNT_KEY, PersistentDataType.INTEGER),
					required(source, FALLING_SPEED_KEY, PersistentDataType.DOUBLE),
					required(source, DROP_HEIGHT_KEY, PersistentDataType.INTEGER),
					readBoolean(source, LANDING_EFFECTS_KEY),
					readBoolean(source, CONTINUOUS_EFFECTS_KEY),
					readBoolean(source, FLARE_EFFECTS_KEY),
					readBoolean(source, SMOKE_ENABLED_KEY),
					required(source, SMOKE_HEIGHT_KEY, PersistentDataType.INTEGER),
					Duration.ofMillis(required(source, REQUEST_COOLDOWN_KEY, PersistentDataType.LONG)),
					required(source, MAX_FALLING_KEY, PersistentDataType.INTEGER),
					required(source, MAX_LANDED_KEY, PersistentDataType.INTEGER),
					Duration.ofMillis(required(source, LANDED_LIFETIME_KEY, PersistentDataType.LONG)));
			return new Complete(new RecoveredDropDescriptor(
					UUID.fromString(request),
					dropSource,
					playerId,
					required(source, PACKAGE_NAME_KEY, PersistentDataType.STRING),
					new BigDecimal(required(source, PACKAGE_PRICE_KEY, PersistentDataType.STRING)),
					settings));
		} catch (RuntimeException malformed) {
			return new Invalid("malformed-context");
		}
	}

	/** @return whether any schema-owned context marker is present */
	public static boolean hasAny(PersistentDataContainer data) {
		PersistentDataContainer source = Objects.requireNonNull(data, "data");
		return source.getKeys().stream().anyMatch(DropContextPersistence::isContextKey);
	}

	/** Removes every schema-owned context key. */
	public static void clear(PersistentDataContainer data) {
		PersistentDataContainer target = Objects.requireNonNull(data, "data");
		target.getKeys().stream()
				.filter(DropContextPersistence::isContextKey)
				.toList()
				.forEach(target::remove);
	}

	private static boolean isContextKey(NamespacedKey key) {
		return "airdrop".equals(key.getNamespace()) && key.getKey().startsWith("context_");
	}

	private static void writeBoolean(
			PersistentDataContainer data, NamespacedKey key, boolean value) {
		data.set(key, PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0);
	}

	private static boolean readBoolean(PersistentDataContainer data, NamespacedKey key) {
		Byte value = required(data, key, PersistentDataType.BYTE);
		if (value != 0 && value != 1) {
			throw new IllegalArgumentException("Invalid boolean");
		}
		return value == 1;
	}

	private static <P, C> C required(
			PersistentDataContainer data,
			NamespacedKey key,
			PersistentDataType<P, C> type) {
		C value = data.get(key, type);
		return Objects.requireNonNull(value, key + " is required");
	}

	private static NamespacedKey key(String value) {
		return Objects.requireNonNull(NamespacedKey.fromString(value));
	}
}
