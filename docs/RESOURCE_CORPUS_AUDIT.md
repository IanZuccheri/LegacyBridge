# Resource/datapack corpus audit

Review date: **2026-09-06**. This review examines the three exact authorized real
releases in corpus **2026.09.06.1**, the existing resource validator and its
separate `datapack-recipes-v1` extension. It does not change corpus records,
download or execute original mods, or claim a new whole-mod conversion.

**None of these three real JARs contains any `data/` entries.** Cooking and
stonecutting are therefore a fixture-driven, bounded format slice, not a measured
high-impact unlock from this cohort. Recipe/tag support cannot discharge these
mods' entity, rendering, UI, networking, reflection or other code dependencies.

## Exact inputs and rights

The already acquired JARs were opened as ZIP data, not classes. Their sizes and
SHA-256 values were rechecked before and after inspection and remained equal.
No binary was added to the repository or public artifact.

| Exact publisher release | Bytes | Original SHA-256 | Observed resource payload |
| --- | ---: | --- | --- |
| [Clumps 6.0.0.28](https://modrinth.com/mod/clumps/version/UutzgwbZ) | 18,397 | `f69abc1d44277b495004fd6d67d0f95f2574581bb26e55543f2dcd2153cd92d0` | Metadata and `pack.mcmeta`; no assets or data |
| [Controlling 7.0.0.29](https://modrinth.com/mod/controlling/version/Z7UMwzSN) | 51,888 | `b2eb85d83bcd953d37f0d629013bd1f386b1927e2a46c70df155060c8d072cdc` | 19 language JSON files, metadata, pack declaration and access transformer; no data |
| [AppleSkin 2.5.1+mc1.16.4](https://modrinth.com/mod/appleskin/version/633NTwhH) | 47,721 | `6dd1d2ef1daa6871b79f5ca2ac1e757ec731f94958f12397b43670b188091b62` | `assets/appleskin/textures/icons.png`, metadata and pack declaration; no data |

These files remain under ignored `build/corpus-intake/public/<project>/`.
[Existing rights receipts](../corpus/intake/rights-candidates.json) pin publisher
project/version IDs, publisher SHA-512, original URLs, license hashes and the
reviewed local acquisition/analysis/modification basis. Clumps and Controlling
use MIT; AppleSkin uses Unlicense. Exact license notices remain under
`corpus/licenses/`; binary redistribution was not selected. Public availability
alone was not treated as permission.

Release-associated source commits are Clumps
`34c57ca0bd068655a63d99eabd215219432c32d4`, Controlling
`1975807fd663f62d5631f94b9dee19372a5a2d08`, and AppleSkin
`5bedbcc501848f3a22a650f1f661e57d6973af83`. The corresponding source build evidence
is MC 1.16.5 / Forge 36.1.16, MC 1.16.5 / Forge 36.0.0, and
**MC 1.16.4 / Forge 35.1.4**, respectively. AppleSkin's publisher also lists
1.16.5; that is not proof of a Forge-36 build. Source/binary reproducibility
remains NOT_VERIFIED. This review reused those rights/source associations;
the resource findings above were checked directly in the exact JARs.

Counts by distinct release, not symbols or tests:

| Pattern in the exact archive | Observed releases |
| --- | ---: |
| `pack.mcmeta`, each declaring format 6 | 3 of 3 |
| Language JSON resources | 1 of 3 |
| GUI texture | 1 of 3 |
| Recipes, including cooking/stonecutting | 0 of 3 |
| Tags, loot tables/modifiers, advancements or functions | 0 of 3 |
| Item/block models, blockstates or sound resources | 0 of 3 |

The 19 translation files are not 19 compatible mods. AppleSkin's GUI icon sheet
is not evidence for item-model compatibility. The frozen records remain
analyzable with conversion BLOCKED and output/runtime stages NOT_RUN. Their
recorded first metadata refusals are historical observations, not new attempts
against this resource profile. Removing them alone unlocks no whole release.

Additional content fingerprints from this read-only pass:

| Entry | SHA-256 |
| --- | --- |
| Clumps `pack.mcmeta` | `978330fe6b47b8db85672a840264acaee95e5a3cd695cf26481ce4b02b4a46bb` |
| Controlling `pack.mcmeta` | `1507694a2141acfcb1022a7333975c07e94dcf83a025ead1e8977644e9d725e7` |
| AppleSkin `pack.mcmeta` | `493a2936702486dda5cb988f022de7c788e1797c6f0fc980843bddbcca7e7eab` |
| Controlling `assets/controlling/lang/en_us.json` | `f4f3b6a561f6269e4e8e900530f31f0acc39f7669ee7bd5ac56a2a4bd1e68b7e` |
| Controlling `assets/controlling/lang/pt_br.json` | `232a0926055dadc738fefa353dfc783b4f199b87a8b689c8b4fce0f1a0d8c741` |

## Real format baseline

The independently inspected local source archives are exactly the merged Forge
development sources for **1.16.5-36.2.42** and **1.20.1-47.4.23**, not a current
wiki's data format. They were opened as ZIP text and hashed during this audit:

- Source `forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar`:
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
- Target `forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar`:
  `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.

Their precise paths and the fuller serializer/reload evidence are recorded in
[RESOURCE_API_BASELINES.md](RESOURCE_API_BASELINES.md). These game sources remain
local audit inputs, not redistributable fixture or mapping data.

Source `net/minecraft/item/crafting/CookingRecipeSerializer.java:20-37` and target
`net/minecraft/world/item/crafting/SimpleCookingSerializer.java:20-38` read the
same plain ingredient, group, string result, experience and cooking-time fields.
The serializer defaults are **200 ticks for smelting, 100 for blasting, 100 for
smoking and 100 for campfire cooking**, with experience defaulting to zero. A
recipe explicitly using 600 ticks is not evidence of a 600-tick serializer default.
The source `IRecipeSerializer.java:24-28` and target `RecipeSerializer.java:25-29`
contain the registrations. Stonecutting's `SingleItemRecipe.Serializer` requires
a string result and explicit integer count in both versions.

Both Forge cooking serializers also accept object-form results, but that reaches
stack/NBT behavior not covered by the plain-string slice. Refusing it is an
intentional scope restriction, not a claim that Forge cannot parse it. Target
`category` did not have the same meaning in the old serializer: absent target
category becomes `MISC`. Preserve-only admission must disclose the recipe-book
grouping difference and refuse explicit unreviewed category fields.

The independent local inspection also confirms:

- Source `RecipeManager.java:54` and target `RecipeManager.java:60` skip recipe
  IDs whose path starts with `_`. The upstream
  [source patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/patches/minecraft/net/minecraft/item/crafting/RecipeManager.java.patch)
  and [target patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/patches/minecraft/net/minecraft/world/item/crafting/RecipeManager.java.patch)
  corroborate this Forge behavior; branch URLs are not substitutes for the exact
  local source hashes. A syntactically accepted ignored file is not a discovered
  recipe. Nested ordinary IDs such as `sub/recipe` are different from `_sub/recipe`.
- Recipe conditions are consulted before deserialization. The target adds a
  reload context to condition evaluation. Even `forge:false`, empty conditions
  or a conditional wrapper must not be stripped to make a recipe appear valid.
- Source `ITag.Builder` and target `TagFile`/`TagLoader` implement replacement;
  target `TagLoader.java:54` clears prior entries when requested. Keep a local
  `replace:true` document byte-for-byte rather than flattening it. This does not
  freeze other resource packs or establish arbitrary Forge/global-tag equivalence.
- Source and target ingredient handling differ for missing/empty tags. The new
  finite graph must have resolved nonempty local leaves, including optional
  entries. It cannot prove runtime contents of external namespaces from a tag ID.

The existing pack adapter changes only validated metadata format **6 to 15**.
Changing that number alone does not convert recipe schemas, NBT, codecs, scripts,
shaders, animation metadata, loot modifiers or arbitrary data packs.

## Fail-closed review and regression boundaries

`ContentResourceValidator` already rejects unknown resource kinds, members,
custom serializers, unsupported model behavior, missing audited item references,
invalid UTF-8 resource JSON, duplicate keys and local reference cycles. Parsing
uses explicit JSON byte/node/depth/string limits, not source class loading.
The input/archive and publication checks remain separate necessary boundaries.

The new entrypoint `validateDatapackRecipes` is distinct from the previous
`validate` policy. It adds the five exact native JSON serializers, requires a
closed local tag namespace and rejects empty or transitively empty tags. The
default player profile is not broadened. In particular, the old policy's
accepted own/Forge tag forms are not evidence that new cooking recipes may
depend on unknown Forge tags.

Review findings and treatment:

1. **Unicode numeric tokens:** the initial bounded parser used
   `Character.isDigit`, allowing non-ASCII digits that are not JSON number
   syntax. BigDecimal acceptance is not the native Gson reader's contract.
   The engine owner narrowed numbers and hexadecimal escape digits to ASCII;
   the changed source was reread. Regression cases cover Arabic/fullwidth
   digits in integer, fraction and exponent positions.
2. **Ignored recipe IDs:** initial recipe path validation accepted leading `_`.
   The engine owner added explicit rejection in the new profile; the changed
   source was reread. Tests cover `_ignored`, `_metadata/ignored`, empty ID and
   unsafe paths. Existing profile behavior is not silently advertised as newly
   corrected by this profile-specific check.
3. **Tag replacement/composition:** preserving `replace:true` is meaningful
   only within the bounded local contract. External declarations and references
   are refused in the new profile, even if a vanilla/Forge ID looks familiar.
   Optional missing references and empty graphs are also refused; no dummy
   registry entry or fabricated nonempty tag is substituted.
4. **Malformed pack metadata decoding:** the initial `targetPack` implementation
   used replacement-decoding `new String(bytes, UTF_8)` while resource JSON used
   a reporting decoder. Invalid description bytes could therefore be silently
   rewritten as replacement characters. The engine owner implemented strict
   UTF-8 reporting and a 1 MiB bound. Independent regression cases passed in
   the serial root build; see the [validation record](DATAPACK_RECIPES_VALIDATION.md).

This is not a general parser fuzzing certificate. File/JAR validation, resource
validation, bytecode validity, runtime discovery and actual behavior remain
different gates. External packs can replace recipes or alter local tags later;
static conversion does not certify an arbitrary pack stack or overlapping recipe
selection order. Conversion itself must not invoke the game's condition or
serializer registries to discover whether an unknown format works.

## Tests and the smallest verification sequence

The new `DatapackResourceValidatorTest` contains **27 unit tests**, independent
of Minecraft class loading. They cover five newly admitted formats plus existing
shaped/shapeless recipes, unchanged input arrays, per-recipe SHA-256 decisions,
immutable results, defaults, numeric/stack limits, absent IDs, ignored IDs,
conditions, unsupported result/ingredient forms, tag closure/replacement,
malformed JSON/UTF-8 and the remaining asset/datapack exclusions. These are test
cases, not 27 supported mods. Tests were authored but not run by this auditor;
the root's serial build and integration receipts establish their actual outcome.

Recommended evidence sequence for the authored fixture:

1. Build the genuine old fixture and preserve its original hash. Add only recipes
   whose inputs/results already belong to the proven content plan or the finite
   audited vanilla set. Retain source IDs and bytes.
2. Run bounded static analysis/planning/conversion through CLI and typed service;
   require identical decisions and outputs, exact adapter bindings, complete
   output bytecode validation and no artifact for each negative case.
3. In separate real source/target test instances, assert every intended recipe
   ID, serializer, positive and negative match, result identity/count and configured
   XP/time. Do not use a server `Done` marker or native loaded-type-bucket count
   as proof that all recipes exist.
4. Exercise native resource reload with an owned, nonempty local-tag override,
   verify changed matching against the new manager, then restore and recheck.
   Directly editing the recipe map does not exercise reload semantics.
5. Keep full apparatus execution, fuel/input consumption, awarded XP, campfire
   drops, stonecutter menu transactions, client recipe-book grouping and connected
   multiplayer synchronization as separately named behavior gates. Matching or
   `assemble` alone proves none of those operations.

No build, test, conversion or game was launched by this audit. No real-mod
conversion status was upgraded and no runtime success is claimed here.

## Conservative resource backlog

| Order | Bounded slice | Measured real-release reach | Dependencies and stop conditions |
| --- | --- | --- | --- |
| R1, current | Four plain native cooking serializers plus stonecutting; preserve bytes; local nonempty tags | Producers observed: 0 of 3; verified whole-mod unlocks: 0 | Exact pinned format proof, original content plan, hard IDs/fields/bounds checks, independent native match/reload proof |
| R2, next evidence task | Add a small authorized cohort that actually contains recipes/tags before ranking new formats by reach | Unknown until exact releases are acquired and reviewed | Exact-release rights/source association, immutable binary hashes, no private redistribution; intake itself enables nothing |
| R3, separate semantic batch | One demonstrably needed conditional-recipe or cross-pack tag contract, selected only after R2 | Not measured | Source/target reload context, false/null branches, optional dependency absence and deterministic refusal; do not generally evaluate or flatten conditions |
| Later, independent slices | Loot tables/modifiers, advancements, functions, NBT results, animation and custom models | No producer evidence in this cohort | Each has its own schema/runtime/side/security semantics; never infer support from a shared `.json` suffix |

This prioritization chooses a small verifiable first slice; it is not an
ecosystem-impact ranking. More resource formats cannot substitute for closing
each admitted mod's complete code and dependency behavior. Keep the product
experimental and every unperformed client/server/behavior/multiplayer gate explicit.
