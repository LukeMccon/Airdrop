package com.airdropmc.helpers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.Crate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrateManagerTest {

    private ServerMock server;
    private WorldMock world;

    @Mock
    private FallingBlock mockFallingBlock;

    @Mock
    private FallingBlock mockFallingBlock2;

    @Mock
    private Crate mockCrate;

    @Mock
    private Crate mockCrate2;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
        clearCrateManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        clearCrateManager();
        MockBukkit.unmock();
    }

    /**
     * Clears the static maps in CrateManager between tests
     */
    private void clearCrateManager() throws Exception {
        // Clear crateMap
        Field crateMapField = CrateManager.class.getDeclaredField("crateMap");
        crateMapField.setAccessible(true);
        ((Map<?, ?>) crateMapField.get(null)).clear();

        // Clear landedCrateMap
        Field landedCrateMapField = CrateManager.class.getDeclaredField("landedCrateMap");
        landedCrateMapField.setAccessible(true);
        ((Map<?, ?>) landedCrateMapField.get(null)).clear();
    }

    // FallingBlock-based crate operations

    @Test
    void addCrate_withFallingBlock_storesCrate() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);

        assertTrue(CrateManager.hasCrate(mockFallingBlock));
    }

    @Test
    void getCrate_withFallingBlock_returnsStoredCrate() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);

        Crate result = CrateManager.getCrate(mockFallingBlock);

        assertSame(mockCrate, result);
    }

    @Test
    void getCrate_withFallingBlock_whenNotPresent_returnsNull() {
        Crate result = CrateManager.getCrate(mockFallingBlock);

        assertNull(result);
    }

    @Test
    void hasCrate_withFallingBlock_whenPresent_returnsTrue() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);

        assertTrue(CrateManager.hasCrate(mockFallingBlock));
    }

    @Test
    void hasCrate_withFallingBlock_whenNotPresent_returnsFalse() {
        assertFalse(CrateManager.hasCrate(mockFallingBlock));
    }

    @Test
    void removeCrate_withFallingBlock_removesCrate() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);

        CrateManager.removeCrate(mockFallingBlock);

        assertFalse(CrateManager.hasCrate(mockFallingBlock));
    }

    @Test
    void removeCrate_withFallingBlock_returnsRemovedCrate() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);

        Crate result = CrateManager.removeCrate(mockFallingBlock);

        assertSame(mockCrate, result);
    }

    @Test
    void removeCrate_withFallingBlock_whenNotPresent_returnsNull() {
        Crate result = CrateManager.removeCrate(mockFallingBlock);

        assertNull(result);
    }

    // Location-based crate operations

    @Test
    void addCrate_withLocation_storesCrate() {
        Location location = new Location(world, 100, 64, 200);

        CrateManager.addCrate(location, mockCrate);

        assertNotNull(CrateManager.getCrate(location));
    }

    @Test
    void getCrate_withLocation_returnsStoredCrate() {
        Location location = new Location(world, 100, 64, 200);
        CrateManager.addCrate(location, mockCrate);

        Crate result = CrateManager.getCrate(location);

        assertSame(mockCrate, result);
    }

    @Test
    void getCrate_withLocation_whenNotPresent_returnsNull() {
        Location location = new Location(world, 100, 64, 200);

        Crate result = CrateManager.getCrate(location);

        assertNull(result);
    }

    @Test
    void removeCrate_withLocation_removesCrateWithoutDestroySideEffect() {
        Location location = new Location(world, 100, 64, 200);
        CrateManager.addCrate(location, mockCrate);

        CrateManager.removeCrate(location);

        assertNull(CrateManager.getCrate(location));
        verifyNoInteractions(mockCrate);
    }

    @Test
    void removeCrate_withLocation_returnsRemovedCrate() {
        Location location = new Location(world, 100, 64, 200);
        CrateManager.addCrate(location, mockCrate);

        Crate result = CrateManager.removeCrate(location);

        assertSame(mockCrate, result);
    }

    @Test
    void removeCrate_withLocation_whenNotPresent_returnsNull() {
        Location location = new Location(world, 100, 64, 200);

        Crate result = CrateManager.removeCrate(location);

        assertNull(result);
    }

    @Test
    void removeCrate_withLocation_whenNotPresent_doesNotCallDestroy() {
        Location location = new Location(world, 100, 64, 200);

        CrateManager.removeCrate(location);

        verifyNoInteractions(mockCrate);
    }

    // Multiple crates tests

    @Test
    void addCrate_canStoreMultipleFallingBlockCrates() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);
        CrateManager.addCrate(mockFallingBlock2, mockCrate2);

        assertSame(mockCrate, CrateManager.getCrate(mockFallingBlock));
        assertSame(mockCrate2, CrateManager.getCrate(mockFallingBlock2));
    }

    @Test
    void addCrate_canStoreMultipleLocationCrates() {
        Location location1 = new Location(world, 100, 64, 200);
        Location location2 = new Location(world, 200, 64, 300);

        CrateManager.addCrate(location1, mockCrate);
        CrateManager.addCrate(location2, mockCrate2);

        assertSame(mockCrate, CrateManager.getCrate(location1));
        assertSame(mockCrate2, CrateManager.getCrate(location2));
    }

    @Test
    void addCrate_replacesExistingCrate_forSameFallingBlock() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);
        CrateManager.addCrate(mockFallingBlock, mockCrate2);

        assertSame(mockCrate2, CrateManager.getCrate(mockFallingBlock));
    }

	    @Test
	    void addCrate_replacesExistingCrate_forSameLocation() {
	        Location location = new Location(world, 100, 64, 200);

	        CrateManager.addCrate(location, mockCrate);
	        CrateManager.addCrate(location, mockCrate2);

	        assertSame(mockCrate2, CrateManager.getCrate(location));
	    }

	    @Test
	    void removeFallingCratesInChunk_removesMatchingFallingCrates_only() {
	        Location inChunkLocation = new Location(world, 8, 64, 8);
	        Location outChunkLocation = new Location(world, 40, 64, 40);
	        Location landedLocation = new Location(world, 9, 64, 9);
	        Crate landedCrate = mock(Crate.class);

	        when(mockFallingBlock.getWorld()).thenReturn(world);
	        when(mockFallingBlock.getLocation()).thenReturn(inChunkLocation);
	        when(mockFallingBlock2.getWorld()).thenReturn(world);
	        when(mockFallingBlock2.getLocation()).thenReturn(outChunkLocation);

	        CrateManager.addCrate(mockFallingBlock, mockCrate);
	        CrateManager.addCrate(mockFallingBlock2, mockCrate2);
	        CrateManager.addCrate(landedLocation, landedCrate);

	        CrateManager.removeFallingCratesInChunk(world.getChunkAt(0, 0));

	        assertFalse(CrateManager.hasCrate(mockFallingBlock));
	        assertTrue(CrateManager.hasCrate(mockFallingBlock2));
	        assertSame(landedCrate, CrateManager.getCrate(landedLocation));
	        verify(mockCrate).destroy();
	        verify(mockCrate2, never()).destroy();
	        verify(landedCrate, never()).destroy();
	    }

	    @Test
	    void removeFallingCratesInChunk_whenChunkIsNull_doesNothing() {
	        CrateManager.addCrate(mockFallingBlock, mockCrate);

	        CrateManager.removeFallingCratesInChunk(null);

	        assertTrue(CrateManager.hasCrate(mockFallingBlock));
	        verifyNoInteractions(mockCrate);
	    }

    // Legacy method test

    @Test
    @SuppressWarnings("deprecation")
    void getCrateMap_returnsInternalMap() {
        CrateManager.addCrate(mockFallingBlock, mockCrate);

        Map<FallingBlock, Crate> map = CrateManager.getCrateMap();

        assertNotNull(map);
        assertTrue(map.containsKey(mockFallingBlock));
    }

    // Different locations are distinct keys

    @Test
    void getCrate_withDifferentLocation_returnsNull() {
        Location location1 = new Location(world, 100, 64, 200);
        Location location2 = new Location(world, 100, 65, 200); // Different Y

        CrateManager.addCrate(location1, mockCrate);

        assertNull(CrateManager.getCrate(location2));
    }

    @Test
    void getCrate_withDifferentWorldInstanceSameUuid_returnsStoredCrate() {
        UUID worldId = UUID.randomUUID();
        World storedWorld = mock(World.class);
        World lookupWorld = mock(World.class);
        when(storedWorld.getUID()).thenReturn(worldId);
        when(lookupWorld.getUID()).thenReturn(worldId);

        Location storedLocation = new Location(storedWorld, 100.4, 64.2, 200.8);
        Location lookupLocation = new Location(lookupWorld, 100.9, 64.9, 200.1);

        CrateManager.addCrate(storedLocation, mockCrate);

        assertSame(mockCrate, CrateManager.getCrate(lookupLocation));
    }

    @Test
    void getCrate_withDifferentWorldUuid_returnsNull() {
        World storedWorld = mock(World.class);
        World lookupWorld = mock(World.class);
        when(storedWorld.getUID()).thenReturn(UUID.randomUUID());
        when(lookupWorld.getUID()).thenReturn(UUID.randomUUID());

        Location storedLocation = new Location(storedWorld, 100, 64, 200);
        Location lookupLocation = new Location(lookupWorld, 100, 64, 200);

        CrateManager.addCrate(storedLocation, mockCrate);

        assertNull(CrateManager.getCrate(lookupLocation));
    }
}
