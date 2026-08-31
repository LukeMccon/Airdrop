package com.airdropmc.api;

/** Identifies the successful operation that published a package registry snapshot. */
public enum PackageRegistryCause {
	/** Initial plugin configuration publication. */
	STARTUP,
	/** Full configuration reload publication. */
	RELOAD,
	/** Package creation publication. */
	CREATE,
	/** Package inventory update publication. */
	UPDATE,
	/** Package deletion publication. */
	DELETE
}
