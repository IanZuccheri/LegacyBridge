# COMPOSE-01: exact native launcher lookup audit

Reviewed 2026-09-08. **STATIC_LOCAL_AUDIT_ONLY; LB-08 / COMPOSE-01 remains
PARTIAL.** This document records read-only inspection of already prepared local
platform archives, exact launcher bytecode, matching-version local source
archives and the selected JDK source. No game, test helper, Gradle build or
native probe was run for this audit. No platform archive was changed or acquired.

The finite result is two potentially reachable stale sibling JAR lookups on the
SOURCE Java 17 application-classpath route. TARGET contains another stale name,
but its selected bootstrap/module lookup route reads archive entries directly
and does not expand that index. None of these findings is an observation of a
running game, a proven startup failure, or a complete native classloader closure.

## Reviewed inputs and coverage

The authoritative selection remains
[compose-platform-inventory-2026-09-07.json](evidence/compose-platform-inventory-2026-09-07.json),
SHA-256 `ec9a5abb69cf7cde7b16d3c756aae0eeef322fef387425ca3b119b47a1f796c1`.
All 96 selected files were rehashed in both their template and materialized
stage locations: SOURCE 30 JARs; TARGET 65 JARs and `win_args.txt`. There was
no selected-platform hash drift. This was a platform audit, not a replacement
for the complete staged-run verifier.

| Role | Template root | Materialized stage root |
| --- | --- | --- |
| SOURCE | `build/logic-preserving-v1/source-server` | `build/composition-probe-runs/run-stage-source-20260908/source` |
| TARGET | `build/logic-preserving-v1/target-server` | `build/composition-probe-runs/run-stage-target-20260908/target` |

The existing stage plan/receipt hashes were rechecked and still agree with the
[session ledger](evidence/compose-sessions-2026-09-08.json):

| Role | `stage-plan.json` SHA-256 | `stage-receipt.json` SHA-256 |
| --- | --- | --- |
| SOURCE | `4261552c7ce30dc14b9d503ad4a732f7a635923e9aeee47c6c8321688f1ed25a` | `9348043af06d9158f3b6ea06a15642b540e952b79f3f747c4979400d97c57be1` |
| TARGET | `c9f0199d1f46fca3c631c8014aca9582db9edde5d78d0373032aabb390992d62` | `5f3eb7903b674a54259f6b4d3c114382c0e3cf873916dcbc5fd40698f19fd188` |

`CompositionRunPlanner.java` SHA-256 at inspection was
`bc1398ed97b7db9a8a90cfcfd5527df74767a59602ebf238e516f9ebe65cffd0`.
`sourceBootstrap` selects `-jar forge-1.16.5-36.2.42.jar`; `targetBootstrap`
expands the pinned 16-line Windows arguments into direct arguments without an
argument-file invocation. `auditManifestReferences` checks main-section
`Class-Path` references but does not interpret `META-INF/INDEX.LIST`. Its
manifest check must not be described as proof of all late native lookups.

All 95 selected JAR main manifests and index entries were inspected. Only the
SOURCE launcher declares `Class-Path`: all 26 entries resolve to selected files
relative to that launcher. No selected main manifest declares
`Launcher-Agent-Class`. SOURCE Mixin 0.8.4 and TARGET Mixin 0.8.5 declare
`Premain-Class` / `Agent-Class` for `MixinAgent`; those are not the launcher-agent
attribute. Existing multi-release metadata is retained. The helper's restrictive
manifest policy is not an appropriate native-platform policy.

## Exact index findings

Each selected index contains exactly one JAR-name entry. There are two SOURCE
indexes and ten TARGET indexes. The following three names do not identify their
containing archive; each sibling is absent in both the template and current
stage and is absent from the selected inventory.

| Role / containing archive | `INDEX.LIST:3` | Resolved sibling, relative to the role's root | Selected-route finding |
| --- | --- | --- | --- |
| SOURCE `server-1.16.5-20210115.111550-extra.jar` | `netty-all-4.1.25.Final.jar` | `libraries/net/minecraft/server/1.16.5-20210115.111550/netty-all-4.1.25.Final.jar` | Potential conditional lookup through JDK `URLClassPath`; sibling absent |
| SOURCE `nashorn-core-compat-15.1.1.1.jar` | `nashorn.jar` | `libraries/net/minecraftforge/nashorn-core-compat/15.1.1.1/nashorn.jar` | Potential conditional lookup through JDK `URLClassPath`; sibling absent |
| TARGET `nashorn-core-15.4.jar` | `nashorn.jar` | `libraries/org/openjdk/nashorn/nashorn-core/15.4/nashorn.jar` | Index not expanded by the inspected BootstrapLauncher/SecureJar module route |

