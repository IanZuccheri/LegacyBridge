# Experimental food-item-v1

This developer-only opt-in is the first new item/block batch. It reuses the
body-preserving engine without extending the admission contracts of
`simple-content-v1`, `logic-preserving-v1` or `lifecycle-preserving-v1`.
The player UI and installer still select `simple-content-v1`.

```text
legacy-converter convert original.jar --conversion-profile food-item-v1 --mapping-bundle prepared-local-data --output new.jar --report conversion.json
```

The typed service selects `ConversionProfile.FOOD_ITEM_V1`; it does not invoke
the CLI. Analysis, planning, conversion, offline validation, publication,
installation and runtime testing remain distinct. No original class is defined
or initialized during static conversion.

## Closed first slice

Exactly two final public Java 8 classes are accepted: an owned `@Mod` entrypoint
and its direct Item subclass. The entrypoint has the existing proven native
DeferredRegister/RegistryObject shape, one constructor supplier, and one
attachment to its mod event bus. The Item subclass has only this constructor
shape (shown with source-development names):

```java
super(new Item.Properties().stacksTo(16).food(
    new Food.Builder().nutrition(3).saturationMod(0.5F).build()));
```

Stack size is an integer literal in 1..64; nutrition is an integer literal in
1..20; saturation modifier is a finite float literal in 0..1. Literal values and
their bytecode are preserved, not calculated by running the mod. The behavior
fixture uses exactly 16, 3 and 0.5; grammar/range tests do not establish native
runtime coverage of every admitted literal combination.

The two original classes and all three method bodies survive conversion.
No registration bootstrap replaces them. The validator makes a private
proof-only constructor projection to reuse the previous registration proof;
that projection is never published. Its ContentPlan is used for resource
validation only, not as a substitute for food construction.

Extra methods, inherited behavior beyond native Item, fields on the Item,
custom static initialization, early registry reads, computed food values,
extra food calls and unknown external references abort the operation. This is
not a general-purpose food-subclass translator.

## Exact rules, not similar names

[The API audit](FOOD_API_BASELINES.md) records actual source/target declarations,
production descriptors, source hashes and behavioral differences. Pinned
official and SRG mappings are composed with real API inventories. Independently
reviewed exact recipes additionally prove access, owner, descriptor and invocation
kind. A generic HIGH mapping is never automatically promoted to VERIFIED.

Five native food operations are admitted: Builder construction, nutrition,
saturation modifier, build, and Item.Properties.food. The shared reviewed item,
registry and constructor-supplier rules remain necessary. Every transformation
has an implemented catalog binding and a versioned TransformationLog entry.

`FoodContractAdapter` implements validated descriptor-component migration,
profile manifest serialization and adapter bindings. It does not emulate eating
or embed old game code. New operation IDs `item.food-properties.v1`,
`item.food-manifest.v1` and `item.food-bindings.v1` have revision 1. Existing IDs,
revisions and runtime identity `legacybridge_runtime` / `0.1.1-SNAPSHOT` stay
unchanged. The CompatibilityReport and TransformationLog schemas are preserved;
food admission evidence adds its own schema-1 object.

Source and target linkage/access checks and ASM checks precede publication.
Unknown symbols, failed checks and unsupported contracts produce diagnostics
without a partial output. Cancellation before publication removes the private
candidate. No original, platform class or unrelated file is rewritten.

## Semantics and necessary migration

Native Item handles the food. This slice has no effects, always-eat/meat/fast
flags, container remainder, food-property override or custom use callback. The
target's stack/entity-sensitive food query therefore sees the literal native
properties for this closed caller envelope.

The target emits native `EAT`, `ITEM_INTERACT_START` and `ITEM_INTERACT_FINISH`
game events, absent in the source game-event system. These target events are
retained and explicitly recorded in the report/adapter contract. Old and new
world/sensor reactions are **not** claimed equivalent. Minecraft is not patched
to suppress target behavior. Third-party hooks and arbitrary contexts are not
covered by this isolated fixture.

The separate native probes exercise real ItemStack use/finish endpoints with
an owned FakePlayer on the logical server thread. Native GameType transitions
must be checked independently of invulnerable/instabuild ability flags; flags
alone do not make the server player creative. FakePlayer.tick is a no-op
in both baselines. Therefore the test cannot certify a complete 32-tick use
sequence, Forge finish-use event delivery, animation or multiplayer. Likewise,
calling native pickup on an actual live dropped entity is not a test of automatic
collision timing. See [artifact-bound validation](FOOD_ITEM_VALIDATION.md).

## Dependencies, resources and data

The existing strict source/target dependency graph is used: mandatory dependencies
are retained, versions and sides checked, and unimplemented external dependencies
refused. The profile does not compose lifecycle/config/command capabilities merely
because the same engine implements a different profile for them.

modId, version, registry name, owned class identity, supported model/localization
bytes and resource locations are preserved. Existing strict resource validation
still applies. Loader metadata, inert manifest and pack format are rewritten by
implemented operations. The fixture uses the already admitted native gold-ingot
texture as a deliberate test placeholder rather than copying a Minecraft asset
or broadening the old resource allowlist for this food batch. This does not
turn gold ingots into food or alias their registry identity.

Explicit local mapping/API data is still required; no old game/Forge JAR,
unapproved mapping bundle, fixture or probe is shipped inside the public product.
The prior local-use logic mapping bundle suffices; adding this profile does not
grant redistribution permission. No hidden downloader or developer-cache lookup
is introduced.

## Everything outside this slice stays blocked

No new support is added for tools, armor, durability, attributes, repairs,
effects, enchantments, brewing, containers, custom use, extra behavior subclasses,
block states/properties, orientation, shapes, collision, block interactions,
block drops/mining rules, ticks, redstone, plants/growth or creative-tab ordering.
Blocks previously admitted by simple-content-v1 retain only their existing
contract. Lifecycle/config/command support remains in its separate closed profile,
not combinable with food-item-v1.

The [corpus audit and prioritized batches](ITEM_BLOCK_CORPUS_AUDIT.md) found
consumer patterns in AppleSkin (food) and Clumps (durability), but no content
producer among the three exact real releases. Food is a small risk-controlled
fixture slice, **not a newly unlocked real mod**. Durability and plain
body-preserving blocks are subsequent candidates, not enabled features. New
authorized producer examples remain a prerequisite to claiming corpus reach.
