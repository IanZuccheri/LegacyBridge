# COMPOSE-01: restricted configuration proposal (headless only)

Date: 2026-09-08. Contract: LB-08 / COMPOSE-01 remains **PARTIAL**.
SOURCE: Minecraft 1.16.5 / Forge 36.2.42. TARGET: Minecraft 1.20.1 /
Forge 47.4.23. No version, adapter ABI or conversion profile was changed.

This step implements a finite **proposal**, not a launcher, config installer or
successful native test. It runs in development-only `legacy-corpus`, excluded
from the public LegacyBridge JAR. No Minecraft, mod class, native file-config
watcher, repair routine, shell command from a plan or platform initializer runs.
The six existing profiles and original `simple-content-v1` remain intact.

## Bound contract

`CompositionLaunchConfigurationPlan.prepare(Snapshot, cancellation)` accepts
only a private-constructor verified unarmed stage snapshot. It rechecks the
current reviewed plan, original/candidate/platform/JDK pins, copied stage,
receipt, journal and directory tree before and after constructing the proposal.
All existing explicit stage properties must match the new proposed values;
conflicts fail instead of silently overriding a prior reviewed choice.

The immutable `Plan` contains authored text, byte lengths, SHA-256 digests,
expected native destinations and exact recipes for differences from native
defaults. Its canonical schema-1 digest also binds the stage plan/receipt,
versioned policy and rechecked selection. `recheck` reconstructs that same
proposal. Reports cannot be used as public selector credentials or receipts.

Result: `RESTRICTED_CONFIGURATION_PLANNED_ONLY` with
`runtimeValidation=NOT_RUN`. All of `materialized`, `executionAllowed`,
`installationAllowed`, `gameExecutionVerified`, `nativeLaunchContractVerified`,
`networkIsolationVerified` and `nativeSaveVerified` remain false. Invalid input
returns a bounded sanitized `COMPOSE_SETTINGS_*` error, without private content
or nested exceptions. Cancellation and interruption cannot return a partial plan.

This does **not** replace the original stage plan, receipt, layout policy or
default-config review. It never writes proposed settings into an existing stage.
Premature materialization invalidates that stage's strict unarmed admission.
Do not manually copy this proposal into the current reviewed stage and reuse
its old receipt. Native materialization requires a separately implemented,
reviewed contract and later explicit game authorization.

## Finite file routes and settings

| Proposed path | SOURCE | TARGET | Expected native consumer/output |
| --- | --- | --- | --- |
| `server.properties` | 50 active keys | 56 active keys | Same path |
| `config/fml.toml` | 4 keys | 14 keys | Same path |
| `config/forge-common.toml` | 1 key | Rejected (unregistered empty spec) | Same path |
| `defaultconfigs/forge-server.toml` | 10 keys | 8 keys | `world/serverconfig/forge-server.toml` |

The server template route avoids precreating `world/`. Both exact Forge
versions copy `defaultconfigs/<registered filename>` only when the registered
destination is absent. Native reading, correction, watchers and save occur
afterwards. The verified unarmed tree has no existing world or configs and
`defaultConfigPath` remains `defaultconfigs`. Template presence is not proof
that Forge consumed it; no destination file is fabricated by this implementation.

All active properties are explicitly authored, including defaults, so no
unknown or additional known property can inherit approval. The old 52/58 key
layout vocabulary included `announce-player-achievements` and
`resource-pack-hash`; native `getLegacy` consumes/removes them. They are
rejected by this new exact 50/56-value contract, not silently removed.

Intentional settings include loopback ports 25597/25598, one whitelisted slot,
authentication retained, query/RCON/JMX/status disabled, no resource-pack URL,
blank text filtering, no structures or animal/monster/NPC spawning, Java
transport, fixed seed 1 and small view/simulation distances. The native 60s
watchdog is retained; it can force process exit/halt, so it is not a supervisor
timeout or successful shutdown proof. Empty player lists and zero-player
observations remain separate requirements. CLI overrides and mod side effects
are not authorized by these settings.

FML changes are exactly `versionCheck: true -> false` and
`maxThreads: -1 -> 1` on both roles. TARGET also changes
`server.advertiseDedicatedServerToLan: true -> false`. All other audited TOML
defaults are retained. SOURCE common default world type stays `default`.
Twenty SOURCE / twenty-two TARGET exact recipes record `path`, `key`, `before`,
`after`, `rule`, `confidence=VERIFIED`, and `reason`. This confidence applies
only to the independently audited finite QA setting recipe, not generic mapping
confidence, conversion compatibility or actual execution. No HIGH is promoted.
No mod/resource transformation occurs, so the conversion `TransformationLog`
and `CompatibilityReport` schemas are unchanged.

## Parsing and semantic limits

`CompositionRestrictedSettings` reuses the existing bounded properties parser
and finite TOML lexer, supplying closed immutable expected schemas. Both proposed
and generated-file validators compare decoded keys and exact values. Comments,
order and supported native whitespace/Properties escapes are allowed; decoded
duplicates, unknown/missing/changed values and cross-role/phase paths fail closed.
Per-file budget is 64 KiB (at most four proposed files); properties retain the
64-key, 64-character key and 8,192-character value bounds. TOML retains 512 lines,
4,096 characters per line, strict UTF-8 and the prior finite dialect.

