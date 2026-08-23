# AIRDR-2 Package Editor Inventory Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace stale full-inventory restoration with one-shot, copy-only package editor sessions that preserve player state and fail closed for unsupported inventory gestures.

**Architecture:** Two small package-private helpers own editor lifecycle and click classification. `CreatePackageGui` and `PackageGui` retain their feature-specific save/control behavior while delegating identity, transition state, and gesture decisions; every recognized event remains cancelled through next-tick view changes. The shared package browser defers browser-to-editor navigation, and all open/close boundaries verify the exact inventory object before acting.

**Tech Stack:** Java 21, Paper API 1.21.8, Bukkit inventory events and scheduler, MockBukkit 3.133.2, JUnit Jupiter, Mockito, Gradle run-paper

---

## File Structure

- Create `src/main/java/com/airdropmc/packages/PackageEditorSession.java`: one-shot viewer binding, exact inventory ownership, and `NEW/ACTIVE/TRANSITIONING/CLOSED` state.
- Create `src/main/java/com/airdropmc/packages/PackageEditorInteraction.java`: deny-by-default `(ClickType, InventoryAction, cursor)` classification.
- Modify `src/main/java/com/airdropmc/packages/CreatePackageGui.java`: self-owned registration/opening, copy-only clicks, deferred close, and idempotent cleanup.
- Modify `src/main/java/com/airdropmc/packages/PackageGui.java`: the same safety boundary plus deferred Back navigation.
- Modify `src/main/java/com/airdropmc/packages/PackagesGui.java`: defer browser-to-editor opening and revalidate the shared browser view.
- Modify `src/main/java/com/airdropmc/controllers/PackageController.java`: let editor instances own registration and observe open failure.
- Delete `src/main/java/com/airdropmc/packages/PlayerInventorySnapshot.java`: remove the stale restore mechanism.
- Delete `src/test/java/com/airdropmc/packages/PlayerInventorySnapshotTest.java`: replace tests that require the unsafe behavior.
- Create `src/test/java/com/airdropmc/packages/PackageEditorSessionTest.java`: lifecycle and exact-identity unit tests.
- Create `src/test/java/com/airdropmc/packages/PackageEditorInteractionTest.java`: complete click/action/cursor policy matrix.
- Create `src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java`: both editors' preservation, cancellation, copying, and cleanup behavior.
- Create `src/test/java/com/airdropmc/packages/PackagesGuiNavigationTest.java`: next-tick browser-to-editor transition and repeated-click protection.
- Modify `src/test/java/com/airdropmc/packages/PackagePersistenceFailureFeedbackTest.java`: preserve unrelated player changes after successful retry.

### Task 1: Add the one-shot session state boundary

**Files:**
- Create: `src/test/java/com/airdropmc/packages/PackageEditorSessionTest.java`
- Create: `src/main/java/com/airdropmc/packages/PackageEditorSession.java`

- [x] **Step 1: Write failing lifecycle and identity tests**

Cover binding once, exact inventory identity, protection in `ACTIVE` and `TRANSITIONING`, processing only in `ACTIVE`, and idempotent retirement:

```java
class PackageEditorSessionTest {
	@Test
	void bindsOneViewerAndExactInventoryOnce() {
		Inventory editor = mock(Inventory.class);
		Player owner = player(UUID.randomUUID());
		Player other = player(UUID.randomUUID());
		PackageEditorSession session = new PackageEditorSession(editor);

		assertTrue(session.bind(owner));
		assertTrue(session.activate(editor));
		assertTrue(session.protects(owner, editor));
		assertTrue(session.canProcess(owner, editor));
		assertFalse(session.bind(other));
		assertFalse(session.protects(other, editor));
		assertFalse(session.protects(owner, mock(Inventory.class)));
	}

	@Test
	void transitioningRemainsProtectedButCannotProcess() {
		Inventory editor = mock(Inventory.class);
		Player owner = player(UUID.randomUUID());
		PackageEditorSession session = activeSession(editor, owner);

		assertTrue(session.beginTransition());
		assertTrue(session.protects(owner, editor));
		assertFalse(session.canProcess(owner, editor));
		assertFalse(session.beginTransition());
	}

	@Test
	void retirementIsTerminalAndIdempotent() {
		PackageEditorSession session = activeSession(mock(Inventory.class), player(UUID.randomUUID()));

		assertTrue(session.retire());
		assertFalse(session.retire());
		assertEquals(PackageEditorSession.State.CLOSED, session.state());
	}
}
```

