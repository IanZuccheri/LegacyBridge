# Authored event priority fixture (private developer evidence)

Minecraft 1.16.5, Forge 36.2.42; Java 17 toolchain emitting Java 8 bytecode.
Four owned classes, twelve methods, a plain Item, one local-tag smelting recipe,
vanilla texture reference and en_us/pt_br translations. Original LegacyBridge
fixture code, covered by the repository LICENSE; no upstream mod code or game
sources are included.

From the repository root with the pinned Java toolchain:
`gradlew.bat -p fixtures/event-priority-1.16.5 build`.

This only compiles/reobfuscates the fixture; it does not launch Forge or prove
event behavior. No test source set is present in this fixture project. Actual
static conversion tests are the opt-in `EventPriorityIntegrationTest` in the
converter module using explicit pinned mapping/API data.

The HIGH handler appends 1 and cancels a fresh owned event. LOW receives cancelled
events and appends 2. Work is posted only from common-setup enqueueWork; the expected
fresh isolated trace is 12 with cancellation true. The negative/false-listener
variants are constructed as owned bytecode mutations in converter tests, never
loaded/executed as mod classes.

See [the exact contract and limitations](../../docs/EVENT_PRIORITY_CANCELLATION.md)
and [artifact-bound evidence](../../docs/evidence/event-priority-2026-09-19.json).
The private candidate returns only proof/hash and is not exported, installable,
a public profile, native behavior evidence or a port of Clumps.
