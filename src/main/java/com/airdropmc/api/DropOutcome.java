package com.airdropmc.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The terminal result of one correlated drop request.
 *
 * <p>Delivery and payment are independent, but every variant rejects
 * combinations that cannot occur in Airdrop's lifecycle.</p>
 */
public sealed interface DropOutcome
		permits DropOutcome.Rejected, DropOutcome.Landed, DropOutcome.Failed {

	/** @return request correlation UUID */
	default UUID requestId() {
		return descriptor().requestId();
	}

	/** @return immutable descriptor allocated before operational validation */
	DropRequestDescriptor descriptor();

	/** @return resolved request context when resolution succeeded */
	Optional<ResolvedDropContext> context();

	/** @return terminal delivery status */
	DeliveryStatus delivery();

	/** @return terminal payment status */
	PaymentStatus payment();

	/**
	 * A request rejected before a crate was committed.
	 *
	 * @param descriptor request descriptor
	 * @param resolvedContext resolved context when resolution succeeded
	 * @param rejection typed rejection details
	 * @param payment known uncharged payment state
	 */
	record Rejected(
			DropRequestDescriptor descriptor,
			Optional<ResolvedDropContext> resolvedContext,
			DropRejection rejection,
			PaymentStatus payment) implements DropOutcome {

		public Rejected {
			descriptor = Objects.requireNonNull(descriptor, "descriptor");
			resolvedContext = Objects.requireNonNull(resolvedContext, "resolvedContext");
			rejection = Objects.requireNonNull(rejection, "rejection");
			payment = Objects.requireNonNull(payment, "payment");
			if (resolvedContext.isPresent()) {
				requireSameRequest(descriptor, resolvedContext.orElseThrow());
			}
			if (payment != PaymentStatus.NOT_APPLICABLE
					&& payment != PaymentStatus.REJECTED) {
				throw new IllegalArgumentException(
						"Rejected delivery requires NOT_APPLICABLE or REJECTED payment");
			}
		}

		@Override
		public Optional<ResolvedDropContext> context() {
			return resolvedContext;
		}

		@Override
		public DeliveryStatus delivery() {
			return DeliveryStatus.REJECTED;
		}
	}

	/**
	 * A request whose landed barrel was committed.
	 *
	 * @param resolvedContext complete resolved request context
	 * @param airdrop landed airdrop snapshot
	 * @param payment final payment state
	 */
	record Landed(
			ResolvedDropContext resolvedContext,
			LandedAirdropView airdrop,
			PaymentStatus payment) implements DropOutcome {

		public Landed {
			resolvedContext = Objects.requireNonNull(resolvedContext, "resolvedContext");
			airdrop = Objects.requireNonNull(airdrop, "airdrop");
			payment = Objects.requireNonNull(payment, "payment");
			if (!airdrop.requestId().equals(Optional.of(resolvedContext.descriptor().requestId()))) {
				throw new IllegalArgumentException("Landed view must belong to the resolved request");
			}
			if (payment != PaymentStatus.NOT_APPLICABLE && payment != PaymentStatus.CHARGED) {
				throw new IllegalArgumentException(
						"Landed delivery requires NOT_APPLICABLE or CHARGED payment");
			}
		}

		@Override
		public DropRequestDescriptor descriptor() {
			return resolvedContext.descriptor();
		}

		@Override
		public Optional<ResolvedDropContext> context() {
			return Optional.of(resolvedContext);
		}

		@Override
		public DeliveryStatus delivery() {
			return DeliveryStatus.LANDED;
		}
	}

	/**
	 * A resolved request whose crate did not land successfully.
	 *
	 * @param resolvedContext complete resolved request context
	 * @param delivery failed, cancelled, or shutdown delivery state
	 * @param payment final payment state
	 */
	record Failed(
			ResolvedDropContext resolvedContext,
			DeliveryStatus delivery,
			PaymentStatus payment) implements DropOutcome {

		public Failed {
			resolvedContext = Objects.requireNonNull(resolvedContext, "resolvedContext");
			delivery = Objects.requireNonNull(delivery, "delivery");
			payment = Objects.requireNonNull(payment, "payment");
			if (delivery != DeliveryStatus.FAILED
					&& delivery != DeliveryStatus.CANCELLED
					&& delivery != DeliveryStatus.SHUTDOWN) {
				throw new IllegalArgumentException(
						"Failed outcome delivery must be FAILED, CANCELLED, or SHUTDOWN");
			}
			if (!validFailedPayment(delivery, payment)) {
				throw new IllegalArgumentException(
						"Invalid payment " + payment + " for " + delivery + " delivery");
			}
		}

		@Override
		public DropRequestDescriptor descriptor() {
			return resolvedContext.descriptor();
		}

		@Override
		public Optional<ResolvedDropContext> context() {
			return Optional.of(resolvedContext);
		}
	}

	private static boolean validFailedPayment(DeliveryStatus delivery, PaymentStatus payment) {
		if (delivery == DeliveryStatus.SHUTDOWN) {
			return payment == PaymentStatus.NOT_APPLICABLE
					|| payment == PaymentStatus.CHARGED
					|| payment == PaymentStatus.UNKNOWN;
		}
		return payment == PaymentStatus.NOT_APPLICABLE
				|| payment == PaymentStatus.REFUNDED
				|| payment == PaymentStatus.REFUND_FAILED
				|| payment == PaymentStatus.UNKNOWN;
	}

	private static void requireSameRequest(
			DropRequestDescriptor descriptor, ResolvedDropContext context) {
		if (!descriptor.requestId().equals(context.descriptor().requestId())) {
			throw new IllegalArgumentException("Resolved context must belong to the descriptor");
		}
	}
}
