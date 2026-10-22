# Genuine inferred-listener variant

This is one source variant of `lifecycle-content-1.16.5`, not a new real mod or an
additional independently supported mod. It retains modId `legacybridge_lifecycle`,
version 1.0.0 and every registry/resource/config/command identity. **Never load both
variants together:** they intentionally declare the same modId.

Only this project's `LifecycleContent.java` is local. Its constructor uses the
real Forge 36 Consumer-only listener overload:

```java
FMLJavaModLoadingContext.get().getModEventBus().addListener(
        (Consumer<FMLCommonSetupEvent>) LifecycleContent::commonSetup);
```

Unlike the explicit-class-token baseline, Forge's native listener registration
must infer the event type. This must be validated separately in the original and
converted native runtimes; finding an equivalent descriptor alone is not proof
that generic inference still works. No explicit token is substituted in the
original source merely to improve conversion results.

Gradle reuses the baseline's other three owned classes, resources and five archive
tests through source directories, excluding only its entrypoint by exact canonical
file identity. This variant adds one archive-only check for the Consumer descriptor
and absence of the explicit four-argument listener descriptor. No original fixture
class is loaded by those tests. Shared source changes affect the next build of both
variants; keep exact input hashes in separate runtime receipts.

Versions remain ForgeGradle 6.0.54, Forge 36.2.42, Minecraft official mappings
1.16.5, Java 17 compiler with `--release 8`, followed by production reobfuscation.
From the LegacyBridge root, build serially:

```text
gradlew -p fixtures/lifecycle-inferred-1.16.5 build --no-daemon --max-workers=1
```

Output: `build/libs/legacybridge-lifecycle-inferred-1.16.5-1.0.0.jar` inside this
project. This README does not claim that build, conversion or native execution has
already passed. The native lifecycle probes can observe either variant because
the controlled public observation signatures are identical, but each run must use
a fresh isolated directory and pin this exact original and its own converted JAR.
The current Default/Configured/InvalidConfig runtime scripts pin the baseline
artifact path and must not silently be reused for this different source input.

COMMON scope remains unsynchronized; no CLIENT/SERVER configs, live reload,
arbitrary listener inference, client visuals or multiplayer compatibility is
claimed. No fixture binaries, game bytecode, mapping dumps or probes are embedded
in the public product. All Rights Reserved remains project-local, without a public
fixture binary redistribution grant. See the baseline fixture README for the shared
item/config/command oracle and the separate API baseline document.
