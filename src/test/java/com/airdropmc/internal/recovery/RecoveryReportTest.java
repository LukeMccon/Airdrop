package com.airdropmc.internal.recovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryReportTest {

	@Test
	void purgeDiagnosticsAreStableAndBounded() {
		RecoveryReport report = RecoveryReport.empty().withRecoveredCrate();
		for (int index = 0; index < 20; index++) {
			report = report.withPurgedCrate("purge-" + index);
		}

		assertTrue(report.degraded());
		assertEquals(1L, report.recoveredCrates());
		assertEquals(20L, report.purgedCrates());
		assertEquals(16, report.diagnostics().size());
		assertEquals(List.of("purge-4", "purge-5"), report.diagnostics().subList(0, 2));
		assertEquals("purge-19", report.diagnostics().getLast());
	}
}
