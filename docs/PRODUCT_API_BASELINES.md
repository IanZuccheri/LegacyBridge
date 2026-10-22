# In-game product: pinned API and loader evidence

Read-only source audit: 2026-09-05. This document supports architecture decisions,
not a claim that the menu, all-in-one artifact, installer or restart support is
implemented. The conversion subset remains `simple-content-v1`.

## Exact inspected inputs

The project pins Minecraft 1.16.5 / Forge 36.2.42 as source and Minecraft 1.20.1 /
Forge 47.4.23 / Java 17 as target in `gradle.properties`.

Locally generated, Mojang-named development source archives under
`${DEVELOPER_HOME}/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/`:

| Relative source archive | SHA-256 |
| --- | --- |
| `1.16.5-36.2.42_mapped_official_1.16.5/forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Published Forge source artifacts in the Gradle module cache under
`${DEVELOPER_HOME}/.gradle/caches/modules-2/files-2.1/net.minecraftforge/`:

| Relative source archive | SHA-256 |
| --- | --- |
| `fmlloader/1.20.1-47.4.23/f4a40c0acc54a5c0447f0402b5aa0f48efae3a83/fmlloader-1.20.1-47.4.23-sources.jar` | `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667` |
| `fmlcore/1.20.1-47.4.23/8ffb911bdeda1e1b4884a5512bfcadcb0f96af73/fmlcore-1.20.1-47.4.23-sources.jar` | `fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1` |

Hashes identify the inspected local bytes; they are not a signature or a grant
to redistribute game sources. Source entry line numbers below refer to those
archives. The legacy fixture is reobfuscated to production SRG member names;
development names are not evidence that the original JAR uses the same namespace.

## Game directory and native client hook

In the target `fmlloader` sources:

- `net/minecraftforge/fml/loading/FMLPaths.java:20-24` declares `GAMEDIR` and
  `MODSDIR("mods")`. `setup(IEnvironment)` takes the launcher game directory
  (lines 51-54); `loadAbsolutePaths(Path)` resolves absolute normalized paths
  (57-75). `public Path get()` is at 95-97.
- Therefore the Forge-facing entry point can pass `FMLPaths.GAMEDIR.get()` to
  game-independent product code. `Path.resolve("legacybridge").resolve("input")`
  is ordinary Java path construction, not a new Forge API. Normalization alone
  does not prove filesystem confinement or protect against links/TOCTOU.

In the target mapped sources:

- `net/minecraftforge/client/event/ScreenEvent.java:32-33` identifies the main
  `MinecraftForge.EVENT_BUS`, on the client. `public Screen getScreen()` is at
  54; `Init.addListener(GuiEventListener)` is at 99; `Init.Post` is at 142.
  This is **not** a mod-lifecycle-bus listener.
- `net/minecraft/client/gui/screens/Screen.java:320-323` and 336-339 post the
  init events, including rebuilds. A title-screen hook must be idempotent under
  resize/reinitialization and keep existing buttons intact.
- `net/minecraft/client/gui/screens/TitleScreen.java:48-49` is client-only and
  extends `Screen`. `Screen` has `protected Screen(Component)` at line 96.
- `net/minecraft/client/Minecraft.java:970` supplies
  `public void setScreen(@Nullable Screen)`. `gameDirectory` is a public final
  `File` at 289, but the common coordinator should not depend on this client class.

The planned UI can add a button through `ScreenEvent.Init.Post`, restricted to
`TitleScreen`, and open its own native `Screen`. No Minecraft class rewrite,
mixin, access transformer or ModLauncher service is needed for that operation.
The listener/class references must remain behind a physical-client boundary;
the single public artifact must still load on a dedicated server.
See the official [screens documentation](https://docs.minecraftforge.net/en/1.20.1/gui/screens/)
and [side separation documentation](https://docs.minecraftforge.net/en/1.20.1/concepts/sides/).

The legacy mapped sources instead declare
`net/minecraftforge/client/event/GuiScreenEvent.java:51`
`InitGuiEvent`, `addWidget(Widget)` at 74, and its `Post` at 106.
That comparison prevents accidentally designing the new product UI against the
old event API; it does **not** enable conversion of legacy custom GUIs.

## Discovery and dependency checks precede the usable main menu

In target `fmlloader`:

- `FMLLoader.beginModScan(Map)` (169-175) invokes `ModDiscoverer.discoverMods()`.
- `FMLLoader.completeScan(IModuleLayerManager)` (178-183) runs
  `ModValidator.stage2Validation()` and obtains the loading mod list.
- `moddiscovery/ModValidator.java:114-126` validates language loaders and calls
  `ModSorter.sort(...)` before scheduling mod class scanning.
- `ModSorter.verifyDependencyVersions()` (186-239) checks side-relevant required
  dependencies and installed optional dependencies against version ranges.

In target `fmlcore`, `ModLoader.gatherAndInitializeMods(...)` starts at 137;
pre-loading errors are checked at 144, before building mod containers at 167-171.
In the mapped sources, `ClientModLoader.begin(...)` invokes that gather operation
at line 66. `Minecraft.java:459` calls `ClientModLoader.begin(...)`; at 571-583 it
only proceeds to the initial screen after `completeModLoading()` permits it.
`ClientModLoader.java:129-139` starts the Forge event bus when no error exists
and selects the loading-error screen when loading failed.

Consequently, a normal in-game screen cannot reliably rescue a legacy JAR
already rejected from `mods`. The supported input is a sibling directory,
`<gameDir>/legacybridge/input`, not a second loader search path. LegacyBridge
must not register it as a locator or put originals on any loader/classpath.
This separation is a product guarantee under the supported loader setup, not
an ability to stop an unrelated third-party locator from scanning arbitrary paths.
See Forge's [dependency declarations](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/#dependency-configurations).

Installing an output on disk does not amend the completed loading mod list.
Activation belongs to a later process startup; this design introduces no
same-session hot loading or registry mutation.

## Why adding a startup service to the public JAR is not harmless

The pinned `fmlloader` sources show a concrete discovery hazard:

1. `ModDirTransformerDiscoverer.java:28-33` recognizes service declarations for
   `ITransformationService`, `IModLocator`, `IDependencyLocator` and
   `ImmediateWindowProvider`.
2. It walks the top level of `mods` (59-66), examines a JAR's module service
   declarations (72-81), and adds matching paths to its discovery list.
3. `allExcluded()` returns those paths (55-56).
4. `moddiscovery/ModsFolderLocator.scanCandidates()` excludes that list from
   normal mod-folder discovery (47-53).
5. `moddiscovery/ModDiscoverer.java:45-47` obtains locator services from the
   ModLauncher `SERVICE` layer.

Thus putting locator/service metadata in an otherwise ordinary all-in-one JAR
can remove its ordinary mod discovery path. This is not a validated dual-role
bootstrap design. Do not add those services merely to combine modules or create
a menu button. Any later bootstrap proposal must separately prove discovery
of the runtime itself and its interaction with module layers in production.

## Shutdown is not launcher restart

`Minecraft.java:1551-1554` implements `public void stop()` by posting
`GameShuttingDownEvent` when running and setting `running = false`.
It does not relaunch a JVM or authenticate a launcher session.

No launcher-specific restart mechanism was validated by this audit. The
architecture therefore defaults to an honest manual-launcher fallback after
a safely completed apply operation. A future optional restart integration
needs its own capability check and end-to-end proof; it must not reconstruct
commands from or persist authentication tokens. No restart should occur with
an open world or an incomplete transaction.

## Preserve the actual runtime identity and adapter ABI

Current repository evidence, not a hypothetical future API:

- `legacy-runtime/src/main/resources/META-INF/mods.toml` and
  `LegacyBridgeRuntime.MOD_ID` declare `legacybridge_runtime`.
- `AdapterCatalog.RUNTIME_VERSION` is `0.1.1-SNAPSHOT`; schema is `1`.
- `ModMetadataAdapter.targetRanges(...)` emits dependency
  `legacybridge_runtime=[0.1.1-SNAPSHOT,0.2.0)`.
- `AdapterBindings.verify(...)` also checks the **exact** catalog runtime
  version and each required adapter's `id`, `revision`, `implementationClass`,
  `method`, and `descriptor` in the mod-scoped
  `META-INF/legacybridge/<modId>/adapters.json`. A broad metadata range does not
  make a different catalog version ABI-compatible.
- The bootstrap is
  `dev.legacybridge.runtime.compat.registry.SimpleContentAdapter.register`
  with descriptor `(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/String;)V`.
  It verifies bindings before attaching the native registrations.

The first consolidated public artifact should retain that mod ID and concrete
entry points. Its public filename/display name can change without changing
them. It must not be installed alongside the old standalone runtime with the
same mod ID. Future catalog/version changes require explicit compatibility or
reconversion policy; do not silently relax binding verification to make an old
conversion appear supported.
