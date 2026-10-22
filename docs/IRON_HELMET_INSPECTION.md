# Iron helmet inspection and equipment semantics

The LB-10 equipment slice recognizes a finite authored iron-helmet registration
graph and reports why conversion remains **UNSUPPORTED**. This read-only
operation is not an armor converter, candidate generator or new public profile.
An equal defense value does not make different equip behavior equivalent.

## Inspected original

The original fixture in `fixtures/iron-helmet-1.16.5` registers one item through
DeferredRegister on the mod event bus. Its final Java 8 entry has three fields
and three methods. A zero-capture static supplier creates
`new ArmorItem(ArmorMaterial.IRON, EquipmentSlotType.HEAD, new Item.Properties())`.
Local names are recognized by role; extra classes, properties, callbacks,
materials, slots, state, method handles or dependencies are outside this grammar.

The structural checker also recognizes the corresponding target construction
shape, but this does not authorize translating between them. The service accepts
only source metadata and the source namespace. It checks original bytecode with
ASM, source linkage/access and explicitly selected full API inventories without
loading or executing mod classes.

The finite resource set contains one exact shapeless iron-ingot recipe, a
generated item model referencing the native iron-helmet texture, bounded
`en_us` and `pt_br` translations and pack format 6. Unknown files, malformed
UTF-8, duplicate JSON keys and unsupported keys or references are rejected.
Native resources are referenced, not redistributed. Nothing is rewritten.

## Why a constructor rename is insufficient

The old constructor receives `IArmorMaterial` and `EquipmentSlotType`; the
target receives `ArmorMaterial` and `ArmorItem.Type`. HEAD to HELMET is a
contextual construction change, not a global equipment-slot rename.

The inspected legacy use body requires an empty equipment slot, equips a copy,
sets the held stack count to zero and returns sided success on that stack.
An occupied slot fails without swapping. Target ArmorItem delegates to
Equipable: it checks Binding Curse and stack equality, can swap an occupied
piece and awards ITEM_USED on the logical server. The subsequent
[fixed runtime operation](IRON_HELMET_EMPTY_SLOT_ADAPTER.md) implements the legacy
use sequence, but no contextual conversion recipe or inherited contract is approved.

The audit separates exact declaration checks from historical body facts tied to
the pinned source archives. ApiIndex has no method bodies: caller-supplied
declarations and their recorded provenance cannot authenticate those facts.
Inherited gameplay, dispenser behavior, rendering, repair cost, enchantments,
save/load and multiplayer do not become qualified through constructor matching.

## Typed service and developer command

`LegacyEngineService.inspectIronHelmet(IronHelmetInspectionRequest)` reuses
the bounded worker, queue, cancellation, admission and cooperative deadline.
Its immutable request contains the original path, selected SHA-256 and explicit
full local mapping/API selection, with no output path or install authority.
The included simple-content and cohort data cannot stand in for this inventory.

```text
legacy-converter inspect-iron-helmet original.jar --input-sha256 <sha256> --mapping-profile <explicit-local-profile.properties>
```

The CLI calls the same service and prints canonical JSON. Exit code 3 is the
expected unsupported diagnostic, not a successful conversion. No cache is
discovered and no download is performed. The full local data is a developer
dependency for this operation, not a new requirement for public simple-content.

The immutable schema-1 report
`LEGACY_IRON_HELMET_SEMANTIC_INSPECTION` distinguishes constructor recognition,
source ASM/linkage, API declarations, finite resources and blockers:

- `HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED`
- `HELMET_USE_CONTRACT_CHANGED`
- `HELMET_INHERITED_BEHAVIOR_UNQUALIFIED`

Earlier structural, metadata, API or resource failures have their own structured
errors. Recognizing a constructor can remain true when later checks fail; it is
not a compatibility flag. The API audit now names the available runtime operation
without claiming installed-runtime verification or conversion permission.
It also names the separate guarded HEAD effect and its preconditions, explicitly
marking composedWithUse, conversionRecipeImplemented and runtimeArtifactVerified
false. The native server/client sound-event differences remain historical pinned
facts, not equivalence derived from the supplied declaration index.
It additionally names the [closed dispenser operation](IRON_HELMET_DISPENSER.md)
and its receiver/stack/list/query boundaries. Its conversionRecipeImplemented,
registeredForItem and runtimeArtifactVerified fields are false. This does not
broaden the inspection's three-method grammar to accept the new four-method
dispenser fixture or prove native query order.
Convertible, candidate generation, publication,
installation and class execution remain false. Runtime validation is NOT_RUN
and the transformation count is zero. Existing CompatibilityReport and
TransformationLog schemas remain unchanged; no fictitious log or adapter is
created for transformations that never occurred.

## Integrity and qualification

Input must be a canonical regular file without observed symlink or junction
aliases. The selected original hash is checked again before returning even a
rejected diagnostic. Shared safe ZIP/snapshot handling adds name, expansion and
entry limits. This operation captures at most 16 files and 2 MiB, with 128 KiB
per class and 64 KiB per resource, then requires the smaller exact graph.
These are cooperative bounds, not a hard heap or filesystem-race sandbox.
Cancellation cannot publish a candidate because no publication path exists.

Tests read native bytecode as data and use finite source-based counterexamples;
they do not execute native equipment use or the original mod. Six public
conversion profiles continue to reject this original. No Minecraft client,
server or screen automation is part of this delivery.

The corpus has no armor-producing release in its current bounded review.
This is an authored fixture, not a claim that a real third-party mod was ported.
Results and artifact hashes are recorded in the
[validation ledger](evidence/iron-helmet-inspection-2026-10-05.json).

## Next development

The [empty-slot runtime operation](IRON_HELMET_EMPTY_SLOT_ADAPTER.md) is now
implemented. Do not repeat this diagnostic or that operation as the next deliverable.
Other inherited paths remain blocked; implementing one operation does not authorize
the contextual constructor conversion. Follow the current contract cursor for the
next finite contract and keep ITEM-EQUIPMENT's native gates pending.
