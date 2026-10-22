# Preserved boolean block interaction

LB-10 / BLOCK-STATE now implements `BOOLEAN_TOGGLE_USE` as a private finite
candidate. It preserves the original mod's interaction body rather than replacing
its subclass with a registration bootstrap. This is not a new public conversion
profile, an install receipt, or proof of working gameplay.

## Admitted program

The authored Forge 36.2.42 fixture contains two Java 8 classes and eight methods:
one entry point and one final direct Block subclass. DeferredRegister attaches
BLOCKS then ITEMS to the mod bus; a deferred BlockItem supplier performs the only
registry lookup. Registry names, modId, bounded strength/stack literals and owned
class/field names are data, not a fixture hash allowlist.

The subclass has exactly one public static final native BooleanProperty, created
with the literal `active`. Its constructor calls super before registering the
single state's false default using Boolean.FALSE. The state builder adds only
that property. The public native use callback has this exact control flow:

- Logical client: return SUCCESS without a world write.
- Logical server: cycle the callback's state, cast the erased Object result to
  BlockState, call setBlock once on the callback's world and position with flags
  3, discard the boolean result, and return CONSUME.

The accepted branch target, local-variable slots, invocation opcodes, field
identities, literal flags and side effects are checked structurally. No player,
item, different position, chunk, async task or packet is introduced. CONSUME is
not a guarantee that the ignored setBlock operation succeeded.

## Transformations and independent checks

`VerifiedLogicMappings.compileBooleanToggleBlock` composes exact source and
target symbols from supplied official/SRG mappings and API declarations. It
requires both native callback declarations, their closest owner, visibility,
instance/static kind and finality. Missing/private overrides cannot be treated
as ordinary owned methods. HIGH confidence is not automatically promoted.

The existing full-cube STONE properties recipe performs the only contextual
contract expansion. It is distinct from the half-shape recipe. Class remapping
retains the owned subclass, constructor, branch, fields, debug information and
method bodies; the source and target structural proofs must agree.

Every logged rewrite names an implemented adapter operation. The two new OFFLINE
operations generate the private manifest and versioned adapter bindings. Existing
LOGIC, PLAIN_BLOCK, PLAIN_BLOCK_PROPERTIES, MINING, METADATA and PACK operations
retain their identities. No empty runtime shim or simulated world is added.

ASM validation and offline linkage/access checks run before serialization and
again after reopening the archive. The reopened class graph, metadata, resource
ownership, hashes and content identity must match. Temporary bytes are discarded;
`LegacyEngineService.planBooleanToggleBlock` returns proof/hash through the bounded
worker and cancellation contract, not a JAR or publication authority.

## Resources

Exactly three local models form the visual graph: the block's false state uses
the audited vanilla cube_all/stone reference, true uses cube_all/cobblestone, and
the item inherits the inactive block model. Blockstates contain exactly
`active=false` and `active=true`, each naming its matching local model. Extra
variants, transforms, model overrides, elements or texture substitutions fail.
The model and texture references were inspected in both exact client archives;
no vanilla assets are copied into LegacyBridge.

Admitted recipes/localization and model bytes remain unchanged. Only named
metadata/pack operations and the additive target pickaxe tag generate or rewrite
entries. Unknown resources are not silently removed. This candidate does not
compose implicitly with the optional self-drop table or the other block variants.

## Evidence and remaining limits

The [source audit](BLOCK_INTERACTION_AUDIT.md) explains native update flags,
dispatch and version differences. The [delivery ledger](evidence/boolean-toggle-block-2026-10-01.json)
records executable static checks, exact hashes and package verification.
Tests never load or execute original mod classes. They include reversed/escaping
branches, extra writes, wrong argument provenance, changed API declarations,
resource mismatches, public-profile refusal and cancellation before publication.

Native client/server behavior, two-player updates, reconnect/save-load, offhand,
sneaking, creative/survival/spectator, Forge cancellation/DENY, permissions/reach,
write failure and inventory/drop invariants remain NOT_RUN. The target's native
interaction acknowledgements, feature gates, neighbor hooks and vanilla behavior
are not declared identical to 1.16.5. Rendering is statically checked, not observed.

Redstone, ticks, BlockEntities, general callbacks, networking, player mutations,
arbitrary shapes and cross-profile composition remain unsupported here. The six
public grammars, Convert/Apply flow, runtime ABI and license are unchanged.
