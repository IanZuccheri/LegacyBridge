# Preserved update screen (developer-only)

2026-09-19 — LB-04 / UI-PROMOTION / `PRESERVED_UPDATE_SCREEN`.
Implementation is separate from native acceptance. The opt-in developer host
now exposes the [completed update coordinator](PRESERVED_UPDATE_COORDINATOR.md).
It does not promote a player conversion profile or support arbitrary updates.

## Workflow

The existing `-Dlegacybridge.developer.preservedUi=true` title-menu entry is
unchanged; default remains false. In **Source**, choose **Mode: update**.

1. Select the incoming admission directory/hash and explicit full mapping bundle
   directory/manifest hash. **Prepare original / candidate** remains available;
   handing off a new admission preserves the selected route/predecessor, resets
   recovery to Apply and invalidates consent. It never applies automatically.
2. In **Previous**, enter the previous managed **ADD** transaction directory and
   its exact plan hash. This is not a freely chosen JAR in `mods`.
3. **Review files** submits the typed two-sided selection to the existing bounded
   worker. Result displays the actual reviewed modId, old/new versions and both
   artifact hashes. Editing a field—even reverting the edit—or switching kind/
   recovery mode invalidates that review. Last-reviewed details are observations,
   not a claim that edited fields have been checked.
4. **Update** opens a separate native confirmation identifying the reviewed mod,
   versions and review UUID. The form binds the accepted operation UUID, exact
   selection, successful preview, receipt and current revision; it consumes the
   **review UUID**, not the returned operation UUID. Cross-kind consent refuses.
5. **Apply and close** is manual exit after that exact accepted operation has
   finished safely with a receipt and no world/connection/server. Reopen through
   the launcher. There is no automatic restart or hot activation.
6. For **Resume/Restore**, keep the incoming admission/data and ADD predecessor,
   but select the **UPDATE** transaction/hash and previous approval in Recovery.
   A new review and new recovery-specific confirmation are mandatory. No fresh
   old/new version preview is invented for recovery.
7. History reads the separate schema-1 update receipts, with search, paging and
   predecessor UUID/plan pin. It never selects files, reconstructs a form or
   grants consent. Switching back to new-install mode reads add-only history.

## Reused safety and UI contracts

- Same `PreservedDeveloperScreen`, controller, session bridge, navigation owner,
  bounded worker and shutdown lease; no extra screen host/executor/installer.
- No filesystem/network/CLI/installer calls on the screen, including review
  construction and field validation. Disk checks are delegated to the coordinator.
- Fields are bounded and require absolute normalized paths, exact lowercase
  SHA-256 and canonical recovery UUID. Real paths/ownership/dependencies/loaded
  aliases and hashes are still independently rechecked by the backend.
- Native widgets, focus/cursor restoration, resize, narrated scrollable result,
  page/home/end navigation and inert sanitized copying are shared with add-only.
  New strings exist in en_us and pt_br. None of these implementation checks
  constitute an observed GUI-scale/keyboard/narrator test.
- Source/predecessor/data fields are not editable while accepted work runs.
  Kind/preparation controls refuse busy transitions. The separate confirmation
  remains inside the existing owner/session/navigation guard.
- Closing invalidates form consent; accepted review/installation retains the
  coordinator lease until its result/receipt. History is cancellable; accepted
  installation is not. Existing OS/direct-mod-API limitations remain.
- Activation remains `NOT_OBSERVED`. Installation is not discovery, startup,
  behavior or multiplayer proof.

## API provenance and checks

Consulted [Forge 1.20.1 screens documentation](https://docs.minecraftforge.net/en/1.20.1/gui/screens/)
for widget/focus/reinitialization contracts, and the actual pinned mapped source
archive `forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar`, SHA-256
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
Checked `EditBox.setResponder/setCursorPosition/setMaxLength/setEditable`,
`ConfirmScreen`'s BooleanConsumer constructor and `Screen.rebuildWidgets/resize/removed`.
No new Minecraft/Forge 1.16.5 symbol, remapping rule or adapter is introduced;
the source fixture and existing mappings are unchanged.

Headless evidence is recorded in the [versioned ledger](evidence/preserved-update-screen-2026-09-19.json).
Pure form tests cover bounds, bad paths/pins, route/field/mode edits, stale/missing
preview/receipt/selection, rebuild, one-use consent and separate recovery pins.
ASM tests inspect actual compiled client-only bytes without defining screens.
The real fixture test passes the native form through review/confirmation, actual
update and restoration, checks versions/hashes and intact originals, and verifies
that review/continue/history do not install. It is not a click-through UI test.

## Limits and continuation

Only one owned, unloaded preserved ADD predecessor and an independently admitted
newer `logic-preserving-v1` artifact. Chained updates, simple-content predecessors,
composed/cohort updates, mandatory inbound consumers, loaded/unknown files and
pre-discovery repairs remain unsupported. Full mapping data remains explicit
local-only; no hidden acquisition, redistribution or old game implementation.
Public Convert/Apply, the six profiles, runtime/ABI and all journals are unchanged.

Native UI/client/server, activation/behavior/multiplayer and packaged cacheless
runtime validation are **NOT_RUN** for this artifact. No game was opened.
`UI-PROMOTION` remains PARTIAL and its requires remain mandatory for promotion.

Next headless development: **LB-08 / LINKAGE-GENERAL /
OWNED_CONSTRUCTOR_DELEGATION**. Use the existing static composition/linkage base
to prove one finite owned `this(...)` constructor chain and a genuine alternate
fixture, keeping the six profile admission grammars intact. Do not widen public
conversion/install by deleting the single-constructor guard. COMPOSE-01 native
acceptance remains deferred, not satisfied; the next development is a private
admission/proof slice, not public promotion or another native QA harness.
