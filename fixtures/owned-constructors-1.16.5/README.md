# Authored owned-constructor fixture 1.0.0

Original test sources, Forge 36.2.42 / Minecraft 1.16.5, Java 8 classfiles built
using JDK 17 and ForgeGradle 6.0.54. No third-party mod was downloaded.
Local fixture use follows the repository task; metadata is not a grant to
redistribute repository binaries. Do not include this fixture in LegacyBridge.jar.

`ConstructorContent` registers `legacybridge_constructors:count_token` through
an owned constructor supplier. `DelegatingItem()` delegates to `(int)`, then
`(int,int)`, then the native Item constructor. Parameter 16 reaches stacksTo;
seed 3 and a per-instance CountRule are retained. Unwinding writes trace 1→12→123.
The override retains vanilla `super.isFoil` short-circuit semantics; otherwise
`3 * count + 1` being even produces foil. The helper counter increments only
when evaluated. These are expected observations, not executed native evidence.

Version pin: original reobfuscated JAR SHA-256
`d55473d5e666808a0bb1aae6067149d0d23b03c7e3f8644bba14d8daee381681`.
Private serialized candidate SHA-256
`1915061842d47720f6c38b4bbb9693470266a83e62c7f49e020f3b3690a90e5b`.
The candidate is validated/read back/discarded, not installed or exported.
Client/server/behavior/multiplayer: NOT_RUN. Every current conversion profile
must reject this fixture; only the separate private inspection admits it.

From the repository root, build serially with its wrapper:
`gradlew.bat -p fixtures/owned-constructors-1.16.5 build --max-workers=1`.
Opt-in integration tests require `-PverifyCompositionEnvironment=true` and
`-PlogicMappingProfile=<absolute explicit local properties>`.
Full contract and limitations: [constructor proof](../../docs/OWNED_CONSTRUCTOR_DELEGATION.md).
