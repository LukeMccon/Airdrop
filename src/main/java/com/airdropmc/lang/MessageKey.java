package com.airdropmc.lang;

public enum MessageKey {
	PREFIX("prefix", "{primary}[{text}Airdrop{primary}]"),

	COMMANDS_PLAYER_ONLY("commands.player-only", "Must be a player to use this command"),
	COMMANDS_PACKAGES_CONSOLE_ONLY("commands.packages-console-only",
			"Uses inventory GUI, not available to the console"),
	COMMANDS_PACKAGE_SPECIFY("commands.package-specify", "Must specify a package name"),
	COMMANDS_PACKAGE_EXAMPLE("commands.package-example", "Example: /airdrop starter"),
	COMMANDS_HELP_HEADER("commands.help.header", "{primary}Airdrop commands"),
	COMMANDS_HELP_DROP("commands.help.drop", "{text}/airdrop <package>{primary} — request a package; press Tab to see packages you can use"),
	COMMANDS_HELP_PACKAGE("commands.help.package", "{text}/airdrop package <name>{primary} — inspect a package"),
	COMMANDS_HELP_VERSION("commands.help.version", "{text}/airdrop version{primary} — show compatibility information"),
	COMMANDS_HELP_ADMIN_CREATE("commands.help.admin-create", "{text}/airdrop package create <name> <price>{primary} — create a package"),
	COMMANDS_HELP_ADMIN_DELETE("commands.help.admin-delete", "{text}/airdrop package delete <name>{primary} — delete a package"),
	COMMANDS_HELP_ADMIN_PACKAGES("commands.help.admin-packages", "{text}/airdrop packages{primary} — open package management"),
	COMMANDS_HELP_ADMIN_RELOAD("commands.help.admin-reload", "{text}/airdrop reload{primary} — reload configuration"),
	COMMANDS_HELP_ADMIN_STATUS("commands.help.admin-status", "{text}/airdrop status{primary} — show operational status"),

	PACKAGES_INFO("packages.info",
			"{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{text}  Package: {accent}{name}\n{text}  Price: {accent}${price}\n{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{info}"),
	PACKAGES_DELETED("packages.deleted", "{accent}{name}{primary} was successfully deleted"),
	PACKAGES_SAVED("packages.saved", "Package {accent}{name}{primary} was saved successfully"),
	PACKAGES_CREATED("packages.created", "Package {accent}{name}{primary} was created successfully"),
	PACKAGES_EDIT_CANCELED("packages.edit-canceled", "Package edit was canceled"),
	PACKAGES_CREATE_CANCELED("packages.create-canceled", "Package creation was canceled"),
	PACKAGES_DELETE_SPECIFY("packages.delete-specify", "Need to specify a package name to delete"),
	PACKAGES_DELETE_PERMISSION("packages.delete-permission",
			"Must be an admin with {accent}airdrop.admin{error} permissions or a server operator to delete a package"),
	PACKAGES_CREATE_ARGS("packages.create-args", "Package create command requires a name and price"),
	PACKAGES_CREATE_USAGE("packages.create-usage", "Usage: /airdrop package create <name> <price>"),
	PACKAGES_CREATE_EXAMPLE("packages.create-example", "Example: /airdrop package create starter 12.0"),
	PACKAGES_NAME_REQUIRED("packages.name-required", "You must provide a name for the package"),
		PACKAGES_NAME_INVALID("packages.name-invalid",
				"Package names may only contain letters, numbers, underscores, and dashes"),
		PACKAGES_NAME_RESERVED("packages.name-reserved",
				"Package names cannot use reserved names: all, *, package, packages, version, status, reload"),
		PACKAGES_NAME_SUBCOMMAND_RESERVED("packages.name-subcommand-reserved",
				"The package names create and delete are reserved because they are package subcommands"),
	PACKAGES_PRICE_REQUIRED("packages.price-required", "You must provide the package price as a double"),
	PACKAGES_PRICE_INVALID("packages.price-invalid", "Package price must be a finite non-negative number"),
	PACKAGES_CREATE_OPEN_ERROR("packages.create-open-error", "Unable to open package editor right now"),
	PACKAGES_ITEM_LIMIT("packages.item-limit",
			"Package can only hold {accent}{max}{error} item stacks (barrel capacity). Remove extra items before saving."),

	ERROR_CANNOT_AFFORD("errors.cannot-afford",
			"{accent}{player}{error} cannot afford package price of {accent}{price}{error}"),
	ERROR_INSUFFICIENT_PERMISSIONS("errors.insufficient-permissions",
			"You lack permission to drop that package (requires {accent}{permission}{error})"),
	ERROR_ECONOMY_UNAVAILABLE("errors.economy-unavailable",
			"Priced packages are unavailable because economy is disabled or no provider is available. Contact a server administrator."),
	ERROR_PLUGIN_NOT_READY("errors.plugin-not-ready",
			"Airdrop is still starting or is shutting down. Try again shortly."),
	ERROR_RELOAD_UNAVAILABLE("errors.reload-unavailable", "Reload unavailable while plugin is shutting down"),
	ERROR_STATUS_UNAVAILABLE("errors.status-unavailable", "Airdrop status is unavailable"),
	ERROR_RELOAD_FAILED_RETAINED("errors.reload-failed-retained",
			"Reload failed. The previous configuration remains active. Check the server log."),
	ERROR_PACKAGES_RELOAD_FAILED("errors.packages-reload-failed",
			"Reload failed because packages configuration is unavailable"),
	ERROR_SKY_NOT_CLEAR("errors.sky-not-clear", "Sky must be clear above your location to call an airdrop"),
	ERROR_PACKAGE_NOT_FOUND("errors.package-not-found",
			"Package {accent}{name}{error} not found. Type {warning}/airdrop{error} and press Tab to see packages you can use, or inspect one with {warning}/airdrop package <name>"),
	ERROR_PACKAGE_EXISTS("errors.package-exists", "A package named {accent}{name}{error} already exists"),
	ERROR_PACKAGE_DELETE_NOT_FOUND("errors.package-delete-not-found",
			"Unable to delete package: {error-detail}{name}{error} not found"),
	ERROR_PACKAGE_SAVE_FAILED(
			"errors.package-save-failed",
			"Could not save package changes. No changes were made. Check the server log."),
	ERROR_DROP_REQUEST_PENDING("errors.drop-request-pending", "A drop request is already being processed"),
	ERROR_DROP_COOLDOWN("errors.drop-cooldown", "Wait {seconds} seconds before requesting another airdrop"),
	ERROR_DROP_FALLING_LIMIT("errors.drop-falling-limit",
			"Too many airdrops are currently falling; try again shortly"),
	ERROR_DROP_LANDED_LIMIT("errors.drop-landed-limit",
			"Too many landed airdrops are active; try again later"),
	ERROR_DROP_LOCATION_RESERVED("errors.drop-location-reserved",
			"An airdrop already owns this landing location"),
	ERROR_DROP_SHUTTING_DOWN("errors.drop-shutting-down",
			"Airdrops are unavailable while the plugin is shutting down"),

