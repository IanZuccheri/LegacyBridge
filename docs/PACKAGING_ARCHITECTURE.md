# Single-artifact packaging: pinned Forge product

P3 packaging decision, based on source and artifact inspection on 2026-09-05.
Executed final-artifact build, ordinary client/server and fixture evidence is
recorded separately in [P3 packaging validation](PACKAGING_VALIDATION.md).
This document specifies the artifact boundary and acceptance gates; it does not
by itself certify a successful production launch. The conversion profile remains
`simple-content-v1`. See [the product architecture](PRODUCT_ARCHITECTURE.md) and
[the existing engine validation](ENGINE_SERVICE_VALIDATION.md) for independent
contracts and earlier evidence.

## Decision: platform libraries plus one Jar-in-Jar dependency

The public `LegacyBridge.jar` is an ordinary JavaFML mod for **Minecraft 1.20.1,
Forge 47.4.23, Java 17**. Its Forge dependency is exact, not a claim that every
47.x patch supplies the same library ABI. Widening that dependency requires a
new library inventory and production-loader validation.

The `legacy-product` assembly combines project-owned outputs at the outer JAR
root and includes exactly one unmodified third-party binary through ForgeGradle
6 Jar-in-Jar. The player installs no second LegacyBridge JAR.

| Component | Packaging and resolution |
| --- | --- |
| Runtime, neutral compatibility API | Outer mod JAR; existing packages and adapter identity preserved, one copy of each class |
| Converter service and workflow/controller | Outer mod JAR; project-owned classes only, no CLI package |
| Minecraft client interface | Outer mod JAR, behind physical-client-only registration; no common-side reference requiring client class resolution |
| ASM core, commons, tree, analysis, util 9.9.1 | Supplied by the exact Forge installation; zero bundled copies |
| NightConfig core/TOML 3.6.4 | Supplied by the exact Forge installation; zero bundled copies |
| SRG Utils 0.6.2 | One unchanged nested JAR, JarJar requested range `[0.6.2,0.6.3)` |
| Logging | Forge/Mojang host logging only; no bundled SLF4J API, simple provider, Log4j implementation or additional binding |
| Engine JSON | Existing project-owned bounded JSON parser and canonical writers; no additional JSON dependency |
| Mapping/API data | Included finite simple-content contract; full inventories remain explicit local imports, never private bundle exports in the release |

The published `forge-1.20.1-47.4.23-userdev.jar` `config.json` lists all five ASM
9.9.1 artifacts and NightConfig core/TOML 3.6.4. The corresponding published
`fmlloader` POM independently specifies ASM core/tree/commons 9.9.1,
NightConfig 3.6.4, ModLauncher 10.0.9 and SecureJarHandler 2.1.10. These match
the engine's actual pinned ASM and TOML dependencies. This is a verified
baseline choice, not an assumption that a generic Forge runtime supplies
whatever library versions the engine happens to compile against.

Private relocation was considered for stronger library-version isolation.
For this exact baseline it would duplicate already matching platform libraries
and introduce additional bytecode/resource transformation verification. In
particular, mapping-engine string constants describe external symbols and must
not accidentally be changed as a side effect of library relocation. Relocating
the LGPL SRG Utils library would also create a modified library distribution
with additional corresponding-source obligations. Using unchanged SRG Utils
and the verified host libraries is the narrower strategy for this milestone.
It is **not** private dependency isolation or a promise of compatibility with
arbitrary modpacks.

## JarJar negotiation is global, not a private classloader

ForgeGradle generates `META-INF/jarjar/metadata.json` describing the nested
SRG Utils artifact and its accepted version range. Only the explicitly selected
dependency is eligible. Do not use an unfiltered runtime-classpath expansion:
that can accidentally include Minecraft or Forge. The upstream
[ForgeGradle 6 Jar-in-Jar documentation](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/)
describes range negotiation and that packaging hazard.

In the pinned loader source, `JarInJarDependencyLocator.java:44-58` passes all
discovered mod sources to `JarSelector.detectAndSelect`. Its `:68-91` handles
ordinary nested libraries as `GAMELIBRARY` dependencies. Consequently, another
mod's JarJar declaration for `net.minecraftforge:srgutils` participates in the
same negotiation; this is not an independent copy reserved for LegacyBridge.

The range `[0.6.2,0.6.3)` restricts the negotiated dependency. Incompatible
declared requirements produce a loader error; LegacyBridge does not choose a
random version, suppress that failure or widen the range. An unrelated mod
which flattens duplicate packages without declaring the dependency can still
cause module or linkage failures. Such a modpack is outside the validated
baseline, not evidence that JarJar isolates duplicate class definitions.

## Modules, services and duplicate ownership

The pinned sources establish the relevant boundaries:

- SecureJarHandler `JarMetadata.java:36-47` selects `module-info.class` when
  present, otherwise derives automatic-module metadata and honors
  `Automatic-Module-Name`.
