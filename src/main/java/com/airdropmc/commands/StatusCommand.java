package com.airdropmc.commands;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.AirdropStatus;
import com.airdropmc.api.AirdropVersions;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** Renders the supported, immutable operational status snapshot. */
public final class StatusCommand {
	private static final String MODRINTH_URL = "https://modrinth.com/plugin/airdrop";

	private StatusCommand() {
	}

	/** Handles the admin-only status command before normal readiness gating. */
	public static void onCommand(CommandSender sender) {
		if (!PermissionsHelper.isAdmin(sender)) {
			ChatHandler.sendError(sender, MessageKey.ADMIN_PERMISSION_REQUIRED);
			return;
		}
		AirdropApi api = Bukkit.getServicesManager().load(AirdropApi.class);
		if (api == null) {
			ChatHandler.sendError(sender, MessageKey.ERROR_STATUS_UNAVAILABLE);
			return;
		}
		sendStatus(sender, api.versions(), api.status());
	}

	static void sendStatus(
			CommandSender sender, AirdropVersions versions, AirdropStatus status) {
		Objects.requireNonNull(sender, "sender");
		Objects.requireNonNull(versions, "versions");
		Objects.requireNonNull(status, "status");
		String none = localized(MessageKey.SYSTEM_STATUS_VALUE_NONE);
		String notPublished = localized(MessageKey.SYSTEM_STATUS_VALUE_NOT_PUBLISHED);
		Map<String, String> placeholders = new LinkedHashMap<>();
		placeholders.put("plugin_version", versions.pluginVersion());
		placeholders.put("extension_api_version", versions.extensionApiVersion());
		placeholders.put("paper_version", versions.paperApiVersion());
		placeholders.put("java_version", versions.javaVersion());
		placeholders.put("readiness", status.readiness().name());
		placeholders.put("economy", status.economy().name());
		placeholders.put("economy_provider", status.economyProviderName().orElse(none));
		placeholders.put("package_count", Integer.toString(status.packageCount()));
		placeholders.put("package_revision", Long.toString(status.packageRevision()));
		placeholders.put("pending_count", Integer.toString(status.pendingCount()));
		placeholders.put("falling_count", Integer.toString(status.fallingCount()));
		placeholders.put("max_falling", displayLimit(status.maxFalling(), notPublished));
		placeholders.put("landed_count", Integer.toString(status.landedCount()));
		placeholders.put("max_landed", displayLimit(status.maxLanded(), notPublished));
		placeholders.put("diagnostic_category", status.lastDiagnosticCategory()
				.map(category -> category + " — ")
				.orElse(""));
		placeholders.put("diagnostic", status.lastDiagnostic().orElse(none));
		placeholders.put("docs_url", MODRINTH_URL);
		ChatHandler.sendWithoutPrefix(sender, MessageKey.SYSTEM_STATUS_INFO, placeholders);
	}

	private static String displayLimit(OptionalInt limit, String notPublished) {
		return limit.isPresent() ? Integer.toString(limit.getAsInt()) : notPublished;
	}

	private static String localized(MessageKey key) {
		String value = ChatHandler.get(key);
		return value == null ? key.getDefault() : value;
	}
}
