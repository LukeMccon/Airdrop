# AIRDR-8 Package Name Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every accepted package one case-insensitive command identity and one canonical permission node while rejecting command and permission-name conflicts at every ingestion path.

**Architecture:** A central `PackageNamePolicy` validates syntax, rejects reserved identities, and canonicalizes with `Locale.ROOT`. `PackageManager` keys its runtime registry by canonical identity but preserves exact names for display and YAML persistence; commands, GUIs, APIs, and permission checks all reuse that policy.

**Tech Stack:** Java 21, Paper/Bukkit configuration and command APIs, MockBukkit, JUnit 5, Mockito, Gradle

---

### Task 1: Define one package-name and command-name contract

**Files:**
- Create: `src/main/java/com/airdropmc/AirdropCommandNames.java`
- Create: `src/main/java/com/airdropmc/packages/PackageNamePolicy.java`
- Create: `src/test/java/com/airdropmc/packages/PackageNamePolicyTest.java`
- Modify: `src/main/java/com/airdropmc/commands/CmdAirdrop.java`
- Modify: `src/main/java/com/airdropmc/AirdropTabCompleter.java`

- [ ] **Step 1: Write failing policy tests**

Create `PackageNamePolicyTest` with tests equivalent to:

```java
class PackageNamePolicyTest {
	private Locale defaultLocale;

	@BeforeEach
	void rememberLocale() {
		defaultLocale = Locale.getDefault();
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(defaultLocale);
	}

	@Test
	void acceptsSupportedCharactersAndCanonicalizesWithLocaleRoot() {
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));

		PackageNamePolicy.Result result = PackageNamePolicy.validate("TITLE_2-Test");

		assertTrue(result.accepted());
		assertEquals("title_2-test", result.canonicalName());
	}

	@Test
	void rejectsMissingAndUnsupportedNames() {
		for (String name : Arrays.asList(null, "", " ", "test.items", "two words")) {
			assertFalse(PackageNamePolicy.validate(name).accepted(), String.valueOf(name));
		}
	}

	@Test
	void rejectsEveryReservedIdentityWithoutCaseDifferences() {
		for (String name : List.of("all", "ALL", "*", "package", "PACKAGES", "Version", "reLOAD")) {
			PackageNamePolicy.Result result = PackageNamePolicy.validate(name);
			assertFalse(result.accepted(), name);
			assertEquals(PackageNamePolicy.Rejection.RESERVED, result.rejection(), name);
		}
	}

	@Test
	void commandNamesAndReservedPackageNamesStayAligned() {
		assertEquals(Set.of("package", "packages", "version", "reload"),
				AirdropCommandNames.topLevel());
		assertTrue(AirdropCommandNames.topLevel().stream()
				.noneMatch(name -> PackageNamePolicy.validate(name).accepted()));
	}
}
```

- [ ] **Step 2: Run the policy test and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageNamePolicyTest
```

Expected: compilation fails because `AirdropCommandNames` and `PackageNamePolicy` do not exist.

- [ ] **Step 3: Implement centralized command names**

Create `AirdropCommandNames` with the complete contract:

```java
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
```

Replace literal routing cases in `CmdAirdrop` with the four constants and replace the two private completion lists in `AirdropTabCompleter` with `AirdropCommandNames.visibleTo(PermissionsHelper.isAdmin(commandSender))`.

- [ ] **Step 4: Implement the package-name policy**

Create `PackageNamePolicy`:

```java
package com.airdropmc.packages;

