# Original owned stack repair use fixture

Authored input for the separate private LB-10 item-use contract. The converter
now implements structural/mapping/linkage admission; native behavior and public
conversion/installation are not qualified. The fixture targets Forge
1.16.5 / 36.2.42 with Java-8 bytecode from the pinned Java-17 toolchain. It is not
included in the public LegacyBridge JAR and does not extend any public profile.

Two final owned classes contain six methods: three entry/registration methods,
the item constructor, repairOne(ItemStack) and the native use(World, PlayerEntity,
Hand) override. The registry identity is
`legacybridge_stack_use:bridge_stack_use_item`, version `1.0.0`.

The helper retains the preceding fixture's null, exact item identity, native
damaged-state and damage-range guards. It decrements damage once on the same
received stack and reports whether a write occurred. It does not change count,
copy a stack, retain state, consume repair material or implement wear/breakage.
Native Forge damage hooks and numeric NBT coercion remain in the call path.

The use callback reads the received player's stack in the received hand. Client
execution returns native SUCCESS for that stack without invoking repairOne.
Server execution invokes the helper once, returning native CONSUME on a write
or PASS otherwise. It does not add packets, scheduling, use duration, cooldowns,
permissions or a custom inventory synchronization protocol.

## Source observations and expected limits

The pinned Forge 36.2.42 sources define Item.use, LivingEntity.getItemInHand,
World.isClientSide and ActionResult.success/consume/pass with these signatures.
The client startUseItem loop stops after a consuming result, so this deliberate
client SUCCESS prevents fallback to another hand after this callback is reached.
It can also cause a client swing when the server later returns PASS. That is an
explicit design tradeoff, not a claim of matching server outcome prediction.

Native PlayerInteractionManager.useItem handles spectator mode, cooldown and
Forge right-click cancellation before dispatch. Its creative path restores the
previous damage after an item callback changes it. Therefore a direct endpoint
result is not evidence of effective repair through the creative interaction
path. Later QA must distinguish survival/adventure/creative and both hands,
event cancellation, cooldown, client prediction, inventory synchronization,
save/load and reconnect. No claim of native behavior or multiplayer validation
follows from archive tests. The callback alone does not establish thread safety
for arbitrary external callers of the public helper.

One authored shapeless recipe, one generated model and en_us/pt_br translations
accompany the original. Vanilla model/texture identifiers are references only;
no game assets or platform classes are copied. The four tests inspect ZIP
metadata, resource content and Java-8 classfile structure without loading or
executing the fixture classes. All runtime dimensions remain NOT_RUN.

Build serially with the existing dependencies; this command does not launch a game:

~~~powershell
.\gradlew.bat -p fixtures/owned-stack-repair-use-1.16.5 build --offline --no-daemon --max-workers=1
~~~

The [private conversion contract](../../docs/OWNED_STACK_REPAIR_USE_CONTRACT.md)
checks all six methods and preserves their bodies. It does not widen the prior
OWNED_STACK_DAMAGE grammar or qualify Apply. Build and headless results are
recorded separately in the project evidence ledger.
