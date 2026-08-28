package com.airdropmc.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CannotAffordExceptionTest {

    @Test
    void constructor_setsPlayerNameAndPrice() {
        CannotAffordException exception = new CannotAffordException("TestPlayer", 100.50);

        assertEquals("TestPlayer", exception.getPlayerName());
        assertEquals(100.50, exception.getPrice());
    }

    @Test
    void getPlayerName_returnsCorrectValue() {
        CannotAffordException exception = new CannotAffordException("Steve", 50.0);

        assertEquals("Steve", exception.getPlayerName());
    }

    @Test
    void getPrice_returnsCorrectValue() {
        CannotAffordException exception = new CannotAffordException("Alex", 999.99);

        assertEquals(999.99, exception.getPrice());
    }

    @ParameterizedTest
    @CsvSource({
            "Player1, 0.0",
            "Player2, 100.0",
            "Player3, 999999.99",
            "'', 50.0"
    })
    void constructor_handlesVariousInputs(String playerName, double price) {
        CannotAffordException exception = new CannotAffordException(playerName, price);

        assertEquals(playerName, exception.getPlayerName());
        assertEquals(price, exception.getPrice());
    }

    @Test
    void isException_extendsException() {
        CannotAffordException exception = new CannotAffordException("Test", 10.0);

        assertInstanceOf(Exception.class, exception);
    }
}
