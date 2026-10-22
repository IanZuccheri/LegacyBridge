# Original owned-hierarchy fixture

Project-authored, no third-party mod download or private source. Uses repository
terms; it is development input and **must not be packaged in LegacyBridge.jar**.
Minecraft 1.16.5, Forge 36.2.42, official 1.16.5 mappings, Java 8 classfile target.
Build with the repository Gradle wrapper and Java 17, `-p fixtures/owned-hierarchy-1.16.5 build`.
ForgeGradle generates a real reobfuscated input JAR, not synthetic Minecraft classes.

Mod ID `legacybridge_hierarchy`, version `1.0.0`, registry item `count_token`.
Required dependencies: Forge `[36.2.42]`, Minecraft `[1.16.5]`, BOTH/NONE.
Four owned classes, 13 methods:

- `HierarchyContent`: existing DeferredRegister/Supplier registration envelope.
- `base.BaseTokenItem`: abstract Item base, protected constructor and final seed 3;
  virtual abstract score hook, inherited projected method, native foil override.
- `leaf.HierarchyItem`: final registered leaf in another package, protected score
  implementation, native override using super, inherited symbolic alias, private counter.
- `leaf.CountRule`: integer helper `count * seed + 1`.

Expected observations, **not executed**: stack size 16; absent vanilla foil,
odd counts are foil and even counts are not; projected(n) returns `3*n+1` through
leaf dispatch. Score increments the counter belonging to that Item instance;
it is not per-ItemStack storage, synchronization or a capability. Vanilla foil
short-circuits score. Native comparison must account for repeated renderer calls
and use controlled invocation counts, not assume one foil call per frame.

Resources: smelting `token_material` tag into `count_token` (40 ticks, zero XP),
tag references vanilla iron ingot, generated model references vanilla iron-ingot
texture, en_us/pt_br translations. Resource bytes are preserved in the candidate;
pack format/Forge metadata/provenance bindings are explicitly rewritten/logged.

Static candidate contract and limitations: [OWNED_HIERARCHY_DISPATCH](../../docs/OWNED_HIERARCHY_DISPATCH.md).
No existing public profile accepts this layout. The private planner returns only
an inert report/hash after actual ZIP serialization/readback and scratch cleanup,
not an installable output. Original hash, candidate hash and headless evidence:
[ledger](../../docs/evidence/owned-hierarchy-2026-09-19.json).
