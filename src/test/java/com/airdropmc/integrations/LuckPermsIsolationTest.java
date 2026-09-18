package com.airdropmc.integrations;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.PermissionsHelper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuckPermsIsolationTest {

	private static final String LUCKPERMS_INTERNAL_NAME = "net/luckperms/";
	private static final String LUCKPERMS_BINARY_NAME = "net.luckperms.";

	@Test
	void onlyIsolatedAdapterLinksLuckPermsTypes() throws Exception {
		assertDoesNotLinkLuckPerms(Airdrop.class);
		assertDoesNotLinkLuckPerms(PermissionsHelper.class);
		assertDoesNotLinkLuckPerms(loadClass("com.airdropmc.integrations.OptionalIntegrations"));
		assertLinksLuckPerms(loadClass("com.airdropmc.integrations.LuckPermsIntegration"));
	}

	private static Class<?> loadClass(String name) throws ClassNotFoundException {
		return Class.forName(name, false, LuckPermsIsolationTest.class.getClassLoader());
	}

	private static void assertDoesNotLinkLuckPerms(Class<?> type) throws Exception {
		String constantPool = classBytes(type);
		assertFalse(constantPool.contains(LUCKPERMS_INTERNAL_NAME),
				type.getName() + " must not link LuckPerms internal names");
		assertFalse(constantPool.contains(LUCKPERMS_BINARY_NAME),
				type.getName() + " must not carry LuckPerms binary names");
	}

	private static void assertLinksLuckPerms(Class<?> type) throws Exception {
		String constantPool = classBytes(type);
		assertTrue(constantPool.contains(LUCKPERMS_INTERNAL_NAME),
				type.getName() + " should be the isolated LuckPerms linkage boundary");
	}

	private static String classBytes(Class<?> type) throws Exception {
		String resourceName = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream stream = type.getResourceAsStream(resourceName)) {
			assertNotNull(stream, "Missing compiled class resource " + resourceName);
			return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
		}
	}
}