| Containing archive | SHA-256 |
| --- | --- |
| SOURCE extra | `1892828be0713d0d2b2b8003103e97c9b28b37b916bea54bd18c72e3c18fb683` |
| SOURCE Nashorn compatibility | `244453e27ea4709223c1418723c6be1c1e47437fa06ececf2981c939f6c3cd50` |
| TARGET Nashorn | `6f816e84dfd63a81d4eaa7829c08337bbaff3ec683ff3bf6bbd90d017a00dc6f` |

The nine remaining TARGET indexes refer to their own existing, selected archive:
Netty 4.1.82.Final `buffer`, `codec`, `handler`, `resolver`, `transport`,
`transport-classes-epoll`, `transport-native-unix-common`, and the
`transport-native-epoll` classifiers `linux-x86_64` and `linux-aarch_64`.
Their exact paths and hashes are in the rehashed inventory. They introduce no
new sibling filenames. Their presence does not establish that a native library
is loaded on this Windows machine.

## Why SOURCE has a conditional late lookup

The selected source launcher has SHA-256
`dfeab9c7d582fce5b1112e9d3038fa0e7075296e467031ce1e2c0ba9fdcf441f`.
Its manifest directly places both indexed source archives on the application
classpath. Read-only `javap -p -c` of that exact launcher's
`net.minecraftforge.server.ServerMain` confirms calls to
`ClassLoader.getSystemClassLoader()` and `Class.forName` for the launcher and
Forge SPI, before dispatch to its runner. These entries are therefore part of
the selected native route, not merely files copied for an unused configuration.

The local Java 17 source establishes the conditional mechanism:

- `java.base/jdk/internal/loader/ClassLoaders.java:87-98` creates the application
  loader with `URLClassPath`; `BuiltinClassLoader.java:754-770` uses
  `ucp.getResource` for classpath class lookup.
- `URLClassPath.java:771-787` reads `JarIndex` and registers its relative URLs
  without opening each indexed sibling yet.
- `URLClassPath.java:950-964` first checks the containing JAR's actual entry.
  Only a direct miss can proceed to that JAR's index.
- `jdk/internal/util/jar/JarIndex.java:154-163` looks up the resource name or its
  immediate containing package. `URLClassPath.java:985-1008` resolves the indexed
  name against the containing archive URL and constructs another `JarLoader`.
- `URLClassPath.java:1026-1029` continues after a privileged I/O failure opening
  that sibling. A missing sibling is not itself proof that startup must abort.
  A successfully opened inconsistent index can instead fail validation at
  `1051-1053`.
- `URLClassPath.java:1088-1091` returns no manifest classpath when an index is
  present; `1115-1147` separately implements manifest URL resolution.

The source extra already contains
`io/netty/bootstrap/ServerBootstrap.class`; both Nashorn archives already contain
`org/openjdk/nashorn/api/scripting/NashornScriptEngineFactory.class`. Those exact
class entries do not need the stale sibling to be found by their containing JAR.
As a static counterexample to complete lookup closure, the source archives lack
`io/netty/bootstrap/__legacybridge_absent_lookup__.class` and
`org/openjdk/nashorn/api/scripting/__legacybridge_absent_lookup__.class`, while
their indexes map the respective packages to the stale siblings. A search that
reaches the corresponding JAR for such a name can attempt the sibling. These
illustrative names were only checked as absent ZIP entries; they were not passed
to a classloader and are not claimed to be game requests.

Thus the finite SOURCE issue is conditional reachability of two unselected local
sibling paths, not evidence of two missing required libraries. This audit does
not create those files, patch the indexes, suppress indexing or change Java.

## Why the same conclusion does not follow for TARGET

The pinned TARGET arguments have SHA-256
`659e71b783f09de7cfc05591472191b73ea5321864412ce63f49013c266781b8`.
They select eight module-path entries and a separate explicit
`-DlegacyClassPath=...` list. Nashorn is on that legacy list, not on the JDK
application classpath by virtue of this property.

`BootstrapLauncher` 1.1.2 source `BootstrapLauncher.java:40-88,93-141` reads that
list, applies the explicit ignore list, constructs `SecureJar` objects and a
`JarModuleFinder`, then creates `ModuleClassLoader("MC-BOOTSTRAP", ...)` and
loads the ModLauncher service in that layer. Its `181-205` code includes an
optional `legacyClassPath.file` override, but the selected arguments do not set
that property. Exact selected BootstrapLauncher bytecode independently confirms
the `SecureJar.from`, `JarModuleFinder.of`, `ModuleClassLoader` constructor and
layer/service calls. No launcher override was exercised or authorized here.

SecureJarHandler 2.1.10 provides the actual module resource route:

