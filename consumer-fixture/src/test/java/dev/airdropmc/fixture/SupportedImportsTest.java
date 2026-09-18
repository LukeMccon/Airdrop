package dev.airdropmc.fixture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedImportsTest {
	private static final Path FIXTURE_SOURCE = Path.of(
			"src", "main", "java", "dev", "airdropmc", "fixture", "AirdropConsumerFixture.java");

	@Test
	void fixtureUsesOnlyTheSupportedAirdropApiInSourceAndBytecode() throws IOException {
		assertTrue(Files.isRegularFile(FIXTURE_SOURCE), () -> "Missing consumer source " + FIXTURE_SOURCE);
		String source = Files.readString(FIXTURE_SOURCE, StandardCharsets.UTF_8);
		List<String> forbiddenImports = source.lines()
				.map(String::trim)
				.filter(line -> line.startsWith("import com.airdropmc."))
				.filter(Predicate.not(line -> line.startsWith("import com.airdropmc.api.")))
				.toList();
		assertTrue(forbiddenImports.isEmpty(), forbiddenImports::toString);

		Path classes = Path.of("build", "classes", "java", "main");
		try (Stream<Path> paths = Files.walk(classes)) {
			for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
				String constantPool = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
				int packageReference = constantPool.indexOf("com/airdropmc/");
				while (packageReference >= 0) {
					assertTrue(
							constantPool.startsWith("com/airdropmc/api/", packageReference),
							() -> classFile + " references an unsupported Airdrop implementation package");
					packageReference = constantPool.indexOf("com/airdropmc/", packageReference + 1);
				}
			}
		}
	}

	@Test
	void fixtureConsumesTheDependencyFreeMavenPomWithoutModuleMetadata() throws IOException {
		Path repository = Path.of(requiredProperty("airdrop.repository"));
		List<Path> poms;
		try (Stream<Path> paths = Files.walk(repository)) {
			poms = paths.filter(path -> path.toString().endsWith(".pom")).toList();
		}
		assertEquals(1, poms.size(), poms::toString);
		String pom = Files.readString(poms.getFirst(), StandardCharsets.UTF_8);
		assertTrue(pom.contains("<groupId>maven.modrinth</groupId>"));
		assertTrue(pom.contains("<artifactId>airdrop</artifactId>"));
		assertTrue(pom.contains("<version>" + requiredProperty("airdrop.version") + "</version>"));
		assertFalse(pom.contains("<dependencies>"));
		try (Stream<Path> paths = Files.walk(repository)) {
			assertFalse(paths.anyMatch(path -> path.toString().endsWith(".module")));
		}

		String build = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8);
		assertTrue(build.contains("mavenPom()"));
		assertTrue(build.contains("ignoreGradleMetadataRedirection()"));
		assertFalse(build.contains("artifact()"));
		assertTrue(build.contains("exclusiveContent"));
	}

	@Test
	void generatedPluginMetadataDeclaresAirdropAsAHardDependency() throws IOException {
		String pluginYml = Files.readString(
				Path.of("build", "resources", "main", "plugin.yml"), StandardCharsets.UTF_8);
		assertTrue(pluginYml.contains("name: AirdropConsumerFixture"));
		assertTrue(pluginYml.contains("main: dev.airdropmc.fixture.AirdropConsumerFixture"));
		assertTrue(pluginYml.contains("depend: [Airdrop]"));
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		assertTrue(value != null && !value.isBlank(), () -> "Missing system property " + name);
		return value;
	}
}