import com.airdropmc.AirdropCommandNames;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PackageNamePolicy {
	private static final Pattern SUPPORTED_CHARACTERS = Pattern.compile("^[A-Za-z0-9_-]+$");
	private static final Set<String> PERMISSION_IDENTITIES = Set.of("all", "*");

	public enum Rejection {
		MISSING,
		INVALID_CHARACTERS,
		RESERVED
	}

	public record Result(String canonicalName, Rejection rejection) {
		public boolean accepted() {
			return rejection == null;
		}

		public String diagnostic(String originalName) {
			if (accepted()) {
				return "Package name is valid";
			}
			return switch (rejection) {
				case MISSING -> "Package name is required";
				case INVALID_CHARACTERS -> "Package name '" + originalName
						+ "' may only contain letters, numbers, underscores, and dashes";
				case RESERVED -> "Package name '" + originalName + "' is reserved";
			};
		}
	}

	private PackageNamePolicy() {
	}

	public static Result validate(String name) {
		if (name == null || name.isBlank()) {
			return new Result(null, Rejection.MISSING);
		}
		String canonicalName = name.toLowerCase(Locale.ROOT);
		if (PERMISSION_IDENTITIES.contains(canonicalName)
				|| AirdropCommandNames.topLevel().contains(canonicalName)) {
			return new Result(null, Rejection.RESERVED);
		}
		if (!SUPPORTED_CHARACTERS.matcher(name).matches()) {
			return new Result(null, Rejection.INVALID_CHARACTERS);
		}
		return new Result(canonicalName, null);
	}

	public static String requireCanonical(String name) {
		Result result = validate(name);
		if (!result.accepted()) {
			throw new IllegalArgumentException(result.diagnostic(name));
		}
		return result.canonicalName();
	}

	public static String permissionNode(String name) {
		return "airdrop.package." + requireCanonical(name);
	}
}
```

- [ ] **Step 5: Run the policy and existing completion tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageNamePolicyTest \
  --tests com.airdropmc.commands.TabCompletionPermissionsTest
```

Expected: both test classes pass.

- [ ] **Step 6: Commit the contract**

```bash
git add src/main/java/com/airdropmc/AirdropCommandNames.java \
  src/main/java/com/airdropmc/packages/PackageNamePolicy.java \
  src/main/java/com/airdropmc/commands/CmdAirdrop.java \
  src/main/java/com/airdropmc/AirdropTabCompleter.java \
  src/test/java/com/airdropmc/packages/PackageNamePolicyTest.java
git commit -m "AIRDR-8: centralize package name policy"
```

### Task 2: Make the runtime registry case-insensitive and configuration loading fail closed

**Files:**
- Modify: `src/main/java/com/airdropmc/packages/PackageManager.java`
- Modify: `src/test/java/com/airdropmc/packages/PackageManagerConfigRobustnessTest.java`
- Modify: `src/test/java/com/airdropmc/packages/PackageManagerMutationTest.java`

- [ ] **Step 1: Write failing configuration-ingestion tests**

Add tests that install YAML packages through the existing `setPackagesConfig` helper:

```java
@Test
void reload_skipsInvalidAndReservedNamesWithDiagnostics() throws Exception {
	YamlConfiguration config = new YamlConfiguration();
	for (String name : List.of("all", "*", "package", "packages", "version", "reload", "bad name")) {
		addPackage(config, name, 1.0);
	}
	addPackage(config, "valid_name", 1.0);
	setPackagesConfig(config);

	try (MockedStatic<AirdropLogger> logger = mockStatic(AirdropLogger.class)) {
		PackageManager.reload();

		assertEquals(Set.of("valid_name"), PackageManager.getPackages());
		for (String rejected : List.of("all", "*", "package", "packages", "version", "reload", "bad name")) {
			logger.verify(() -> AirdropLogger.warning(argThat(message ->
					message.contains(rejected) && message.contains("Skipping package"))), atLeastOnce());
		}
	}
}

@Test
void reload_rejectsEveryCaseVariantAndWarnsAboutTheConflict() throws Exception {
	YamlConfiguration config = new YamlConfiguration();
	addPackage(config, "starter", 2.0);
	addPackage(config, "Starter", 1.0);
	addPackage(config, "other", 4.0);
	setPackagesConfig(config);

	try (MockedStatic<AirdropLogger> logger = mockStatic(AirdropLogger.class)) {
		PackageManager.reload();

		assertEquals(Set.of("other"), PackageManager.getPackages());
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("STARTER"));
		logger.verify(() -> AirdropLogger.warning(argThat(message ->
				message.contains("starter") && message.contains("Starter")
						&& message.contains("conflicts"))), atLeastOnce());
	}
}

@Test
void reload_rejectsCaseCollisionBeforeReadingEitherPayload() throws Exception {
	YamlConfiguration config = new YamlConfiguration();
	addPackage(config, "Starter", "invalid");
	addPackage(config, "starter", 2.0);
	setPackagesConfig(config);

	PackageManager.reload();

	assertTrue(PackageManager.getPackages().isEmpty());
	assertThrows(PackageNotFoundException.class, () -> PackageManager.get("STARTER"));
}
```

