# SERVER config fixture (original source)

Authored in this repository, not a downloaded third-party mod. Built against
Minecraft 1.16.5 / Forge 36.2.42, Java 8 bytecode, official 1.16.5 mappings.

Four classes / sixteen methods: native deferred item, plain Item, native
boolean/int ForgeConfigSpec, and an observable logical-server snapshot.
Item/model, tag, smelting recipe and en_us/pt_br resources exercise unchanged
byte preservation. The model references the vanilla iron-ingot texture; no
Minecraft texture or binary is redistributed here.

Register SERVER unconditionally on both physical sides (including integrated
server). On the Forge bus, NORMAL/non-cancelled explicit listeners read loaded
values on FMLServerStartedEvent and clear active/values on FMLServerStoppedEvent.
Keys/defaults/range: enabled=true, factor=3, 1..8. Started/stopped counters are
observations, not synchronization or per-player state.

Expected fresh normal start: active=true, enabled=true, factor=3, starts=1,
stops=0. Expected normal stop: active=false, enabled=false, factor=0, starts=1,
stops=1. A subsequent world's start reads its newly loaded spec. Startup failure
can stop without starting; equal counts are NOT an unconditional invariant.
Native tests, custom files, second world, reload and connection sync NOT_RUN.

Build with the root wrapper and `-p fixtures/server-config-1.16.5 build`.
Private `planServerConfig` returns proof/hash only; no public conversion profile,
fixture class execution or installable output is authorized by the static proof.
See ../../docs/SERVER_CONFIG_SCOPE.md. No game launch is required for these checks.
