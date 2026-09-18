package com.airdropmc.api;

/** The terminal payment state associated with an airdrop request. */
public enum PaymentStatus {
	/** No payment applies to this free or system request. */
	NOT_APPLICABLE,
	/** Payment was not taken. */
	REJECTED,
	/** A withdrawal was confirmed and has not been refunded. */
	CHARGED,
	/** A confirmed withdrawal was subsequently refunded. */
	REFUNDED,
	/** The provider definitively rejected the refund. */
	REFUND_FAILED,
	/** The provider result was ambiguous; no automatic retry is attempted. */
	UNKNOWN
}
