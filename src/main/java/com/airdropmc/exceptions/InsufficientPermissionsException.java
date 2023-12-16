package com.airdropmc.exceptions;

public class InsufficientPermissionsException extends Exception {
    /**
     * Indicates that a player does not have permissions to carry out an operation
     */
    public InsufficientPermissionsException(String message) {
        super(message);
    }
}
