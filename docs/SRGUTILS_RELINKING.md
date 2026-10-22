# LB-03: local SRG Utils rebuild and relinking

## Current license decision 2026-10-22

The rights holder selected GPL-3.0-only with the explicit Minecraft/Forge
linking permission for current LegacyBridge code. See [LICENSING.md](../LICENSING.md)
and [LINKING_EXCEPTION.md](../LINKING_EXCEPTION.md). The earlier owner-term
decision is resolved; no 5% revenue share or mandatory fee applies. SRG Utils
retains its LGPL-2.1-only terms, sources, notices and rebuild/replacement route.

The implementation and evidence below describe the 2026-09-12 technical
experiment. References to an unchanged proprietary LICENSE and an outstanding
owner choice are historical, not restrictions on the current source grant.
Native Forge loading of a modified library remains unqualified; relicensing
does not turn the recorded headless checks into native acceptance. Preserve
the application source, dependency sources and build/relink materials with the
release as described in [the current rights record](PACKAGING_RIGHTS.md).

## Historical technical experiment

2026-09-12, `PACKAGE-RIGHTS / TECHNICAL_RELINK_V1`. Developer-only tooling is
implemented in `legacy-corpus`; the public LegacyBridge JAR, engine, adapters,
conversion profiles and project LICENSE are unchanged. This is a technical
experiment, not rights clearance or a new way to transform Minecraft/Forge.
The edited dependency is the independent SRG Utils library, not platform/game
classes or an original mod. Nothing is installed or launched in Minecraft.

## What is implemented

`dev.legacybridge.corpus.relink.SrgRelinkTool` exposes three explicit operations:

1. `prepare <product.jar> <reviewed-sha256> <new-kit>` verifies the selected
   artifact, exact included SRG Utils 0.6.2 binary/sources/license, runtime
   version and Jar-in-Jar identity, then exports the eight preferred Java
   source files and original binary/source/license into a **new** directory.
2. `build <kit> <annotations-24.1.0.jar> <new-build>` snapshots those eight
   sources, compiles with the Java 17 JDK compiler using `--release 8`, `-g`,
   UTF-8, `-proc:none` and `-implicit:none`, and produces a local library,
   its source archive and a versioned report. The compile classpath contains
   only the explicitly selected, pinned annotation library. No processor,
   developer classpath discovery, Gradle lookup or download is enabled.
3. `replace <product.jar> <reviewed-sha256> <build-dir> <reviewed-library-sha256>
   <new-copy>` verifies input hashes, module identity, source binding and the
   static declaration/ASM gate. It creates `LegacyBridge-relinked-PRIVATE.jar`
   and its report in a new directory. It neither replaces the public build
   nor installs the private copy in a player's mods folder.

Every successful report is `LEGACY_SRG_RELINK`, schema 1, with recipe
`srgutils-0.6.2-javac17-release8-v1`. Reports explicitly retain
`installationAllowed=false`, `publicReleaseApproved=false`,
`forgeLoading=NOT_RUN` and `rightsClearance=REVIEW_REQUIRED`.
Compilation/replacement does not execute the library or mod classes. Separate
tests execute only the reviewed library and engine, never the input/output mods.

## Exact inputs and source correspondence

| Input | SHA-256 |
| --- | --- |
| Published SRG Utils 0.6.2 binary | `f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e` |
| Its published eight-file source archive | `3f0c9f692d6243ef6c854408aa93b47ccb77343cc6d80d8a5d85c16b46f93dec` |
| Included unmodified LGPL 2.1 text | `20c17d8b8c48a600800dfd14f95d5cb9ff47066a9641ddeab48dc54aec96e331` |
| Compile-only JetBrains annotations 24.1.0 | `27a770dc7ce50500918bb8c3c0660c98290630ec796b5e3cf6b90f403b3033c6` |

