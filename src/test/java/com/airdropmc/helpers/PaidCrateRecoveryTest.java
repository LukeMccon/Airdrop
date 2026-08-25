package com.airdropmc.helpers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.config.DropOptions;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.listeners.CrateCleanupListener;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaidCrateRecoveryTest {

	private static final NamespacedKey CRATE_ID_KEY = key("airdrop:crate_id");
	private static final NamespacedKey PAID_KEY = key("airdrop:paid");
	private static final NamespacedKey EXPIRES_AT_KEY = key("airdrop:expires_at");
	private static final NamespacedKey RECOVERY_STATE_KEY = key("airdrop:recovery_state");

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private DropAdmissionController admission;
	private Block barrelBlock;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("recovery_world");
		world.loadChunk(0, 0);
		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("PaidCrateRecoveryTest"));
		when(plugin.getServer()).thenReturn(server);
		Airdrop.setPluginInstance(plugin);
		CrateManager.setWorldSaverForTesting(World::save);
		admission = new DropAdmissionController();
		setStatic("dropAdmissionController", admission);
		setStatic("shuttingDown", false);
		setStatic("ready", true);
		barrelBlock = world.getBlockAt(8, 64, 8);
		CrateManager.clearAll();
	}

	@AfterEach
	void tearDown() throws Exception {
		CrateManager.clearAll();
		admission.clear();
		Airdrop.setPluginInstance(null);
		setStatic("dropAdmissionController", null);
		setStatic("shuttingDown", false);
		setStatic("ready", false);
		CrateManager.resetWorldSaverForTesting();
		MockBukkit.unmock();
	}

	@Test
	void gracefulChunkBoundaryRecoversSameInventoryExpiryAndOneLease() throws Exception {
		Crate original = landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		Crate.PersistedBarrelData before = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(before);
		ChunkFixture fixture = chunkFixture(barrelBlock);

		assertTrue(CrateManager.prepareChunkForUnload(fixture.chunk()));

		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(1, admission.snapshot().landedClaims());
		Crate.PersistedBarrelData recoverable = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(recoverable);
		assertEquals(Crate.RecoveryState.RECOVERABLE, recoverable.recoveryState());
		assertEquals(before.expiresAtMillis(), recoverable.expiresAtMillis());

		ChunkFixture recoveryFixture = chunkFixture(barrelBlock);
		CrateManager.recoverCratesInChunk(plugin, admission, recoveryFixture.chunk());

		Crate recovered = CrateManager.getCrate(barrelBlock.getLocation());
		assertNotNull(recovered);
		assertNotSame(original, recovered);
		assertEquals(1, admission.snapshot().landedClaims());
		Barrel barrel = (Barrel) barrelBlock.getState();
		assertEquals(new ItemStack(Material.DIAMOND), barrel.getInventory().getItem(0));
		Crate.PersistedBarrelData live = Crate.readPaidPersistence(barrel);
		assertNotNull(live);
		assertEquals(Crate.RecoveryState.LIVE, live.recoveryState());
		assertEquals(before.expiresAtMillis(), live.expiresAtMillis());
		verify(recoveryFixture.savedWorld()).save();

		ChunkFixture repeatedFixture = chunkFixture(barrelBlock);
		CrateManager.recoverCratesInChunk(plugin, admission, repeatedFixture.chunk());

		assertSame(recovered, CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(1, admission.snapshot().landedClaims());
		verify(repeatedFixture.savedWorld(), never()).save();
	}

	@Test
	void chunkUnloadListenerPreservesExistingSaveDecisionForPaidBarrel() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		ChunkFixture fixture = chunkFixture(barrelBlock);
		ChunkUnloadEvent event = mock(ChunkUnloadEvent.class);
		when(event.getChunk()).thenReturn(fixture.chunk());

		new CrateCleanupListener().onChunkUnload(event);

		verify(event, never()).setSaveChunk(true);
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(1, admission.snapshot().landedClaims());
		Crate.PersistedBarrelData persisted = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(persisted);
		assertEquals(Crate.RecoveryState.RECOVERABLE, persisted.recoveryState());
	}

	@Test
	void worldUnloadSavesBeforeDetachingPaidBarrel() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		World savedWorld = savedWorld();
		WorldUnloadEvent event = mock(WorldUnloadEvent.class);
		when(event.getWorld()).thenReturn(savedWorld);

		new CrateCleanupListener().onWorldUnload(event);

		verify(savedWorld).save();
		verify(event, never()).setCancelled(true);
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(1, admission.snapshot().landedClaims());
		Crate.PersistedBarrelData persisted = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(persisted);
		assertEquals(Crate.RecoveryState.RECOVERABLE, persisted.recoveryState());
	}

	@Test
	void worldUnloadSaveFailureRestoresLiveBarrelAndCancelsUnload() throws Exception {
		Crate original = landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		World savedWorld = savedWorld();
		doThrow(new IllegalStateException("save failed")).when(savedWorld).save();
		WorldUnloadEvent event = mock(WorldUnloadEvent.class);
		when(event.getWorld()).thenReturn(savedWorld);

		new CrateCleanupListener().onWorldUnload(event);

		verify(savedWorld).save();
		verify(event).setCancelled(true);
		assertSame(original, CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(1, admission.snapshot().landedClaims());
		Crate.PersistedBarrelData persisted = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(persisted);
		assertEquals(Crate.RecoveryState.LIVE, persisted.recoveryState());
	}

	@Test
	void cancelledWorldUnloadRecoversBarrelOnNextTick() throws Exception {
		Crate original = landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		World savedWorld = savedWorld();
		WorldUnloadEvent event = mock(WorldUnloadEvent.class);
		when(event.getWorld()).thenReturn(savedWorld);

		new CrateCleanupListener().onWorldUnload(event);
		verify(savedWorld).save();
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));

		CrateManager.setWorldSaverForTesting(ignored -> { });
		server.getScheduler().performTicks(1L);

		Crate recovered = CrateManager.getCrate(barrelBlock.getLocation());
		assertNotNull(recovered);
		assertNotSame(original, recovered);
		assertEquals(1, admission.snapshot().landedClaims());
		Crate.PersistedBarrelData persisted = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(persisted);
		assertEquals(Crate.RecoveryState.LIVE, persisted.recoveryState());
	}

	@Test
	void suspendedPaidLeaseKeepsLandedCapacityReserved() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		ChunkFixture fixture = chunkFixture(barrelBlock);

		assertTrue(CrateManager.prepareChunkForUnload(fixture.chunk()));
		assertEquals(1, admission.snapshot().landedClaims());

		DropLimitSettings oneLanded = new DropLimitSettings(
				Duration.ofSeconds(30), 3, 1, Duration.ofSeconds(600));
		DropLimitException failure = assertThrows(DropLimitException.class,
				() -> admission.acquireSystem(
						DropLocationKey.from(world.getBlockAt(24, 64, 24).getLocation()), oneLanded));
		assertEquals(DropLimitException.Reason.LANDED_CAPACITY, failure.getReason());

		ChunkFixture recoveryFixture = chunkFixture(barrelBlock);
		CrateManager.recoverCratesInChunk(plugin, admission, recoveryFixture.chunk());
		assertEquals(1, admission.snapshot().landedClaims());
	}

	@Test
	void missingMarkerOnReloadReleasesSuspendedLeaseAndLocation() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		ChunkFixture unloadFixture = chunkFixture(barrelBlock);

		assertTrue(CrateManager.prepareChunkForUnload(unloadFixture.chunk()));
		assertEquals(1, admission.snapshot().landedClaims());
		assertEquals(1, admission.snapshot().locations());
		Barrel ordinary = (Barrel) barrelBlock.getState();
		ordinary.getPersistentDataContainer().remove(CRATE_ID_KEY);
		ordinary.getPersistentDataContainer().remove(PAID_KEY);
		ordinary.getPersistentDataContainer().remove(EXPIRES_AT_KEY);
		ordinary.getPersistentDataContainer().remove(RECOVERY_STATE_KEY);
		assertTrue(ordinary.update(true, false));

		ChunkFixture recoveryFixture = chunkFixture(barrelBlock);
		CrateManager.recoverCratesInChunk(plugin, admission, recoveryFixture.chunk());

		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
		assertEquals(0, admission.snapshot().locations());
		assertEquals(Material.BARREL, barrelBlock.getType());
		assertEquals(new ItemStack(Material.DIAMOND),
				((Barrel) barrelBlock.getState()).getInventory().getItem(0));
		verify(recoveryFixture.savedWorld()).save();
	}

	@Test
	void missingMarkerSaveFailureKeepsSuspendedLeaseReserved() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		ChunkFixture unloadFixture = chunkFixture(barrelBlock);

		assertTrue(CrateManager.prepareChunkForUnload(unloadFixture.chunk()));
		Barrel ordinary = (Barrel) barrelBlock.getState();
		ordinary.getPersistentDataContainer().remove(CRATE_ID_KEY);
		ordinary.getPersistentDataContainer().remove(PAID_KEY);
		ordinary.getPersistentDataContainer().remove(EXPIRES_AT_KEY);
		ordinary.getPersistentDataContainer().remove(RECOVERY_STATE_KEY);
		assertTrue(ordinary.update(true, false));
		ChunkFixture recoveryFixture = chunkFixture(barrelBlock);
		doThrow(new IllegalStateException("save failed"))
				.when(recoveryFixture.savedWorld()).save();

		CrateManager.recoverCratesInChunk(plugin, admission, recoveryFixture.chunk());

		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(1, admission.snapshot().landedClaims());
		assertEquals(1, admission.snapshot().locations());
		assertEquals(Material.BARREL, barrelBlock.getType());
		verify(recoveryFixture.savedWorld(), times(2)).save();
	}

	@Test
	void retiredRecoverableIdentityIsPurgedInsteadOfReplayed() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		Crate.PersistedBarrelData persisted = Crate.readPaidPersistence((Barrel) barrelBlock.getState());
		assertNotNull(persisted);
		assertTrue(CrateManager.removeCrateAndDetach(barrelBlock.getLocation()));
		writeRecoverableBarrel(
				barrelBlock,
				persisted.crateId(),
				persisted.expiresAtMillis(),
				new ItemStack(Material.DIAMOND));
		ChunkFixture fixture = chunkFixture(barrelBlock);

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());

		assertEquals(Material.AIR, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
		verify(fixture.savedWorld()).save();
	}

	@Test
	void hotDisablePurgesPaidBarrelAndReleasesLease() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		CrateManager.setWorldSaverForTesting(ignored -> { });

		CrateManager.purgeForHotDisable(plugin);

		assertEquals(Material.AIR, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void hotDisablePurgesUntrackedRecoverableBarrelWhenStartupNeverBecomesReady() throws Exception {
		writeRecoverableBarrel(
				barrelBlock,
				UUID.randomUUID().toString(),
				System.currentTimeMillis() + 60_000L,
				new ItemStack(Material.DIAMOND));
		ChunkFixture fixture = chunkFixture(barrelBlock);
		when(fixture.savedWorld().getLoadedChunks()).thenReturn(new Chunk[] { fixture.chunk() });
		Server loadedWorldServer = mock(Server.class);
		when(loadedWorldServer.getWorlds()).thenReturn(List.of(fixture.savedWorld()));
		when(plugin.getServer()).thenReturn(loadedWorldServer);
		setStatic("ready", false);

		CrateManager.purgeForHotDisable(plugin);

		assertEquals(Material.AIR, barrelBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void gracefulShutdownPreservesRecoverablePaidBarrel() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		CrateManager.setWorldSaverForTesting(ignored -> { });

		CrateManager.prepareForShutdown(plugin);

		assertEquals(Material.BARREL, barrelBlock.getType());
		Barrel barrel = (Barrel) barrelBlock.getState();
		assertEquals(new ItemStack(Material.DIAMOND), barrel.getInventory().getItem(0));
		Crate.PersistedBarrelData persisted = Crate.readPaidPersistence(barrel);
		assertNotNull(persisted);
		assertEquals(Crate.RecoveryState.RECOVERABLE, persisted.recoveryState());
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void recoverySaveFailurePurgesBarrelAndReleasesLease() {
		writeRecoverableBarrel(
				barrelBlock,
				UUID.randomUUID().toString(),
				System.currentTimeMillis() + 60_000L,
				new ItemStack(Material.DIAMOND));
		ChunkFixture fixture = chunkFixture(barrelBlock);
		doThrow(new IllegalStateException("save failed")).when(fixture.savedWorld()).save();

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());

		assertEquals(Material.AIR, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
		verify(fixture.savedWorld(), times(2)).save();
	}

	@Test
	void untrackedLiveBarrelIsPurgedInsteadOfReplayed() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		assertTrue(CrateManager.removeCrateAndDetach(barrelBlock.getLocation()));
		assertEquals(Crate.RecoveryState.LIVE,
				Crate.readPaidPersistence((Barrel) barrelBlock.getState()).recoveryState());
		ChunkFixture fixture = chunkFixture(barrelBlock);

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());

		assertEquals(Material.AIR, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
		verify(fixture.savedWorld()).save();
	}

	@Test
	void malformedPaidMetadataWithoutStringIdentityIsPurgedFailClosed() {
		barrelBlock.setType(Material.BARREL);
		Barrel barrel = (Barrel) barrelBlock.getState();
		barrel.getPersistentDataContainer().set(CRATE_ID_KEY, PersistentDataType.INTEGER, 1);
		barrel.getPersistentDataContainer().set(PAID_KEY, PersistentDataType.BYTE, (byte) 1);
		barrel.getPersistentDataContainer().set(
				EXPIRES_AT_KEY, PersistentDataType.LONG, System.currentTimeMillis() + 60_000L);
		barrel.getPersistentDataContainer().set(
				RECOVERY_STATE_KEY, PersistentDataType.STRING, Crate.RecoveryState.RECOVERABLE.name());
		assertTrue(barrel.update(true, false));
		ChunkFixture fixture = chunkFixture(barrelBlock);

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());

		assertEquals(Material.AIR, barrelBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
		verify(fixture.savedWorld()).save();
	}

	@Test
	void hotDisablePurgesSuspendedBarrelWithPartialMetadata() throws Exception {
		landPaidCrate(barrelBlock, List.of(new ItemStack(Material.DIAMOND)));
		assertTrue(CrateManager.prepareChunkForUnload(chunkFixture(barrelBlock).chunk()));
		Barrel barrel = (Barrel) barrelBlock.getState();
		barrel.getPersistentDataContainer().remove(CRATE_ID_KEY);
		assertTrue(barrel.update(true, false));
		when(plugin.getServer()).thenReturn(server);

		CrateManager.purgeForHotDisable(plugin);

		assertEquals(Material.AIR, barrelBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
		assertEquals(0, admission.snapshot().locations());
	}

	@Test
	void legacyIdentityOnlyBarrelIsPurgedFailClosed() {
		barrelBlock.setType(Material.BARREL);
		Barrel barrel = (Barrel) barrelBlock.getState();
		barrel.getPersistentDataContainer().set(CRATE_ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
		assertTrue(barrel.update(true, false));
		ChunkFixture fixture = chunkFixture(barrelBlock);

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());

		assertEquals(Material.AIR, barrelBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void duplicateRecoverableIdsAreBothPurged() {
		Block otherBlock = world.getBlockAt(9, 64, 8);
		String duplicateId = UUID.randomUUID().toString();
		writeRecoverableBarrel(barrelBlock, duplicateId, System.currentTimeMillis() + 60_000L,
				new ItemStack(Material.DIAMOND));
		writeRecoverableBarrel(otherBlock, duplicateId, System.currentTimeMillis() + 60_000L,
				new ItemStack(Material.EMERALD));
		ChunkFixture fixture = chunkFixture(barrelBlock, otherBlock);

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());

		assertEquals(Material.AIR, barrelBlock.getType());
		assertEquals(Material.AIR, otherBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
		verify(fixture.savedWorld()).save();
	}

	@Test
	void expiredRecoverableBarrelBecomesOrdinaryWithoutResettingExpiry() {
		ItemStack item = new ItemStack(Material.DIAMOND);
		writeRecoverableBarrel(
				barrelBlock, UUID.randomUUID().toString(), System.currentTimeMillis() - 1L, item);
		ChunkFixture fixture = chunkFixture(barrelBlock);

		CrateManager.recoverCratesInChunk(plugin, admission, fixture.chunk());
		server.getScheduler().performTicks(1L);

		assertEquals(Material.BARREL, barrelBlock.getType());
		Barrel ordinary = (Barrel) barrelBlock.getState();
		assertEquals(item, ordinary.getInventory().getItem(0));
		assertFalse(Crate.hasAirdropMarker(ordinary));
		assertEquals(0, admission.snapshot().landedClaims());
		verify(fixture.savedWorld()).save();
	}

	private Crate landPaidCrate(Block block, List<ItemStack> contents) throws Exception {
		DropAdmissionController.Lease lease = admission.acquireSystem(
				DropLocationKey.from(block.getLocation()),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		Crate crate = new Crate(
				new Location(world, block.getX() + 0.5, 100, block.getZ() + 0.5),
				world, contents, options, lease, true, ignored -> { });
		crate.land(block);
		return crate;
	}

	private void writeRecoverableBarrel(Block block, String crateId, long expiresAt, ItemStack item) {
		block.setType(Material.BARREL);
		Barrel barrel = (Barrel) block.getState();
		Inventory snapshotInventory = barrel.getSnapshotInventory();
		snapshotInventory.setItem(0, item);
		barrel.getPersistentDataContainer().set(CRATE_ID_KEY, PersistentDataType.STRING, crateId);
		barrel.getPersistentDataContainer().set(PAID_KEY, PersistentDataType.BYTE, (byte) 1);
		barrel.getPersistentDataContainer().set(EXPIRES_AT_KEY, PersistentDataType.LONG, expiresAt);
		barrel.getPersistentDataContainer().set(
				RECOVERY_STATE_KEY, PersistentDataType.STRING, Crate.RecoveryState.RECOVERABLE.name());
		assertTrue(barrel.update(true, false));
		barrel.getInventory().setStorageContents(snapshotInventory.getStorageContents());
	}

	private World savedWorld() {
		World savedWorld = mock(World.class);
		when(savedWorld.getUID()).thenReturn(world.getUID());
		when(savedWorld.getName()).thenReturn(world.getName());
		return savedWorld;
	}

	private ChunkFixture chunkFixture(BlockState... states) {
		World savedWorld = savedWorld();
		Chunk chunk = mock(Chunk.class);
		when(chunk.getWorld()).thenReturn(savedWorld);
		when(chunk.getX()).thenReturn(0);
		when(chunk.getZ()).thenReturn(0);
		when(chunk.getTileEntities()).thenReturn(states);
		return new ChunkFixture(chunk, savedWorld);
	}

	private ChunkFixture chunkFixture(Block... blocks) {
		BlockState[] states = new BlockState[blocks.length];
		for (int index = 0; index < blocks.length; index++) {
			states[index] = blocks[index].getState();
		}
		return chunkFixture(states);
	}

	private record ChunkFixture(Chunk chunk, World savedWorld) {
	}

	private static void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static NamespacedKey key(String value) {
		return Objects.requireNonNull(NamespacedKey.fromString(value));
	}
}
