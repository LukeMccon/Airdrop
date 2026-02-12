package com.airdropmc.exceptions;

public class EconomyUnavailableException extends Exception {

	public EconomyUnavailableException() {
		super("No economy provider is currently available");
	}
}
