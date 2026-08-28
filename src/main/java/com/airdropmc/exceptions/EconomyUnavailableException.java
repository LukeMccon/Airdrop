package com.airdropmc.exceptions;

public class EconomyUnavailableException extends Exception {

	public EconomyUnavailableException() {
		super("Economy is disabled or no economy provider is currently available");
	}
}
