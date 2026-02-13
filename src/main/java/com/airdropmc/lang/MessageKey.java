package com.airdropmc.lang;

public enum MessageKey {
	PREFIX("prefix", "{primary}[{text}Airdrop{primary}]"),

	COMMANDS_PLAYER_ONLY("commands.player-only", "Must be a player to use this command"),
	COMMANDS_PACKAGES_CONSOLE_ONLY("commands.packages-console-only",
			"Uses inventory GUI, not available to the console"),
	COMMANDS_PACKAGE_SPECIFY("commands.package-specify", "Must specify a package name"),
	COMMANDS_PACKAGE_EXAMPLE("commands.package-example", "Example: /airdrop starter"),

	PACKAGES_INFO("packages.info",
			"{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{text}  Package: {accent}{name}\n{text}  Price: {accent}${price}\n{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{info}"),
	PACKAGES_DELETED("packages.deleted", "{accent}{name}{primary} was successfully deleted"),
	PACKAGES_SAVED("packages.saved", "Package {accent}{name}{primary} was saved successfully"),
	PACKAGES_CREATED("packages.created", "Package {accent}{name}{primary} was created successfully"),
	PACKAGES_EDIT_CANCELED("packages.edit-canceled", "Package edit was canceled"),
	PACKAGES_CREATE_CANCELED("packages.create-canceled", "Package creation was canceled"),
	PACKAGES_DELETE_SPECIFY("packages.delete-specify", "Need to specify a package name to delete"),
	PACKAGES_DELETE_PERMISSION("packages.delete-permission",
			"Must be an admin with airdrop.admin permissions or a server operator to delete a package"),
	PACKAGES_CREATE_ARGS("packages.create-args", "Package create command requires a name and price"),
		PACKAGES_CREATE_USAGE("packages.create-usage", "Usage: /airdrop package create <name> <price>"),
		PACKAGES_CREATE_EXAMPLE("packages.create-example", "Example: /airdrop package create starter 12.0"),
		PACKAGES_NAME_REQUIRED("packages.name-required", "You must provide a name for the package"),
		PACKAGES_PRICE_REQUIRED("packages.price-required", "You must provide the package price as a double"),
		PACKAGES_PRICE_INVALID("packages.price-invalid", "Package price must be a finite non-negative number"),
	PACKAGES_ITEM_LIMIT("packages.item-limit",
			"Package can only hold {max} item stacks (barrel capacity). Remove extra items before saving."),

		ERROR_CANNOT_AFFORD("errors.cannot-afford",
				"{player} cannot afford package price of {accent}{price}{error}"),
		ERROR_INSUFFICIENT_PERMISSIONS("errors.insufficient-permissions",
				"You lack permission to drop that package (requires {accent}airdrop.package.{package}{error})"),
	ERROR_SKY_NOT_CLEAR("errors.sky-not-clear", "Sky must be clear above your location to call an airdrop"),
	ERROR_PACKAGE_NOT_FOUND("errors.package-not-found",
			"Package '{name}' not found. Use {warning}/airdrop packages{error} to see available packages"),
	ERROR_PACKAGE_EXISTS("errors.package-exists", "A package named '{name}' already exists"),
	ERROR_PACKAGE_DELETE_NOT_FOUND("errors.package-delete-not-found",
			"Unable to delete package: {error-detail}{name}{error} not found"),

	DROP_CHARGED("drop.charged", "{accent}${amount}{primary} has been taken from your account"),

	GUI_SAVE("gui.save", "Save"),
	GUI_CANCEL("gui.cancel", "Cancel"),
	GUI_BACK("gui.back", "Back"),
	GUI_PACKAGES_TITLE("gui.packages-title", "Packages"),
	GUI_PACKAGE_PRICE("gui.package-price", "${price}"),

	ADMIN_PERMISSION_REQUIRED("admin.permission-required", "You must have airdrop.admin permission to do this"),
	ADMIN_PACKAGE_SAVE_REQUIRED("admin.package-save-required", "Must be admin to save package changes"),

	SYSTEM_CONFIG_LOADED("system.config-loaded", "Config loaded successfully"),
	SYSTEM_ECONOMY_MISSING("system.economy-missing",
			"Disabling due to missing economy provider (Treasury or Vault)"),
	SYSTEM_RELOAD_SUCCESS("system.reload-success", "Configuration and language reloaded"),
	SYSTEM_DEBUG_STATUS("system.debug-status", "Debug logging is currently {accent}{status}{primary}"),
	SYSTEM_DEBUG_UPDATED("system.debug-updated", "Debug logging {accent}{status}{primary}"),
	SYSTEM_DEBUG_USAGE("system.debug-usage", "Usage: /airdrop debug [on|off|toggle]"),
	SYSTEM_PACKAGE_PRICE_MISSING("system.package-price-missing", "Could not find price for package: {name}"),
	SYSTEM_PACKAGE_PRICE_INVALID("system.package-price-invalid",
			"Invalid package price for {name}: {price}. Falling back to 0.0"),
	SYSTEM_VERSION_INFO("system.version-info",
			"{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{text}  Airdrop {accent}v{version}\n{text}  Paper API {accent}{api_version}\n{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{text}  Found a bug? Report it here:\n{text}  {accent}airdropmc.com/report-bug\n{text}  Have an idea? Request a feature:\n{text}  {accent}airdropmc.com/request-feature");

	private final String key;
	private final String defaultValue;

	MessageKey(String key, String defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
	}

	public String getKey() {
		return key;
	}

	public String getDefault() {
		return defaultValue;
	}
}