- [x] **Step 2: Run the new test and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorSessionTest`

Expected: compilation fails because `PackageEditorSession` does not exist.

- [x] **Step 3: Implement the minimal session helper**

Implement this package-private API:

```java
final class PackageEditorSession {
	enum State { NEW, ACTIVE, TRANSITIONING, CLOSED }

	private final Inventory inventory;
	private UUID viewerId;
	private State state = State.NEW;

	PackageEditorSession(Inventory inventory) {
		this.inventory = Objects.requireNonNull(inventory);
	}

	boolean bind(Player player) {
		if (state != State.NEW || viewerId != null) return false;
		viewerId = player.getUniqueId();
		return true;
	}

	boolean activate(Inventory openedInventory) {
		if (state != State.NEW || viewerId == null || openedInventory != inventory) return false;
		state = State.ACTIVE;
		return true;
	}

	boolean protects(HumanEntity actor, Inventory topInventory) {
		return viewerId != null
				&& viewerId.equals(actor.getUniqueId())
				&& topInventory == inventory
				&& (state == State.ACTIVE || state == State.TRANSITIONING);
	}

	boolean canProcess(HumanEntity actor, Inventory topInventory) {
		return protects(actor, topInventory) && state == State.ACTIVE;
	}

	boolean beginTransition() {
		if (state != State.ACTIVE) return false;
		state = State.TRANSITIONING;
		return true;
	}

	boolean retire() {
		if (state == State.CLOSED) return false;
		state = State.CLOSED;
		return true;
	}

	State state() { return state; }
	UUID viewerId() { return viewerId; }
}
```

- [x] **Step 4: Run the session tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorSessionTest`

Expected: all session tests pass.

- [x] **Step 5: Commit the session boundary**

```bash
git add src/main/java/com/airdropmc/packages/PackageEditorSession.java src/test/java/com/airdropmc/packages/PackageEditorSessionTest.java
git commit -m "AIRDR-2: add package editor session state"
```

### Task 2: Add a deny-by-default gesture policy

**Files:**
- Create: `src/test/java/com/airdropmc/packages/PackageEditorInteractionTest.java`
- Create: `src/main/java/com/airdropmc/packages/PackageEditorInteraction.java`

- [x] **Step 1: Write the failing gesture matrix**

Define the intended virtual results and parameterize rejected click/action combinations:

```java
class PackageEditorInteractionTest {
	@Test
	void allowsOnlyExpectedEmptyCursorPickups() {
		assertEquals(FULL_STACK, classify(ClickType.LEFT, InventoryAction.PICKUP_ALL, null, false));
		assertEquals(SINGLE_ITEM, classify(ClickType.RIGHT, InventoryAction.PICKUP_HALF, air(), false));
		assertEquals(CONTROL, classify(ClickType.LEFT, InventoryAction.PICKUP_ALL, null, true));
		assertEquals(DENY, classify(ClickType.RIGHT, InventoryAction.PICKUP_HALF, null, true));
	}

	@ParameterizedTest
	@MethodSource("deniedInteractions")
	void deniesUnsupportedInteractions(ClickType click, InventoryAction action, ItemStack cursor) {
		assertEquals(DENY, classify(click, action, cursor, false));
	}

	static Stream<Arguments> deniedInteractions() {
		return Stream.of(
				arguments(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, null),
				arguments(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, null),
				arguments(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, null),
				arguments(ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR, null),
				arguments(ClickType.DROP, InventoryAction.DROP_ONE_SLOT, null),
				arguments(ClickType.MIDDLE, InventoryAction.CLONE_STACK, null),
				arguments(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, new ItemStack(Material.STONE)),
				arguments(ClickType.LEFT, InventoryAction.PICKUP_FROM_BUNDLE, null),
				arguments(ClickType.UNKNOWN, InventoryAction.UNKNOWN, null));
	}
}
```

- [x] **Step 2: Run the policy test and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInteractionTest`

Expected: compilation fails because the interaction classifier does not exist.

- [x] **Step 3: Implement the minimal classifier**

```java
final class PackageEditorInteraction {
	enum VirtualAction { DENY, CONTROL, FULL_STACK, SINGLE_ITEM }