SOURCE properties use ISO-8859-1. TARGET is deliberately strict UTF-8 here;
the actual target loader's Latin-1 fallback for malformed UTF-8 is **not**
accepted. This is a supported-dialect restriction, not a claim to reproduce
every valid native configuration. Native silent repairs/defaulting are not
emulated. The existing default-only reviewer still rejects these nondefault
FML/LAN values and remains a distinct policy.

SOURCE treats seed zero as random; seed 1 removes that specific ambiguity,
not version-to-version terrain differences. SOURCE generator settings are empty;
TARGET's normalized default is `{}`. SOURCE `default` and TARGET
`minecraft:normal` select each version's native world route, not equivalent
world generation. SOURCE stores `snooper-enabled` but its runtime field is
already forced false; pinning the stored flag false does not invent a toggle.
SOURCE text filtering is null; TARGET's blank setting selects null through its
own factory. TARGET's initial enabled `vanilla` pack still permits Forge's
native mod-pack insertion. No safe-mode or data-removal workaround is added.

The original fixture's support furnace still requires an already loaded air
position at spawn Y=200. These proposed settings do not prove that prerequisite,
recipe loading, save/load, class linkage, source Java 17 startup or behavior.
Pure generated-file validators are not a bound post-native review: mutable
properties/config/world/log/observation content still needs a new integrated
post-save contract without weakening the old strict unarmed verifier.

## Restricted services are NOT network isolation

The previous checkpoint's phrase "no-network configuration" described a desired
gate, not an implemented or achievable config-only guarantee. This continuation
corrects that boundary explicitly. Forge's global version flag returns before
its HTTP check work; its checker thread can still be created. `maxThreads=1`
restricts the mod-loading ForkJoinPool only, not Minecraft or process-wide threads.
TARGET's LAN flag disables that pinger, not all network access.

In the exact TARGET authlib 4.0.43, server startup constructs
`YggdrasilAuthenticationService`, which obtains service keys and schedules a
fetch immediately with a 24-hour repeat. This happens independently of accepting
players and outside Forge version checking. Loopback/whitelist, or disabling
online mode, would not prove offline/no-egress operation. No authentication
flags, security settings, OS firewall or platform classes were changed.
OS/process containment, late lookup closure and actual no-egress observation
remain separate pending gates. No universal launcher restart API is introduced.

## Exact source provenance

Read-only source ZIP reads and `javap` on already available pinned artifacts;
no inspected class was initialized. Finite authored policy facts/tests only:
no game/Forge code, binaries, private settings or old mappings are redistributed.

- SOURCE mapped sources SHA-256:
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
  `ServerProperties.java:13-41,65-95`; `PropertyManager.java:28-44,75-95,139-149`;
  `DimensionGeneratorSettings.java:184-233`; `ForgeHooks.java:919-924`.
- TARGET mapped sources SHA-256:
  `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
  `DedicatedServerProperties.java:49-118,142-173`; `Settings.java:34-60,91-112,155-165`;
  `WorldOptions.java:66-79`; `DedicatedServer.java:196-198` (LAN).
- SOURCE launcher SHA-256:
  `dfeab9c7d582fce5b1112e9d3038fa0e7075296e467031ce1e2c0ba9fdcf441f`.
  `FMLConfig.class` SHA-256:
  `4cd82eba6dac99423662a99b1b92a5c7030a09f8277d73634505363aecd4b69b`.
- TARGET fmlloader source SHA-256:
  `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`.
  FML configuration definitions and `ImmediateWindowHandler:33-35` client-only
  provider routing. Keeping early-window defaults does not authorize any window.
- TARGET fmlcore source SHA-256:
  `fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1`.
  Both `ConfigFileTypeHandler:29-43,69-79`; `ServerLifecycleHooks` SOURCE63-80 /
  TARGET74-96; `VersionChecker` SOURCE122-125 / TARGET110-116;
  `ModWorkManager` SOURCE96-98 / TARGET97-99.
- TARGET authlib JAR SHA-256:
  `697043d19e0b84b04f011dbec1bec3d80c04d8c468e7e4c6b221c0183bc1c0ef`.
  `YggdrasilAuthenticationService` constructor bytecode41-61 calls service keys;
  `YggdrasilServicesKeyInfo.get` bytecode19-38 schedules fetch with initial delay0.

## Developer command and next gate

Opt-in `:legacy-corpus:planCompositionSettings` prints a proposal only, using
the same five explicit selectors as `verifyCompositionStage`:

```powershell
.\gradlew.bat :legacy-corpus:planCompositionSettings `
  -PcompositionRunRole=SOURCE `
  -PcompositionRunId=<reviewed-run-id> `
  '-PcompositionJavaHome=<exact-reviewed-Java-home>' `
  -PcompositionReviewedPlanSha256=<reviewed-plan-sha256> `
  -PcompositionReviewedReceiptSha256=<reviewed-receipt-sha256> `
  --no-daemon --max-workers=1
```

It is not part of normal `check`, not reachable from CLI conversion/UI, and
never uses stored native argv. Headless test/build and actual unarmed stage
proposal evidence are recorded separately in the
[continuation ledger](evidence/compose-restricted-settings-2026-09-08.json).
The next eligible headless work in **the same COMPOSE-01** is a bound restricted
settings materialization/recovery contract and post-save content verification,
including necessary world/log/observation checks. Native launch, source-Java17,
original/target comparison and acceptance still need separate authorization and
evidence. Do not advance LINKAGE-GENERAL, installation/UI or acquisition gates.
