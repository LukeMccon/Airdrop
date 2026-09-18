package com.airdropmc.exceptions;

import com.airdropmc.packages.PackageMaterializationException;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainExceptionMessageTest {

	@Test
	void packageRejectionsHaveStableMessages() {
		assertAll(
				() -> assertEquals("Package already exists: starter",
						new DuplicatePackageException("starter").getMessage()),
				() -> assertEquals("Insufficient permissions for package: starter",
						new InsufficientPermissionsException("starter").getMessage()),
				() -> assertEquals("Package not found: starter",
						new PackageNotFoundException("starter").getMessage()));
	}

	@Test
	void skyRejectionHasStableMessage() {
		SkyNotClearException exception = new SkyNotClearException(new Location(null, 10, 64, -5));

		assertEquals("Sky is not clear above the requested drop location", exception.getMessage());
	}

	@Test
	void economyAvailabilityRejectionsHaveStableReasonsAndMessages() {
		EconomyUnavailableException disabled =
				new EconomyUnavailableException(EconomyUnavailableException.Reason.DISABLED);
		EconomyUnavailableException missingProvider =
				new EconomyUnavailableException(EconomyUnavailableException.Reason.NO_PROVIDER);

		assertAll(
				() -> assertEquals(EconomyUnavailableException.Reason.DISABLED, disabled.getReason()),
				() -> assertEquals("Economy is disabled", disabled.getMessage()),
				() -> assertEquals(EconomyUnavailableException.Reason.NO_PROVIDER,
						missingProvider.getReason()),
				() -> assertEquals("No economy provider is available", missingProvider.getMessage()));
	}

	@Test
	void economyAvailabilityRejectionRequiresAReason() {
		NullPointerException failure = assertThrows(NullPointerException.class,
				() -> new EconomyUnavailableException(null));

		assertEquals("reason", failure.getMessage());
	}

	@ParameterizedTest
	@EnumSource(DropLimitException.Reason.class)
	void dropLimitRejectionsIdentifyTheirReason(DropLimitException.Reason reason) {
		assertEquals("Drop rejected: " + reason, new DropLimitException(reason).getMessage());
	}

	@Test
	void packageMaterializationPreservesSuppliedDiagnostics() {
		IllegalArgumentException cause = new IllegalArgumentException("invalid value");
		PackageMaterializationException exception =
				new PackageMaterializationException("Could not materialize package", cause);

		assertAll(
				() -> assertEquals("Could not materialize package", exception.getMessage()),
				() -> assertSame(cause, exception.getCause()));
	}
}
