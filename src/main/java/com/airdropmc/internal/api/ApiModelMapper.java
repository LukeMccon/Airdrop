package com.airdropmc.internal.api;

import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.ResolvedDropSettings;
import com.airdropmc.config.DropOptions;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.packages.Package;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.Objects;

/** Converts mutable implementation values into supported detached snapshots. */
@ApiStatus.Internal
public final class ApiModelMapper {

	private ApiModelMapper() {
	}

	public static AirdropPackage packageSnapshot(Package pkg) {
		Package requiredPackage = Objects.requireNonNull(pkg, "pkg");
		return new AirdropPackage(
				requiredPackage.getName(),
				BigDecimal.valueOf(requiredPackage.getPrice()),
				requiredPackage.getItems().stream().filter(Objects::nonNull).toList());
	}

	public static ResolvedDropSettings resolveSettings(
			DropOptions options, DropLimitSettings limitSettings) {
		return Objects.requireNonNull(options, "options").resolve(
				Objects.requireNonNull(limitSettings, "limitSettings"));
	}
}
