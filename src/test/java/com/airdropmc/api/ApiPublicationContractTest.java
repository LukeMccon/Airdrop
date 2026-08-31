package com.airdropmc.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiPublicationContractTest {
	private static final Set<String> CHECKSUM_SUFFIXES = Set.of(
			".md5", ".sha1", ".sha256", ".sha512");

	@Test
	void publicationContainsExactlyRuntimeSourcesJavadocsPomAndMetadata() throws IOException {
		List<Path> primaryFiles = publicationFiles().stream()
				.filter(Predicate.not(ApiPublicationContractTest::isChecksum))
				.toList();
		long expectedMetadataCount = pluginVersion().endsWith("-SNAPSHOT") ? 2L : 1L;

		assertEquals(4L + expectedMetadataCount, primaryFiles.size(), primaryFiles::toString);
		assertEquals(3, primaryFiles.stream().filter(path -> path.toString().endsWith(".jar")).count());
		assertEquals(1, primaryFiles.stream().filter(path -> path.toString().endsWith(".pom")).count());
		assertEquals(expectedMetadataCount, primaryFiles.stream()
				.filter(path -> path.getFileName().toString().equals("maven-metadata.xml"))
				.count());
		assertFalse(primaryFiles.stream().anyMatch(path -> path.toString().endsWith(".module")));
	}

	@Test
	void everyPublishedFileHasVerifiedChecksums() throws IOException, NoSuchAlgorithmException {
		List<Path> files = publicationFiles();
		for (Path publishedFile : files.stream().filter(Predicate.not(ApiPublicationContractTest::isChecksum)).toList()) {
			for (String suffix : CHECKSUM_SUFFIXES) {
				Path checksumFile = publishedFile.resolveSibling(publishedFile.getFileName() + suffix);
				assertTrue(Files.isRegularFile(checksumFile), checksumFile::toString);
				String algorithm = suffix.substring(1).replace("sha", "SHA-").toUpperCase(Locale.ROOT);
				if (algorithm.equals("MD5")) {
					algorithm = "MD5";
				}
				String expected = HexFormat.of().formatHex(
						MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(publishedFile)));
				assertEquals(expected, Files.readString(checksumFile, StandardCharsets.US_ASCII).trim());
			}
		}
	}

	@Test
	void pomUsesConsumerCoordinateWithoutDependenciesOrGradleMetadataRedirect() throws IOException {
		Path pom = onlyFile(path -> path.toString().endsWith(".pom"));
		String content = Files.readString(pom, StandardCharsets.UTF_8);

		assertTrue(content.contains("<groupId>maven.modrinth</groupId>"));
		assertTrue(content.contains("<artifactId>airdrop</artifactId>"));
		assertTrue(content.contains("<version>" + pluginVersion() + "</version>"));
		assertFalse(content.contains("<dependencies>"));
		assertFalse(content.contains("do_not_remove: published-with-gradle-metadata"));
	}

	@Test
	void artifactsContainRuntimeSourcesAndOnlySupportedApiJavadocs() throws IOException {
		Path runtime = onlyFile(path -> path.toString().endsWith(".jar")
				&& !path.toString().endsWith("-sources.jar")
				&& !path.toString().endsWith("-javadoc.jar"));
		Path sources = onlyFile(path -> path.toString().endsWith("-sources.jar"));
		Path javadocs = onlyFile(path -> path.toString().endsWith("-javadoc.jar"));

		assertJarContains(runtime, "com/airdropmc/api/AirdropApi.class");
		assertJarContains(runtime, "com/airdropmc/api/event/AirdropOutcomeEvent.class");
		assertJarContains(sources, "com/airdropmc/api/AirdropApi.java");
		assertJarContains(sources, "com/airdropmc/api/event/AirdropOutcomeEvent.java");
		assertJarContains(javadocs, "com/airdropmc/api/AirdropApi.html");
		assertJarContains(javadocs, "com/airdropmc/api/event/AirdropOutcomeEvent.html");
		try (JarFile archive = new JarFile(javadocs.toFile())) {
			assertFalse(archive.stream().anyMatch(entry -> entry.getName().startsWith("com/airdropmc/internal/")));
			assertFalse(archive.stream().anyMatch(entry -> entry.getName().startsWith("com/airdropmc/events/")));
		}
	}

	private static void assertJarContains(Path archivePath, String entryName) throws IOException {
		try (JarFile archive = new JarFile(archivePath.toFile())) {
			assertNotNull(archive.getJarEntry(entryName), archivePath + " missing " + entryName);
		}
	}

	private static Path onlyFile(Predicate<Path> predicate) throws IOException {
		List<Path> matches = publicationFiles().stream().filter(predicate).toList();
		assertEquals(1, matches.size(), matches::toString);
		return matches.getFirst();
	}

	private static List<Path> publicationFiles() throws IOException {
		Path repository = Path.of(requiredProperty("airdrop.apiPublicationRepository"));
		assertTrue(Files.isDirectory(repository), repository::toString);
		try (Stream<Path> paths = Files.walk(repository)) {
			return paths.filter(Files::isRegularFile).sorted().toList();
		}
	}

	private static String pluginVersion() {
		return requiredProperty("airdrop.pluginVersion");
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		assertNotNull(value, () -> "Missing system property " + name);
		assertFalse(value.isBlank(), () -> "Blank system property " + name);
		return value;
	}

	private static boolean isChecksum(Path path) {
		String filename = path.getFileName().toString();
		return CHECKSUM_SUFFIXES.stream().anyMatch(filename::endsWith);
	}
}
