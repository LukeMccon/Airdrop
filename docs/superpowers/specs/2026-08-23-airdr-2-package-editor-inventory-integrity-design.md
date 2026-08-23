# AIRDR-2 Package Editor Inventory Integrity Design

## Summary

AIRDR-2 removes the package editors' session-wide player inventory snapshot and restore behavior. Package editing remains copy-only: an operator may clone an item representation from the player inventory into a detached virtual editor, but the editor never consumes, restores, or otherwise writes the player's inventory or cursor.

The interaction boundary will fail closed. A one-shot editor session owns one player and one exact top inventory. Every interaction in that view is cancelled while the session is active or transitioning, and only a narrow, tested set of empty-cursor pickup gestures produces virtual editor changes. Inventory view changes are deferred to the next server tick and revalidated before execution.

## Problem

`PlayerInventorySnapshot` captures every player inventory slot and the cursor when an editor opens. Save, cancel, close, back, quit, and kick flows restore those old values later.

Any legitimate change made while the editor is open can consequently be lost or reversed. A newly received item can disappear, an external removal can be undone, and a payment or other inventory mutation can be rolled back.

The current editor handlers also treat most non-right-click gestures as ordinary left clicks and open or close inventories synchronously from `InventoryClickEvent`. Those behaviors enlarge the item-handling surface and conflict with Paper's documented inventory event requirements.

## Goals

- Preserve every unrelated player inventory and cursor change made while an editor is open.
- Keep package editing copy-only without moving real source items.
- Prevent virtual package items and generated controls from entering the player inventory during ordinary editor interactions.
- Give each editor a stable, one-shot owner and inventory identity.
- Cancel recognized interactions throughout next-tick view transitions.
- Make close, quit, kick, and scheduled cleanup idempotent.
- Keep failed persistence attempts open and retryable, preserving AIRDR-1 behavior.
- Validate normal and adversarial inventory gestures with unit tests and a real Paper runtime matrix.

## Non-Goals

- PDC-marked controls and visible-name collision fixes; AIRDR-7 owns those changes.
- Package browser pagination, sorting, and empty-state work from AIRDR-7.
- A browser-wide editor session registry or random session tokens.
- Closing every GUI during reload or plugin disable; AIRDR-7 owns that broader lifecycle requirement.
- Defending against another plugin that deliberately uncancels or mutates the same inventory event after Airdrop.
- Adding a player inventory snapshot, slot ledger, or speculative repair without observed Paper runtime evidence.
- Redesigning package persistence or transaction semantics beyond retaining AIRDR-1's success and failure contract.

The reload/disable lifecycle remains a known separate risk until AIRDR-7 is implemented. AIRDR-2 does not claim to close that issue.

## Considered Approaches

### Continue full snapshot restoration

This preserves the current implementation but is the direct cause of AIRDR-2. Even a cloned snapshot is stale by the time it is restored and cannot distinguish editor effects from legitimate external changes.

### Transfer real items and return them later

This could make package editing feel like a physical container, but it creates recovery paths for disconnects, full inventories, failures, and server shutdown. Package contents are templates, so physical transfer adds risk without product value.

### Repair selected slots after every interaction

A per-event repair can be appropriate if Paper applies an inventory mutation despite cancellation. No such behavior has been observed, and speculative repair could overwrite a legitimate concurrent change.

### Cancel and clone into a virtual editor

This is the selected approach. The player inventory is a read-only source for explicitly allowed clicks. Airdrop clones the source `ItemStack` into the detached top inventory and never writes back to the player.

## Architecture

### One-shot editor sessions

`CreatePackageGui` and `PackageGui` remain per-editor listener instances. Each instance has a small lifecycle:

```text
NEW -> ACTIVE -> TRANSITIONING -> CLOSED
          ^            |
          |            | persistence failure before commit/transition
          +------------+
```

- `NEW`: constructed but not successfully opened.
- `ACTIVE`: bound to one player UUID and the exact editor `Inventory` object.
- `TRANSITIONING`: save, cancel, or back has scheduled a next-tick view change. Protection remains active, but editor logic is disabled.
- `CLOSED`: terminal and unregistered. Repeated cleanup has no effect.

An editor instance may be opened only once. The first open attempt binds the viewer UUID. A later attempt is rejected even if the first open failed.

The stable identity for AIRDR-2 is the bound player UUID plus exact object identity of the top `Inventory`. A random token is not useful unless events carry it, and a broader holder/PDC identity system belongs to AIRDR-7.

### Opening an editor

The listener is registered before the open attempt so the editor cannot become visible without protection.