	static VirtualAction classify(ClickType click, InventoryAction action, ItemStack cursor, boolean controlSlot) {
		if (cursor != null && !cursor.getType().isAir()) return VirtualAction.DENY;
		if (click == ClickType.LEFT && action == InventoryAction.PICKUP_ALL) {
			return controlSlot ? VirtualAction.CONTROL : VirtualAction.FULL_STACK;
		}
		if (!controlSlot && click == ClickType.RIGHT && action == InventoryAction.PICKUP_HALF) {
			return VirtualAction.SINGLE_ITEM;
		}
		return VirtualAction.DENY;
	}
}
```

Do not broaden the right-click action until a Paper 1.21.8 runtime observation proves another exact pair is required.

- [x] **Step 4: Run the policy test and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInteractionTest`

Expected: the allowed and denied matrix passes.

- [x] **Step 5: Commit the gesture policy**

```bash
git add src/main/java/com/airdropmc/packages/PackageEditorInteraction.java src/test/java/com/airdropmc/packages/PackageEditorInteractionTest.java
git commit -m "AIRDR-2: restrict package editor gestures"
```

### Task 3: Remove inventory restoration and make editor opening one-shot

**Files:**
- Delete: `src/main/java/com/airdropmc/packages/PlayerInventorySnapshot.java`
- Delete: `src/test/java/com/airdropmc/packages/PlayerInventorySnapshotTest.java`
- Create: `src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java`
- Modify: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageGui.java`
- Modify: `src/main/java/com/airdropmc/controllers/PackageController.java`

- [x] **Step 1: Write failing preservation and one-shot opening tests**

Use MockBukkit to open each editor, mutate a player slot and cursor after opening, close it, and assert exact preservation. Also verify a second open is rejected:

```java
@Test
void ordinaryClosePreservesInventoryAndCursor() {
	PlayerMock player = operator();
	PackageGui gui = new PackageGui(packageWithStone());
	assertTrue(gui.openInventory(player));
	ItemStack unrelated = new ItemStack(Material.GOLD_INGOT, 4);
	ItemStack cursor = new ItemStack(Material.EMERALD, 2);
	player.getInventory().setItem(0, unrelated);
	player.setItemOnCursor(cursor);

	player.closeInventory();

	assertSame(unrelated, player.getInventory().getItem(0));
	assertSame(cursor, player.getItemOnCursor());
}

@Test
void editorInstanceCannotBeOpenedTwice() {
	PlayerMock first = operator();
	PlayerMock second = operator();
	CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);

	assertTrue(gui.openInventory(first));
	assertFalse(gui.openInventory(second));
	assertSame(guiInventory(first), first.getOpenInventory().getTopInventory());
}
```

Add direct close/quit/cancelled-kick tests that verify no call to `PlayerInventory#setContents`, `Player#setItemOnCursor`, or `Player#updateInventory` is made by editor cleanup.

- [x] **Step 2: Run the integrity test and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest`

Expected: tests fail because `openInventory` returns `void` and close still restores the snapshot.

- [x] **Step 3: Implement self-owned open and terminal cleanup in both editors**

For each editor:

After constructing `inv` in each constructor, construct the session from that final inventory:

```java
private final PackageEditorSession session;

// Constructor, after assigning inv:
session = new PackageEditorSession(inv);

public boolean openInventory(Player player) {
	if (!session.bind(player)) return false;
	Airdrop plugin = Airdrop.getPluginInstance();
	if (plugin == null || !plugin.isEnabled()) return retire();
	Bukkit.getPluginManager().registerEvents(this, plugin);
	try {
		InventoryView view = player.openInventory(inv);
		if (view == null || view.getTopInventory() != inv || !session.activate(view.getTopInventory())) {
			retire();
			return false;
		}
		return true;
	} catch (RuntimeException error) {
		retire();
		throw error;
	}
}

private boolean retire() {
	if (!session.retire()) return false;
	HandlerList.unregisterAll(this);
	return false;
}
```

Remove the snapshot field, capture, restore calls, and `viewerId`. Delete `PlayerInventorySnapshot` and its tests. Change close and quit handlers to call only `retire()` for the matching exact session.

Update `PackageController.createPackageCommand` to remove manual listener registration and send `PACKAGES_CREATE_OPEN_ERROR` when `openInventory(player)` returns `false`.

- [x] **Step 4: Run focused preservation tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest --tests com.airdropmc.packages.PackageControllerPermissionsTest`

Expected: player slot/cursor preservation and controller tests pass.

- [x] **Step 5: Commit removal of stale restoration**

