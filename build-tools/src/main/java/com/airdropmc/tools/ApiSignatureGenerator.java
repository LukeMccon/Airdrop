package com.airdropmc.tools;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationDefaultAttribute;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.ConstantAttribute;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Generates a normalized signature without loading any inspected class. */
public final class ApiSignatureGenerator {
	private static final String API_PREFIX = "com.airdropmc.api.";
	private static final int TYPE_FLAGS = AccessFlag.PUBLIC | AccessFlag.PROTECTED
			| AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.INTERFACE
			| AccessFlag.ABSTRACT | AccessFlag.ANNOTATION | AccessFlag.ENUM;
	private static final int FIELD_FLAGS = AccessFlag.PUBLIC | AccessFlag.PROTECTED
			| AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.VOLATILE
			| AccessFlag.TRANSIENT | AccessFlag.ENUM;
	private static final int METHOD_FLAGS = AccessFlag.PUBLIC | AccessFlag.PROTECTED
			| AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.SYNCHRONIZED
			| AccessFlag.VARARGS | AccessFlag.NATIVE | AccessFlag.ABSTRACT
			| AccessFlag.STRICT;

	private ApiSignatureGenerator() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length < 2) {
			throw new IllegalArgumentException(
					"Usage: ApiSignatureGenerator <output> <classes-directory>...");
		}
		Path output = Path.of(arguments[0]);
		Set<String> signatures = new TreeSet<>();
		for (int index = 1; index < arguments.length; index++) {
			Path classesDirectory = Path.of(arguments[index]);
			if (!Files.isDirectory(classesDirectory)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(classesDirectory)) {
				files.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().endsWith(".class"))
						.sorted()
						.forEach(path -> inspect(path, signatures));
			}
		}
		if (signatures.isEmpty()) {
			throw new IllegalStateException("No supported com.airdropmc.api signatures were generated");
		}

		Files.createDirectories(output.toAbsolutePath().getParent());
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(
				temporary,
				String.join("\n", signatures) + "\n",
				StandardCharsets.UTF_8);
		try {
			Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
		}
		System.out.printf(Locale.ROOT, "Generated %d normalized API signatures at %s%n",
				signatures.size(), output);
	}

	private static void inspect(Path classFile, Set<String> signatures) {
		try (DataInputStream input = new DataInputStream(new BufferedInputStream(
				Files.newInputStream(classFile)))) {
			ClassFile type = new ClassFile(input);
			if (!type.getName().startsWith(API_PREFIX)) {
				return;
			}
			int innerFlags = type.getInnerAccessFlags();
			int effectiveFlags = innerFlags >= 0 ? innerFlags : type.getAccessFlags();
			if (!isSupported(effectiveFlags) || isSet(effectiveFlags, AccessFlag.SYNTHETIC)) {
				return;
			}

			String owner = type.getName();
			signatures.add(typeSignature(type, effectiveFlags));
			addAnnotations("TYPE_ANNOTATION " + owner, type.getAttributes(), signatures);
			addDeprecation("TYPE_DEPRECATED " + owner, type.getAttributes(), signatures);
			addRecordComponents(type, signatures);
			addPermittedSubclasses(type, signatures);
			for (FieldInfo field : type.getFields()) {
				addField(owner, field, signatures);
			}
			for (MethodInfo method : type.getMethods()) {
				addMethod(owner, method, signatures);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not inspect classfile " + classFile, exception);
		}
	}

	private static String typeSignature(ClassFile type, int effectiveFlags) {
		String[] interfaces = type.getInterfaces();
		Arrays.sort(interfaces);
		return "TYPE " + flags(effectiveFlags, TYPE_FLAGS)
				+ " " + type.getName()
				+ " SUPER " + value(type.getSuperclass())
				+ " INTERFACES " + String.join(",", interfaces)
				+ " GENERIC " + signature(type.getAttributes());
	}

	private static void addField(String owner, FieldInfo field, Set<String> signatures) {
		int access = field.getAccessFlags();
		if (!isSupported(access) || isSet(access, AccessFlag.SYNTHETIC)) {
			return;
		}
		String identity = owner + "." + field.getName();
		signatures.add("FIELD " + flags(access, FIELD_FLAGS)
				+ " " + identity
				+ " DESCRIPTOR " + field.getDescriptor()
				+ " GENERIC " + signature(field.getAttributes())
				+ " CONSTANT " + constant(field));
		addAnnotations("FIELD_ANNOTATION " + identity, field.getAttributes(), signatures);
		addDeprecation("FIELD_DEPRECATED " + identity, field.getAttributes(), signatures);
	}

	private static void addMethod(String owner, MethodInfo method, Set<String> signatures) {
		int access = method.getAccessFlags();
		if (method.isStaticInitializer()
				|| !isSupported(access)
				|| isSet(access, AccessFlag.SYNTHETIC)
				|| isSet(access, AccessFlag.BRIDGE)) {
			return;
		}
		String identity = owner + "." + method.getName() + method.getDescriptor();
		ExceptionsAttribute exceptions = method.getExceptionsAttribute();
		String[] exceptionNames = exceptions == null ? new String[0] : exceptions.getExceptions();
		Arrays.sort(exceptionNames);
		signatures.add("METHOD " + flags(access, METHOD_FLAGS)
				+ " " + identity
				+ " GENERIC " + signature(method.getAttributes())
				+ " THROWS " + (exceptionNames.length == 0
						? "-"
						: String.join(",", exceptionNames)));
		addAnnotations("METHOD_ANNOTATION " + identity, method.getAttributes(), signatures);
		addParameterAnnotations(identity, method.getAttributes(), signatures);
		addDeprecation("METHOD_DEPRECATED " + identity, method.getAttributes(), signatures);
		AttributeInfo defaultAttribute = attribute(
				method.getAttributes(), AnnotationDefaultAttribute.tag);
		if (defaultAttribute instanceof AnnotationDefaultAttribute annotationDefault) {
			signatures.add("METHOD_DEFAULT " + identity + " "
					+ annotationDefault.getDefaultValue());
		}
	}

	private static void addAnnotations(
			String prefix, List<AttributeInfo> attributes, Set<String> signatures) {
		for (String tag : List.of(
				AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag)) {
			AttributeInfo attribute = attribute(attributes, tag);
			if (!(attribute instanceof AnnotationsAttribute annotations)) {
				continue;
			}
			Arrays.stream(annotations.getAnnotations())
					.map(Annotation::toString)
					.sorted()
					.forEach(annotation -> signatures.add(prefix + " " + tag + " " + annotation));
		}
	}

	private static void addParameterAnnotations(
			String identity, List<AttributeInfo> attributes, Set<String> signatures) {
		for (String tag : List.of(
				ParameterAnnotationsAttribute.visibleTag,
				ParameterAnnotationsAttribute.invisibleTag)) {
			AttributeInfo attribute = attribute(attributes, tag);
			if (!(attribute instanceof ParameterAnnotationsAttribute parameterAnnotations)) {
				continue;
			}
			Annotation[][] annotations = parameterAnnotations.getAnnotations();
			for (int parameter = 0; parameter < annotations.length; parameter++) {
				int parameterIndex = parameter;
				Arrays.stream(annotations[parameter])
						.map(Annotation::toString)
						.sorted()
						.forEach(annotation -> signatures.add(
								"PARAMETER_ANNOTATION " + identity + " " + parameterIndex
										+ " " + tag + " " + annotation));
			}
		}
	}

	private static void addDeprecation(
			String signature, List<AttributeInfo> attributes, Set<String> signatures) {
		if (attribute(attributes, "Deprecated") != null) {
			signatures.add(signature);
		}
	}

	private static void addRecordComponents(ClassFile type, Set<String> signatures)
			throws IOException {
		AttributeInfo record = type.getAttribute("Record");
		if (record == null) {
			return;
		}
		ConstPool pool = type.getConstPool();
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(record.get()))) {
			int componentCount = input.readUnsignedShort();
			for (int component = 0; component < componentCount; component++) {
				String name = pool.getUtf8Info(input.readUnsignedShort());
				String descriptor = pool.getUtf8Info(input.readUnsignedShort());
				String generic = "-";
				int attributeCount = input.readUnsignedShort();
				for (int index = 0; index < attributeCount; index++) {
					String attributeName = pool.getUtf8Info(input.readUnsignedShort());
					int length = input.readInt();
					byte[] value = input.readNBytes(length);
					if ("Signature".equals(attributeName)) {
						try (DataInputStream signatureInput = new DataInputStream(
								new ByteArrayInputStream(value))) {
							generic = pool.getUtf8Info(signatureInput.readUnsignedShort());
						}
					}
				}
				signatures.add("RECORD_COMPONENT " + type.getName() + "." + name
						+ " DESCRIPTOR " + descriptor + " GENERIC " + generic);
			}
		}
	}

	private static void addPermittedSubclasses(ClassFile type, Set<String> signatures)
			throws IOException {
		AttributeInfo permitted = type.getAttribute("PermittedSubclasses");
		if (permitted == null) {
			return;
		}
		Set<String> names = new TreeSet<>();
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(permitted.get()))) {
			int count = input.readUnsignedShort();
			for (int index = 0; index < count; index++) {
				names.add(type.getConstPool().getClassInfo(input.readUnsignedShort()));
			}
		}
		for (String name : names) {
			signatures.add("PERMITS " + type.getName() + " " + name);
		}
	}

	private static String signature(List<AttributeInfo> attributes) {
		AttributeInfo attribute = attribute(attributes, SignatureAttribute.tag);
		return attribute instanceof SignatureAttribute generic
				? generic.getSignature()
				: "-";
	}

	private static String constant(FieldInfo field) {
		AttributeInfo attribute = attribute(field.getAttributes(), ConstantAttribute.tag);
		if (!(attribute instanceof ConstantAttribute constant)) {
			return "-";
		}
		Object value = field.getConstPool().getLdcValue(constant.getConstantValue());
		if (value instanceof String text) {
			return "java.lang.String:base64:"
					+ Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
		}
		if (value instanceof Float number) {
			return "float:" + Float.toHexString(number);
		}
		if (value instanceof Double number) {
			return "double:" + Double.toHexString(number);
		}
		return value == null ? "null" : value.getClass().getName() + ":" + value;
	}

	private static AttributeInfo attribute(List<AttributeInfo> attributes, String name) {
		return attributes.stream()
				.filter(attribute -> name.equals(attribute.getName()))
				.findFirst()
				.orElse(null);
	}

	private static boolean isSupported(int access) {
		return AccessFlag.isPublic(access) || AccessFlag.isProtected(access);
	}

	private static boolean isSet(int access, int flag) {
		return (access & flag) != 0;
	}

	private static String flags(int access, int mask) {
		return String.format(Locale.ROOT, "0x%04x", access & mask);
	}

	private static String value(String value) {
		return value == null ? "-" : value;
	}
}
