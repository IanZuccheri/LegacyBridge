# Preserved developer screen (LB-04 / UI-PROMOTION)

Follow-up 2026-09-19: [PRESERVED_UPDATE_SCREEN](PRESERVED_UPDATE_SCREEN.md) extends
this same host with explicit ADD/UPDATE routing, predecessor selection, reviewed
version/hash comparison, separate update consent, recovery and inert history.
The initial add-only scope below is historical; native acceptance remains NOT_RUN.

Implementation slice: `PRESERVED_DEVELOPER_SCREEN`, 2026-09-19. Experimental/Beta;
native acceptance **NOT_RUN**. This is not public profile promotion, conversion
of arbitrary behavior, or completion of LB-04. The existing public Convert/Apply
route still admits only its existing simple-content contract.

## Explicit entry and scope

The single public `LegacyBridge.jar` contains the physical-client host and screen.
Only the explicit JVM property `-Dlegacybridge.developer.preservedUi=true` adds
the separate **Preserved (dev)** title-screen button. It is false by default;
ordinary `ConversionScreen` still uses the ordinary, preserved-disabled controller.
The two screens cannot own live controllers simultaneously. A closing controller
continues to reserve the menu until its workers have terminated.

The review screen consumes a prepared logic-preserving-v1 admission, not a
legacy JAR selected by filename. The later [preparation workflow](PRESERVED_PREPARATION_WORKFLOW.md)
adds a child screen that prepares it using the existing engine/store, then only
populates this form. Alternatively enter these independently reviewed values:

1. Absolute admission directory and its exact plan SHA-256.
2. Absolute full mapping-bundle directory and exact manifest SHA-256.
3. For recovery only: installation transaction directory, installation plan hash,
   prior approval UUID, and explicit RESUME or RESTORE mode.

The existing [admission journal](PRESERVED_ADMISSION_JOURNAL.md) and
[installer](PRESERVED_ADD_ONLY_INSTALL.md) define how those records are prepared.
The review/confirmation page itself does not convert or prepare admissions;
that is the separate controlled preparation operation. Neither screen auto-discovers
private data, downloads it, or infers pins from a historical receipt. The explicit local
full data remains `LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`; it is not
embedded. Included simple-content data is not a fallback for this route.

## Review, consent and installation

`PreservedDeveloperForm` is a bounded JDK-only form: paths at most 4,096 characters,
hashes exactly 64 lowercase hex characters, approval UUID at most 36 characters.
Syntax checks perform no file I/O. Relative/non-normalized/control-character
paths fail before submitting work; the existing backend still resolves real
paths, rejects unsafe aliases and revalidates every identity/hash/ABI/dependency.

Review calls only `ConversionController.reviewPreservedInstall`. Its worker,
queue, cancellation policy, session gate and shutdown lease remain the existing
[coordinator](PRESERVED_INSTALL_COORDINATOR.md), not another executor or installer.
Successful review is not consent. The form binds its revision to that operation;
an edit, mode change, history refresh or closure invalidates confirmation even if
the previous text is restored. Widget rebuilds with identical text do not.

Confirm opens a native `ConfirmScreen` for the current review UUID. Confirmation
consumes its one-use form token and invokes the existing controller's independent
one-use current-session check. A failed attempt needs fresh review. Unknown,
stale, blocked, terminal or receiptless observations never grant confirmation.

APPLY remains add-only; RESUME/RESTORE use the explicitly selected transaction.
The result view shows actual state, identity, hashes, transaction, installed path,
receipt and structured error/remediation. `PENDING_RESTART` means disk commit,
not discovery, activation, bytecode execution or correct behavior. The screen
does not rewrite the controller's typed result into a simple-content job.

## Client session and shutdown boundaries

`PreservedHostSession` captures its owner thread, accepts publication only there,
and exposes immutable `InstallSession` values via an `AtomicReference`. Unavailable
or detached hosts fail closed. A worker never accesses a Minecraft object.

