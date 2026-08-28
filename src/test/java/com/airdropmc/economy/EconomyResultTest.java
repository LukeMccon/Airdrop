package com.airdropmc.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyResultTest {

	@Test
	void resultsDistinguishSuccessRejectionAndAmbiguity() {
		EconomyResult success = EconomyResult.ok();
		EconomyResult rejected = EconomyResult.rejected("insufficient funds");
		EconomyResult unknown = EconomyResult.unknown("provider failed");

		assertTrue(success.success());
		assertEquals(EconomyResult.Outcome.SUCCESS, success.outcome());
		assertFalse(rejected.success());
		assertEquals(EconomyResult.Outcome.REJECTED, rejected.outcome());
		assertEquals("insufficient funds", rejected.message());
		assertFalse(unknown.success());
		assertEquals(EconomyResult.Outcome.UNKNOWN, unknown.outcome());
	}
}