All sources came from the exact archive already inside LegacyBridge, not a
moving Git branch or a decompilation. `org.jetbrains.annotations.Nullable` is
actually imported by these sources; no annotation stub was invented. The
locally available annotations 24.1.0 POM declares Apache-2.0. It is a separately
selected compile-only input, **not packaged** in the rebuilt library/public mod.
No new artifact acquisition was performed.

This is an **independent rebuild recipe**, not a claim to reproduce the upstream
release build byte for byte. Upstream's current build specifies Java 8 but is
not an attestation for the 0.6.2 release; its current main-class settings even
differ from the exact published manifest. See the
[upstream build](https://raw.githubusercontent.com/MinecraftForge/SrgUtils/master/build.gradle).
The exact unmodified source release builds into 41 library classes here.
Reports distinguish declaration/ASM success, finite behavior checks and
`upstreamBuildReproduced=false`.

The rebuilt source archive includes all eight selected source files, LGPL text
and `RELINK-BUILD.json`: per-source hashes, modified-file list, compiler version,
options and dependency hash. The library manifest binds its SHA-256 and marks
`LegacyBridge-Local-Rebuild: true`. Modified sources must retain the upstream
copyright/SPDX notice and include a dated change notice, for example:

```java
// LegacyBridge local modification: 2026-09-12 - explain this local change.
```

That syntactic check is not legal review. The executable build/relink tool's
preferred source is in `legacy-corpus/src/main/java/dev/legacybridge/corpus/relink/`
and its developer sources JAR; a JSON recipe alone is not claimed to fulfill
all corresponding-source/build-material obligations. Those materials and the
project terms must be included in the chosen reviewed release arrangement.

## Running without an IDE or Gradle cache

Prepare the developer distribution once, serially, using the project Java17
build (`:legacy-corpus:installDist`). With copied distribution libraries, the
public JAR and the explicit annotation input, the following needs only JDK17:

```text
java -Xmx512m -cp "lib/*" dev.legacybridge.corpus.relink.SrgRelinkTool prepare LegacyBridge.jar <product-sha256> new-kit
java -Xmx512m -cp "lib/*" dev.legacybridge.corpus.relink.SrgRelinkTool build new-kit annotations.jar new-build
java -Xmx512m -cp "lib/*" dev.legacybridge.corpus.relink.SrgRelinkTool replace LegacyBridge.jar <product-sha256> new-build <library-sha256-from-build> new-copy
```

Parents must exist; output directories must not. All paths refer to explicit
local inputs. Edit only your exported source kit, not the embedded public JAR.
Repeated builds use separate output directories. The library's package/module
identity and Jar-in-Jar coordinate/range stay unchanged for this private test:
`net.minecraftforge.srgutils`, `net.minecraftforge:srgutils:0.6.2`,
`[0.6.2,0.6.3)`. Do not distribute these local builds as upstream originals.

As a development alternative, `:legacy-corpus:relinkLibrary --args='...'`
invokes the same tool. No additional LegacyBridge mod JAR is required by players.
The tool, annotation dependency and fixtures are not public-mod contents.

## Safety and static checks

Inputs are bounded regular-file snapshots with hash pins, real-path/alias
checks and revalidation before copy publication. ZIP checks reject unsafe names,
case aliases, duplicates, central/local CRC/count inconsistencies, truncation,
multi-disk/ZIP64 and excessive size/counts. This is a finite developer format,
not a general archive repair tool. Bounds: 32 MiB archive/total output, 2 MiB
per inflated entry, 4,096 public-archive entries, 128 library entries and the
eight explicit source paths (512 KiB each). Compiler diagnostics are bounded.

Work occurs in a newly owned sibling temporary directory. Publication is a
no-replace move; ordinary failures clean only that invocation's stage. Existing
outputs and racing foreign files are preserved. This is not an installer,
durable journal, hostile-filesystem sandbox or guarantee against sudden power
loss; crash leftovers are not approved outputs. No persistent game files,
tokens, launcher arguments or live loaded JARs are touched.

The static library gate uses the existing offline `BytecodeValidator`, without
class definition, and checks class identity/version, hierarchy and existing
public/protected field/method descriptors/access/declared exceptions. All 41
original class declarations must remain. This does not prove arbitrary library
semantics, reflection, package-private contracts or full external linkage.

Replacement changes exactly the nested library, its corresponding source
archive and the third-party notice (the original text is retained under an
explicit historical-baseline heading). It adds `RELINK.json`. **583 unrelated
entry payloads remain byte-identical**, including application classes, runtime
identity, metadata/range, adapters, conversion rules and included data. ZIP
serialization changes the enclosing file hash; that is not a change to those
entry payloads.

Forge's Jar-in-Jar performs dependency range negotiation. A plain Java loader
test does not prove its selection or JPMS behavior with another modpack.
[ForgeGradle Jar-in-Jar documentation](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/).

## Evidence and remaining gates

[Versioned evidence](evidence/relink-2026-09-12.json) records exact hashes,
commands, failures/corrections and validation dimensions. Twenty focused tests
exercise prepare/build/replace, repeatability, corrupt/hash-changed inputs,
missing/changed ABI, unsafe ZIP paths, CRC/size limits, missing source notices,
compiler errors and write/overwrite refusal. A first compilation missed the
existing JSON parser's checked exception; that was corrected and rebuilt.

The private modified-library test changes only a dedicated `remapClass` sentinel
in the actual source. Its changed return is observed in a separate library
classloader; a normal mapping still resolves identically. The ordinary and
private-copy **public-JAR engines** are then loaded without a parent application
classpath or Forge/game classes. The simple fixture produces the same canonical
report and bytes; a corrupt JAR is refused with `MALFORMED_ZIP`, without output.
Original/converted mod classes never enter these classpaths. This is stronger
than an archive-only check, but not native Forge discovery or gameplay.

An initial independent CLI rebuild exposed cross-JVM `Map.of` iteration order
in the nested provenance report. The report is now sorted before hashing; this
is a reproducibility fix, not a mapping-rule change. Fresh-process comparison
is recorded separately from same-process repeated builds.

The corrected installed CLI passed all three operations in fresh Java processes
outside the project. Copied explicit inputs plus empty user/Gradle/temp homes
were sufficient; those homes and owned scratch remained empty afterwards.
The baseline rebuilt library/source hashes exactly matched the focused test's
baseline. This is not a network-denial sandbox, clean-machine acquisition test
or an offline Minecraft claim. Both used the same JDK/OS/timezone.
Closeout also passed 75 contract checks, with two unavailable native-symlink
privilege checks skipped; module assembly and package/CLI audits passed again.

The current public JAR remains
`76189bcfb81012d1bce3f63c2b43a819a1ef1473b357d35df295e570c3f39c22`.
Native client/server, actual Jar-in-Jar negotiation, gameplay and multiplayer
of a rebuilt dependency are **NOT_RUN**. Hard memory isolation is not provided.
No public artifact or license was changed and nothing was published.

## Historical rights-holder decision

`PACKAGE-RIGHTS` remains BLOCKED for terms/release review, not because this
technical route must be implemented again. The current project LICENSE does
not grant modification/distribution permission. Choose a direction before any
terms change: retain proprietary terms with appropriately reviewed own-use/
library-replacement/debugging permissions, or consider an open-source license.
Neither direction is applied automatically or guaranteed compliant by a build.
The relevant LGPL conditions, source/build materials and any chosen linking
route still need review. See the
[library's complete upstream LGPL text](https://raw.githubusercontent.com/MinecraftForge/SrgUtils/master/LICENSE.txt)
and [packaging-rights checklist](PACKAGING_RIGHTS.md). This is an engineering
record, not legal advice or a three-year source offer made on the owner's behalf.