- [ ] **Step 2: Write failing registry-mutation tests**

Extend `PackageManagerMutationTest` with:

```java
@Test
void registryLookupAndDuplicateDetectionIgnoreCase() throws Exception {
	assertTrue(PackageManager.reload());

	assertSame(PackageManager.get("starter"), PackageManager.get("STARTER"));
	assertTrue(PackageManager.has("StArTeR"));
	assertThrows(DuplicatePackageException.class, () -> PackageManager.createPackage(
			new Package("STARTER", 3.0, List.of())));
}

@Test
void differentlyCasedUpdateUsesStoredYamlKey() throws Exception {
	config.set("packages.Starter.price", 10.0);
	config.set("packages.Starter.items", List.of());
	config.set("packages.starter", null);
	assertTrue(PackageManager.reload());
	when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);

	assertTrue(PackageManager.updatePackageInventory("STARTER", List.of(new ItemStack(Material.DIRT))));

	ArgumentCaptor<FileConfiguration> candidate = ArgumentCaptor.forClass(FileConfiguration.class);
	verify(packagesConfig).saveConfig(candidate.capture());
	assertTrue(candidate.getValue().isSet("packages.Starter.items"));
	assertFalse(candidate.getValue().isSet("packages.STARTER.items"));
}

@Test
void differentlyCasedDeleteRemovesStoredYamlKey() throws Exception {
	config.set("packages.Starter.price", 10.0);
	config.set("packages.Starter.items", List.of());
	config.set("packages.starter", null);
	assertTrue(PackageManager.reload());
	when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);

	assertTrue(PackageManager.deletePackage("STARTER"));

	ArgumentCaptor<FileConfiguration> candidate = ArgumentCaptor.forClass(FileConfiguration.class);
	verify(packagesConfig).saveConfig(candidate.capture());
	assertFalse(candidate.getValue().isSet("packages.Starter"));
}

@Test
void createPackageRejectsInvalidNameBeforePersistence() {
	assertThrows(IllegalArgumentException.class, () -> PackageManager.createPackage(
			new Package("reload", 3.0, List.of())));
	verify(packagesConfig, never()).saveConfig(any(FileConfiguration.class));
}
```

- [ ] **Step 3: Run focused manager tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageManagerConfigRobustnessTest \
  --tests com.airdropmc.packages.PackageManagerMutationTest
```

Expected: case-insensitive lookup, reserved-name ingestion, conflict resolution, and stored-key persistence assertions fail.

- [ ] **Step 4: Canonicalize registry access and preserve exact names**

Update `PackageManager` so `get`, `has`, and enumeration follow this logic:

```java
public static Set<String> getPackages() {
	return packages.values().stream()
			.map(Package::getName)
			.collect(Collectors.toUnmodifiableSet());
}

public static Package get(String packageName) throws PackageNotFoundException {
	PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
	Package pkg = validation.accepted() ? packages.get(validation.canonicalName()) : null;
	if (pkg == null) {
		throw new PackageNotFoundException(packageName);
	}
	return pkg;
}

