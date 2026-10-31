package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/** Additional consumer observations, deliberately separate from the exact lifecycle parser. */
final class ConsumerIntegrationSupport {
	private static final Pattern MARKER = Pattern.compile("\\bAIRDR_CONSUMER_([A-Z_]+)\\b");
	private static final Pattern ATTRIBUTE = Pattern.compile("\\b([A-Za-z][A-Za-z0-9]*)=([^\\s]+)");

	private ConsumerIntegrationSupport() {
	}

	static PackageSnapshot snapshot(ILightkeeperFramework framework, String packageName) {
		String token = UUID.randomUUID().toString();
		int offset = framework.server().output().size();
		execute(framework, "airdrop-consumer snapshot " + token + " " + packageName);
		Marker marker = awaitMarker(framework, offset, token, "SNAPSHOT");
		assertThat(marker.required("primaryThread")).isEqualTo("true");
		String items = marker.required("items");
		return new PackageSnapshot(Long.parseLong(marker.required("revision")),
				marker.required("name"), marker.required("price"),
				items.equals("NONE") ? List.of() : List.of(items.split(",")));
	}

	static List<String> registryMarkers(ILightkeeperFramework framework, int offset) {
		return outputSince(framework, offset).stream()
				.filter(line -> line.contains("AIRDR_CONSUMER_PACKAGE_REGISTRY ")).toList();
	}

	static Request request(ILightkeeperFramework framework, PlayerHandle player, String packageName) {
		String token = UUID.randomUUID().toString();
		int offset = framework.server().output().size();
		execute(framework, "airdrop-consumer request " + token + " " + player.uniqueId() + " " + packageName);
		Marker handle = awaitMarker(framework, offset, token, "HANDLE");
		UUID requestId = UUID.fromString(handle.required("requestId"));
		assertThat(handle.required("descriptorId")).isEqualTo(requestId.toString());
		assertThat(handle.required("playerId")).isEqualTo(player.uniqueId().toString());
		assertThat(handle.required("name")).isEqualTo(packageName);
		assertThat(handle.required("source")).isEqualTo("PLAYER");
		assertThat(handle.required("primaryThread")).isEqualTo("true");
		return new Request(token, offset, requestId);
	}

	static Marker awaitHandleResult(ILightkeeperFramework framework, Request request, String type) {
		Marker marker = awaitMarker(framework, request.outputOffset(), request.token(), type);
		assertThat(marker.required("requestId")).isEqualTo(request.requestId().toString());
		assertThat(marker.required("resultRequestId")).isEqualTo(request.requestId().toString());
		return marker;
	}

	static List<Marker> markers(ILightkeeperFramework framework, int offset) {
		List<Marker> result = new ArrayList<>();
		for (String line : outputSince(framework, offset)) {
			var type = MARKER.matcher(line);
			if (!type.find()) {
				continue;
			}
			Map<String, String> values = new LinkedHashMap<>();
			var attributes = ATTRIBUTE.matcher(line.substring(type.end()));
			while (attributes.find()) {
				values.put(attributes.group(1), attributes.group(2));
			}
			result.add(new Marker(type.group(1), Map.copyOf(values)));
		}
		return List.copyOf(result);
	}

	private static Marker awaitMarker(ILightkeeperFramework framework, int offset, String token, String type) {
		eventually(Duration.ofSeconds(30), () -> {
			List<Marker> correlated = markers(framework, offset).stream()
					.filter(marker -> token.equals(marker.values().get("token"))).toList();
			assertThat(correlated).as("consumer command/callback errors for %s", token)
					.noneMatch(marker -> "ERROR".equals(marker.values().get("status")));
			assertThat(correlated).filteredOn(marker -> marker.type().equals(type)).hasSize(1);
		});
		Marker marker = markers(framework, offset).stream()
				.filter(value -> token.equals(value.values().get("token")) && type.equals(value.type()))
				.findFirst().orElseThrow();
		assertThat(marker.required("status")).isEqualTo("OK");
		return marker;
	}

	private static List<String> outputSince(ILightkeeperFramework framework, int offset) {
		List<String> output = framework.server().output();
		assertThat(output).hasSizeGreaterThanOrEqualTo(offset);
		return output.subList(offset, output.size());
	}

	private static void execute(ILightkeeperFramework framework, String command) {
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE, command).success())
				.as("consumer command dispatch: %s", command).isTrue();
	}

	record PackageSnapshot(long revision, String name, String price, List<String> items) {
		PackageSnapshot {
			items = List.copyOf(items);
		}
	}

	record Request(String token, int outputOffset, UUID requestId) {
	}

	record Marker(String type, Map<String, String> values) {
		String required(String name) {
			String value = values.get(name);
			assertThat(value).as("%s on %s", name, type).isNotBlank();
			return value;
		}
	}
}
