package com.airdropmc.internal.diagnostics;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stores the last safe operator diagnostic and owns all untrusted-text
 * sanitization used by status and debug surfaces.
 */
@ApiStatus.Internal
public final class AirdropDiagnostics {

	private static final int MAX_DIAGNOSTIC_CODE_POINTS = 160;
	private static final int MAX_LABEL_CODE_POINTS = 80;
	private static final int MAX_UNTRUSTED_INPUT_CODE_POINTS = 2_048;
	private static final String FALLBACK_MESSAGE = "Operation failed; see the server log";
	private static final String FALLBACK_LABEL = "unknown";

	private static final Pattern ANSI = Pattern.compile(
			"\\x1B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\x07]*(?:\\x07|\\x1B\\\\))");
	private static final Pattern BUKKIT_COLOR = Pattern.compile(
			"(?i)(?:§|&)(?:[0-9A-FK-ORX]|#[0-9A-F]{6})");
	private static final Pattern URI_CREDENTIALS = Pattern.compile(
			"(?i)\\b([a-z][a-z0-9+.-]*://)[^\\s/@:]+(?::[^\\s/@]*)?@");
	private static final Pattern BEARER_SECRET = Pattern.compile(
			"(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]+");
	private static final Pattern NAMED_SECRET = Pattern.compile(
			"(?i)\\b(token|secret|password|passwd|credential|authorization|api[-_]?key)"
					+ "\\s*[:=]\\s*[^\\s,;]+");
	private static final Pattern WINDOWS_PATH = Pattern.compile(
			"(?i)(?:[a-z]:\\\\|\\\\\\\\)[^\\s,;]+");
	private static final Pattern KNOWN_RELATIVE_PATH = Pattern.compile(
			"(?i)\\b(?:plugins?|config|logs?|worlds?|server)[\\\\/][^\\s,;]+");
	private static final Pattern UNIX_PATH = Pattern.compile(
			"(?<![:/\\p{Alnum}])/[^\\s,;]+");
	private static final Pattern LONG_SECRET_FRAGMENT = Pattern.compile(
			"(?<![\\p{Alnum}_-])[A-Za-z0-9_-]{24,}(?![\\p{Alnum}_-])");
	private static final Pattern BRACED_CONTENT = Pattern.compile("\\{[^{}]*}");
	private static final Pattern CONTROLS = Pattern.compile("[\\p{Cc}\\p{Cf}]");
	private static final Pattern LINE_BREAKS = Pattern.compile("\\R");
	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

	/** Stable diagnostic source categories exposed by name through {@code AirdropStatus}. */
	public enum Category {
		STARTUP,
		CONFIGURATION,
		PACKAGE_REGISTRY
	}

	/** Immutable, detached and already-sanitized operator diagnostic. */
	public record Snapshot(Category category, String message) {
		public Snapshot {
			Objects.requireNonNull(category, "category");
			Objects.requireNonNull(message, "message");
		}
	}

	private volatile Optional<Snapshot> latest = Optional.empty();

	/** Records one failure without retaining the throwable or its stack trace. */
	public synchronized void record(Category category, Throwable failure) {
		Objects.requireNonNull(failure, "failure");
		latest = Optional.of(new Snapshot(
				Objects.requireNonNull(category, "category"),
				sanitize(failure.getMessage(), MAX_DIAGNOSTIC_CODE_POINTS, FALLBACK_MESSAGE)));
	}

	/** Clears only a diagnostic produced by the same successful subsystem. */
	public synchronized void clear(Category category) {
		Category required = Objects.requireNonNull(category, "category");
		if (latest.filter(snapshot -> snapshot.category() == required).isPresent()) {
			latest = Optional.empty();
		}
	}

	/** @return the immutable last diagnostic, when one has been recorded */
	public Optional<Snapshot> snapshot() {
		return latest;
	}

	/** Sanitizes an external display label such as an economy provider name. */
	public static String sanitizeLabel(String unsafe) {
		return sanitize(unsafe, MAX_LABEL_CODE_POINTS, FALLBACK_LABEL);
	}

	private static String sanitize(String unsafe, int maximumCodePoints, String fallback) {
		if (unsafe == null || unsafe.isBlank()) {
			return fallback;
		}
		String boundedInput = unsafe.codePointCount(0, unsafe.length())
				> MAX_UNTRUSTED_INPUT_CODE_POINTS
				? unsafe.substring(0, unsafe.offsetByCodePoints(
						0, MAX_UNTRUSTED_INPUT_CODE_POINTS))
				: unsafe;
		String safe = ANSI.matcher(boundedInput).replaceAll("");
		safe = BUKKIT_COLOR.matcher(safe).replaceAll("");
		safe = URI_CREDENTIALS.matcher(safe).replaceAll("$1[redacted]@");
		safe = BEARER_SECRET.matcher(safe).replaceAll("credential [redacted]");
		safe = NAMED_SECRET.matcher(safe).replaceAll("$1=[redacted]");
		safe = WINDOWS_PATH.matcher(safe).replaceAll("[path]");
		safe = KNOWN_RELATIVE_PATH.matcher(safe).replaceAll("[path]");
		safe = UNIX_PATH.matcher(safe).replaceAll("[path]");
		safe = LONG_SECRET_FRAGMENT.matcher(safe).replaceAll("[redacted]");
		safe = BRACED_CONTENT.matcher(safe).replaceAll("[redacted]");
		safe = CONTROLS.matcher(safe).replaceAll(" ");
		safe = LINE_BREAKS.matcher(safe).replaceAll(" ");
		safe = safe.replace('{', ' ').replace('}', ' ');
		safe = WHITESPACE.matcher(safe).replaceAll(" ").trim();
		if (safe.isBlank()) {
			return fallback;
		}
		return truncate(safe, maximumCodePoints);
	}

	private static String truncate(String value, int maximumCodePoints) {
		int count = value.codePointCount(0, value.length());
		if (count <= maximumCodePoints) {
			return value;
		}
		int end = value.offsetByCodePoints(0, maximumCodePoints - 1);
		return value.substring(0, end).stripTrailing() + "…";
	}
}