public static boolean has(String packageName) {
	PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
	return validation.accepted() && packages.containsKey(validation.canonicalName());
}
```

Import `java.util.stream.Collectors`. Keep the concurrent map, but treat every key as canonical.

- [ ] **Step 5: Validate and group configured names before reading payloads**

Pre-validate, sort, and group configuration keys as follows:

```java
List<String> configuredNames = new ArrayList<>(config.getKeys(false));
configuredNames.sort(Comparator.naturalOrder());
Map<String, List<String>> namesByCanonical = new TreeMap<>();

for (String name : configuredNames) {
	PackageNamePolicy.Result validation = PackageNamePolicy.validate(name);
	if (!validation.accepted()) {
		AirdropLogger.warning("Skipping package '" + name + "': " + validation.diagnostic(name));
		continue;
	}
	namesByCanonical.computeIfAbsent(validation.canonicalName(), ignored -> new ArrayList<>()).add(name);
}

for (Map.Entry<String, List<String>> entry : namesByCanonical.entrySet()) {
	List<String> exactNames = entry.getValue();
	if (exactNames.size() > 1) {
		AirdropLogger.warning("Skipping packages " + exactNames
				+ " because their names conflict without case differences as '" + entry.getKey() + "'");
		continue;
	}
	String name = exactNames.getFirst();

	ConfigurationSection section = config.getConfigurationSection(name);
	if (section == null) {
		continue;
	}

	ArrayList<ItemStack> items = new ArrayList<>();
	List<?> rawList = config.getList(name + ".items");
	if (rawList != null) {
		for (Object obj : rawList) {
			if (obj instanceof ItemStack itemStack) {
				items.add(itemStack);
			}
		}
	}

	Object rawPrice = config.get(name + ".price");
	if (!(rawPrice instanceof Number number)) {
		logInvalidPrice(name, rawPrice);
		continue;
	}
	double price = number.doubleValue();
	if (!Package.isValidPrice(price)) {
		logInvalidPrice(name, rawPrice);
		continue;
	}

	List<ItemStack> limitedItems = limitToBarrelCapacity(items, name);
	packages.put(entry.getKey(), new Package(name, price, limitedItems));
}
```

All members of a case-collision group are rejected before section, price, or item validation. This prevents malformed payloads from deciding which contents inherit an existing shared permission. Exact names are sorted so warnings remain stable; the YAML file is not rewritten.

- [ ] **Step 6: Make mutations use canonical identity and stored YAML names**

Replace the three mutation methods with the following implementations:

```java
public static boolean createPackage(Package pkg) throws DuplicatePackageException {
	String canonicalName = PackageNamePolicy.requireCanonical(pkg.getName());
	if (packages.containsKey(canonicalName)) {
		throw new DuplicatePackageException(pkg.getName());
	}
	if (!Package.isValidPrice(pkg.getPrice())) {
		throw new IllegalArgumentException("Package price must be finite and non-negative");
	}
	PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
	FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
	if (fileConfig == null) {
		throw new IllegalStateException("Packages configuration is unavailable");
	}

	List<ItemStack> limitedItems = limitToBarrelCapacity(pkg.getItems(), pkg.getName());
	YamlConfiguration candidate = copyConfiguration(fileConfig);
	candidate.set(PACKAGES + "." + pkg.getName() + ".price", pkg.getPrice());
	candidate.set(PACKAGES + "." + pkg.getName() + ".items", new ArrayList<>(limitedItems));
	if (!packagesConfig.saveConfig(candidate)) {
		return false;
	}

	Package committedPackage = new Package(pkg.getName(), pkg.getPrice(), limitedItems);
	packages.put(canonicalName, committedPackage);
	refreshPackagesGui();
	return true;
}