- `SimpleJarMetadata.java:13-17` includes packages and service providers in an
  automatic module. `impl/Jar.java:235-250` reads `META-INF/services`.
- Forge `ModJarMetadata.java:69-77` creates the ordinary mod's automatic module
  using its mod-file name/version, packages and declared services.
- ModLauncher `ModuleLayerHandler.java:75-81` resolves JPMS configurations and
  defines their layers. SecureJarHandler `ModuleClassLoader.java:127-139`
  dispatches class loading by owning package/module, not by a private nested-JAR
  classloader for each parent mod.

Accordingly, the outer archive has one owning copy of each project class and
one authoritative manifest, `mods.toml`, pack metadata and adapter catalog.
It must not flatten SRG Utils classes as well as nest its JAR. The inspected
unchanged SRG Utils binary declares
`Automatic-Module-Name: net.minecraftforge.srgutils`; it has no explicit
`module-info.class`, multi-release classes or service-provider entries. Its
original manifest remains inside that unchanged dependency. Dependency
manifests are not merged into the outer mod manifest.

The public archive permits no outer `module-info.class`, inherited dependency
module descriptor, startup service declaration, copied signature or duplicate
entry. Audit duplicate classes recursively across the permitted nested binary
and the outer archive. Own modules share one outer JAR, not several nested
LegacyBridge modules with overlapping compatibility API packages.

There is no `ITransformationService`, `IModLocator`, `IDependencyLocator` or
`ImmediateWindowProvider` implementation in the public artifact. The pinned
`ModDirTransformerDiscoverer.java:28-33,72-81` discovers those service entries
early; `ModsFolderLocator.java:47-53` excludes the resulting paths from normal
mod-folder discovery. Packaging does not need that dual-role bootstrap hazard.

## Logging and retained development CLI

The converter's SLF4J imports are confined to
`dev.legacybridge.converter.cli.LegacyBridgeCli`. The reusable service does not
depend on those imports or the CLI presentation adapter. Excluding the CLI
package from the player assembly therefore also avoids introducing the CLI's
SLF4J 2.0.17 API/provider into Forge's existing logging environment.

The runtime uses `com.mojang.logging.LogUtils` and host-provided `org.slf4j.Logger`.
No additional provider is installed and no service file is synthesized to
override Forge logging. The standalone `legacy-converter` application
distribution retains its existing commands, application launcher and CLI-only
logging dependencies for development. The Minecraft interface calls the typed
engine service, never `CLI.main`, stdout parsing or `System.exit`.

## Runtime identity, physical sides and discovery

The public display name and filename do not rename the runtime:

```text
modId: legacybridge_runtime
adapter catalog runtime version: 0.1.1-SNAPSHOT
adapter catalog schema: 1
bootstrap: dev.legacybridge.runtime.compat.registry.SimpleContentAdapter.register
descriptor: (Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/String;)V
```

Converted mods' dependency and exact adapter bindings remain valid against
this identity. Do not install the old standalone runtime beside the public
artifact: both declare the same mod ID. Do not relax adapter verification merely
to hide a duplicate or version mismatch. [The API baseline record](PRODUCT_API_BASELINES.md)
documents the metadata range and exact per-operation binding checks separately.

2026-09-12 additive offline extension: [finite cohort target planning](DEPENDENCY_COHORT_TARGET.md)
adds three concrete metadata-operation descriptors and `CohortMetadataAdapter`
to the neutral API/public catalog. Existing entry descriptors/revisions and
runtime call bodies remain unchanged; no ABI version or public profile changes.
The public engine contains the planner/request, but no fixture bytes, full local
mapping bundle or candidate installation authority. Static package checks do not
transfer historical game validation to this new hash.

The only ordinary mod entry point remains the runtime entry point. The client
interface is discovered/registered with a physical `CLIENT` restriction. Common
service/controller code must not reference `net.minecraft.client` types in
fields, method descriptors or initialization paths. The same final JAR must
therefore be tested on a dedicated server without resolving client-only classes.
Client classes being present as bytes in the JAR does not authorize common-side
loading of them.

The title-screen hook uses the target's `ScreenEvent.Init.Post` on
`MinecraftForge.EVENT_BUS`; it does not transform Minecraft classes. Original
mods remain beneath `<gameDir>/legacybridge/input`, not in `mods` or on a
classpath. Forge can reject a legacy JAR already placed in `mods` before the
menu becomes available. Installing converted results is separate from
activation on a later startup; continuing to convert cannot change the loaded
mod set. No verified launcher restart mechanism is implied by packaging.

## Redistributable contents and data setup limitation

The allowed release contents are project-owned implementation/resources,
versioned project-owned adapter/capability metadata, third-party notices, the
unchanged permitted SRG Utils binary and its JarJar metadata. No fixtures,
test/probe/harness classes, old Forge or Minecraft binaries, decompiled game
sources, development paths, credentials or locally generated mapping payloads
belong in the public artifact.

