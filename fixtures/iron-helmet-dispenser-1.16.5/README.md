# Original iron helmet dispenser fixture

Authored for LegacyBridge's finite `IRON_HELMET_DISPENSER_CLOSED_RECEIVERS` audit.
This is a separate original Forge 1.16.5 / 36.2.42 mod, compiled to Java 8 bytecode.
It does not change the earlier `iron-helmet-1.16.5` fixture.

One final entrypoint registers a native IRON/HEAD helmet and exposes
`dispense(IBlockSource, ItemStack)`, a direct call to the original public static
`ArmorItem.dispenseArmor` API. The pinned MCP data maps that native method to
`func_226626_a_`. No source game class is copied or included.

The native armor constructor independently registers native dispenser behavior.
The helper is an inspectable endpoint, not an extra automatic trigger, event
listener, replacement behavior, custom entity or execution harness.

`src/test/resources/dispenser-scenarios.json` contains authored scenario
declarations, not observations. It distinguishes native player and loot-enabled
Zombie first-result selection, no-result eligibility cases, and an unsupported
ArmorStand selected before a supported player. Native source can equip that
ArmorStand; the restricted target must reject before splitting instead of skipping
it or pretending no target was found. Default zombies do not have loot pickup
enabled and must not be treated as eligible merely because their HEAD is empty.

All client/server/behavior results are **NOT_RUN**. Archive-only tests inspect
the compiled helper's exact opcode/owner/name/descriptor, ownership, pins and
authored resources without loading the entrypoint or any game class. The scenario
declarations remain test resources and are not included in the mod JAR.

No conversion profile, candidate, installation authority or armor compatibility
is implied. Query/predicate behavior, equipment effects, fallback dropping,
external hooks, synchronization and gameplay require separate qualification.
Assets reference vanilla resource identifiers without bundling game textures.
The fixture is original project work under the existing repository terms;
no external mod was acquired or redistributed.
