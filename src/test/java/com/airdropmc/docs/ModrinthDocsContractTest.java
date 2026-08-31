package com.airdropmc.docs;

import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockBukkitExtension.class)
class ModrinthDocsContractTest {
	private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
	private static final Path DOCUMENT = PROJECT_ROOT.resolve("docs/modrinth.md");
	private static final Path SCRIPT = PROJECT_ROOT.resolve("scripts/modrinth-docs");
	private static final String CANONICAL_URL = "https://modrinth.com/plugin/airdrop";
	private static final String TEST_TOKEN = "modrinth-test-token-that-must-not-leak";
	private static final Pattern CONFIG_REFERENCE = Pattern.compile(
			"(?m)^<a id=\"config-([a-z0-9-]+)\"></a>\\s*\\|\\s*`([^`]+)`\\s*"
					+ "\\|\\s*([^|\\r\\n]+)\\|\\s*([^|\\r\\n]+)\\|\\s*([^|\\r\\n]+)"
					+ "\\|\\s*([^|\\r\\n]+)\\|\\s*([^|\\r\\n]+)\\|\\s*$");
	private static final Pattern PACKAGE_EXAMPLE = Pattern.compile(
			"(?s)<!-- packages-example:start -->\\R```ya?ml\\R(.*?)\\R```\\R"
					+ "<!-- packages-example:end -->");

	@Test
	void canonicalBodyHasStableSectionsAndProjectLinks() throws IOException {
		assertTrue(Files.isRegularFile(DOCUMENT), "docs/modrinth.md must be the canonical project body");
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
		assertFalse(body.isBlank());

		for (String heading : List.of(
				"# Airdrop",
				"## Quick links",
				"## Compatibility",
				"## Installation",
				"### Free quick start",
				"### Paid setup",
				"## Commands and permissions",
				"## Configuration",
				"## Package files",
				"## Troubleshooting",
				"## Developer integration",
				"## Project and support")) {
			assertTrue(body.contains(heading), () -> "Missing canonical heading: " + heading);
		}

		for (String link : List.of(
				CANONICAL_URL,
				CANONICAL_URL + "#installation",
				CANONICAL_URL + "#configuration",
				CANONICAL_URL + "#troubleshooting",
				CANONICAL_URL + "#developer-integration",
				"https://github.com/LukeMccon/Airdrop",
				CANONICAL_URL + "/versions",
				"https://github.com/LukeMccon/Airdrop/issues/new?labels=bug",
				"https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement")) {
			assertTrue(body.contains(link), () -> "Missing canonical link: " + link);
		}

		assertFalse(body.toLowerCase().contains("github.com/lukemccon/airdrop/wiki"));
		assertFalse(body.contains("1.21.11+"), "Compatibility claims must stay bounded");
	}

	@Test
	void developerGuidePinsConsumerDependenciesAndLifecycleDiscovery() throws IOException {
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

		for (String heading : List.of(
				"### Compile against Airdrop without shading it",
				"### Discover the service, then schedule Bukkit work",
				"### Requests return typed spawn and terminal stages",
				"### Queries return immutable active-drop snapshots",
				"### Events expose ordering and cancellation boundaries",
				"### Treat delivery and payment as separate results")) {
			assertTrue(body.contains(heading), () -> "Missing integration heading: " + heading);
			assertTrue(script.contains("require_line \"" + heading + "\""),
					() -> "Local documentation verifier must require: " + heading);
		}

		for (String dependency : List.of(
				"maven.modrinth:airdrop:5.0.0",
				"<id>papermc</id>",
				"maven.modrinth</groupId>",
				"airdrop</artifactId>",
				"5.0.0</version>",
				"io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT",
				"<scope>provided</scope>",
				"depend: [Airdrop]",
				"softdepend: [Airdrop]")) {
			assertTrue(body.contains(dependency), () -> "Missing consumer dependency contract: " + dependency);
		}
		assertTrue(body.contains("Do not shade Airdrop"));
		assertTrue(body.contains("getServicesManager().load(AirdropApi.class)"));
		assertTrue(body.contains("api.readiness().whenComplete"));
		assertTrue(body.contains("getScheduler().runTask"));
		assertTrue(body.contains("ReadinessState"));
	}