- `ModuleClassLoader.java:102-119,194-229` reads class bytes and resources through
  the selected module's reader or `jar.findFile`, with package-to-module lookup.
- `JarModuleFinder.java:69-76` delegates reader `find` / `open` to that JAR's
  module data provider.
- `jarhandling/impl/Jar.java:64-69,79-84,284-291` resolves the entry inside the
  union filesystem of the supplied archive paths and opens the resolved entry.
  Its multi-release path selection stays inside that filesystem. The exact
  selected `Jar.findFile` bytecode confirms this resolution route.
- ModLauncher 10.0.9 `TransformingClassLoader.java:30-50` extends
  `ModuleClassLoader`, also confirmed by exact selected bytecode.
  `ModuleLayerHandler.java:51-57,70-87` builds subsequent module layers and uses
  the BOOT module loader as fallback.

No index expansion occurs in those methods. The TARGET stale `nashorn.jar`
entry therefore is not an active sibling dependency for this inspected route.
This conclusion is specific to the selected BootstrapLauncher/SecureJar/
ModLauncher route. It does not prove that arbitrary later game/library code
cannot create another loader or explicitly open a path. No whole-program
reachability or operating-system file-access attestation was performed.

## Local primary-source pins

The JDK source archive is `C:/Program Files/Java/jdk-17/lib/src.zip`, SHA-256
`24fee80297ef15a886e283a31ac4eca18888ca8e942a21afb3f60a8535dc573b`.
The source archive itself was rehashed. It is implementation evidence for the
selected installation, not measurement of a future native process's Java version.

The following already-local source paths are relative to
`${DEVELOPER_HOME}/.gradle/caches/modules-2/files-2.1/`:

| Source archive path | SHA-256 |
| --- | --- |
| `cpw.mods/bootstraplauncher/1.1.2/3a8305ce392f55b6caae82468b75bcad2b14b6bd/bootstraplauncher-1.1.2-sources.jar` | `a72dd76c897b11d9c1555840a8f53b59c19b2201af5c8856c2502e23b919e669` |
| `cpw.mods/securejarhandler/2.1.10/96331802dca7d09fd109500a708200ae758ff412/securejarhandler-2.1.10-sources.jar` | `4f66b479d202582e0c61cb4f7489d754a2efc0e6b8d10ee9c6e603935b1cea13` |
| `cpw.mods/modlauncher/10.0.9/3a731130cea80690b50d02303e3a0bd5c2a808fc/modlauncher-10.0.9-sources.jar` | `aa4c68fd4094298969b95f67e3c84cbf345814e07c8bc36c7724f484573e230c` |
| `cpw.mods/modlauncher/8.1.3/8639a7c4b795b471b88573617fb8c78688109ce4/modlauncher-8.1.3-sources.jar` | `0def361f305a0927b636b1fa0e0b392b02a077b072cc9bc4b74aa83a5900ebdb` |
| `net.minecraftforge/fmlloader/1.20.1-47.4.23/f4a40c0acc54a5c0447f0402b5aa0f48efae3a83/fmlloader-1.20.1-47.4.23-sources.jar` | `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667` |

The exact selected TARGET binary hashes corroborating the route are:

| Binary | SHA-256 |
| --- | --- |
| BootstrapLauncher 1.1.2 | `853b61fd165caf4103b5b969afbc5b2b037abc310ed644518cd967b50d5189a4` |
| SecureJarHandler 2.1.10 | `7ec9207f47bc8847a16566b9a3b4ced8d073c49592897eb30ce2aaf643fdfcab` |
| ModLauncher 10.0.9 | `a9b3dcde1434d02702a251ef1d0671dbc90250b349778e1c27151d86c2a4aa81` |

## Remaining gates

The two SOURCE sibling lookups remain unqualified for actual native execution.
A future approved executor needs an explicit decision about these finite absent
paths and evidence for the selected source Java 17 run. An unsigned static
report cannot establish that no file was opened or no late lookup occurred.
All native library/mod/argument/receipt paths remain immutable; an output policy
must not silently admit these siblings as generated output.

SOURCE Forge 36.2.42 native startup on Java 17 remains unrun and is not established
by an earlier Java 8 run. Native authorization, process binding, bounded native
outputs, actual startup/save/close/exit/EOF observations and SOURCE/TARGET
behavior comparison remain separate gates. This audit supplies neither an
executor nor authority to arm a stage or run a game.

The native observation index still supplies no runs; its inspected SHA-256 is
`aa8162c3c1aa2f409ff8a668817feec095f429c9754105fd23b9d929b96fe399`.
Do not accept COMPOSE-01, unlock LINKAGE-GENERAL, expand compatible-mod claims,
or promote installation/UI from these static findings.
