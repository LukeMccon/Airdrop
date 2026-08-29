package com.airdropmc.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionCompatibilityPolicyTest {

	@Test
	void obsoleteCannotAffordExceptionIsNotPublished() {
		assertThrows(ClassNotFoundException.class,
				() -> Class.forName("com.airdropmc.exceptions.CannotAffordException"));
	}
}