```bash
git add -A src/main/java/com/airdropmc/packages src/main/java/com/airdropmc/controllers/PackageController.java src/test/java/com/airdropmc/packages
git commit -m "AIRDR-2: remove package editor inventory restores"
```

### Task 4: Route clicks through the safe copy-only policy

**Files:**
- Modify: `src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java`
- Modify: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageGui.java`

- [x] **Step 1: Add failing handler tests for allowed and denied actions**

For both editor classes, construct top and bottom events and assert:

```java
@Test
void allowedBottomLeftClickClonesWithoutConsumingSource() {
	ItemStack source = new ItemStack(Material.DIAMOND, 5);
	player.getInventory().setItem(0, source);
	InventoryClickEvent event = click(player, bottomRawSlot(0), ClickType.LEFT,
			InventoryAction.PICKUP_ALL, null);

	gui.onInventoryClick(event);

	assertTrue(event.isCancelled());
	assertEquals(5, source.getAmount());
	assertSame(source, player.getInventory().getItem(0));
	assertNotSame(source, editor.getItem(0));
	assertEquals(5, editor.getItem(0).getAmount());
}

@ParameterizedTest
@MethodSource("deniedEditorEvents")
void deniedActionsAreCancelledNoOps(ClickType click, InventoryAction action, ItemStack cursor) {
	InventorySnapshot before = snapshot(player, editor);
	InventoryClickEvent event = click(player, editableTopSlot(), click, action, cursor);

	gui.onInventoryClick(event);

	assertTrue(event.isCancelled());
	assertUnchanged(before, player, editor);
}
```

Include transition-state cancellation and drag events covering top-only, bottom-only, and mixed raw slots.

- [x] **Step 2: Run the handler tests and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest`

Expected: denied actions currently trigger manual add/remove behavior or use the old broad `isRightClick` branch.

- [x] **Step 3: Implement exact routing and copy-only mutations**

At the start of each click handler:

```java
Inventory top = e.getView().getTopInventory();
if (!(e.getWhoClicked() instanceof Player player) || !session.protects(player, top)) return;
e.setCancelled(true);
if (!session.canProcess(player, top)) return;
```

Classify the gesture before control or edit logic:

```java
boolean controlSlot = clickedInventory == inv && isControlSlot(e.getSlot());
VirtualAction action = PackageEditorInteraction.classify(
		e.getClick(), e.getAction(), e.getCursor(), controlSlot);
if (action == VirtualAction.DENY) return;
```

Use exact slot checks for controls. Treat only slots `0` through `PackageManager.MAX_PACKAGE_ITEM_STACKS - 1` as editable; unused layout slots are denied. For editable top slots, `FULL_STACK` clears the virtual slot and `SINGLE_ITEM` decrements only the virtual stack. For bottom slots, clone the source and insert the full amount or one item. Never call a player inventory setter.

For drag handlers, require the same session identity, cancel in both `ACTIVE` and `TRANSITIONING`, and apply no manual changes.

- [x] **Step 4: Run the interaction tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInteractionTest --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest`

Expected: allowed copies work without source changes and every denied action is a cancelled no-op.

- [x] **Step 5: Commit the safe interaction boundary**

```bash
git add src/main/java/com/airdropmc/packages/CreatePackageGui.java src/main/java/com/airdropmc/packages/PackageGui.java src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java
git commit -m "AIRDR-2: make package editor interactions copy-only"
```

### Task 5: Defer editor transitions and make cleanup idempotent

**Files:**
- Modify: `src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java`
- Modify: `src/test/java/com/airdropmc/packages/PackagePersistenceFailureFeedbackTest.java`
- Modify: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageGui.java`

- [x] **Step 1: Add failing lifecycle and scheduler tests**

Test that save/cancel/back remain open until the next scheduler tick, deny a second interaction during that tick, never close a newer view, and preserve unrelated inventory/cursor state. Update the AIRDR-1 retry assertion:

```java
when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
gui.save(saveEvent);

assertSame(editor, player.getOpenInventory().getTopInventory());
server.getScheduler().performOneTick();

assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
assertEquals(new ItemStack(Material.GOLD_INGOT, 4), player.getInventory().getItem(0));
```

Add close+quit, kick+quit, repeated control, and cancelled-kick tests. For a successful kick, run the scheduler and retire only after the mock player becomes offline or the view closes.