public static boolean updatePackageInventory(String packageName, List<ItemStack> items)
		throws PackageNotFoundException {
	Package pkg = get(packageName);
	String storedName = pkg.getName();
	PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
	FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
	if (fileConfig == null) {
		throw new IllegalStateException("Packages configuration is unavailable");
	}

	List<ItemStack> limitedItems = limitToBarrelCapacity(items, storedName);
	YamlConfiguration candidate = copyConfiguration(fileConfig);
	candidate.set(PACKAGES + "." + storedName + ".items", new ArrayList<>(limitedItems));
	if (!packagesConfig.saveConfig(candidate)) {
		return false;
	}

	pkg.setItems(limitedItems);
	return true;
}

public static boolean deletePackage(String packageName) throws PackageNotFoundException {
	Package pkg = get(packageName);
	String storedName = pkg.getName();
	String canonicalName = PackageNamePolicy.requireCanonical(storedName);
	PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
	FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
	if (fileConfig == null) {
		throw new IllegalStateException("Packages configuration is unavailable");
	}

	YamlConfiguration candidate = copyConfiguration(fileConfig);
	candidate.set(PACKAGES + "." + storedName, null);
	if (!packagesConfig.saveConfig(candidate)) {
		return false;
	}

	packages.remove(canonicalName);
	refreshPackagesGui();
	return true;
}
```

- [ ] **Step 7: Run manager tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageManagerConfigRobustnessTest \
  --tests com.airdropmc.packages.PackageManagerMutationTest
```

Expected: both classes pass.

- [ ] **Step 8: Commit registry behavior**

```bash
git add src/main/java/com/airdropmc/packages/PackageManager.java \
  src/test/java/com/airdropmc/packages/PackageManagerConfigRobustnessTest.java \
  src/test/java/com/airdropmc/packages/PackageManagerMutationTest.java
git commit -m "AIRDR-8: enforce canonical package identity"
```

### Task 3: Enforce the policy through commands, GUIs, and public APIs

**Files:**
- Modify: `src/main/java/com/airdropmc/controllers/PackageController.java`
- Modify: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `src/test/java/com/airdropmc/controllers/PackageControllerPermissionsTest.java`
- Modify: `src/test/java/com/airdropmc/packages/PackagePersistenceFailureFeedbackTest.java`

- [ ] **Step 1: Write failing command and public-API tests**

Extend `PackageControllerPermissionsTest`:

```java
@Test
void createPackageCommandRejectsReservedNamesBeforeOpeningGui() {
	PlayerMock player = server.addPlayer();
	player.setOp(true);

	for (String name : List.of("all", "*", "package", "packages", "version", "reload", "ReLoAd")) {
		PackageController.createPackageCommand(player,
				new String[]{"package", "create", name, "10.0"});
		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType(), name);
		Component message = player.nextComponentMessage();
		assertNotNull(message, name);
		assertTrue(PlainTextComponentSerializer.plainText().serialize(message)
				.toLowerCase(Locale.ROOT).contains("reserved"), name);
	}
}

@Test
void bothPublicCreateOverloadsReuseManagerNamePolicy() {
	assertThrows(IllegalArgumentException.class,
			() -> PackageController.createPackage("all", 1.0));
	assertThrows(IllegalArgumentException.class,
			() -> PackageController.createPackage("reload", 1.0, List.of()));
}
```

- [ ] **Step 2: Write a failing direct-GUI rejection test**

Add to `PackagePersistenceFailureFeedbackTest`:

```java
@Test
void directlyConstructedInvalidCreateGuiCannotPersistPackage() {
	when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
	PlayerMock player = operator();
	CreatePackageGui gui = new CreatePackageGui("reload", 3.0);
	gui.openInventory(player);
	Inventory editor = player.getOpenInventory().getTopInventory();

	gui.save(saveClick(player));

	assertSame(editor, player.getOpenInventory().getTopInventory());
	assertThrows(PackageNotFoundException.class, () -> PackageManager.get("reload"));
	Component message = player.nextComponentMessage();
	assertNotNull(message);
	assertTrue(PlainTextComponentSerializer.plainText().serialize(message)
			.toLowerCase(Locale.ROOT).contains("reserved"));
}
```

