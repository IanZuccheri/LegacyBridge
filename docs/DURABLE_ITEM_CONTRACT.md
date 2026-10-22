# LB-10: durable item / native repair predicate (revision 1)

Implemented finite static candidate, 2026-09-19. This is a separate **private candidate**, not a
seventh public profile, a public Convert/Apply expansion or tool/armor support.

## Oracle written before rules

The project-owned original fixture has two Java-8 classes compiled/reobfuscated
against Minecraft 1.16.5 / Forge 36.2.42. DeferredRegister registers one final
Item subclass: `legacybridge_durable:token`, version 1.0.0. Constructor:
`new Item.Properties().durability(128)`. The only override is
`isValidRepairItem(damaged, ingredient)`, returning identity comparison of
`ingredient.getItem()` with `Items.IRON_INGOT`. No state or inventory changes.

Expected native observations (NOT_RUN): max damage 128, max stack 1; repair
predicate true for iron ingot, false for gold ingot and empty stack, irrespective
of damaged-stack count/damage. This is NOT an oracle for anvil XP, enchantments,
actual ingredient consumption, breakage, save/load or anti-duplication.

Static oracle: retain every original executable body, class/member identity,
registry/resource identity, control flow and accepted resource bytes. Prove the
literal constructor and exact predicate in source, mapped target and serialized
readback; verify ASM, real API declarations, access, opcodes and override linkage.
Any unaccounted effect blocks the whole candidate. Only proof/hash escape the
bounded cancellable engine job, never bytes or installation authority.

Negatives: invalid durability, stacksTo/defaultDurability/no-repair/remainder/
tool flags, other native getters or ingredient fields, inverted comparison or
wrong branch/argument/return, added state/helpers/handlers, extra overrides,
early registry lookup, unknown dependencies, changed mapping/API declarations.
These are unsupported patterns, not evidence that they can never be ported.

## Exact API audit

Read-only source archives, SHA-256:
- 1.16.5-36.2.42 mapped official sources:
  bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376.
- 1.20.1-47.4.23 mapped official sources:
  4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932.

Both ItemStack.getItem() return AIR for normal empty stacks and the current
registry delegate for nonempty stacks. Source uses an empty-cache/delegate-null
guard, target calls isEmpty; this does not prove malformed/corrupt stack equivalence.
Items.IRON_INGOT is the registered vanilla iron_ingot singleton on both baselines.
The predicate admits normal native stacks only, not arbitrary registry replacement.

Both Item.Properties.durability(int) set maxDamage and maxStackSize=1, returning
the same properties. Both stacksTo(int) throw after positive durability; order
therefore matters. Do not substitute stacksTo or defaultDurability by signature.
Both Item.isValidRepairItem(ItemStack,ItemStack) are public virtual and default
false; preserve the owned override rather than calling the base implementation.
Both Forge Item.isRepairable(stack) return canRepair && isDamageable(stack),
with canRepair initially true. This hook is distinct from ingredient eligibility.

RepairContainer.createResult (source line 106) and AnvilMenu.createResult
(target line 130) dispatch the override on the result item's identity, with
original input and material stacks. Their material branch computes at most
maxDamage/4 per ingredient. These sources alone do not certify the enclosing
Forge hooks, rename/enchantment/cost/consumption lifecycle or gameplay equality.

Verified production symbols (independently recomposed by the recipe compiler,
not used as a generic manually maintained mapping database):

| Official member | Source production | Target production |
|---|---|---|
| Item.Properties.durability(int) | func_200918_c | m_41503_ |
| Item.isValidRepairItem(ItemStack,ItemStack) | func_82789_a | m_6832_ |
| ItemStack.getItem() | func_77973_b | m_41720_ |
| Items.IRON_INGOT | field_151042_j | f_42416_ |

Source names were read from the genuine reobfuscated fixture with javap; target
names from the pinned target createMcpToSrg output, then confirmed against both
real API inventories and official/SRG composition. Constructor opcodes remain
INVOKESPECIAL, getter/property INVOKEVIRTUAL, ingredient GETSTATIC, and owned
override dispatch virtual. The final native stack class and final static iron
field are checked explicitly. No method handles or additional contracts admitted.

The target has native features, rendering and item hooks absent/different in the
source. This slice does not emulate those, add tool actions, mutate damage,
register attributes or supply creative ordering. Official/SRG mappings and full
explicit API inventories must determine exact production symbols and confirm
both declarations; no large hand-maintained mapping table or confidence upgrade.

[Forge item documentation](https://docs.minecraftforge.net/en/1.20.1/items/)
provides orientation only; the pinned local sources above govern these rules.
The frozen three-release corpus contains no durable-item producer. Clumps reads/
writes existing damage inside XP/Mending/entity behavior; this fixture does not
unlock Clumps or measure ecosystem reach. No acquisition or redistribution.

## Implemented engine and validation

`LegacyEngineService.planDurableItem(request)` uses the existing bounded worker,
progress/cancellation and selected-hash checks. Explicit LOGIC_PRESERVING_V1 base
selection is necessary but is not authorization for ordinary conversion. All six
existing profiles refuse the new fixture; public Convert/Apply remain unchanged.

The recipe compiler uses official/SRG composition and real declarations, not a
generic confidence promotion. The source, mapped target and reopened candidate
must rederive the same closed proof. The resource-only constructor projection
is never emitted. LoggedClassRemapper and body-preservation checks retain
instructions/control flow/debug; ClassWriter uses offline COMPUTE_FRAMES/MAXS;
ASM and offline access/linkage validate every owned output class.
Every remapping is bound to the implemented LOGIC descriptor operation. The
two new OFFLINE adapters emit private identity and schema-1 bindings, not fake
in-game behavior. Runtime ABI and all 45 previous adapter descriptors are intact.

631 final focused engine regressions passed, including 49 new cases, with zero
failures/errors/skips; four fixture archive tests and all-module assemble,
public/runtime JAR audits, engine boundary and CLI smoke passed.
Earlier 209 checks overlap and are not added to the final count.
Contract verification: 75 passed, two pre-existing Windows symlink assumption skips;
23 prompts / 81 cuts verified at LB-10/BLOCK-STATE. Final assemble/audits/CLI
passed again with the identical public hash; this is not native qualification.
[Ledger, hashes, exact inputs and limitations](evidence/durable-item-2026-09-19.json).
Initial fixture-template identifier and test constructor errors were corrected;
failed logs remain local. No native run is inferred from these checks.

Public artifact SHA-256: `240a35f90be74e74d0d06eb25f04202c6030d003235819d02c93d344a37d8daf`.
Fixture SHA-256: `6d14e862d077d873d6f85e351e3c06f4913089ad33f70a011af16cec76e2b07a`.
Private candidate SHA-256: `a1d513a0434870de8d933badc4f5be87f5206644d168802ac0f586ed872964ae`;
it is validated in memory/temporary storage and discarded, not exported.

## Limits and gates

New candidate remains developer-only and uses explicit full mapping/API inputs.
No old game/Forge data added to the public JAR, no hidden download/cache lookup.
Runtime ABI, simple-content-v1 and five other profiles remain unchanged.
Native source/target, client/server, persistence, multiplayer and behavior gates
remain NOT_RUN. No Minecraft or server is started during this implementation.
