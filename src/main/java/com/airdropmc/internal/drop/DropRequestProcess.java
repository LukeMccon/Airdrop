package com.airdropmc.internal.drop;

import com.airdropmc.Crate;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.api.ResolvedDropContext;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.paid.PaidDropSession;
import org.jetbrains.annotations.ApiStatus;

/** Main-thread-confined mutable state for one request. */
@ApiStatus.Internal
final class DropRequestProcess {

	enum Phase {
		RESOLVING,
		ADMITTED,
		CHECKING_AFFORDABILITY,
		WITHDRAWING,
		SPAWNING,
		FALLING,
		LANDED,
		REFUNDING,
		TERMINAL
	}

	final DefaultDropHandle handle;
	ResolvedDropContext context;
	DropAdmissionController.Lease lease;
	PaidDropSession paymentSession;
	Crate crate;
	Phase phase = Phase.RESOLVING;
	PaymentStatus payment = PaymentStatus.NOT_APPLICABLE;
	DeliveryStatus pendingFailure = DeliveryStatus.FAILED;
	boolean requestEventPublished;

	DropRequestProcess(DefaultDropHandle handle) {
		this.handle = handle;
	}
}
