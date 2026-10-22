# Item/block corpus audit — 2026-09-06

This read-only review uses the three exact real releases in corpus
`2026.09.06.1`. It does not change that snapshot, conversion rules or validation
statuses. **None of the three inspected source trees defines/registers a tool,
armor item, food item, block, custom block state, redstone behavior or creative
tab.** Item-related consumer code exists in Clumps and AppleSkin; that is not
evidence of content-authoring compatibility or a whole-mod unlock.

The current implementation batch selects a **project-authored native food item**.
Food-value reading and durability mutation each occur in one real release; food
was selected for its smaller finite definition surface and native behavior oracle,
not greater measured reach. No real release is enabled by this choice. There is
insufficient evidence here to rank three item/block slices by ecosystem impact.

## Scope, rights and identities

All existing binary hashes/sizes below were rechecked before inspection and again
after read-only archive access. No JAR was downloaded, transformed, loaded or
executed. Official GitHub commit trees were fetched without truncation; all their
Java sources were read as bounded text in memory and searched for declarations,
registrations and the requested consumer patterns. Relevant matched bodies were
reviewed in context. No third-party source or binary was added to the product.

| Exact release / publisher identity | Existing JAR SHA-256 | Bytes | Release-associated source / license |
|---|---|---:|---|
| [Clumps 6.0.0.28](https://modrinth.com/mod/clumps/version/UutzgwbZ), `Wnxd13zP` / `UutzgwbZ` | `f69abc1d44277b495004fd6d67d0f95f2574581bb26e55543f2dcd2153cd92d0` | 18,397 | [commit 34c57ca0bd068655a63d99eabd215219432c32d4](https://github.com/jaredlll08/Clumps/tree/34c57ca0bd068655a63d99eabd215219432c32d4); MIT |
| [Controlling 7.0.0.29](https://modrinth.com/mod/controlling/version/Z7UMwzSN), `xv94TkTM` / `Z7UMwzSN` | `b2eb85d83bcd953d37f0d629013bd1f386b1927e2a46c70df155060c8d072cdc` | 51,888 | [commit 1975807fd663f62d5631f94b9dee19372a5a2d08](https://github.com/jaredlll08/Controlling/tree/1975807fd663f62d5631f94b9dee19372a5a2d08); MIT |
| [AppleSkin 2.5.1+mc1.16.4](https://modrinth.com/mod/appleskin/version/633NTwhH), `EsAfCjCV` / `633NTwhH` | `6dd1d2ef1daa6871b79f5ca2ac1e757ec731f94958f12397b43670b188091b62` | 47,721 | [v2.5.1 commit 5bedbcc501848f3a22a650f1f661e57d6973af83](https://github.com/squeek502/AppleSkin/tree/5bedbcc501848f3a22a650f1f661e57d6973af83); Unlicense |

Exact license notices, their hashes, publisher SHA-512 values and the reviewed
local-use/modification permission basis remain in
[rights-candidates.json](../corpus/intake/rights-candidates.json) and
[licenses](../corpus/licenses/). Public repository visibility was not treated as
permission. Binary redistribution was not selected. Source-to-binary
reproducibility remains **NOT_VERIFIED**.

The source trees contain 6, 15 and 18 Java files respectively. These are review
scope counts, never supported-mod counts. Source build evidence is Minecraft
1.16.5 / Forge 36.1.16 for Clumps, 1.16.5 / 36.0.0 for Controlling, and
**1.16.4 / 35.1.4** for AppleSkin. AppleSkin's publisher also lists 1.16.5, but
that does not establish a Forge-36 build or admit the adjacent source profile.
The engine baselines remain 1.16.5 / 36.2.42 and 1.20.1 / 47.4.23 / Java 17.

The existing ASM observations were consulted, not rerun. Clumps' summarized
Minecraft references are truncated; absence from that list is not evidence of
absence. This audit's negative source findings apply to the reviewed immutable
trees, corroborated by existing class/resource inventories, not a proof about
unknown generated code or arbitrary releases.

## Evidence counted by exact release

`—` means not found in the inspected source, not an automatic UNSUPPORTED verdict
for every possible implementation. The final column counts distinct releases
with the stated observed pattern, not symbols, files, test cases or compatible mods.

| Pattern | Clumps | Controlling | AppleSkin | Releases observed |
|---|---|---|---|---:|
| Tool/tier or armor/material definitions | — | — | — | 0 of 3 |
| Own item durability configuration | — | — | — | 0 of 3 |
| Existing ItemStack damage read/write | Mending repair in XP pickup | — | — | 1 of 3 |
| Native food/item definition or registration | — | — | — | 0 of 3 |
| Food values / effects read from another item | — | — | FoodHelper | 1 of 3 |
| Eligibility query for consuming food | — | — | Player hunger/fullness query | 1 of 3 |
| Custom item use/finish/release callback | — | — | — | 0 of 3 |
| Item or Block registry population | — | — | — | 0 of 3 |
| Existing world block-state behavior read | Slipperiness under XP entity | — | — | 1 of 3 |
| Own block-state properties/transitions | — | — | — | 0 of 3 |
| Redstone emission/neighbor logic | — | — | — | 0 of 3 |
| Creative-tab definition/membership | — | — | — | 0 of 3 |

### Clumps: durability is a consumer dependency

[EntityXPOrbBig.java:171–205](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/entities/EntityXPOrbBig.java#L171)
selects existing enchanted equipment during server-side XP pickup, checks stack
damage, uses the stack's repair ratio and writes reduced damage. Repair is nested
inside the denomination/count loop; XP accounting and cancellation are part of
the behavior. Equipment-slot references do not define an ArmorItem. Adding a
durable-item constructor or damage accessor alone cannot preserve this closure.
The same file reads slipperiness under the entity at lines 117–118: this is world
interaction, not block definition. Its only
[DeferredRegister](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/Clumps.java#L30)
targets entities. The actual JAR has metadata/pack declarations but no item/block
assets or data recipes.

### AppleSkin: food observation is not food production

[FoodHelper.java:17–105](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/helpers/FoodHelper.java#L17)
reads an existing item's Food, hunger, saturation, full-hunger eligibility and
effects. Its FoodValues allocation creates AppleSkin's display data object, not
Minecraft Food or an Item. The method named `getModifiedFoodValues` delegates to
the default-value method in this release; its name does not establish a dynamic
Forge food-context contract. Health estimation also depends on player hunger,
game rules, effects and reflection-backed exhaustion data. There is no item-use
override or explicit food consumption here. The
[HUD handler](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/client/HUDOverlayHandler.java#L130)
uses those queries to choose held-item overlays; that is not a food registration.
The JAR contains one GUI icon sheet and metadata, not food-item models or recipes.

### Controlling: input UI, not item use

The [entrypoint](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/Controlling.java)
and reviewed source tree concern client keybindings, screen replacement, widgets
and custom UI events. Mouse/key handlers do not implement Item use callbacks.
The JAR's content consists of translations, metadata and its access transformer;
no item/block content authoring pattern was observed. None of the proposed
item/block batches removes its screen/input/access-transformer requirements.

Representative raw source SHA-256 values, fetched from the commits above:

| Source file | SHA-256 |
|---|---|
| Clumps `entities/EntityXPOrbBig.java` | `6c8f29840de2120829b924bc03130f56d7119e9f00f3f4f77eb097592e5d979f` |
| Clumps `Clumps.java` | `7659bab8bb3ebbef201ae8e23c67c99d780148d979f8a4049c022a3a51b41b55` |
| AppleSkin `helpers/FoodHelper.java` | `a00fb2336cfd095f36fba680579e3b4e717c7b041a305ac588bb707031cecb33` |
| AppleSkin `client/HUDOverlayHandler.java` | `1aa5ddfe1348f86c1ada93ba11622411d378d5f57bd92a71dfbd7c2ea4c82621` |
| Controlling `Controlling.java` | `31e07ce8932808ced8d3b0e37b44e8a22f757bce9dc0048a37e646d774242a34` |

## Conservative implementation batches

This is an item/block-specific extension plan, not a rewrite of the frozen
[whole-mod backlog](../corpus/BACKLOG.md). Estimates are experienced-engineer
person-days for the stated slice and its targeted tests after API feasibility;
they are not deadlines or claims that unperformed client/multiplayer gates pass.
All listed batches have **zero verified whole-mod unlocks** in this cohort.

| Order | Slice and decision | Relevant observed releases | Prerequisites / admission boundary | Risk / estimate |
|---|---|---|---|---|
| IB1 — current batch | One native food-producing Item, literal nutrition/saturation, default flags; retain original bodies | AppleSkin reads food: 1; food producers: 0 | Separate finite profile/recipes over preserved native registration; no effects, containers, timing/use overrides or contextual food suppliers | Medium; 3–6 days |
| IB2 — next candidate | Ordinary durable Item plus bounded existing-stack damage reads/writes | Clumps consumes damage APIs: 1; durable-item producers: 0 | Exact native damage/stackability contract, NBT/save proof, negative bounds/unknown hooks; no tool tiers, armor, enchantment or Mending emulation | Medium; 4–8 days |
| IB3 — third candidate | Plain Block plus BlockItem while preserving owned registration bodies | Block producers: 0; Clumps' slipperiness read is not this slice | Reuse the audited STONE/resource baseline without changing `simple-content-v1`; prove target mining tags/material consequences separately | Medium–high; 5–10 days |
| IB4 — later, separate batches | One audited native tool tier; independently one native armor material/slot | 0 producer examples for either | Acquire better authorized examples first; tool actions/mining rules and equipment attributes/durability/repair must each have actual API and behavior proof | High; not estimated before examples/API feasibility |
| IB5 — later, separate batches | Custom item use; finite block-state transition; finite redstone behavior | 0 examples for each | Side/thread/callback contracts, persistence and neighbor/update timing; separate positive and negative fixtures, no generic hook forwarding | High; unknown reach and unestimated effort |

IB1 and IB2 tie on **consumer relevance**, not demonstrated convertibility.
Choosing IB1 first is an engineering-risk decision. The new
`fixtures/food-item-1.16.5` source defines `legacybridge_food:ration`, stack size 16,
nutrition 3 and saturation modifier 0.5, with no effects, container or callback.
This authored fixture is not a new real-mod corpus record. Its actual build,
conversion and runtime receipts must be reported independently by implementation;
this audit does not certify them.

A separate IB0 research task should seek small, materially different, authorized
exact releases that actually define food, durable items/tools/armor and simple
blocks. Review release-specific licenses/source association before acquisition,
keep binaries private to local build storage, and version new records explicitly.
No such acquisition or corpus mutation occurred here. Technical intake effort is
roughly 2–4 days per small reviewed cohort; rights/author response delays are
unknown. Intake itself unlocks nothing.

### Acceptance boundaries for the selected food slice

Require exact native declarations and implemented operations for every admitted
call, source/target ASM and offline reference validation, unchanged owned logic
and resources, original hashes unchanged, and refusal without partial output for
effects/unsupported flags/custom use/unknown references. Native behavior must be
tested separately on original and target: actual consumption at bounded hunger
levels, saturation and stack count, full-hunger refusal, food metadata and relevant
resource/translation loading. A command that only prints nutrition is not a
consumption test; server success does not certify client animation or multiplayer.

There is a necessary target-native difference: the exact target source
`net/minecraft/world/entity/LivingEntity.java:3281` emits `GameEvent.EAT` on eating.
This belongs to the newer game-event system, absent from the 1.16 baseline.
The reviewed target source archive SHA-256 is
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`, identified in
[the API baseline provenance](LOGIC_API_BASELINES.md). Retaining native target
eating must document this difference; it cannot be sold as identical sensor or
arbitrary world-context behavior. Do not suppress it by patching Minecraft.

## Status and stop conditions

The frozen real-release records remain analyzable, with conversion BLOCKED and
output-bytecode/client/server/behavior/multiplayer validations NOT_RUN. Their
recorded first conversion refusals are the Clumps manifest timestamp and the
side-specific dependency gates for Controlling/AppleSkin; these are historical
observations, not new conversion runs by this audit. The
[existing semantic review](../corpus/intake/semantic-review.md) records additional
source dependencies, not later converter diagnostics.

Plain-food support cannot discharge AppleSkin's HUD/tooltip, config file I/O,
reflection, networking, custom events or adjacent source-baseline decision.
Damage support cannot discharge Clumps' XP entity, per-orb mending, persistence,
spawn state, cancellation or rendering. Item/block work does not address
Controlling's custom screen and keybinding semantics. Keep each full release
blocked until its entire admitted behavior closure is implemented and verified.

Preserve the existing simple, logic and lifecycle profile contracts and their
artifact-bound evidence. No default UI/installer expansion, old-game embedding,
global unverified mapping promotion, dummy adapter, compatibility percentage or
universal-support claim follows from this document. Builds, games and conversion
commands were not run during this audit; only this documentation file was added.
