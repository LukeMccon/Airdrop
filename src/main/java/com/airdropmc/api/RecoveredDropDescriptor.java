package com.airdropmc.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable request data safe to persist for a landed crate without retaining
 * original package items or fabricating a complete resolved request context.
 */
public final class RecoveredDropDescriptor {
	private static final Pattern PACKAGE_NAME = Pattern.compile("^[A-Za-z0-9_-]+$");

	private final UUID requestId;
	private final DropSource source;
	private final UUID playerId;
	private final String packageName;
	private final BigDecimal packagePrice;
	private final ResolvedDropSettings settings;

	/**
	 * Creates a validated recovery descriptor.
	 *
	 * @param requestId original request identity
	 * @param source original request source
	 * @param playerId requesting player for a player source, otherwise {@code null}
	 * @param packageName original configured package name
	 * @param packagePrice exact original package price
	 * @param settings resolved primitive settings captured for the request
	 */
	public RecoveredDropDescriptor(
			UUID requestId,
			DropSource source,
			UUID playerId,
			String packageName,
			BigDecimal packagePrice,
			ResolvedDropSettings settings) {
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.source = Objects.requireNonNull(source, "source");
		if (source == DropSource.PLAYER && playerId == null) {
			throw new IllegalArgumentException("PLAYER recovery requires a playerId");
		}
		if (source == DropSource.SYSTEM && playerId != null) {
			throw new IllegalArgumentException("SYSTEM recovery cannot have a playerId");
		}
		this.playerId = playerId;
		this.packageName = requireName(packageName);
		this.packagePrice = Objects.requireNonNull(packagePrice, "packagePrice");
		if (packagePrice.signum() < 0) {
			throw new IllegalArgumentException("packagePrice must be non-negative");
		}
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	/**
	 * Creates the persistence-safe subset of a resolved request context.
	 *
	 * @param context complete resolved request context
	 * @return persistence-safe recovery descriptor
	 */
	public static RecoveredDropDescriptor from(ResolvedDropContext context) {
		ResolvedDropContext required = Objects.requireNonNull(context, "context");
		DropRequestDescriptor descriptor = required.descriptor();
		return new RecoveredDropDescriptor(
				descriptor.requestId(),
				descriptor.source(),
				descriptor.playerId().orElse(null),
				required.airdropPackage().name(),
				required.airdropPackage().price(),
				required.settings());
	}

	/**
	 * Returns the original request identity.
	 *
	 * @return original request identity
	 */
	public UUID requestId() {
		return requestId;
	}

	/**
	 * Returns the original request source.
	 *
	 * @return original request source
	 */
	public DropSource source() {
		return source;
	}

	/**
	 * Returns the requesting player identity when the source was a player.
	 *
	 * @return requesting player identity when the source was a player
	 */
	public Optional<UUID> playerId() {
		return Optional.ofNullable(playerId);
	}

	/**
	 * Returns the original configured package name.
	 *
	 * @return original configured package name
	 */
	public String packageName() {
		return packageName;
	}

	/**
	 * Returns the exact original package price.
	 *
	 * @return exact original package price
	 */
	public BigDecimal packagePrice() {
		return packagePrice;
	}

	/**
	 * Returns the detached resolved primitive settings.
	 *
	 * @return detached resolved primitive settings
	 */
	public ResolvedDropSettings settings() {
		return settings;
	}

	@Override
	public boolean equals(Object candidate) {
		if (this == candidate) {
			return true;
		}
		if (!(candidate instanceof RecoveredDropDescriptor other)) {
			return false;
		}
		return requestId.equals(other.requestId)
				&& source == other.source
				&& Objects.equals(playerId, other.playerId)
				&& packageName.equals(other.packageName)
				&& packagePrice.equals(other.packagePrice)
				&& settings.equals(other.settings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId, source, playerId, packageName, packagePrice, settings);
	}

	private static String requireName(String value) {
		String required = Objects.requireNonNull(value, "packageName");
		if (required.isBlank()) {
			throw new IllegalArgumentException("packageName must not be blank");
		}
		if (!PACKAGE_NAME.matcher(required).matches()) {
			throw new IllegalArgumentException(
					"packageName may only contain letters, numbers, underscores, and dashes");
		}
		return required;
	}
}