`openInventory(Player)` verifies all of the following:

1. The session is `NEW` and has not been bound before.
2. The plugin exists and is enabled.
3. `Player.openInventory(editorInventory)` returns a non-null `InventoryView`.
4. The returned view's top inventory is the exact editor inventory object.

If registration, opening, or view verification fails, the session moves to `CLOSED`, unregisters, and reports failure to the caller. No close event is required for cleanup.

The package creation command may perform this operation directly because it runs outside an inventory event.

The shared package browser must not open a package editor inside its click handler. It cancels the browser click, captures the player UUID and selected package name, and schedules the editor creation, registration, and open attempt for the next tick. The task proceeds only if:

- the plugin remains enabled;
- the player is still online;
- the player's current top inventory is the exact package browser inventory;
- the package still exists and the player remains authorized.

Repeated clicks may schedule multiple tasks, but tasks run sequentially. After the first successful task replaces the browser view, every later task fails the exact-view check and does nothing.

### Event routing and cancellation

A click or drag belongs to a session only when:

- the actor UUID equals the bound viewer UUID; and
- the event view's top inventory is the exact editor inventory.

For matching events in either `ACTIVE` or `TRANSITIONING`, Airdrop cancels the event first at `HIGHEST` priority. It performs virtual editor logic only in `ACTIVE`. During `TRANSITIONING`, every interaction is a cancelled no-op until the scheduled task completes or retires the session.

`NEW` and `CLOSED` sessions do not process events. An event from a different viewer or top inventory is ignored.

### Fail-closed click allowlist

Click type alone is insufficient because ordinary `LEFT` and `RIGHT` clicks may represent placement, cursor swaps, or bundle actions. An allowed editor gesture must match all of:

- exact `ClickType`;
- expected `InventoryAction`;
- empty/air cursor;
- expected clicked inventory and editable/control slot;
- an active, authorized session.

The intended allowlist is:

| Target | Gesture | Required action | Virtual result |
|---|---|---|---|
| Control slot | Exact `LEFT` | Tested empty-cursor pickup action | Execute the control |
| Editable top slot | Exact `LEFT` | Tested empty-cursor pickup action | Remove the virtual stack |
| Editable top slot | Exact `RIGHT` | Tested empty-cursor pickup action | Remove one virtual item |
| Player bottom slot | Exact `LEFT` | Tested empty-cursor pickup action | Clone the full source stack into the editor |
| Player bottom slot | Exact `RIGHT` | Tested empty-cursor pickup action | Clone one source item into the editor |

Expected action pairs, including amount-one behavior, will be encoded explicitly after tests establish Paper 1.21.8 behavior. A pair not explicitly allowed is a cancelled no-op.

The deny-by-default set includes shift clicks, number keys, offhand swaps, double clicks, drops, control-drops, middle/creative clone, cursor swaps and placement, all bundle actions, move-to-other-inventory, hotbar actions, outside clicks, `UNKNOWN`, and future unrecognized actions.

A non-empty cursor cannot interact with the editor. Closing the view leaves that cursor untouched.

### Drag behavior

Every drag in a recognized editor view is cancelled in `ACTIVE` and `TRANSITIONING`, including top-only, bottom-only, and mixed raw-slot drags. Airdrop applies no manual drag changes.

### Virtual item boundaries

Bottom-inventory items are read only. For an allowed source click, the editor clones the source `ItemStack`, adjusts only the clone's requested amount, and inserts or merges it only into editable top slots.

Top-inventory edit operations mutate only editable top slots. Generated controls cannot be moved by an inventory transaction because the event is cancelled, and manual logic never inserts them into the player inventory.

Save reads only the explicit editable slot range rather than the entire top inventory. This structurally excludes control slots from persistence. Existing sanitization and package boundaries continue cloning retained `ItemStack` values.

### Persistence and transitions

A save attempt runs while the session is `ACTIVE` on the server thread:

- validation or persistence failure leaves the session `ACTIVE` and open;
- successful persistence changes the session to `TRANSITIONING` and schedules close;
- known domain failures send the existing error feedback and remain retryable.

Cancel and back change to `TRANSITIONING` immediately and schedule their view change.

Paper documents that inventory view changes must not run inside `InventoryClickEvent`. Every close or open triggered by an editor control therefore runs on the next tick.

Before a scheduled transition changes a view, it verifies:

- plugin availability;
- `TRANSITIONING` state;
- bound player UUID;
- player availability where required; and
- the player's current top inventory is still the exact editor inventory.

