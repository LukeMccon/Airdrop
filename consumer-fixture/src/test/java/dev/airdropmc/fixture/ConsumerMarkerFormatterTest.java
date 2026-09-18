package dev.airdropmc.fixture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumerMarkerFormatterTest {
	private static final UUID REQUEST_ID = UUID.fromString("018f7c42-7d3c-7b0e-a661-68b02e1e9f8b");

	@Test
	void readyMarkerHasNoRequestIdentity() {
		assertEquals(
				"AIRDR_CONSUMER_READY sequence=1 primaryThread=true",
				ConsumerMarkerFormatter.ready(1L, true));
	}

	@Test
	void lifecycleMarkerCarriesCorrelatedIdentitySequenceAndThread() {
		assertEquals(
				"AIRDR_CONSUMER_LANDING_ATTEMPT requestId=" + REQUEST_ID
						+ " sequence=4 primaryThread=true",
				ConsumerMarkerFormatter.request("LANDING_ATTEMPT", REQUEST_ID, 4L, true));
	}

	@Test
	void rejectedOutcomeCarriesDeliveryPaymentAndReason() {
		assertEquals(
				"AIRDR_CONSUMER_OUTCOME requestId=" + REQUEST_ID
						+ " sequence=6 primaryThread=true delivery=REJECTED"
						+ " payment=REJECTED reason=ECONOMY_PROVIDER_UNAVAILABLE",
				ConsumerMarkerFormatter.outcome(
						REQUEST_ID,
						6L,
						true,
						"REJECTED",
						"REJECTED",
						"ECONOMY_PROVIDER_UNAVAILABLE"));
	}

	@Test
	void snapshotPreservesItemOrderAndPlainPrice() {
		assertEquals("AIRDR_CONSUMER_SNAPSHOT sequence=7 primaryThread=true token=snapshot_1"
						+ " status=OK name=premium revision=3 price=10.25 items=BREAD:2,IRON_HELMET:1",
				ConsumerMarkerFormatter.snapshot("snapshot_1", 7, true, 3, "premium", "10.25",
						List.of("BREAD:2", "IRON_HELMET:1")));
		assertEquals("AIRDR_CONSUMER_SNAPSHOT sequence=8 primaryThread=true token=empty"
						+ " status=OK name=empty revision=3 price=0.0 items=NONE",
				ConsumerMarkerFormatter.snapshot("empty", 8, true, 3, "empty", "0.0", List.of()));
	}

	@Test
	void handleCarriesDescriptorAndPlayerIdentities() {
		UUID playerId = UUID.fromString("118f7c42-7d3c-7b0e-a661-68b02e1e9f8b");
		assertEquals("AIRDR_CONSUMER_HANDLE requestId=" + REQUEST_ID
						+ " sequence=7 primaryThread=true token=direct_1 status=OK descriptorId=" + REQUEST_ID
						+ " playerId=" + playerId + " name=premium source=PLAYER",
				ConsumerMarkerFormatter.handle("direct_1", REQUEST_ID, 7, true,
						REQUEST_ID, playerId, "premium", "PLAYER"));
	}

	@Test
	void stageMarkersPreserveActualCallbackThreadAndResultIdentity() {
		UUID resultId = UUID.fromString("218f7c42-7d3c-7b0e-a661-68b02e1e9f8b");
		for (String type : List.of("HANDLE_SPAWN", "HANDLE_OUTCOME")) {
			assertEquals("AIRDR_CONSUMER_" + type + " requestId=" + REQUEST_ID
							+ " sequence=8 primaryThread=false token=direct_1 status=OK resultRequestId="
							+ resultId + " payment=REFUNDED",
					ConsumerMarkerFormatter.handleResult(type, "direct_1", REQUEST_ID, resultId,
							8, false, " payment=REFUNDED"));
		}
	}

	@Test
	void failuresRemainTokenCorrelated() {
		assertEquals("AIRDR_CONSUMER_COMMAND sequence=9 primaryThread=true"
						+ " token=missing status=ERROR reason=PACKAGE_NOT_FOUND",
				ConsumerMarkerFormatter.failure("COMMAND", "missing", 9, true, "PACKAGE_NOT_FOUND"));
	}

	@Test
	void rejectsAmbiguousTokensInvalidSequencesAndInvalidItems() {
		assertThrows(IllegalArgumentException.class,
				() -> ConsumerMarkerFormatter.failure("COMMAND", "two tokens", 1, true, "ERROR"));
		assertThrows(IllegalArgumentException.class, () -> ConsumerMarkerFormatter.ready(0, true));
		assertThrows(IllegalArgumentException.class,
				() -> ConsumerMarkerFormatter.snapshot("t", 1, true, -1, "p", "0", List.of()));
		for (String item : List.of("BREAD:0", "bread:1", "BREAD:1,DIAMOND:2")) {
			assertThrows(IllegalArgumentException.class,
					() -> ConsumerMarkerFormatter.snapshot("t", 1, true, 0, "p", "0", List.of(item)));
		}
	}

	@Test
	void landedOutcomeUsesExplicitNoneReason() {
		assertEquals(
				"AIRDR_CONSUMER_OUTCOME requestId=" + REQUEST_ID
						+ " sequence=6 primaryThread=true delivery=LANDED"
						+ " payment=NOT_APPLICABLE reason=NONE",
				ConsumerMarkerFormatter.outcome(
						REQUEST_ID, 6L, true, "LANDED", "NOT_APPLICABLE", "NONE"));
	}
}