SRG Utils 0.6.2's published POM and source headers identify `LGPL-2.1-only`.
Its binary JAR has no embedded LICENSE/NOTICE entry, so retaining an unchanged
binary is not enough by itself: release notices must supply the applicable
license and corresponding-source/replacement information. LegacyBridge's own
license does not supersede users' rights in that library. See the
[SRG Utils upstream license](https://github.com/MinecraftForge/SrgUtils/blob/master/LICENSE.txt).
This is an engineering distribution boundary, not a legal opinion that every
possible release arrangement complies with the license.

The locally prepared mapping bundle remains
`LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`. Its hashes prove consistency,
not permission to redistribute Mojang/MCP-derived data. P3 must not turn a
private development export into public resources merely to claim independence.
The permitted existing workflow is explicit local preparation from appropriate
inputs followed by explicit import; no download or Gradle-cache search occurs
on import. See [portable data and its rights audit](PORTABLE_MAPPING_DATA.md).

The public JAR includes the [finite simple-content contract](EMBEDDED_SIMPLE_CONTENT_DATA.md)
for the existing player profile, with byte-hash/package checks and no external
setup. This is not a universal pre-cleared mapping database. Broader-profile
missing setup remains a structured error; public rights review stays open.
Inventing mappings or embedding old game code is not a fallback.

## Source and artifact fingerprints

The source archives below are read-only inspection inputs under the local
Gradle module cache, not files to redistribute. Paths are relative to
`${DEVELOPER_HOME}/.gradle/caches/modules-2/files-2.1/`.

| Archive | SHA-256 |
| --- | --- |
| `net.minecraftforge/fmlloader/1.20.1-47.4.23/f4a40c0acc54a5c0447f0402b5aa0f48efae3a83/fmlloader-1.20.1-47.4.23-sources.jar` | `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667` |
| `cpw.mods/modlauncher/10.0.9/3a731130cea80690b50d02303e3a0bd5c2a808fc/modlauncher-10.0.9-sources.jar` | `aa4c68fd4094298969b95f67e3c84cbf345814e07c8bc36c7724f484573e230c` |
| `cpw.mods/securejarhandler/2.1.10/96331802dca7d09fd109500a708200ae758ff412/securejarhandler-2.1.10-sources.jar` | `4f66b479d202582e0c61cb4f7489d754a2efc0e6b8d10ee9c6e603935b1cea13` |
| `net.minecraftforge/srgutils/0.6.2/e8e7d58835222461405514ab69fd6b29726e950d/srgutils-0.6.2.jar` | `f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e` |

The `fmlloader` POM is in the same version directory beneath
`7871dc7a67266ae6ffb2ba3fc58c0fdc34a2bc66/`. The inspected `userdev` config is in
`${DEVELOPER_HOME}/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-userdev.jar`.
The preceding source-entry line numbers refer to these pinned source bytes, not
the moving upstream branch.

## Acceptance gates: final artifact, not only the development classpath

The following are separate evidence requirements. Until the final validation
record records an executed gate against the final JAR hash, it remains pending.

1. Serial build/test, retained CLI commands and CLI/service result parity pass;
   packaging must not expand conversion rules or alter adapter/report schemas.
2. Inspect the final archive recursively: exact runtime identity/version, one
   nested unchanged SRG Utils artifact and strict version range, no copies of
   provided ASM/NightConfig/logging libraries, no duplicate classes or prohibited
   data/services/harnesses. Verify final metadata after reobfuscation.
3. Install the exact Forge 47.4.23 release using its normal installer in an
   isolated instance. Launch the final public artifact from `mods` using the
   normal production launch target, without userdev source sets or Gradle output
   directories on its classpath.
4. Dedicated-server gate: ordinary mod discovery reports `legacybridge_runtime`,
   JarJar discovers the allowed library, server loading reaches readiness, and
   no client class resolution or duplicate module/provider error occurs.
5. Client gate: ordinary discovery succeeds; the title button opens the actual
   client screen, the typed service executes off the render thread, local-input
   selection/progress/errors work, and the session's loaded mod set is unchanged.
   A server reaching readiness does not satisfy this GUI gate.
6. Conversion/runtime regression gate: with explicitly imported permitted data,
   convert the positive fixture without a developer cache; check reports and
   output before separately loading the converted result on a later startup.
   Prove registration/resources/recipe behavior separately from static bytecode
   validity. Negative inputs must not publish partial JARs.

Existing LegacyBridge `run-validation` development launches are useful earlier
evidence but do not replace gate 3. A bounded audit found only other-version
ordinary server instances nearby; they are not authorized installation targets.
The [official Forge download index](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
publishes the pinned installer with SHA-1
`ed31ce02ac69176f34353235cb2508d5a0f1e088`; an isolated installation can be
prepared without modifying a player's existing instance. Installer downloads
for an explicitly requested test are separate from, and do not add, hidden
product mapping downloads.
