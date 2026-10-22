# Original owned stack damage fixture

Development input for the next LB-10 / ITEM-EQUIPMENT / OWNED_STACK_DAMAGE
slice. Forge 1.16.5 / 36.2.42, Java-8 bytecode via the pinned Java-17 toolchain.
This authored original is not converted or bundled in LegacyBridge.jar.

Two final owned classes contain five methods. The entry registers
legacybridge_stack_damage:bridge_stack_item. Its item constructor sets native
durability 128; the public repairOne(ItemStack) helper checks null, exact item
identity, native damaged state and the range before one damage-minus-one write.
It returns a boolean and does not replace stacks, change count or store state.

This is an explicit endpoint for future isolated QA, **not automatic gameplay**:
there is no right-click, command, attack, mining or scheduled repair trigger.
It adds no attributes, ingredient predicate, wear/break handling or synchronization.
Native damage hooks remain in the path. See the
[source audit and expected oracle](../../docs/OWNED_STACK_DAMAGE_API_AUDIT.md).

One shapeless iron-ingot recipe, a generated-item model and en_us/pt_br
translations accompany the original. Vanilla texture/model IDs are referenced;
no game assets, old Forge/Minecraft classes or harnesses are copied.

Build serially from the main project with the existing prepared dependencies:

~~~powershell
.\gradlew.bat -p fixtures/owned-stack-damage-1.16.5 build --offline --no-daemon --max-workers=1
~~~

The four tests inspect archive metadata/resources/class structure only. No
original class or native game is loaded. Passing the build does not implement
the future conversion contract or prove behavior, persistence or multiplayer.
