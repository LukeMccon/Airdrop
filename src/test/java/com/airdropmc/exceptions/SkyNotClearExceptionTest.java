package com.airdropmc.exceptions;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyNotClearExceptionTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructor_setsLocation() {
        Location location = new Location(world, 100, 64, 200);
        SkyNotClearException exception = new SkyNotClearException(location);

        assertEquals(location, exception.getLocation());
    }

    @Test
    void getLocation_returnsCorrectValue() {
        Location location = new Location(world, 50, 100, 75);
        SkyNotClearException exception = new SkyNotClearException(location);

        Location result = exception.getLocation();

        assertEquals(50, result.getX());
        assertEquals(100, result.getY());
        assertEquals(75, result.getZ());
        assertEquals(world, result.getWorld());
    }

	@Test
	void locationPayloadIsIsolatedFromConstructorAndGetterMutations() {
		Location location = new Location(world, 10, 20, 30);
		SkyNotClearException exception = new SkyNotClearException(location);

		location.setX(99);
		Location firstResult = exception.getLocation();
		firstResult.setY(99);
		Location secondResult = exception.getLocation();

		assertAll(
				() -> assertNotSame(location, firstResult),
				() -> assertNotSame(firstResult, secondResult),
				() -> assertEquals(10, secondResult.getX()),
				() -> assertEquals(20, secondResult.getY()),
				() -> assertEquals(30, secondResult.getZ()));
	}

	@Test
	void constructorRejectsNullLocation() {
		NullPointerException failure = assertThrows(
				NullPointerException.class, () -> new SkyNotClearException(null));

		assertEquals("location", failure.getMessage());
	}

    @Test
    void isException_extendsException() {
        Location location = new Location(world, 0, 0, 0);
        SkyNotClearException exception = new SkyNotClearException(location);

        assertInstanceOf(Exception.class, exception);
    }
}
