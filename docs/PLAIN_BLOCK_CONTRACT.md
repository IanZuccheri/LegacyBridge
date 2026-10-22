# LB-10 / BLOCK-STATE — preserved plain Block and BlockItem

Implementation slice: **PLAIN_BLOCK_WITH_ITEM**, 2026-09-26.
Experimental **private candidate**, not a seventh public conversion profile.
The native, client/server and multiplayer gates remain **NOT_RUN**. No new real
corpus release is claimed compatible. See the [versioned ledger](evidence/plain-block-2026-09-26.json).

## What is implemented

One Java 8 mod-owned final class retains its constructor, static initializer and
two private static factories. It declares one native Block and one native BlockItem
via DeferredRegister on the mod bus. The BlockItem looks up the registered local
Block only inside its deferred factory. The original registry ID, class/helper
identity, literals, method order and debug attributes are retained; no generated
registration bootstrap replaces them.

The finite language admits STONE, the native full cube/default state/default sound,
literal finite nonnegative hardness/resistance up to 32767 (excluding negative
zero), and BlockItem stack size 1..64. No creative membership is inferred.
One or more recipes from the existing bounded datapack contract, required models
and blockstate, and any supplied translation files are validated. Translation
files/locales/keys are not mandatory. Original admitted resource bytes remain unchanged.

The authored fixture is under `fixtures/plain-block-1.16.5`: modId
`legacybridge_plain_block`, registry `bridge_block`, hardness 2, resistance 6,
stack size 32, one shapeless iron-ingot recipe. It references the vanilla stone
texture without bundling it. **No loot table is provided or invented**; this
fixture does not demonstrate self-drops.

## Exact contract rewrite, not a material rename

Source Material.STONE plus Properties.of(Material) is removed only at the single
proved factory expression. No Material-to-MapColor class rule exists. The implemented
OFFLINE operation `block.stone-properties-recipe.v1` supplies five canonical target
operations; their actual names/descriptors/access are independently resolved from
the exact target official/SRG mappings and API inventory:

1. BlockBehaviour.Properties.of().
2. Read MapColor.STONE.
3. Apply mapColor(MapColor).
4. Read NoteBlockInstrument.BASEDRUM.
5. Apply instrument(NoteBlockInstrument).

The original strength literals/call and Block constructor remain through separate
exact rules. The remapper sees a private projection without the consumed pair;
all other executable/debug/state shape is compared before the checked native
expansion is inserted at the original debug position. The complete target graph
is then independently re-derived and compared to the source proof.

This expansion has a VERIFIED `ADAPTER_CALL` log entry tied to the **OFFLINE**
operation, not a claim that a runtime shim call was emitted. Normal class/member/
descriptor remaps have their own operation bindings. RegistryObject.get's changed
erasure is permitted only by the implemented late-lookup descriptor recipe; the
Block cast and native constructor remain.

The existing `BlockMiningAdapter.targetTag` generates exactly one additive
`data/minecraft/tags/blocks/mineable/pickaxe.json`. Source writes under
`data/minecraft/tags/**` remain refused. The new resource is explicitly claimed,
hashed and logged as RESOURCE_REWRITE; on ZIP readback its bytes are regenerated
and compared before it is excluded from the source resource-language validator.
Absence, drift or another unowned resource is never treated as preservation.

## Safety and integration

`LogicPreservingConverter.planPlainBlock` and
`LegacyEngineService.planPlainBlock` return structured proof/hash only.
The service uses the existing bounded worker, queue, progress, selected-input hash
and cancellation mechanism; it does not call CLI.main or render-thread work.
No output JAR, public profile, staging receipt, Apply or activation is authorized.
The serialized candidate is reopened through the safe archive reader, validated,
and discarded; temporary storage is cleaned in finally even on cancellation.

ASM validation, declaration/access/dataflow linkage and source/target/readback
contract checks are separate gates. No original initializer, factory or class is
loaded or executed. Material references outside the consumed expression, unknown
members/dependencies/metadata and every extra execution effect block the candidate.

The shared simple-content-v1 bootstrap, six existing public grammar selectors,
runtime identity/ABI and old catalog descriptors are not expanded. Four new
OFFLINE catalog descriptors bind the finite rewrite, erasure and private metadata.
There is no old game/Forge code or fixture packaged in LegacyBridge.jar.

Advanced planning still needs explicit complete local mapping/API inputs.
The included finite simple-content data do not imply complete inventories for
this candidate; no Gradle-cache lookup or hidden download was added to the product.

## Exact API audit and semantic limits

Source Minecraft 1.16.5 / Forge 36.2.42; target Minecraft 1.20.1 / Forge 47.4.23,
Java 17. Actual locally generated ForgeGradle source archives were read, not shipped:

| Sources archive | SHA-256 |
| --- | --- |
| 1.16.5-36.2.42 mapped_official_1.16.5 sources | bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376 |
| 1.20.1-47.4.23 mapped_official_1.20.1 sources | 4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932 |

Source Material.java:38,100–108,154–155 and AbstractBlock.java:804–855 provide
STONE/default predicates. Source strength at 945–948 and target BlockBehaviour
1112–1113,1218–1225 both clamp resistance; the supported literals avoid exceptional
values. Target defaults at 969–1025 require explicit color and instrument. Source
MaterialColor:19 and target MapColor:18 use the same STONE palette ID 11 and
RGB 7368816; this is checked from the definitions, not inferred from the name.
Source material-solid predicates only correspond to the target shape-derived
solidity for this native full cube; custom shapes/states do not inherit this proof.

Source PickaxeItem:17–28 uses material; target PickaxeItem:6–7 and
DiggerItem:34–35,64–81 use tags. Source NoteBlockInstrument:45–77 checks wool before
STONE; unknown behavior-changing tags or dependencies cannot silently override
that context. Existing [baseline audit](COMPAT_API_BASELINES.md) remains applicable.

Source BlockItem:33–35 and target BlockItem:40–42 store the Block after Item super.
Source GameData:339–348 and target GameData:307–325/BuiltInRegistries:142,163 support
this bounded blocks-before-items path, not arbitrary registry scheduling.
The official [1.16 registry guide](https://docs.minecraftforge.net/en/1.16.x/concepts/registries/)
and [1.20.1 registry guide](https://docs.minecraftforge.net/en/1.20.1/concepts/registries/)
were consulted for registration/lifetime context; pinned sources govern exact rules.

**Necessary native difference:** target BlockItem.place emits GameEvent.BLOCK_PLACE
(target BlockItem:87), absent from the source placement path. Target feature-set
admission also exists; only default VANILLA_SET properties are admitted.
No suppression of target events or patches of Minecraft/Forge are attempted.
Sensor reactions and mod interactions are not claimed identical.

## Pending work and next development

BLOCK-STATE remains PARTIAL. Native discovery, placement, mining speed, note
instrument, collision, crafting/model/translation loading and multiplayer must be
compared independently on original and converted fixtures before promotion.

The subsequent [horizontal state and rotation/mirror contracts](HORIZONTAL_BLOCK_CONTRACT.md)
are now implemented as separate private variants, not a widening of this plain
contract. Next development: STATIC_BOTTOM_HALF_SHAPE with its own geometry,
material/default and model proof, then interaction/drop separately.
No general block-state callbacks are enabled.
Ticks/redstone, growth, custom materials/sounds/shapes, loot, block entities,
capabilities, networking and arbitrary subclasses remain unsupported here.
