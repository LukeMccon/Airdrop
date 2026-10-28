package dev.airdropmc.fixture;

import java.util.List;
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

	static String snapshot(
			String token,
			long sequence,
			boolean primaryThread,
			long revision,
			String name,
			String price,
			List<String> items) {
		if (revision < 0) {
			throw new IllegalArgumentException("revision must not be negative");
		}
		List<String> checkedItems = Objects.requireNonNull(items, "items").stream()
				.map(item -> {
					if (!Objects.requireNonNull(item, "item").matches("[A-Z][A-Z0-9_]*:[1-9][0-9]*")) {
						throw new IllegalArgumentException("item must be MATERIAL:amount");
					}
					return item;
				})
				.toList();
		return nonRequest("SNAPSHOT", sequence, primaryThread,
				" token=" + requireValue(token, "token")
						+ " status=OK name=" + requireValue(name, "name")
						+ " revision=" + revision
						+ " price=" + requireValue(price, "price")
						+ " items=" + (checkedItems.isEmpty() ? "NONE" : String.join(",", checkedItems)));
	}

	static String handle(
			String token,
			UUID requestId,
			long sequence,
			boolean primaryThread,
			UUID descriptorId,
			UUID playerId,
			String name,
			String source) {
		return request("HANDLE", requestId, sequence, primaryThread,
				" token=" + requireValue(token, "token")
						+ " status=OK descriptorId=" + Objects.requireNonNull(descriptorId, "descriptorId")
						+ " playerId=" + Objects.requireNonNull(playerId, "playerId")
						+ " name=" + requireValue(name, "name")
						+ " source=" + requireValue(source, "source"));
	}

	static String handleResult(
			String type,
			String token,
			UUID handleId,
			UUID resultId,
			long sequence,
			boolean primaryThread,
			String details) {
		return request(type, handleId, sequence, primaryThread,
				" token=" + requireValue(token, "token")
						+ " status=OK resultRequestId=" + Objects.requireNonNull(resultId, "resultId")
						+ Objects.requireNonNull(details, "details"));
	}

	static String failure(
			String type,
			String token,
			long sequence,
			boolean primaryThread,
			String reason) {
		return nonRequest(type, sequence, primaryThread,
				" token=" + requireValue(token, "token")
						+ " status=ERROR reason=" + requireValue(reason, "reason"));
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
