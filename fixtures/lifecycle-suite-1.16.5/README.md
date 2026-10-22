# Integrated LB-09 fixture (original project-owned source)

Development-only Forge 36.2.42 / Minecraft 1.16.5 fixture, Java 8 bytecode.
Never distributed inside LegacyBridge.jar. No third-party mod was acquired.

Seven owned classes combine one native deferred item, late RegistryObject.get in
queued common setup, two HIGH/LOW listeners for an owned cancelable event,
COMMON boolean/integer configuration with Loading/Reloading callbacks filtered
by exact spec identity and type, and a Forge command subscriber.

Commands: /bridgesuite scale <value:0..100>, /bridgesuite flag <true|false>.
Native permission level 2; only owned integer counters/results change. The scale
handler reads current native config values. Each command registration callback
creates a fresh tree from its supplied dispatcher; no dispatcher is cached.
The config observations are volatile individual fields, NOT an atomic snapshot.
Loading/reloading keep their native calling thread; there is no world access.

Expected (not executed in a game): setup observes the registered item; the event
trace is 12 and post returns cancelled; initial config is enabled=true/factor=3;
a matching reload updates observations and the reload counter, whereas unrelated
specs/types cause no reads or writes. A scale of 4 returns 12 by default and 0
when disabled. Original/converted game comparison remains NOT_RUN.

Build from repository root (serially):
    gradlew -p fixtures/lifecycle-suite-1.16.5 build --max-workers=1

Private service: LegacyEngineService.planLifecycleSuite with the existing
LIFECYCLE_PRESERVING_V1 request envelope and explicit complete API/mapping data.
Returns proof/hash, never a publicly installable JAR. All six public grammars
reject this combination. No input class is loaded or executed by the engine.

The separate ../lifecycle-suite-api/BrigadierSuiteProbe.java tests actual
Brigadier APIs in isolated JVMs WITHOUT Forge, Minecraft, or any fixture mod
classes. That probe is not evidence of game lifecycle or console/player behavior.
