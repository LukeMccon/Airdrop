package com.airdropmc.api;

/** Identifies the successful operation that published a package registry snapshot. */
public enum PackageRegistryCause {
	STARTUP,
	RELOAD,
	CREATE,
	UPDATE,
	DELETE
}
