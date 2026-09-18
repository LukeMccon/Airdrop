package dev.airdropmc.fixture;

import java.util.Objects;
import java.util.UUID;

final class ConsumerMarkerFormatter {
	private ConsumerMarkerFormatter() {
	}

	static String ready(long sequence, boolean primaryThread) {
		return nonRequest("READY", sequence, primaryThread, "");
	}

	static String request(String type, UUID requestId, long sequence, boolean primaryThread) {
		return request(type, requestId, sequence, primaryThread, "");
	}

	static String request(
			String type,
			UUID requestId,
			long sequence,
			boolean primaryThread,
			String details) {
		return "AIRDR_CONSUMER_" + requireType(type)
				+ " requestId=" + Objects.requireNonNull(requestId, "requestId")
				+ " sequence=" + requireSequence(sequence)
				+ " primaryThread=" + primaryThread
				+ Objects.requireNonNull(details, "details");
	}

	static String outcome(
			UUID requestId,
			long sequence,
			boolean primaryThread,
			String delivery,
			String payment,
			String reason) {
		return request(
				"OUTCOME",
				requestId,
				sequence,
				primaryThread,
				" delivery=" + requireValue(delivery, "delivery")
						+ " payment=" + requireValue(payment, "payment")
						+ " reason=" + requireValue(reason, "reason"));
	}

	static String nonRequest(
			String type, long sequence, boolean primaryThread, String details) {
		return "AIRDR_CONSUMER_" + requireType(type)
				+ " sequence=" + requireSequence(sequence)
				+ " primaryThread=" + primaryThread
				+ Objects.requireNonNull(details, "details");
	}

	private static String requireType(String type) {
		return requireValue(type, "type");
	}

	private static String requireValue(String value, String name) {
		String required = Objects.requireNonNull(value, name);
		if (required.isBlank() || required.chars().anyMatch(Character::isWhitespace)) {
			throw new IllegalArgumentException(name + " must be one non-blank token");
		}
		return required;
	}

	private static long requireSequence(long sequence) {
		if (sequence < 1L) {
			throw new IllegalArgumentException("sequence must be positive");
		}
		return sequence;
	}
}