- [ ] **Step 3: Run focused boundary tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.controllers.PackageControllerPermissionsTest \
  --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest
```

Expected: reserved command input opens an editor, public APIs only fail later on missing configuration, and direct GUI save leaks `IllegalArgumentException`.

- [ ] **Step 4: Replace command-local validation with the central policy**

Remove `VALID_PACKAGE_NAME_PATTERN` from `PackageController` and use:

```java
String packageName = args[2] == null ? "" : args[2].trim();
PackageNamePolicy.Result nameValidation = PackageNamePolicy.validate(packageName);
if (!nameValidation.accepted()) {
	ChatHandler.sendError(sender,
			nameValidation.rejection() == PackageNamePolicy.Rejection.MISSING
					? MessageKey.PACKAGES_NAME_REQUIRED
					: MessageKey.PACKAGES_NAME_INVALID);
	return;
}
```

The two public overloads keep constructing `Package` and calling `PackageManager.createPackage`; the manager remains the mandatory enforcement boundary.

- [ ] **Step 5: Report direct GUI policy rejection without success**

Extend the existing `CreatePackageGui.save` catch block:

```java
try {
	if (!PackageManager.createPackage(pkg)) {
		ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_SAVE_FAILED);
		return;
	}
} catch (DuplicatePackageException error) {
	ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_EXISTS,
			Map.of("name", error.getPackageName()));
	return;
} catch (IllegalArgumentException error) {
	ChatHandler.sendError(p, MessageKey.PACKAGES_NAME_INVALID);
	return;
}
```

The invalid path leaves the editor open and sends no creation-success message.

- [ ] **Step 6: Clarify localized validation feedback**

Change both the enum fallback and `en.yml` value to:

```text
Package names may only contain letters, numbers, underscores, and dashes and cannot use reserved names: all, *, package, packages, version, reload
```

- [ ] **Step 7: Run boundary and language tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.airdropmc.controllers.PackageControllerPermissionsTest \
  --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest
./gradlew processResources
```

Expected: boundary tests pass and resource processing succeeds.

- [ ] **Step 8: Commit boundary enforcement**

```bash
git add src/main/java/com/airdropmc/controllers/PackageController.java \
  src/main/java/com/airdropmc/packages/CreatePackageGui.java \
  src/main/java/com/airdropmc/lang/MessageKey.java \
  src/main/resources/lang/en.yml \
  src/test/java/com/airdropmc/controllers/PackageControllerPermissionsTest.java \
  src/test/java/com/airdropmc/packages/PackagePersistenceFailureFeedbackTest.java
git commit -m "AIRDR-8: reject reserved package names at boundaries"
```

### Task 4: Canonicalize permissions and prove command reachability

**Files:**
- Modify: `src/main/java/com/airdropmc/helpers/PermissionsHelper.java`
- Modify: `src/main/java/com/airdropmc/commands/DropCommand.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `src/test/java/com/airdropmc/helpers/PermissionsHelperTest.java`
- Create: `src/test/java/com/airdropmc/commands/DropCommandPackageIdentityTest.java`
- Modify: `src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java`

- [ ] **Step 1: Replace the legacy permission test with failing canonical-node tests**

Use these behaviors in `PermissionsHelperTest`:

```java
@Test
void hasPermissionChecksOnlyCanonicalPackageNode() {
	Player player = mock(Player.class);
	when(player.hasPermission("airdrop.admin")).thenReturn(false);
	when(player.isOp()).thenReturn(false);
	when(player.hasPermission("airdrop.package.mixedcase")).thenReturn(true);

	assertTrue(PermissionsHelper.hasPermission(player, "MixedCase"));
	verify(player).hasPermission("airdrop.package.mixedcase");
	verify(player, never()).hasPermission("airdrop.package.MixedCase");
}

