# AIRDR-23 Disabled Economy Providers Design

## Goal

Treat a registered Vault or VaultUnlocked economy provider whose `isEnabled()` check returns false or cannot be evaluated as unavailable during provider discovery. Paid drops must then use the existing economy-unavailable path without starting affordability or withdrawal work.

## Chosen Approach

Keep availability validation inside `EconomyProviderDiscovery`. A small guarded probe will call each provider's `isEnabled()` method and return false when the call returns false, throws a `RuntimeException`, or fails with a `LinkageError`.

The alternatives are less suitable for this issue:

- Validating in provider-wrapper constructors duplicates discovery policy and makes modern-to-legacy fallback less direct.
- Rechecking before every transaction broadens the behavior change beyond discovery and still cannot eliminate a provider becoming unavailable after a successful check.
- Checking only Bukkit service registration or the registering plugin does not detect the reported case, where Vault remains registered while its underlying economy implementation is disabled.

## Behavior and Control Flow

1. Discovery requests the registered VaultUnlocked provider.
2. A missing provider returns no modern result and continues to legacy discovery.
3. A present modern provider must pass the guarded enabled probe before async capability detection. A disabled or failing provider produces no modern result, allowing the existing legacy fallback.
4. A healthy modern provider that supports async operations and supplies an async implementation remains preferred.
5. Legacy discovery requests the registered Vault provider and applies the same guarded enabled probe before constructing `VaultEconomyProvider`.
6. A disabled or failing legacy provider produces `Optional.empty()`.
7. `Airdrop.refreshEconomyProvider()` already converts an empty discovery result to an unavailable refresh result and publishes a null provider.
8. `DropController.playerInitiatedDropPackage()` already rejects a paid drop when the published provider is null, before admission, payload materialization, affordability, or withdrawal. `DropCommand` already translates that rejection to the configured economy-unavailable message.

## Failure Boundaries

The guarded enabled probe catches `RuntimeException` and `LinkageError`, matching the repository's existing provider-discovery boundary. It does not catch unrelated fatal JVM errors. Failures from a modern provider continue to legacy discovery; failures from a legacy provider yield no provider.

No provider-operation behavior changes. The provider is checked only when discovery runs at startup, configuration reload, or a supported service registration or unregistration event. Detecting a provider that becomes disabled without any discovery-triggering event is excluded from AIRDR-23.

## Compatibility

- A healthy VaultUnlocked async provider remains preferred over legacy Vault.
- A healthy legacy provider remains the fallback when modern Vault is absent, disabled, failing, or not natively async.
- Free drops do not require a provider.
- Configurations with economy support disabled continue to publish the disabled state rather than unavailable.
- Existing economy-unavailable language and paid-drop control flow remain unchanged.

## Tests

Use focused discovery tests to drive the change:

- Explicitly mark existing healthy modern and legacy mocks enabled.
- Verify a disabled legacy provider returns no provider.
- Verify a disabled modern provider falls back to a healthy legacy provider.
- Verify a modern enabled check throwing a linkage failure falls back to healthy legacy.
- Verify a legacy enabled check throwing a runtime exception returns no provider.
- Preserve the existing healthy-modern preference assertion.

Update lifecycle test helpers so their intentionally healthy mock providers report enabled. Retain the existing controller regression proving a missing provider rejects a paid drop before payload and payment work. Add a command-level assertion for the configured economy-unavailable message because that mapping does not currently have direct coverage; no production command or language changes are planned.

Run the focused discovery tests after the red and green phases, followed by the full `./gradlew test` suite.

## Exclusions

- Polling provider health.
- Rechecking provider health before every economy call.
- Changing payment, refund, or timeout behavior.
- Changing service-event handling, configuration keys, or language defaults.
- Supporting additional economy APIs.
