# Datapack recipes: first versioned vertical slice

`datapack-recipes-v1` is an explicit developer CLI/service profile for Minecraft
1.16.5 / Forge 36.2.42 to Minecraft 1.20.1 / Forge 47.4.23. It reuses the **same**
finite registration interpreter, ASM pipeline, implemented runtime adapters and
private-candidate/no-overwrite publication as `simple-content-v1`. It does not preserve
arbitrary mod logic or combine the food/lifecycle profiles. The old default
resource policy and player UI remain unchanged. No additional player JAR or
bootstrap service is introduced.

This is a small recipe-data expansion, **not general datapack compatibility**.
Every class and resource must still pass its selected policy. One rejected
resource aborts the complete conversion. Neither an unknown condition nor an
unsupported resource is evaluated, discarded or silently copied.

## Format contract

The source metadata must admit the pinned source versions and have the existing
strict `pack_format=6` envelope. Pack format alone is not serializer evidence.
The new reviewed recipe policy is implemented in `DatapackRecipeContract` and
`ContentResourceValidator.validateDatapackRecipes`; its independent provenance
is in [the exact source/API audit](RESOURCE_API_BASELINES.md). These are original
validation rules, not redistributed game code, assets or mapping files.

| Source format | This profile | Byte handling |
| --- | --- | --- |
| Vanilla shaped/shapeless | Existing bounded schema; now closed local tag references | Preserve exactly |
| Smelting, blasting, smoking, campfire cooking | One ordinary ingredient or flat nonempty alternatives; string item result | Preserve exactly |
| Stonecutting | Same ingredient language; string result and mandatory bounded count | Preserve exactly |
| Own item/block tags | Existing IDs; nonempty local closure, acyclic; `replace` boolean retained | Preserve exactly |
| Existing static models/blockstates/PNG/lang/event-alias sounds | Unchanged original asset policy | Preserve exactly |
| Pack metadata | Existing implemented pack adapter, 6 to 15 | Rewrite and journal |
| Target mod metadata/manifest, bootstrap/plan/bindings, STONE mining tag | Existing exact adapters | Rewrite/generate and journal |
| Conditions (even false), conditional wrappers, custom serializers/ingredients, NBT results | UNSUPPORTED | No output |
| External/vanilla tag declarations or references, absent optional tags, empty/transitively empty tags | UNSUPPORTED | No output |
| Loot tables, conditions/functions, global loot modifiers, advancements, functions/commands | UNSUPPORTED | No output |
| Animated PNG/texture metadata, custom models/blockstates, local audio | UNSUPPORTED | No output |

Unchanged files retain original whitespace, ordering, Unicode, newlines and
all bytes, not a parse/re-serialize approximation. `preservedResources` records
their SHA-256 hashes. A schema-1 `resourceValidation` section records each recipe
path, serializer, implemented preservation rule, disposition and hash. Its
`PASSED_STATIC` state and `runtimeValidation=NOT_RUN` never imply native reload,
client rendering, actual machine processing or multiplayer validation.

The existing conversion schema 2 and TransformationLog schema 1 remain intact.
Every actual rewrite retains before/after hashes, rule, VERIFIED confidence and
an exact existing adapter binding. Preserving bytes is not a transformation, so
no fictional ADAPTER_CALL/RESOURCE_REWRITE is added for it. Adapter identity and
ABI remain `legacybridge_runtime:0.1.1-SNAPSHOT`; there is no new runtime shim.
The generated manifest still names the existing `simple-content-v1` bootstrap
family, not the broader data admission policy. The separate conversion report
is the authority for the selected `datapack-recipes-v1` envelope. That manifest
alone is not sufficient installation approval; the current installer revalidates
resources using the old policy and correctly refuses these additional recipes.

## Bounds and references

- Cooking `group` defaults to `""`, experience to 0. Time defaults are **200, 100,
  100, 100** respectively; campfire recipes can explicitly choose 600, but the
  serializer default is 100 in both pinned versions.
- Explicit experience must be finite numeric 0..100; time an exact integer
  1..72000. Cooking string results create one item. Object results are refused.
- Stonecutting count is required, 1..the known result item's stack limit. Extra
  time/experience/count/category/extension fields outside their format reject.
- Item IDs must exist in the proven registration plan or the existing small
  audited vanilla-item set. Missing or foreign IDs cannot become air silently.
- Each ingredient/tag reference resolves within the admitted local graph. Both
  replacement and additive tag documents retain their original bytes, but this
  cannot freeze contributions from unrelated packs at runtime.
- Empty ingredient arrays, nested arrays, ambiguous item+tag objects, cycles,
  missing required/optional tags and unknown fields reject. Recipes with a path
  beginning `_` reject because the actual Forge managers ignore them.
- Existing archive/payload limits, JSON 1 MiB, depth 64, 20,000 nodes and 16,384
  string characters remain. JSON numeric digits and Unicode-escape hex are now
  restricted to ASCII as required by JSON; valid Unicode text remains allowed.
- Cancellation checkpoints cover parsing, graph traversal, validation and
  publication. A cancelled candidate cannot be installed or partly published.

## Disclosed differences and proof boundaries

Target cooking defaults to recipe-book category MISC. Source smelting/blasting
can infer BLOCKS from the result; a preserved block-result recipe may therefore
move to the miscellaneous tab. No category rewrite is implemented. Client
recipe-book presentation is a separate pending gate.

Empty-tag and condition evaluation behavior differs across the two versions,
which is why those ambiguous cases are excluded rather than treated as inert
JSON. Reload uses each native server's real resource pipeline and newly acquired
RecipeManager; manually changing a recipe map is not a reload test.

Native `matches`/`assemble` verify recipe choice/result copying but do not consume
the input, spend fuel, advance furnace/campfire ticks or award XP. In particular,
target campfires have additional feature checks/game events. No claim about
machine timing, XP delivery, book unlocks, client sync, relog or multiplayer follows
from a recipe-manager endpoint test. Arbitrary higher-priority packs can override
IDs or create ambiguous matches; single-JAR analysis does not validate a modpack.

## Developer use

```text
legacy-converter convert mod.jar --conversion-profile datapack-recipes-v1 --mapping-bundle <explicit-local-bundle> --output <new.jar> --report <report.json>
```

The service selects `ConversionProfile.DATAPACK_RECIPES_V1` on ConversionRequest;
it never calls CLI.main or reads stdout. Mapping data selection, local-use rights
and no-hidden-download behavior are unchanged. The new profile is **not enabled
in the player screen or transactional installer**: those still validate the
original simple-content policy. For developer native QA, stage only the approved
output into a fresh stopped test installation and start Forge normally.

See [validation evidence](DATAPACK_RECIPES_VALIDATION.md) for what actually ran.

## Next format batches, not enabled here

1. Constant Forge conditions plus negative branch/reference semantics and native
   reload contexts; then serializers/ingredients only with independently verified
   code contracts. Do not generalize from a JSON `type` name.
2. A closed loot-table entry/condition/function set with native deterministic
   drops; global loot modifiers require ordering, codec and combined-mod evidence.
3. Advancements/rewards, functions/commands and client formats each need separate
   schema/version/reference rules and feature-specific runtime fixtures. Animated
   textures need bounded metadata/frame validation plus real client reload.

These are sequencing dependencies, not promises of universal support or a
compatibility percentage. No whole real mod is declared newly compatible here.