@Test
void hasPermissionDoesNotAcceptExactCaseLegacyNode() {
	Player player = mock(Player.class);
	when(player.hasPermission("airdrop.admin")).thenReturn(false);
	when(player.isOp()).thenReturn(false);
	when(player.hasPermission("airdrop.package.MixedCase")).thenReturn(true);

	assertFalse(PermissionsHelper.hasPermission(player, "MixedCase"));
	verify(player, never()).hasPermission("airdrop.package.MixedCase");
}

@Test
void hasPermissionRejectsInvalidIdentityBeforeAdminOrGlobalBypass() {
	Player player = mock(Player.class);
	when(player.hasPermission("airdrop.admin")).thenReturn(true);

	assertFalse(PermissionsHelper.hasPermission(player, "all"));
	assertFalse(PermissionsHelper.hasPermission(player, "reload"));
	assertFalse(PermissionsHelper.hasPermission(player, "bad.name"));
	verify(player, never()).hasPermission("airdrop.package.all");
}
```

Retain the Turkish-locale test and the existing admin/global tests.

- [ ] **Step 2: Write a failing drop-command reachability test**

Create a MockBukkit test that loads a configured `Starter`, invokes `DropCommand` with `STARTER`, and verifies the resolved package reaches the controller:

```java
@Test
void dropCommandResolvesAcceptedPackageWithoutCaseDifferences() throws Exception {
	PlayerMock player = server.addPlayer();
	Package expected = PackageManager.get("Starter");

	try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
		DropCommand.onCommand(player, new String[]{"STARTER"});

		controller.verify(() -> DropController.playerInitiatedDropPackage(expected, player));
	}
}
```

Fixture setup must install a `PackagesConfig` containing `packages.Starter`, call `PackageManager.reload()`, and clear static Airdrop fields during teardown.

Add a second test that allows real permission rejection and checks the command's diagnostic:

```java
@Test
void permissionDenialDisplaysCanonicalNode() {
	PlayerMock player = server.addPlayer();

	DropCommand.onCommand(player, new String[]{"Starter"});

	Component message = player.nextComponentMessage();
	assertNotNull(message);
	String text = PlainTextComponentSerializer.plainText().serialize(message);
	assertTrue(text.contains("airdrop.package.starter"));
	assertFalse(text.contains("airdrop.package.Starter"));
}
```

- [ ] **Step 3: Add preserved-name completion coverage**

Configure `packages.Starter`, invoke both completers with an empty prefix, and assert each result contains `Starter` exactly once and contains neither `starter` nor any reserved configured fixture.

```java
assertEquals(1, results.stream().filter("Starter"::equals).count());
assertFalse(results.contains("starter"));
for (String reserved : List.of("all", "package", "packages", "version", "reload")) {
	assertEquals(1, results.stream().filter(reserved::equals).count(), reserved);
}
```

The final assertion count is one because legitimate command suggestions remain present while colliding package entries are rejected.

- [ ] **Step 4: Run permission and command tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.helpers.PermissionsHelperTest \
  --tests com.airdropmc.commands.DropCommandPackageIdentityTest \
  --tests com.airdropmc.commands.TabCompletionPermissionsTest
```

Expected: the legacy exact-case permission fallback and case-sensitive registry lookup cause failures before implementation.

- [ ] **Step 5: Enforce one canonical permission node**

Replace `PermissionsHelper.hasPermission` with:

```java
public static boolean hasPermission(Player player, String packageName) {
	PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
	if (!validation.accepted()) {
		return false;
	}
	if (isAdmin(player)) {
		return true;
	}

	String packageNode = PackageNamePolicy.permissionNode(packageName);
	return player.hasPermission(packageNode)
			|| player.hasPermission(AIRDROP_PACKAGES_ALL);
}
```

Remove the `AIRDROP_PACKAGE` constant and direct `Locale` import from `PermissionsHelper`; package-specific node construction and locale handling now belong exclusively to `PackageNamePolicy`.

Change `DropCommand` permission feedback to use the same policy output:

```java
} catch (InsufficientPermissionsException e) {
	ChatHandler.sendError(player, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS,
			Map.of("permission", PackageNamePolicy.permissionNode(e.getPackageName())));
}
```

