package com.airdropmc;

import java.util.List;
import java.util.Set;

public final class AirdropCommandNames {
	public static final String PACKAGE = "package";
	public static final String PACKAGES = "packages";
	public static final String VERSION = "version";
	public static final String RELOAD = "reload";

	private static final Set<String> TOP_LEVEL = Set.of(PACKAGE, PACKAGES, VERSION, RELOAD);
	private static final List<String> NON_ADMIN = List.of(PACKAGE, PACKAGES, VERSION);
	private static final List<String> ADMIN = List.of(PACKAGE, PACKAGES, VERSION, RELOAD);

	private AirdropCommandNames() {
	}

	public static Set<String> topLevel() {
		return TOP_LEVEL;
	}

	public static List<String> visibleTo(boolean admin) {
		return admin ? ADMIN : NON_ADMIN;
	}
}
