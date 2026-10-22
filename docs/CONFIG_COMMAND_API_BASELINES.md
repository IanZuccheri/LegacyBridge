# Native lifecycle/config/command API baselines

Inspected 2026-09-06. Scope: the separate original
`fixtures/lifecycle-content-1.16.5` fixture, not general configuration/event/command
compatibility. Findings below came from the exact locally available mapped game
and Forge sources plus `javap -s` on both native Brigadier versions. Source reading
and bytecode inspection did not initialize any mod class.

The versions remain Minecraft 1.16.5 / Forge 36.2.42 / eventbus 4.0.0 and Minecraft
1.20.1 / Forge 47.4.23 / eventbus 6.2.33. Official version manifests identify
Brigadier 1.0.17 and 1.1.8 respectively. These installed source archives are audit
inputs, not files to bundle or publish. Mapping/export permission requirements
remain separate; no redistribution grant is inferred from local availability.

## Exact inspected inputs

| Local artifact identity | SHA-256 |
|---|---|
| forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |
| fmlcore-1.20.1-47.4.23-sources.jar | `fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1` |
| eventbus-4.0.0-sources.jar | `88af63462fd2880bb12ed3a9c8fa98cff0e0b2195dd60001175f717e12a03f43` |
| eventbus-6.2.33-sources.jar | `6973f199d644a1ba81d8f703c8599ddff07448f2f62c5c0c664d765c3ead16a6` |
| brigadier-1.0.17.jar | `296b69b63f3d2a2092bf94fa12add4deb89e7d1f977157f7ee1b6b6d0fd52a58` |
| brigadier-1.1.8.jar | `cf65afa612fffbe4dc976115dac0312f48b9c84b3ddaf58bec8018a9454440d7` |

## Lifecycle and late registry reads

Both exact eventbus sources declare the same erased descriptor for the selected
`IEventBus.addListener` overload:

```text
(Lnet/minecraftforge/eventbus/api/EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)V
```

The fixture supplies NORMAL, false and the explicit FMLCommonSetupEvent class token.
This is a deliberate bounded variant: it does not need runtime generic-type
inference to identify the event. A class literal is not permission to perform
reflection during conversion; this bytecode remains original owned code.

`FMLCommonSetupEvent` inherits `ParallelDispatchEvent.enqueueWork(Runnable)`, whose
descriptor is `(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;` in
both versions. The handler only submits work and ignores its returned future. The
queued action runs later; it is not replaced with an immediate call. The actual
native scheduler and resulting item availability must be independently observed.

The RegistryObject migration moves `net/minecraftforge/fml/RegistryObject` to
`net/minecraftforge/registries/RegistryObject`. The genuine source fixture invokes
`get():net.minecraftforge.registries.IForgeRegistryEntry` because its legacy
generic bound determines that erased return. The source also has a distinct
`get():Object` bridge; these are not interchangeable source descriptors. The target
call returns `Object`, requiring an explicit, contextual descriptor operation and
preservation of the fixture's Item CHECKCAST. This fixture has one such read, in
the enqueued work, not a static initializer or constructor.

## COMMON configuration

Both exact ForgeConfigSpec sources implement the selected Builder surface:

```text
Builder.<init>()V
Builder.define(String,boolean): ForgeConfigSpec.BooleanValue
Builder.defineInRange(String,int,int,int): ForgeConfigSpec.IntValue
Builder.build(): ForgeConfigSpec
ConfigValue.get(): Object
```

BooleanValue and IntValue inherit `ConfigValue.get()` rather than declaring
specialized public getters. A compiler may reference the inherited method through
the subclass owner. Preserve CHECKCAST Boolean/Integer and their real
`booleanValue():boolean` / `intValue():int` unboxing calls; do not invent `getInt()`
or boolean-returning descriptors.

**Actual descriptor change:** `ModLoadingContext.registerConfig` accepts
`(ModConfig.Type,ForgeConfigSpec)` in Forge 36 and `(ModConfig.Type,IConfigSpec)` in
Forge 47. Both overloads return void. Forge 47's ForgeConfigSpec actually implements
`net.minecraftforge.fml.config.IConfigSpec`, so the selected non-empty native spec
can satisfy the target interface. This requires an explicit verified descriptor
operation; global text replacement or an unimplemented shim is insufficient.
The optional filename overload is not used or approved by this fixture.

Forge 36 falls back to defaults if the built spec has not yet received config data.
Forge 47.4.23 includes a development-environment guard against reads before config
loading. The fixture never relies on the old early-default behavior: it builds and
registers the spec first, then reads in deferred common setup or a server command.
That lifecycle assumption still needs actual original/target runtime comparison.