Change the enum fallback and `en.yml` message from an appended display-name placeholder to a full canonical-node placeholder:

```text
You lack permission to drop that package (requires {accent}{permission}{error})
```

- [ ] **Step 6: Run permission and command tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.airdropmc.helpers.PermissionsHelperTest \
  --tests com.airdropmc.commands.DropCommandPackageIdentityTest \
  --tests com.airdropmc.commands.TabCompletionPermissionsTest
```

Expected: all three classes pass.

- [ ] **Step 7: Commit permission and reachability behavior**

```bash
git add src/main/java/com/airdropmc/helpers/PermissionsHelper.java \
  src/main/java/com/airdropmc/commands/DropCommand.java \
  src/main/java/com/airdropmc/lang/MessageKey.java \
  src/main/resources/lang/en.yml \
  src/test/java/com/airdropmc/helpers/PermissionsHelperTest.java \
  src/test/java/com/airdropmc/commands/DropCommandPackageIdentityTest.java \
  src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java
git commit -m "AIRDR-8: canonicalize package permissions"
```

### Task 5: Verify AIRDR-8 end to end

**Files:**
- Review: `src/main/java/com/airdropmc/AirdropCommandNames.java`
- Review: `src/main/java/com/airdropmc/packages/PackageNamePolicy.java`
- Review: `src/main/java/com/airdropmc/packages/PackageManager.java`
- Review: `src/main/java/com/airdropmc/controllers/PackageController.java`
- Review: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Review: `src/main/java/com/airdropmc/helpers/PermissionsHelper.java`
- Review: `src/main/java/com/airdropmc/commands/CmdAirdrop.java`
- Review: `src/main/java/com/airdropmc/AirdropTabCompleter.java`
- Review: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Review: `src/main/resources/lang/en.yml`
- Review: `src/test/java/com/airdropmc/**/*.java`

- [ ] **Step 1: Run all focused AIRDR-8 tests without build-cache reuse**

```bash
./gradlew test --rerun-tasks \
  --tests com.airdropmc.packages.PackageNamePolicyTest \
  --tests com.airdropmc.packages.PackageManagerConfigRobustnessTest \
  --tests com.airdropmc.packages.PackageManagerMutationTest \
  --tests com.airdropmc.controllers.PackageControllerPermissionsTest \
  --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest \
  --tests com.airdropmc.helpers.PermissionsHelperTest \
  --tests com.airdropmc.commands.DropCommandPackageIdentityTest \
  --tests com.airdropmc.commands.TabCompletionPermissionsTest
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Run the complete test suite**

```bash
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 3: Run a clean production build**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL` and a plugin JAR under `build/libs/`.

- [ ] **Step 4: Audit requirement coverage**

Confirm from tests and source that:

```text
- all, *, package, packages, version, and reload are rejected without case differences
- invalid YAML entries are skipped with diagnostics
- every member of a case-only YAML conflict is rejected before payload validation with diagnostics
- command, GUI, both public API overloads, and PackageManager share the policy
- accepted packages have one canonical permission node and denial text displays that node
- accepted mixed-case packages are reachable through the drop command
- exact display/YAML casing is preserved
- differently cased update and delete calls modify the stored YAML key
```

- [ ] **Step 5: Inspect the final diff**

```bash
git diff --check
git status --short
git diff 9e917a3 -- src/main/java src/main/resources src/test/java docs/superpowers
```

Expected: no whitespace errors and no changes outside AIRDR-8 implementation, tests, language text, design, and plan documents.

- [ ] **Step 6: Commit any final plan or verification-document updates**

```bash
git add -f docs/superpowers/plans/2026-08-23-airdr-8-package-name-policy.md \
  docs/superpowers/plans/2026-08-23-airdr-8-package-name-policy-doublecheck.md
git commit -m "AIRDR-8: record verified implementation plan"
```
