# Original item attribute + durability fixture

Authored Forge **1.16.5 / 36.2.42**, Java-8 bytecode, built with the repository's
Java-17/ForgeGradle 6.0.54 tooling. It is development input, not a converted mod
and never bundled with the public LegacyBridge JAR.

`legacybridge_attr_durable:bridge_durable_attribute_item` combines two existing
finite patterns in a new original: native Item properties with durability 128,
an iron-ingot repair eligibility predicate, and an owned immutable multimap of
two MAINHAND ADDITION modifiers (damage 2D, speed -1D). The modifiers read the
inherited native UUID objects; other equipment slots return an immutable empty
multimap. There is no shared player or entity state.

There are two classes and seven methods: entry constructor/static initializer/
factory; item constructor/static initializer/attribute callback/repair predicate.
The four archive-only tests check metadata, Java-8 classes/method counts, resource
references, recipe and translations without loading either original class.

The item is **not a sword or tool**. Base Item `hurtEnemy` and `mineBlock` return
false on both inspected baselines. Durability capacity does not automatically
cause wear on attack or mining; this fixture adds no wear, break callback,
inventory mutation, anvil implementation, NBT manipulation or event listener.

The model references vanilla generated-item/iron-ingot resources by identifier.
No Minecraft textures, sources, Guava, test harnesses or nested JARs are bundled.

Build serially from the main project, with the pinned environment prepared:

```powershell
.\gradlew.bat -p fixtures/item-attribute-durability-1.16.5 build --offline --no-daemon --max-workers=1
```

This command builds the original only. The separate
[private composition candidate](../../docs/ITEM_ATTRIBUTE_DURABILITY_CONTRACT.md)
now has strict source/target/readback admission, exact mappings, independent
linkage and negative tests. It returns proof/hash, not a publicly installable JAR.
Existing single-feature candidates remain independent. The
[source audit](../../docs/ITEM_ATTRIBUTE_DURABILITY_API_AUDIT.md) records semantics.
Native client/server/behavior/multiplayer validation remains NOT_RUN.
