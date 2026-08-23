package com.airdropmc.packages;

import com.airdropmc.AirdropCommandNames;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PackageNamePolicy {
	private static final Pattern SUPPORTED_CHARACTERS = Pattern.compile("^[A-Za-z0-9_-]+$");
	private static final Set<String> PERMISSION_IDENTITIES = Set.of("all", "*");

	public enum Rejection {
		MISSING,
		INVALID_CHARACTERS,
		RESERVED
	}

	public record Result(String canonicalName, Rejection rejection) {
		public boolean accepted() {
			return rejection == null;
		}

		public String diagnostic(String originalName) {
			if (accepted()) {
				return "Package name is valid";
			}
			return switch (rejection) {
				case MISSING -> "Package name is required";
				case INVALID_CHARACTERS -> "Package name '" + originalName
						+ "' may only contain letters, numbers, underscores, and dashes";
				case RESERVED -> "Package name '" + originalName + "' is reserved";
			};
		}
	}

	private PackageNamePolicy() {
	}

	public static Result validate(String name) {
		if (name == null || name.isBlank()) {
			return new Result(null, Rejection.MISSING);
		}

		String canonicalName = name.toLowerCase(Locale.ROOT);
		if (PERMISSION_IDENTITIES.contains(canonicalName)
				|| AirdropCommandNames.topLevel().contains(canonicalName)) {
			return new Result(null, Rejection.RESERVED);
		}
		if (!SUPPORTED_CHARACTERS.matcher(name).matches()) {
			return new Result(null, Rejection.INVALID_CHARACTERS);
		}
		return new Result(canonicalName, null);
	}

	public static String requireCanonical(String name) {
		Result result = validate(name);
		if (!result.accepted()) {
			throw new IllegalArgumentException(result.diagnostic(name));
		}
		return result.canonicalName();
	}

	public static String permissionNode(String name) {
		return "airdrop.package." + requireCanonical(name);
	}
}