- [x] **Step 2: Run the lifecycle tests and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest`

Expected: current save/cancel/back change views synchronously and the retry test restores the old slot.

- [x] **Step 3: Implement next-tick transition helpers**

Use one helper in each editor:

```java
private void scheduleTransition(Player player, Runnable viewChange) {
	if (!session.beginTransition()) return;
	Airdrop plugin = Airdrop.getPluginInstance();
	if (plugin == null || !plugin.isEnabled()) {
		retire();
		return;
	}
	Bukkit.getScheduler().runTask(plugin, () -> {
		if (session.state() != State.TRANSITIONING) return;
		if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
			retire();
			return;
		}
		viewChange.run();
	});
}
```

Save calls `scheduleTransition(player, player::closeInventory)` only after persistence returns `true`. Persistence failure leaves `ACTIVE`. Cancel schedules close. Back schedules `Airdrop.getPackagesGui().openInventory(player)` without a synchronous close.

Handle matching close and quit with `retire()`. Handle kick at `MONITOR, ignoreCancelled = true` by scheduling a next-tick observation and retiring only when the player is offline or no longer has the exact editor view. Matching events remain cancelled while `TRANSITIONING`.

- [x] **Step 4: Run lifecycle tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest`

Expected: transitions occur one tick later, repeated paths are idempotent, and player changes survive.

- [x] **Step 5: Commit deferred lifecycle handling**

```bash
git add src/main/java/com/airdropmc/packages/CreatePackageGui.java src/main/java/com/airdropmc/packages/PackageGui.java src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java src/test/java/com/airdropmc/packages/PackagePersistenceFailureFeedbackTest.java
git commit -m "AIRDR-2: defer package editor view transitions"
```

### Task 6: Defer package browser to editor navigation

**Files:**
- Create: `src/test/java/com/airdropmc/packages/PackagesGuiNavigationTest.java`
- Modify: `src/main/java/com/airdropmc/packages/PackagesGui.java`

- [x] **Step 1: Write failing next-tick browser navigation tests**

Open the shared package browser, click a package, and verify the browser remains open during the event and the editor opens only on the next tick:

```java
@Test
void packageEditorOpensOnNextTickOnly() {
	PackagesGui browser = configuredBrowserWith("starter");
	browser.openInventory(player);
	Inventory browserInventory = player.getOpenInventory().getTopInventory();
	InventoryClickEvent click = packageClick(player, ClickType.LEFT, InventoryAction.PICKUP_ALL);

	browser.onInventoryClick(click);

	assertTrue(click.isCancelled());
	assertSame(browserInventory, player.getOpenInventory().getTopInventory());
	server.getScheduler().performOneTick();
	assertNotSame(browserInventory, player.getOpenInventory().getTopInventory());
}
```

Add repeated-click, changed-view-before-tick, missing-package-before-tick, wrong viewer, nonempty cursor, and denied-action cases. Repeated tasks must result in one editor only.

- [x] **Step 2: Run the navigation test and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackagesGuiNavigationTest`

Expected: the current handler opens and registers `PackageGui` synchronously.

- [x] **Step 3: Schedule and revalidate browser navigation**

In the browser click handler, require exact top inventory identity, administrator permission, empty cursor, exact `LEFT`, and `PICKUP_ALL`. Cancel before returning for every recognized browser interaction. Capture only UUID and package name, then schedule:

```java
Bukkit.getScheduler().runTask(plugin, () -> {
	Player current = Bukkit.getPlayer(viewerId);
	if (current == null || !current.isOnline()) return;
	if (current.getOpenInventory().getTopInventory() != inv) return;
	if (!PermissionsHelper.isAdmin(current)) return;
	try {
		PackageGui editor = new PackageGui(PackageManager.get(packageName));
		if (!editor.openInventory(current)) {
			ChatHandler.sendError(current, MessageKey.PACKAGES_CREATE_OPEN_ERROR);
		}
	} catch (PackageNotFoundException error) {
		ChatHandler.sendError(current, MessageKey.ERROR_PACKAGE_NOT_FOUND,
				Map.of("name", error.getPackageName()));
	}
});
```

Remove manual editor listener registration. The first successful task replaces the browser view; later tasks fail the exact-view check.

- [x] **Step 4: Run navigation and editor tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackagesGuiNavigationTest --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest`

Expected: browser navigation is next-tick, single-open, and leak-free.

- [x] **Step 5: Commit deferred browser navigation**