The default type is COMMON, with global `<modid>-common.toml`; it is loaded on both
sides but not synchronized. CLIENT and SERVER have distinct lifecycle/storage/sync
contracts and are not interchangeable. The official documentation confirms these
scope distinctions and native constructor registration. [Forge configuration documentation](https://docs.minecraftforge.net/en/1.20.1/misc/config/)

Config callback types differ:

| Source Forge 36 | Target Forge 47 |
|---|---|
| `fml/config/ModConfig$ModConfigEvent` | `fml/event/config/ModConfigEvent` |
| `fml/config/ModConfig$Loading` | `fml/event/config/ModConfigEvent$Loading` |
| `fml/config/ModConfig$Reloading` | `fml/event/config/ModConfigEvent$Reloading` |
| `ModConfig.getSpec():ForgeConfigSpec` | `ModConfig.getSpec():IConfigSpec` |

These names document audit findings only. Loading/reloading/unloading listeners,
config writes, live reload, validators/lists/enums, SERVER synchronization and
CLIENT-only settings are **not enabled** by this fixture. Unknown equivalents
must remain MANUAL/UNSUPPORTED instead of being inferred from similar names.

## Native command surface

The source uses `net/minecraft/command/Commands` and `CommandSource`; the target
official names are `net/minecraft/commands/Commands` and `CommandSourceStack`.
The inspected relevant methods are:

```text
RegisterCommandsEvent.getDispatcher(): CommandDispatcher
Commands.literal(String): LiteralArgumentBuilder
Commands.argument(String,ArgumentType): RequiredArgumentBuilder
CommandSource / CommandSourceStack.hasPermission(int): boolean
IntegerArgumentType.integer(int,int): IntegerArgumentType
IntegerArgumentType.getInteger(CommandContext,String): int
ArgumentBuilder.requires(Predicate): ArgumentBuilder
ArgumentBuilder.then(ArgumentBuilder): ArgumentBuilder
ArgumentBuilder.executes(Command): ArgumentBuilder
CommandDispatcher.register(LiteralArgumentBuilder): LiteralCommandNode
Command.run(CommandContext): int
```

Brigadier's erased descriptors match across 1.0.17 and 1.1.8 for this exact subset.
Inherited fluent methods may have LiteralArgumentBuilder or RequiredArgumentBuilder
instruction owners, but declarations/return erasure remain on ArgumentBuilder.
Preserve Java's casts and original method handles. This does not prove every
Brigadier API or arbitrary callback semantic equivalence.

The static `@Mod.EventBusSubscriber(modid=...,bus=FORGE)` class declares one
`@SubscribeEvent public static void registerCommands(RegisterCommandsEvent)`.
The original Java 8 method references implement Predicate and Brigadier Command;
they remain owned logic and may not be evaluated during conversion.
The dispatcher is supplied by Forge's event; the fixture does not construct a
dispatcher or manufacture lifecycle events. Native event sources state command
registration can repeat when game resources/dispatcher are recreated.

Other command surfaces have actual differences and are deliberately excluded:
RegisterCommandsEvent constructor/environment/context methods changed; source
`sendSuccess(ITextComponent,boolean)` becomes target
`sendSuccess(Supplier<Component>,boolean)`; command execution wrappers and text
components also changed. This fixture emits no chat messages or custom suggestions,
registers no custom argument type, and returns only an integer through Brigadier.

## Separate required validation

Compilation/archive tests, converter admission, bytecode/linkage validation and
native behavior observations are separate gates. A paired runtime-only probe must
observe actual registration, one common-setup queued work completion, item object
identity, loaded default and explicitly prepared alternate config, native command
returns, permission denial and argument bounds. Each original/output/environment
must be hash-bound. Do not inherit the older logic fixture's successful runtime
trace or claim general command/config support from these source files.

No client visuals, multiplayer behavior, config synchronization, hot reload or
dispatcher-recreation behavior is certified until independently exercised.

## Separate SERVER snapshot candidate (2026-09-19)

[SERVER_CONFIG_SCOPE](SERVER_CONFIG_SCOPE.md) audits the pinned world-scoped
ConfigTracker/ServerLifecycleHooks and source/target event declarations.
Exact started/stopped class remaps are gated by a whole-graph proof, not by
the registerConfig signature alone. SERVER registration is on both physical
sides, native reads occur after start and state clears before native unload.
Source lacks the target config Unloading event; no equivalent is invented.
Initial connection config synchronization is distinct from snapshot fields,
reload/restart and runtime qualification. This is a private candidate only.
