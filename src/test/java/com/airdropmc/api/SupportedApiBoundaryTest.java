package com.airdropmc.api;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.Crate;
import com.airdropmc.PackagesConfig;
import com.airdropmc.config.AbstractConfig;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedApiBoundaryTest {

	private static final List<String> FORBIDDEN_SIGNATURE_FRAGMENTS = List.of(
			"com.airdropmc.Crate",
			"com.airdropmc.config.",
			"com.airdropmc.controllers.",
			"com.airdropmc.packages.Package",
			"com.airdropmc.helpers.CrateManager",
			"com.airdropmc.limits.DropAdmissionController",
			"com.airdropmc.limits.DropLocationKey");

	@Test
	void everyPublicApiTypeIsClosedForMutationAndHasNoImplementationSignatureLeaks()
			throws IOException, URISyntaxException, ClassNotFoundException {
		Set<Class<?>> apiTypes = discoverPublicApiTypes();

		assertTrue(apiTypes.containsAll(Set.of(
				AirdropApi.class,
				AirdropPackage.class,
				AirdropStatus.class,
				AirdropVersions.class,
				AirdropView.class,
				DropRequestOptions.class,
				ResolvedDropSettings.class,
				DropRequestDescriptor.class,
				ResolvedDropContext.class,
				WorldPosition.class,
				ReadinessState.class,
				EconomyState.class,
				DropSource.class,
				DropState.class,
				FallingAirdropView.class,
				LandedAirdropView.class)));

		for (Class<?> type : apiTypes) {
			assertTrue(type.isInterface() || type.isEnum() || type.isRecord()
					|| Modifier.isFinal(type.getModifiers()) || type.isSealed(),
					() -> type.getName() + " must be final, sealed, a record, an enum, or an interface");
			for (Constructor<?> constructor : type.getDeclaredConstructors()) {
				if (Modifier.isPublic(constructor.getModifiers())) {
					assertSupported(constructor.toGenericString());
				}
			}
			for (Method method : type.getDeclaredMethods()) {
				if (!Modifier.isPublic(method.getModifiers())) {
					continue;
				}
				assertSupported(method.toGenericString());
				assertFalse(method.getName().startsWith("set") && method.getReturnType() == void.class,
						() -> method + " is a mutable setter on the supported API");
				assertSupported(method.getGenericReturnType().getTypeName());
				for (Type parameterType : method.getGenericParameterTypes()) {
					assertSupported(parameterType.getTypeName());
				}
			}
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isPublic(field.getModifiers())) {
					assertSupported(field.toGenericString());
				}
			}
		}
	}

	@Test
	void implementationEntryPointsCarryTheInternalClassfileMarker() throws IOException {
		for (Class<?> implementationType : List.of(
				Airdrop.class,
				Crate.class,
				DropOptions.class,
				DropController.class,
				com.airdropmc.packages.Package.class,
				PackageManager.class,
				CrateManager.class,
				Config.class,
				PackagesConfig.class,
				AbstractConfig.class,
				ConfigKeys.class)) {
			String classfile = new String(readClassfile(implementationType), StandardCharsets.ISO_8859_1);
			assertTrue(classfile.contains("org/jetbrains/annotations/ApiStatus$Internal"),
					() -> implementationType.getName() + " must be marked @ApiStatus.Internal");
		}
	}

	private static Set<Class<?>> discoverPublicApiTypes()
			throws IOException, URISyntaxException, ClassNotFoundException {
		Path apiRoot = Path.of(AirdropPackage.class.getProtectionDomain().getCodeSource().getLocation().toURI())
				.resolve("com/airdropmc/api");
		List<Class<?>> discovered = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(apiRoot)) {
			for (Path classfile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
				String relative = apiRoot.relativize(classfile).toString().replace(classfile.getFileSystem().getSeparator(), ".");
				String simpleName = relative.substring(0, relative.length() - ".class".length());
				if (simpleName.startsWith("event.")) {
					continue;
				}
				Class<?> type = Class.forName("com.airdropmc.api." + simpleName, false,
						AirdropPackage.class.getClassLoader());
				if (Modifier.isPublic(type.getModifiers())) {
					discovered.add(type);
				}
			}
		}
		return Set.copyOf(discovered);
	}

	private static byte[] readClassfile(Class<?> type) throws IOException {
		String resource = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream stream = type.getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IOException("Missing class resource " + resource);
			}
			return stream.readAllBytes();
		}
	}

	private static void assertSupported(String signature) {
		for (String forbidden : FORBIDDEN_SIGNATURE_FRAGMENTS) {
			assertFalse(signature.contains(forbidden),
					() -> "Supported API signature leaks " + forbidden + ": " + signature);
		}
	}
}