If a different view is open, the task retires the old session without closing the newer view. Back opens the package browser only after the same checks. Close events caused by a successful transition and the scheduled task may both attempt cleanup; cleanup is idempotent.

### Close, quit, and kick cleanup

`InventoryCloseEvent` and `PlayerQuitEvent` retire and unregister the matching session exactly once. They do not read or write player inventory or cursor state.

`PlayerKickEvent` is cancellable, so it does not retire the session synchronously. At `MONITOR` with `ignoreCancelled = true`, Airdrop schedules a next-tick observation. It retires only when the player is offline or the exact editor view has closed. If the player remains online with the exact editor open, the session remains protected. Close and quit events remain the definitive normal cleanup paths.

### Slot repair policy

No repair is implemented by default. Paper states that cancelled inventory interactions are not executed and cancelled drags apply none of their described changes.

If a real Paper test demonstrates a mutation despite cancellation, the repair will be a separate evidence-based adjustment. It may capture only the implicated slot, hotbar/offhand entry, or cursor immediately before that event and restore only that value after revalidating the same session. A session-wide snapshot will not return.

## Error Handling

- Rejected second open: fail without rebinding or registering again.
- Disabled or missing plugin during open: close the session boundary without opening.
- Null or mismatched returned view: unregister immediately and report open failure.
- Missing package in a deferred browser task: report the existing not-found message and leave the browser open.
- Persistence failure: retain `ACTIVE`, keep the editor open, and send the AIRDR-1 persistence message.
- Scheduled task observes another view: retire without changing that view.
- Repeated terminal signals: no additional effect.

## Testing

### Unit tests with MockBukkit and direct events

Both editor classes will be tested for:

- one-shot binding and rejected repeated opens;
- null/mismatched open cleanup where the test framework permits it;
- wrong viewer and different top inventory routing;
- cancellation during `ACTIVE` and `TRANSITIONING`;
- idempotent close, quit, kick, and late scheduled cleanup;
- a scheduled task never closing a newer unrelated view;
- failed persistence remaining open and active;
- successful retry preserving the unrelated `GOLD_INGOT` introduced by the AIRDR-1 test;
- unrelated inventory slot and cursor preservation for save, cancel, ordinary close, back, quit, and kick;
- source `ItemStack` amount, type, metadata, object boundary, hotbar, offhand, and cursor preservation;
- virtual controls and package items never being written to the player inventory.

The click matrix will cover top and bottom inventories with explicit click/action/cursor combinations:

- allowed empty-cursor left and right pickups;
- shift left/right;
- number keys 0 through 8 and offhand swap;
- double-click collection;
- Q and Ctrl-Q drops;
- middle/creative clone;
- cursor placement and swap actions;
- bundle actions available in Paper 1.21.8;
- outside and unknown actions.

Drag tests will cover top-only, bottom-only, and mixed raw slots.

MockBukkit tests routing, cancellation flags, cloning, state transitions, scheduler ticks, and the absence of Airdrop writes. Constructed events do not prove the complete vanilla client/server transaction.

### Real Paper 1.21.8 verification

`./gradlew runServer` will be used to verify:

- actual click-type/action pairs for allowed left and right gestures, including amount-one stacks;
- shift-click, number/offhand, double-click, drop, middle-click, bundle, cursor, and drag reconciliation;
- cursor disposition across close and navigation;
- browser-to-editor and editor-to-browser next-tick transitions;
- cancelled or rejected opens where reproducible;
- successful kick ordering through close and quit events;
- whether any cancelled action mutates a real slot, hotbar/offhand entry, or cursor.

Any runtime-only case that cannot be automated in this repository will be recorded as a manual validation requirement rather than represented as proven by MockBukkit.

## Acceptance Criteria Mapping

- Unrelated slot changes survive: no editor path writes the player inventory.
- Cursor changes survive: no editor path restores or sets the cursor.
- Save/cancel/close/back/quit/kick: each path uses idempotent session cleanup without player-state restoration.
- Shift-click, number-key, double-click, and drag: recognized events are cancelled and denied by the explicit allowlist.
- No source consumption: allowed additions clone source stacks only.
- No virtual-item extraction: all server transactions are cancelled throughout active and transitioning states, and manual logic never targets the player inventory.
- Stable session identity: one viewer UUID, one exact top inventory, and one-shot lifecycle.
- Slot repair: omitted unless Paper runtime evidence justifies a targeted repair.

## Open Questions

No product or architecture decisions remain. Exact Paper 1.21.8 click/action pairs and kick ordering are verification findings to establish during implementation, not unresolved design choices.