```bash
git add src/main/java/com/airdropmc/packages/PackagesGui.java src/test/java/com/airdropmc/packages/PackagesGuiNavigationTest.java
git commit -m "AIRDR-2: defer package editor navigation"
```

### Task 7: Persist only editable slots and verify AIRDR-2

**Files:**
- Modify: `src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java`
- Modify: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageGui.java`
- Modify: `docs/superpowers/plans/2026-08-23-airdr-2-package-editor-inventory-integrity.md`

- [x] **Step 1: Add failing persistence-boundary tests**

Place generated controls and package-like items in control slots, place valid items in every editable boundary slot, save, and capture the items passed to `PackageManager`. Assert only editable slot contents are included and each retained item is cloned.

```java
List<ItemStack> saved = capturedPersistedItems();
assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, saved.size());
assertTrue(saved.stream().noneMatch(PackageGui::isControlItemStack));
assertNotSame(editor.getItem(0), saved.get(0));
```

- [x] **Step 2: Run the boundary test and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest`

Expected: save currently starts with `inv.getContents()` rather than an explicit editable-slot list.

- [x] **Step 3: Collect explicit editable slots**

Add a helper in each editor:

```java
private List<ItemStack> editableItems() {
	List<ItemStack> items = new ArrayList<>(PackageManager.MAX_PACKAGE_ITEM_STACKS);
	for (int slot = 0; slot < PackageManager.MAX_PACKAGE_ITEM_STACKS; slot++) {
		ItemStack item = inv.getItem(slot);
		if (item != null && !item.getType().isAir()) items.add(item.clone());
	}
	return items;
}
```

Pass only this list through existing sanitization and persistence. Do not inspect control slots during save.

- [x] **Step 4: Run focused tests, full test suite, and build**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageEditorSessionTest \
  --tests com.airdropmc.packages.PackageEditorInteractionTest \
  --tests com.airdropmc.packages.PackageEditorInventoryIntegrityTest \
  --tests com.airdropmc.packages.PackagesGuiNavigationTest \
  --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest
./gradlew clean build
git diff --check
```

Expected: all focused tests and the full build pass; no whitespace errors.

- [x] **Step 5: Start Paper 1.21.8 and record runtime validation**

Run: `./gradlew runServer`

Verify startup first. With a Paper client/operator, execute the gesture matrix from the design: allowed left/right copies, amount-one stacks, shift, number/offhand, double-click, Q/Ctrl-Q, middle, cursor placement/swap, bundles, top/bottom/mixed drag, browser/editor transitions, close/back, and kick ordering.

If no player client is available, record those cases as manual validation requirements and do not claim that MockBukkit proved vanilla reconciliation. Add targeted repair only when a concrete failed runtime case identifies the exact mutated location.

- [x] **Step 6: Review the final diff and acceptance mapping**

Run:

```bash
git status --short
git diff --stat HEAD~6..HEAD
rg -n "PlayerInventorySnapshot|setContents\(|setItemOnCursor\(|updateInventory\(" src/main/java/com/airdropmc/packages
```

Expected: `PlayerInventorySnapshot` is absent; package editor code contains no player inventory or cursor restoration; only AIRDR-2 files and documentation changed.

- [x] **Step 7: Mark the plan complete and commit**

Check every completed step in this plan, then:

```bash
git add src/main/java/com/airdropmc/packages/CreatePackageGui.java src/main/java/com/airdropmc/packages/PackageGui.java src/test/java/com/airdropmc/packages/PackageEditorInventoryIntegrityTest.java
git add -f docs/superpowers/plans/2026-08-23-airdr-2-package-editor-inventory-integrity.md
git commit -m "AIRDR-2: verify package editor inventory integrity" -m "Closes AIRDR-2"
```

Expected: the branch is clean and contains an AIRDR-2 closing reference.

## Execution Record

- Focused AIRDR-2 tests passed on 2026-08-23.
- `./gradlew clean build` passed, including the complete test suite.
- `git diff --check` passed, and the package editor source contains no calls to `setContents`, `setItemOnCursor`, or `updateInventory`.
- Paper startup was attempted. The repository's `runServer` task has no configured Minecraft version; a temporary explicit `1.21.8` invocation also could not resolve a Paper server build in this environment. The temporary override was removed.
- Live-client validation of vanilla reconciliation and the complete gesture matrix remains manual. MockBukkit coverage verifies the plugin's cancellation, copy-only mutation, lifecycle, and persistence logic but is not claimed as proof of client/server inventory reconciliation.
