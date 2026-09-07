# Airdrop 4.1 replaces implementation access with one supported service

Airdrop 4.1 introduces the first versioned extension boundary at API `1.0.0`.
The project has no known integrations with the 4.0 Java surface, so this release
does not add a compatibility-shim layer for that surface. Some implementation
types remain accessible, but their presence is not a compatibility guarantee.
Integrations should use `com.airdropmc.api`.

## Discover the service after declaring Airdrop as a dependency

Declare `depend: [Airdrop]` when your plugin cannot run without it, or
`softdepend: [Airdrop]` when the integration is optional. Compile against Airdrop
without shading it, as shown in the [developer guide](modrinth.md#developer-integration).
A soft dependency controls load order but leaves API classes unavailable when
Airdrop is absent. Keep all Airdrop API references, including listener types,
in a separate integration class, and check availability in the plugin entry
point before calling it. In `ExamplePlugin.java`:

<!-- optional-example:ExamplePlugin.java -->
```java
package dev.airdropmc.example;

import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		if (getServer().getPluginManager().isPluginEnabled("Airdrop")) {
			AirdropIntegration.enable(this);
		}
	}
}
```

In the separate `AirdropIntegration.java`, discover the provider through Bukkit
and wait for readiness. An enabled Airdrop can still have an unavailable
service or be starting; a null-service check alone cannot handle absent API
classes:

<!-- optional-example:AirdropIntegration.java -->
```java
package dev.airdropmc.example;

import com.airdropmc.api.AirdropApi;
import org.bukkit.plugin.java.JavaPlugin;

final class AirdropIntegration {
	static void enable(JavaPlugin plugin) {
		AirdropApi api = plugin.getServer().getServicesManager().load(AirdropApi.class);
		if (api == null) {
			return;
		}

		api.readiness().whenComplete((ready, failure) -> {
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				if (failure != null) {
					plugin.getLogger().warning("Airdrop did not become ready");
					return;
				}
				plugin.getLogger().info("Airdrop API "
						+ ready.versions().extensionApiVersion() + " is ready");
			});
		});
	}
}
```

Either availability check can skip the integration. Register Airdrop event
listeners from the integration class after the availability check. Airdrop
unregisters its service during disable; do not retain the provider across
plugin reloads.

Service calls that accept or return `Player`, `Location`, `Block`, `Entity`,
`ItemStack`, or other mutable Bukkit
objects require the primary server thread. Pure version, state, UUID, count,
enum, and `WorldPosition` values are safe to read off-thread. Completion-stage
continuations do not promise a particular executor.

## Replace controllers and managers with supported operations

Use these replacements:

| Before Airdrop 4.1 | Airdrop 4.1 |
| --- | --- |
| `DropController` methods | `AirdropApi.requestPlayerDrop(...)` or `requestSystemDrop(...)` |
| `PackageManager` and raw package objects | `listPackages()` and `findPackage(...)` |
| `CrateManager` and live `Crate` instances | `activeDrops()` and typed lookup methods |
| plugin metadata getters | `AirdropApi.versions()` |

The new request methods return a `DropHandle`. Observe both its spawn stage and
terminal outcome instead of treating a method return as proof of payment or
delivery. Payment status can remain `UNKNOWN` when an external economy result
is ambiguous; integrations must not retry or refund that state automatically.

## Replace legacy post-events with the supported lifecycle

`PackageDropEvent` and `PackageLandEvent` are unsupported deprecated adapters.
Their presence does not make `com.airdropmc.events` part of the supported API or
guarantee that the adapters will remain for the rest of the 4.x line. New
integrations should listen under `com.airdropmc.api.event`:

1. `AirdropRequestEvent`
2. `AirdropSpawnedEvent`
3. `AirdropLandingAttemptEvent`
4. `AirdropLandedEvent`
5. `AirdropOutcomeEvent`

The request and landing-attempt events are cancellable. Spawned, landed, and
outcome events describe committed state. Registry, recovery, and retirement
events cover package publication and drops that outlive their original request
process. Use the request and crate UUIDs for correlation instead of comparing
mutable Bukkit locations or implementation objects.

## Keep Paper and extension versions separate

`AirdropVersions.extensionApiVersion()` reports the supported Airdrop API.
`AirdropVersions.paperApiVersion()` reports Paper compatibility. The
`api-version` field in Bukkit metadata is the latter and must not drive Airdrop
API compatibility decisions.

See [the API version policy](development/api-versioning.md) for the supported
package boundary and compatibility commands.
