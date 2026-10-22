# Original LegacyBridge CLIENT config fixture

Authored test input under the repository license; no third-party mod is included.
Independent Forge 36.2.42 / Minecraft 1.16.5 build, Java 8 bytecode with JDK 17.
Not included in the public product artifact.

Four classes, one item, one smelting recipe and a closed local ingredient tag.
The item is registered on both physical sides. Config registration/listener are
guarded by FMLEnvironment.dist == CLIENT. Native boolean/int config defaults:
enabled=true, factor=3, range=1..8. Only queued FMLClientSetupEvent work reads them.
Private observations count loads and retain a startup snapshot.

This is not a reload or networking example. It contains no game client calls,
no native test harness and no static-conversion execution seam.
All runtime oracles remain EXPECTED_NOT_EXECUTED.

Implementation, APIs, static proof and native QA backlog:
[CLIENT_CONFIG_SCOPE](../../docs/CLIENT_CONFIG_SCOPE.md).

Build from the repository root, serially:
`gradlew.bat -p fixtures/client-config-1.16.5 build --max-workers=1`.
Use the project Windows-ROOT TLS flags where required; do not disable TLS checks.
