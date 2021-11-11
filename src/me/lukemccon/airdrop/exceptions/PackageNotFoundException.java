package me.lukemccon.airdrop.exceptions;

@SuppressWarnings("serial")
public class PackageNotFoundException extends Exception {

	public PackageNotFoundException() {
		
	}
	
	public PackageNotFoundException(String message) {
		super("Unable to find package with name: "+ message);
	}
}