	DROP_CHARGED("drop.charged", "{accent}${amount}{primary} has been taken from your account"),
	DROP_FAILED("drop.failed", "Airdrop failed; no crate was created"),
	DROP_REFUNDED("drop.refunded", "Airdrop failed; your payment was refunded"),

	GUI_SAVE("gui.save", "Save"),
	GUI_CANCEL("gui.cancel", "Cancel"),
	GUI_BACK("gui.back", "Back"),
	GUI_HELP("gui.help", "Help"),
	GUI_EDITOR_HELP_ADD_STACK("gui.editor-help-add-stack", "Left-click inventory: add stack"),
	GUI_EDITOR_HELP_ADD_ONE("gui.editor-help-add-one", "Right-click inventory: add 1"),
	GUI_EDITOR_HELP_REMOVE_STACK("gui.editor-help-remove-stack", "Left-click package: remove stack"),
	GUI_EDITOR_HELP_REMOVE_ONE("gui.editor-help-remove-one", "Right-click package: remove 1"),
	GUI_PACKAGES_TITLE("gui.packages-title", "Packages"),
	GUI_PACKAGE_PRICE("gui.package-price", "${price}"),

	ADMIN_PERMISSION_REQUIRED("admin.permission-required", "You must have {accent}airdrop.admin{error} permission to do this"),
	ADMIN_PACKAGE_SAVE_REQUIRED("admin.package-save-required", "Must be admin to save package changes"),

	SYSTEM_CONFIG_LOADED("system.config-loaded", "Config loaded successfully"),
	SYSTEM_RELOAD_STARTED("system.reload-started", "Reloading configuration..."),
	SYSTEM_RELOAD_ECONOMY_ACTIVE("system.reload-economy-active",
			"Configuration, language, packages, and economy reloaded. Provider: {accent}{provider}{primary}"),
	SYSTEM_RELOAD_ECONOMY_DISABLED("system.reload-economy-disabled",
			"Configuration, language, packages, and economy reloaded. Economy is disabled; priced packages are blocked"),
	SYSTEM_RELOAD_ECONOMY_UNAVAILABLE("system.reload-economy-unavailable",
			"Reload completed, but no economy provider is available; paid drops are blocked"),
	SYSTEM_PACKAGE_PRICE_MISSING("system.package-price-missing", "Could not find price for package: {accent}{name}"),
	SYSTEM_PACKAGE_PRICE_INVALID("system.package-price-invalid",
			"Invalid package price for {accent}{name}{primary}: {accent}{price}{primary}. Package configuration rejected"),
	SYSTEM_VERSION_INFO("system.version-info",
			"{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{text}  Plugin: {accent}{plugin_version}\n{text}  Extension API: {accent}{extension_api_version}\n{text}  Paper compatibility: {accent}{paper_version}\n{text}  Java compatibility: {accent}{java_version}\n{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n{text}  Docs and support: {accent}{docs_url}"),
	SYSTEM_STATUS_VALUE_NONE("system.status-values.none", "none"),
	SYSTEM_STATUS_VALUE_NOT_PUBLISHED("system.status-values.not-published", "not published"),
	SYSTEM_STATUS_INFO("system.status-info",
			"{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n"
					+ "{text}  Plugin: {accent}{plugin_version}\n"
					+ "{text}  Extension API: {accent}{extension_api_version}\n"
					+ "{text}  Paper compatibility: {accent}{paper_version}\n"
					+ "{text}  Java compatibility: {accent}{java_version}\n"
					+ "{text}  Readiness: {accent}{readiness}\n"
					+ "{text}  Economy: {accent}{economy}{text} (provider: {accent}{economy_provider}{text})\n"
					+ "{text}  Packages: {accent}{package_count}{text} (revision {accent}{package_revision}{text})\n"
					+ "{text}  Pending requests: {accent}{pending_count}\n"
					+ "{text}  Falling drops: {accent}{falling_count}{text} / {accent}{max_falling}\n"
					+ "{text}  Landed drops: {accent}{landed_count}{text} / {accent}{max_landed}\n"
					+ "{text}  Diagnostic: {accent}{diagnostic_category}{diagnostic}\n"
					+ "{primary}━━━━━━━━━━━━━━━━━━━━━━━━\n"
					+ "{text}  Docs: {accent}{docs_url}");

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
