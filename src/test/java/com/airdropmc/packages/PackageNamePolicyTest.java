package com.airdropmc.packages;

import com.airdropmc.AirdropCommandNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageNamePolicyTest {

	private Locale defaultLocale;

	@BeforeEach
	void rememberLocale() {
		defaultLocale = Locale.getDefault();
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(defaultLocale);
	}

	@Test
	void acceptsSupportedCharactersAndCanonicalizesWithLocaleRoot() {
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));

		PackageNamePolicy.Result result = PackageNamePolicy.validate("TITLE_2-Test");

		assertTrue(result.accepted());
		assertEquals("title_2-test", result.canonicalName());
		assertEquals("airdrop.package.title_2-test",
				PackageNamePolicy.permissionNode("TITLE_2-Test"));
	}

	@Test
	void rejectsMissingAndUnsupportedNames() {
		for (String name : Arrays.asList(null, "", " ", "test.items", "two words")) {
			assertFalse(PackageNamePolicy.validate(name).accepted(), String.valueOf(name));
			assertThrows(IllegalArgumentException.class,
					() -> PackageNamePolicy.requireCanonical(name), String.valueOf(name));
		}
	}

	@Test
	void rejectsEveryReservedIdentityWithoutCaseDifferences() {
		for (String name : List.of(
				"all", "ALL", "*", "package", "PACKAGES", "Version", "reLOAD", "create", "DELETE")) {
			PackageNamePolicy.Result result = PackageNamePolicy.validate(name);

			assertFalse(result.accepted(), name);
			assertEquals(PackageNamePolicy.Rejection.RESERVED, result.rejection(), name);
		}
		assertTrue(PackageNamePolicy.isPackageSubcommandIdentity("CrEaTe"));
		assertTrue(PackageNamePolicy.isPackageSubcommandIdentity("DELETE"));
		assertFalse(PackageNamePolicy.isPackageSubcommandIdentity("reload"));
	}

	@Test
	void commandNamesAndReservedPackageNamesStayAligned() {
		assertEquals(Set.of("package", "packages", "version", "reload"),
				AirdropCommandNames.topLevel());
		assertTrue(AirdropCommandNames.topLevel().stream()
				.noneMatch(name -> PackageNamePolicy.validate(name).accepted()));
	}
}