	@Test
	void developerGuideCoversRequestsOutcomesAndEveryQueryBoundary() throws IOException {
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);

		for (String apiMethod : List.of(
				"requestPlayerDrop", "requestSystemDrop", "descriptor()", "context()",
				"spawn()", "outcome()", "activeDrops()", "findByRequestId",
				"findByCrateId", "findByFallingEntity", "findByLandedBlock")) {
			assertTrue(body.contains(apiMethod), () -> "Missing request/query contract: " + apiMethod);
		}
		for (String outcomeType : List.of(
				"DropOutcome.Rejected", "DropOutcome.Landed", "DropOutcome.Failed",
				"DropSpawnResult.NotSpawned", "DropRejectionReason", "DeliveryStatus",
				"PaymentStatus")) {
			assertTrue(body.contains(outcomeType), () -> "Missing typed result contract: " + outcomeType);
		}
		assertTrue(body.contains("programmer errors"));
		assertTrue(body.contains("Expected pre-spawn rejections are `DropOutcome.Rejected`"));
		assertTrue(body.contains("Later failures use `DropOutcome.Failed`"));
		assertTrue(body.contains("`FAILED`, `CANCELLED`, or `SHUTDOWN`"));
		assertTrue(body.contains("primary server thread"));
		assertTrue(body.contains("latest immutable aggregate snapshot"));
	}

	@Test
	void developerGuideCoversCompleteEventProtectionAndPaymentContracts() throws IOException {
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);

		for (String eventType : List.of(
				"AirdropRequestEvent", "AirdropSpawnedEvent", "PackageDropEvent",
				"AirdropLandingAttemptEvent", "AirdropLandedEvent", "PackageLandEvent",
				"AirdropOutcomeEvent", "PackageRegistryChangedEvent",
				"AirdropRecoveredEvent", "AirdropRetiredEvent", "EntityChangeBlockEvent")) {
			assertTrue(body.contains(eventType), () -> "Missing integration event contract: " + eventType);
		}
		for (String registryTerm : List.of(
				"revision", "created", "updated", "deleted", "PackageRegistryCause",
				"RetirementReason")) {
			assertTrue(body.contains(registryTerm), () -> "Missing registry contract: " + registryTerm);
		}
		for (String paymentState : List.of(
				"NOT_APPLICABLE", "REJECTED", "CHARGED", "REFUNDED", "REFUND_FAILED",
				"UNKNOWN")) {
			assertTrue(body.contains(paymentState), () -> "Missing payment state: " + paymentState);
		}
		assertTrue(body.contains("Resolution failures fire no request event"));
		assertTrue(body.contains("exactly once"));
		assertTrue(body.contains("never retry an `UNKNOWN` payment automatically"));
	}

	@Test
	void configurationReferenceCoversEveryShippedLeafWithItsOperatingContract() throws Exception {
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
		YamlConfiguration shipped = loadBukkitYamlResource("config.yml");
		Map<String, Object> leaves = new LinkedHashMap<>();
		for (String key : shipped.getKeys(true)) {
			if (!shipped.isConfigurationSection(key)) {
				leaves.put(key, shipped.get(key));
			}
		}

		Map<String, ConfigReference> references = parseConfigReferences(body);
		assertEquals(leaves.keySet(), references.keySet(),
				"The reference must contain exactly one anchored row for every shipped config leaf");
		for (Map.Entry<String, Object> leaf : leaves.entrySet()) {
			String key = leaf.getKey();
			ConfigReference reference = references.get(key);
			assertEquals(configAnchor(key), reference.anchor(), "Wrong anchor for " + key);
			assertFalse(reference.type().isBlank(), "Missing type for " + key);
			assertEquals("`" + leaf.getValue() + "`", reference.defaultValue(),
					"Documented default must match the shipped config for " + key);
			assertFalse(reference.acceptedValues().isBlank(), "Missing range or allowed values for " + key);
			assertFalse(reference.fallback().isBlank(), "Missing fallback for " + key);
			assertFalse(reference.reloadBehavior().isBlank(), "Missing reload behavior for " + key);
		}
	}

	@Test
	void markedPackageExampleParsesAndMaterializesThroughTheRuntimeLoader() throws Exception {
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
		Matcher example = PACKAGE_EXAMPLE.matcher(body);
		assertTrue(example.find(), "Missing marked packages.yml example");
		String exampleYaml = example.group(1);
		assertFalse(example.find(), "The operating guide must have one marked packages.yml example");

		YamlConfiguration candidate = new YamlConfiguration();
		candidate.loadFromString(exampleYaml);
		Map<String, Package> packages = PackageManager.materializePackages(candidate, Set.of());

		assertEquals(Set.of("starter", "premium"), packages.keySet());
		Package starter = packages.get("starter");
		assertEquals("starter", starter.getName());
		assertEquals(0.0, starter.getPrice());
		assertEquals(List.of(Material.BREAD, Material.TORCH), starter.getItems().stream()
				.map(item -> item.getType())
				.toList());
		assertEquals(List.of(16, 32), starter.getItems().stream()
				.map(item -> item.getAmount())
				.toList());
		assertEquals("premium", packages.get("premium").getName());
		assertEquals(25.5, packages.get("premium").getPrice());
		assertEquals(List.of(Material.DIAMOND), packages.get("premium").getItems().stream()
				.map(item -> item.getType())
				.toList());
		assertTrue(packages.values().stream()
				.allMatch(pkg -> pkg.getItems().size() <= PackageManager.MAX_PACKAGE_ITEM_STACKS));
	}

	@Test
	void shippedPackagesFileIsAnExplicitEmptyRegistry() throws Exception {
		YamlConfiguration packages = loadBukkitYamlResource("packages.yml");

		assertTrue(packages.isConfigurationSection(PackageManager.PACKAGES_SECTION));
		assertTrue(PackageManager.materializePackages(packages, Set.of()).isEmpty());
	}

	@Test
	void invalidPriceMessagesDescribeWholeCandidateRejection() throws IOException {
		String defaultMessage = MessageKey.SYSTEM_PACKAGE_PRICE_INVALID.getDefault();
		Map<?, ?> language = loadYamlResource("lang/en.yml");
		String configuredMessage = String.valueOf(yamlMap(language.get("system")).get("package-price-invalid"));

		for (String message : List.of(defaultMessage, configuredMessage)) {
			assertFalse(message.contains("Falling back to 0.0"));
			assertTrue(message.contains("Package configuration rejected"));
		}
	}

	@Test
	void publisherRendersTheExactBodyAsDeterministicJson() throws Exception {
		assertPublisherAvailable();
		String body = Files.readString(DOCUMENT, StandardCharsets.UTF_8);

		ProcessResult first = runScript(Map.of(), "render-payload");
		ProcessResult second = runScript(Map.of(), "render-payload");

		assertEquals(0, first.exitCode(), first.combinedOutput());
		assertEquals(first.stdout(), second.stdout());
		Map<?, ?> payload = yamlMap(new Yaml(new SafeConstructor(new LoaderOptions())).load(first.stdout()));
		assertEquals(body, payload.get("body"));

		String source = Files.readString(SCRIPT, StandardCharsets.UTF_8);
		assertTrue(source.contains("jq -cn --rawfile"), "Payload must read the body as raw file bytes");
		assertTrue(source.contains("jq -e -j"), "Remote extraction must not append a newline");
		assertTrue(source.contains("cmp"), "Body equality must use a byte comparison");
	}

	@Test
	void localCheckNeedsNoCredentialsOrNetwork() throws Exception {
		assertPublisherAvailable();

		ProcessResult result = runScript(Map.of(), "check-local");

		assertEquals(0, result.exitCode(), result.combinedOutput());
		assertTrue(result.combinedOutput().contains("Modrinth documentation is valid"));
	}

	@Test
	void remoteCheckUsesOnlyTheGuardedLoopbackEndpointAndExactBytes() throws Exception {
		assertPublisherAvailable();
		String payload = successful(runScript(Map.of(), "render-payload")).stdout();
		try (RemoteProject remote = RemoteProject.withPayload(payload)) {
			ProcessResult result = runScript(remote.environment(false), "check-remote");

			assertEquals(0, result.exitCode(), result.combinedOutput());
			assertEquals(1, remote.getCount.get());
			assertEquals(0, remote.patchCount.get());
			assertEquals(List.of("GET"), remote.methods);
			assertTrue(remote.userAgents.getFirst().startsWith("Airdrop-Modrinth-Docs/"));
		}
	}

	@Test
	void remoteCheckFailsClosedOnDriftAndDoesNotFollowRedirects() throws Exception {
		assertPublisherAvailable();
		try (RemoteProject remote = RemoteProject.withPayload("{\"body\":\"drift\"}")) {
			ProcessResult drift = runScript(remote.environment(false), "check-remote");

			assertTrue(drift.exitCode() != 0, drift.combinedOutput());
			assertEquals(0, remote.patchCount.get());
		}

		try (RemoteProject remote = RemoteProject.redirecting()) {
			ProcessResult redirect = runScript(remote.environment(false), "check-remote");

			assertTrue(redirect.exitCode() != 0, redirect.combinedOutput());
			assertEquals(0, remote.trapCount.get(), "Publisher must never follow redirects");
		}
	}

	@Test
	void publishComparesBeforePatchThenRechecksAndConverges() throws Exception {
		assertPublisherAvailable();
		String payload = successful(runScript(Map.of(), "render-payload")).stdout();
		try (RemoteProject remote = RemoteProject.withPayload("{\"body\":\"old\"}")) {
			ProcessResult first = runScript(remote.environment(true), "publish");
			ProcessResult second = runScript(remote.environment(true), "publish");

			assertEquals(0, first.exitCode(), first.combinedOutput());
			assertEquals(0, second.exitCode(), second.combinedOutput());
			assertEquals(1, remote.patchCount.get(), "An already converged body must not be patched again");
			assertEquals(3, remote.getCount.get(), "First publish rechecks; second publish compares once");
			assertEquals(List.of(payload), remote.patchBodies);
			assertEquals(List.of(TEST_TOKEN, TEST_TOKEN, TEST_TOKEN, TEST_TOKEN), remote.authorizations);
			assertFalse(first.combinedOutput().contains(TEST_TOKEN));
			assertFalse(second.combinedOutput().contains(TEST_TOKEN));
		}
	}

	@Test
	void publishRejectsMissingCredentialsAndUnsafeEndpointBeforeAnyRequest() throws Exception {
		assertPublisherAvailable();
		try (RemoteProject remote = RemoteProject.withPayload("{\"body\":\"old\"}")) {
			ProcessResult missingToken = runScript(remote.environment(false), "publish");
			assertTrue(missingToken.exitCode() != 0, missingToken.combinedOutput());
			assertEquals(0, remote.requestCount());

			Map<String, String> unsafe = new LinkedHashMap<>(remote.environment(true));
			unsafe.put("MODRINTH_API_BASE", "http://localhost:" + remote.port() + "/v2");
			ProcessResult unsafeEndpoint = runScript(unsafe, "publish");
			assertTrue(unsafeEndpoint.exitCode() != 0, unsafeEndpoint.combinedOutput());
			assertEquals(0, remote.requestCount());
			assertFalse(unsafeEndpoint.combinedOutput().contains(TEST_TOKEN));

			unsafe.put("MODRINTH_API_BASE", "http://127.0.0.1:12@127.0.0.1:" + remote.port() + "/v2");
			ProcessResult userInfoEndpoint = runScript(unsafe, "publish");
			assertTrue(userInfoEndpoint.exitCode() != 0, userInfoEndpoint.combinedOutput());
			assertTrue(userInfoEndpoint.combinedOutput().contains("guarded 127.0.0.1 test endpoint"));
			assertEquals(0, remote.requestCount(), "URL user-info must not bypass the loopback guard");
			assertFalse(userInfoEndpoint.combinedOutput().contains(TEST_TOKEN));
		}
	}

	@Test
	void buildReadmePluginMetadataAndReleaseWorkflowUseTheCanonicalSource() throws Exception {
		String build = Files.readString(PROJECT_ROOT.resolve("build.gradle.kts"), StandardCharsets.UTF_8);
		assertTrue(build.contains("verifyModrinthDocs"));
		assertTrue(build.contains("commandLine(\"./scripts/modrinth-docs\", \"check-local\")"));
		assertTrue(build.contains("dependsOn(verifyModrinthDocs)"));

		String readme = Files.readString(PROJECT_ROOT.resolve("README.md"), StandardCharsets.UTF_8);
		assertTrue(readme.contains(CANONICAL_URL));
		assertTrue(readme.contains(CANONICAL_URL + "#installation"));
		assertTrue(readme.contains("./gradlew clean build"));
		assertFalse(readme.contains("## Configuration"), "Detailed reference belongs in the canonical body");
		assertFalse(readme.toLowerCase().contains("github.com/lukemccon/airdrop/wiki"));

		Map<?, ?> plugin = loadYamlResource("plugin.yml");
		assertEquals(CANONICAL_URL, plugin.get("website"));
		assertTrue(MessageKey.SYSTEM_VERSION_INFO.getDefault().contains("Docs and support:"));
		Map<?, ?> language = loadYamlResource("lang/en.yml");
		Map<?, ?> system = yamlMap(language.get("system"));
		assertTrue(String.valueOf(system.get("version-info")).contains("Docs and support:"));

		Map<?, ?> workflow = loadYaml(PROJECT_ROOT.resolve(".github/workflows/release.yml"));
		Map<?, ?> jobs = yamlMap(workflow.get("jobs"));
		Map<?, ?> docsJob = yamlMap(jobs.get("publish-modrinth-docs"));
		assertEquals("publish-modrinth", docsJob.get("needs"));
		assertFalse(docsJob.containsKey("env"), "The token must only be available to the publication step");
		assertTrue(docsJob.get("steps") instanceof List<?>);
		Map<?, ?> publicationStep = ((List<?>) docsJob.get("steps")).stream()
				.map(this::yamlMap)
				.filter(step -> String.valueOf(step.get("run")).contains("./scripts/modrinth-docs publish"))
				.findFirst()
				.orElseThrow();
		Map<?, ?> environment = yamlMap(publicationStep.get("env"));
		assertEquals("${{ vars.MODRINTH_PROJECT_ID }}", environment.get("MODRINTH_PROJECT_ID"));
		assertEquals("${{ secrets.MODRINTH_TOKEN }}", environment.get("MODRINTH_TOKEN"));
		String docsJobText = String.valueOf(docsJob);
		assertTrue(docsJobText.contains("${{ github.event.release.tag_name }}"));
		assertTrue(docsJobText.contains("./scripts/modrinth-docs publish"));
		assertTrue(docsJobText.contains("./scripts/modrinth-docs check-remote"));
	}

	private void assertPublisherAvailable() {
		assertTrue(Files.isRegularFile(SCRIPT), "scripts/modrinth-docs must exist");
		assertTrue(Files.isExecutable(SCRIPT), "scripts/modrinth-docs must be executable");
	}

	private ProcessResult runScript(Map<String, String> additions, String command) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(SCRIPT.toString(), command)
				.directory(PROJECT_ROOT.toFile());
		Map<String, String> environment = builder.environment();
		for (String key : List.of(
				"MODRINTH_API_BASE",
				"MODRINTH_DOCS_ALLOW_LOOPBACK",
				"MODRINTH_PROJECT_ID",
				"MODRINTH_TOKEN")) {
			environment.remove(key);
		}
		environment.putAll(additions);

		Process process = builder.start();
		boolean completed = process.waitFor(15, TimeUnit.SECONDS);
		if (!completed) {
			process.destroyForcibly();
		}
		assertTrue(completed, "Publisher process timed out");
		String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		return new ProcessResult(process.exitValue(), stdout, stderr);
	}

	private ProcessResult successful(ProcessResult result) {
		assertEquals(0, result.exitCode(), result.combinedOutput());
		return result;
	}

	private Map<?, ?> loadYamlResource(String name) throws IOException {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
			assertNotNull(input, "Missing generated test resource: " + name);
			return yamlMap(new Yaml(new SafeConstructor(new LoaderOptions())).load(input));
		}
	}

	private Map<?, ?> loadYaml(Path path) throws IOException {
		return yamlMap(new Yaml(new SafeConstructor(new LoaderOptions())).load(
				Files.readString(path, StandardCharsets.UTF_8)));
	}

	private YamlConfiguration loadBukkitYamlResource(String name) throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
			assertNotNull(input, "Missing generated test resource: " + name);
			YamlConfiguration configuration = new YamlConfiguration();
			configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
			return configuration;
		}
	}

	private Map<String, ConfigReference> parseConfigReferences(String body) {
		Map<String, ConfigReference> references = new LinkedHashMap<>();
		Matcher matcher = CONFIG_REFERENCE.matcher(body);
		while (matcher.find()) {
			String key = matcher.group(2);
			ConfigReference reference = new ConfigReference(
					matcher.group(1),
					matcher.group(3).trim(),
					matcher.group(4).trim(),
					matcher.group(5).trim(),
					matcher.group(6).trim(),
					matcher.group(7).trim());
			assertTrue(references.put(key, reference) == null, "Duplicate config reference for " + key);
		}
		return references;
	}

	private String configAnchor(String key) {
		return key.replace('.', '-');
	}

	private Map<?, ?> yamlMap(Object value) {
		assertTrue(value instanceof Map<?, ?>, "Expected a YAML/JSON object");
		return (Map<?, ?>) value;
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {
		private String combinedOutput() {
			return stdout + stderr;
		}
	}

	private record ConfigReference(
			String anchor,
			String type,
			String defaultValue,
			String acceptedValues,
			String fallback,
			String reloadBehavior) {
	}

	private static final class RemoteProject implements AutoCloseable {
		private final HttpServer server;
		private final AtomicReference<String> remotePayload;
		private final boolean redirect;
		private final AtomicInteger getCount = new AtomicInteger();
		private final AtomicInteger patchCount = new AtomicInteger();
		private final AtomicInteger trapCount = new AtomicInteger();
		private final List<String> methods = new CopyOnWriteArrayList<>();
		private final List<String> userAgents = new CopyOnWriteArrayList<>();
		private final List<String> authorizations = new CopyOnWriteArrayList<>();
		private final List<String> patchBodies = new CopyOnWriteArrayList<>();

		private RemoteProject(String payload, boolean redirect) throws IOException {
			this.remotePayload = new AtomicReference<>(payload);
			this.redirect = redirect;
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/v2/project/test-project", this::handleProject);
			server.createContext("/trap", exchange -> {
				trapCount.incrementAndGet();
				send(exchange, 200, "{\"body\":\"trap\"}");
			});
			server.start();
		}

		private static RemoteProject withPayload(String payload) throws IOException {
			return new RemoteProject(payload, false);
		}

		private static RemoteProject redirecting() throws IOException {
			return new RemoteProject("", true);
		}

		private Map<String, String> environment(boolean includeToken) {
			Map<String, String> environment = new LinkedHashMap<>();
			environment.put("MODRINTH_API_BASE", "http://127.0.0.1:" + port() + "/v2");
			environment.put("MODRINTH_DOCS_ALLOW_LOOPBACK", "1");
			environment.put("MODRINTH_PROJECT_ID", "test-project");
			if (includeToken) {
				environment.put("MODRINTH_TOKEN", TEST_TOKEN);
			}
			return environment;
		}

		private int port() {
			return server.getAddress().getPort();
		}

		private int requestCount() {
			return getCount.get() + patchCount.get() + trapCount.get();
		}

		private void handleProject(HttpExchange exchange) throws IOException {
			methods.add(exchange.getRequestMethod());
			userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			if (authorization != null) {
				authorizations.add(authorization);
			}

			if (redirect) {
				exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port() + "/trap");
				send(exchange, 302, "redirect");
				return;
			}

			if ("GET".equals(exchange.getRequestMethod())) {
				getCount.incrementAndGet();
				send(exchange, 200, remotePayload.get());
				return;
			}

			if ("PATCH".equals(exchange.getRequestMethod())) {
				patchCount.incrementAndGet();
				String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				patchBodies.add(payload);
				remotePayload.set(payload);
				send(exchange, 200, payload);
				return;
			}

			send(exchange, 405, "method not allowed");
		}

		private static void send(HttpExchange exchange, int status, String body) throws IOException {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
