package com.airdropmc.exceptions;

public class CannotAffordException extends Exception {

    private final String playerName;
    private final double price;

    public CannotAffordException(String playerName, double price) {
        this.playerName = playerName;
        this.price = price;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getPrice() {
        return price;
    }
}
