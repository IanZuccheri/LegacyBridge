# Original owned-interface/default fixture

Project-authored development input, no downloaded/private third-party mod.
Do not include this fixture in the public product. The existing project/fixture
rights conventions remain unchanged; this is not a publication authorization.
Minecraft 1.16.5, Forge 36.2.42, official 1.16.5 mappings, Java 8 classfiles.
Build using the root wrapper on Java 17: `-p fixtures/owned-interface-1.16.5 build`.
ForgeGradle emits an actual reobfuscated JAR.

Mod ID `legacybridge_defaults`, version `1.0.0`, item `count_token`.
Mandatory dependencies Forge `[36.2.42]` and Minecraft `[1.16.5]`, BOTH/NONE.
Four classes/12 methods: registry entrypoint, direct Item subclass, public flat
`Scoring` interface and final `Counter` helper in a separate package.

Future native oracle, **NOT_RUN**:

- Item stack limit is 16; absent vanilla foil, odd counts are foil and even are not.
- `Counter.score(n)` increments its own calls field, then invokes
  `Scoring.super.score(n) + 2`. The default invokes `bias()` on the same helper,
  so the result is `3*n + 3`.
- The Item captures the interface in an IntUnaryOperator backed by javac's owned
  static lambda implementation; the callback must retain interface dispatch.
- Vanilla foil short-circuits callback execution. Tests must control invocation
  counts; renderer calls are not assumed to occur once per frame.
- Separate Counter objects must not share calls. The counter is per helper/Item
  instance, not per ItemStack, persisted player state or synchronized capability.

Recipe: smelt the local iron-ingot tag into the token in 40 ticks/zero XP.
Model uses the existing vanilla iron-ingot texture; en_us/pt_br translations.
Asset/data bytes remain unchanged; target metadata/pack/provenance changes are
explicitly logged. No texture binary, old Minecraft or Forge is redistributed.

The [private contract](../../docs/OWNED_INTERFACE_DEFAULTS.md) is not an installable
conversion profile. No original class is executed by the analyzer/converter.
[Hashes, static checks and limitations](../../docs/evidence/owned-interface-2026-09-19.json).