The client constructor reads Forge's completed `ModList` IDs and loaded file
paths without reading JARs. On the client thread, each owned-screen tick and each
explicit submission publishes world/connection/server state. A client END-tick
hook also publishes while the confirmation screen is open. Direct navigation
away invalidates the host rather than retaining a false closed-world snapshot.
The controller now retains `SESSION_UNAVAILABLE` when the provider fails during
confirmation; it no longer replaces that cause with generic `SESSION_CHANGED`.

`ScreenEvent.Opening` blocks normal navigation out of owned screens during an
accepted review/install. Back is disabled then. Forced removal closes the
controller non-blockingly; its already acquired shutdown lease remains owned
through durable receipt completion. There is no hard veto of another mod's direct
world APIs, QuickPlay, process kill, OS power loss or an event listener undoing
cancellation. Those are not certified by this host.

**Apply and close — reopen through your launcher** closes only for its own accepted
operation UUID, controller idle, a safe terminal result plus receipt, and no open
world/connection/server. Failure or partial recovery keeps the game open with an
explanation. `Minecraft.stop()` is ordinary shutdown, not automatic restart.
No launcher command line or authentication material is read or persisted.

## History, controls and side separation

History is explicitly refreshed through the existing bounded/cancellable worker
reader (128 receipts, 64 KiB per file, 2 MiB aggregate and existing scan/deadline
limits). It is searchable and displayed one receipt at a time, with schema and
partial-read diagnostics. It never rehydrates fields, jobs or install authority.
UUID ordering does not mean chronology/latest result. Refresh invalidates any
current confirmation; cancelling a read does not restore it.

The screen has admission/data, recovery, result and history tabs; en_us and pt_br;
native edit boxes, buttons, confirmation and scrollable/narrated lists. Tab focus,
cursor positions and scroll are retained through rebuild/resize where applicable;
Page Up/Down and Home/End navigate a focused result list. Copy exports bounded
literal technical text, not an executable chat component or an OS path action.
No thumbnails or unbounded metadata are loaded. Visual layout, narration, GUI
scale and actual input behavior are still pending native QA.

The host event subscriber is restricted to `Dist.CLIENT`, and screens/widgets to
`@OnlyIn(Dist.CLIENT)`. No common entry point links to the screen. No new services,
libraries, modules, bootstrap, adapters or transformation rules were added.

## Exact API audit and validation

Actual pinned mapped Forge source archive inspected read-only:
`forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar`, SHA-256
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
Relevant declarations: `Screen.rebuildWidgets` line 333, `removed` 352, `resize`
429; `EditBox.setResponder` 76, `setCursorPosition` 241, `setMaxLength` 448,
`setEditable` 504; `ConfirmScreen` callback constructor 30 and escape handling
106; `TickEvent.Phase` 24 and `ClientTickEvent` 67; `ScreenEvent.Opening` 993,
`getCurrentScreen` 1013 and nullable `getNewScreen` 1022. Actual target compilation
checks inherited widget methods too. Existing ModList/shutdown/discovery and
source 1.16.5 comparison evidence remains in [PRODUCT_API_BASELINES](PRODUCT_API_BASELINES.md)
and [P4_UI_API_BASELINES](P4_UI_API_BASELINES.md). No legacy API or mapping changed.

The artifact-bound [ledger](evidence/preserved-developer-screen-2026-09-19.json)
records focused tests, serial builds, hashes, failures and package delta. The
checks exercise the pure form, host thread bridge, actual existing authored
fixture/admission/installer, receipt handling and ASM inspection of compiled UI
boundaries. They do not instantiate native widgets or execute either mod fixture.
Synthetic platform metadata is not Forge discovery proof. Current client/server,
activation, GUI, behavior and multiplayer checks remain **NOT_RUN**.

## Next development, without repeating this host

`LB-01 / PRODUCT-COMPOSED / PRESERVED_PREPARATION_WORKFLOW`: connect the existing
finite preserved engine and admission-store preparation to typed, bounded
developer-only product operations. Select original/candidate/data with hashes,
show actual progress/cancellation, retain structured reports and expose a new
admission only after complete fresh validation. No partial publication, saved-PASS
authority, rule duplication or automatic installation; public simple-content and
all promotion prerequisites stay unchanged. The current screen deliberately
starts later in that workflow, from an existing admission.
