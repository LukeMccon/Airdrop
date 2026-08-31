package dev.airdropmc.fixture;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
	void landedOutcomeUsesExplicitNoneReason() {
		assertEquals(
				"AIRDR_CONSUMER_OUTCOME requestId=" + REQUEST_ID
						+ " sequence=6 primaryThread=true delivery=LANDED"
						+ " payment=NOT_APPLICABLE reason=NONE",
				ConsumerMarkerFormatter.outcome(
						REQUEST_ID, 6L, true, "LANDED", "NOT_APPLICABLE", "NONE"));
	}
}
