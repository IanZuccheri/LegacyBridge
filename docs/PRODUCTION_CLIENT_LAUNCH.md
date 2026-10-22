# Ordinary Forge client validation launcher

These are **development validation scripts**, not part of `LegacyBridge.jar`,
not a player launcher, and not an automatic restart implementation. They own
only `build/p3-production/client`, or the explicitly selected
`-ValidationRun p4-ui` instance at `build/p4-ui/client`. They never read real launcher accounts,
credentials or `launcher_profiles.json`, and never modify an existing profile.

The source of game files is the user's existing local vanilla installation,
with every copied JAR/asset checked against official manifest SHA-1 values.
Copies remain private validation inputs; they are not redistributable release
assets. Missing files require the explicit `-AllowDownloads` preparation flag.
There is no download operation in the launch script.

## Installation and exact inputs

The ordinary official `forge-1.20.1-47.4.23-installer.jar` is required at
`build/p3-production/setup`. The script does not fetch the Forge installer.
Its `--installClient` workflow creates the normal patched client libraries and
`versions/1.20.1-forge-47.4.23/1.20.1-forge-47.4.23.json`; it is not ForgeGradle,
`runClient`, a userdev launch target, or a development classpath.

The disposable installation owns its empty `{"profiles":{}}` profile file.
Only the official installer may add its normal profile to that owned file.
No profiles, accounts or launch arguments are imported from the real launcher.

The vanilla metadata selected from Mojang's
[official version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
on 2026-09-05 is pinned in the preparation script:

- Minecraft version: `1.20.1`.
- [Vanilla version metadata](https://piston-meta.mojang.com/v1/packages/19f5ae58f9c31bd3b0923cb822e99e3162bd62ab/1.20.1.json)
  SHA-1: `19f5ae58f9c31bd3b0923cb822e99e3162bd62ab`.
- The client JAR hash and asset-index hash come from that verified metadata.
- Every selected vanilla/Forge launcher library is checked against its declared
  artifact SHA-1, including Windows native-resource JARs.
- Every unique object in the verified asset index is copied or explicitly
  downloaded, then hash checked. `preparation.json` records counts and anchors.

The pre-existing local vanilla JSON had another hash, so it was not accepted as
the exact manifest. The original local JSON/JAR were left untouched; only the
owned installation receives the verified official JSON and verified JAR copy.

```powershell
# Requires explicit consent to the installer's network requests and any missing files.
.\scripts\Prepare-ProductionClient.ps1 -RunInstaller -AllowDownloads

# After an already successful official installation: fill/verify only local assets/libs.
.\scripts\Prepare-ProductionClient.ps1 -AllowDownloads

# Re-verification with no script downloads allowed.
.\scripts\Prepare-ProductionClient.ps1
```

The preparation script defaults to the conventional `%APPDATA%/.minecraft`
source, or accepts `-VanillaDirectory` explicitly. It reads only the selected
version JSON/JAR, required library JARs and asset index/objects. It performs no
scan of accounts, launcher settings, worlds or mods.

## Classpath and module-layer composition

The launch script merges the inherited vanilla library declarations with the
installed Forge declarations; child coordinates override the inherited
version of the same group/artifact/classifier. It evaluates ordinary Windows
OS/feature rules, expands the installed Forge arguments, and uses
`cpw.mods.bootstraplauncher.BootstrapLauncher` with `--launchTarget forgeclient`.
The module-path, ignore-list, module merges, opens and exports come from the
official installed Forge version JSON, not a mod-defined bootstrap service.

The inherited obfuscated vanilla `versions/1.20.1/1.20.1.jar` is verified and
available for official installation, but is **not added as a bootstrap library**.
The pinned real Forge sources establish why:

- `CommonClientLaunchHandler.getMinecraftPaths` loads the installer-produced
  `net.minecraft:client:<Minecraft-MCP>:srg` and `:extra` artifacts.
- `ForgeClientLaunchHandler` additionally supplies the installed
  `net.minecraftforge:forge:<Minecraft-Forge>:client` and `:universal` artifacts.
- `BootstrapLauncher` 1.1.2 filters its ignore list by the actual filename
  prefix. A blindly inherited `1.20.1.jar` would not match the child profile's
  `${version_name}.jar` ignore token and could expose obfuscated classes as an
  extra library module. It is therefore excluded from the effective bootstrap
  classpath, rather than repurposed as the game's implementation.

Only installer/manifest-selected normal libraries enter this launch. The
scripts reject a launch-argument string containing `forgeclientuserdev`,
`forge_gradle` or a `.gradle` path. `user.home` points to the installation's
`empty-user-home`; application temporaries and native-library extraction also
stay in the owned instance. No developer cache is added to its module path.

## Launch is a separate, explicit operation

The start script's default only writes `launch-arguments.txt`; it does not start
a process. The generated file contains only the fixed `OfflineProbe` identity,
all-zero UUID and dummy access token `0`, never copied authentication data.
This is suitable for local title-screen/engine validation, not authenticated
multiplayer or an account-login test. Window size is `1280x720`.

```powershell
$probeArguments = @(
    '-Dlegacybridge.product.probe=true',
    '-Dlegacybridge.product.probe.side=client',
    '-Dlegacybridge.product.probe.timeoutSeconds=180',
    '-Xlog:class+load=info:file=client-classloading.log'
)

# Review only; no Minecraft process is started.
.\scripts\Start-ProductionClient.ps1 -JvmArgument $probeArguments

# Run only when no other project game validation is active and launch is authorized.
.\scripts\Start-ProductionClient.ps1 -JvmArgument $probeArguments -Launch
```

Before explicit launch, the validation coordinator must independently stage the
final public artifact, the **separate, nonpublic** validation harness, fixture
and explicit local mapping bundle. These scripts never copy a harness or mod
into `mods` on the caller's behalf. Java process PID and own stdout/stderr logs
are reported; the helper console is hidden. The Minecraft client window itself
is an intentional part of the authorized client validation.

Java 17 is required. Windows root-store configuration is scoped to this Java
process; TLS certificate verification remains enabled. External Java-option
environment variables are cleared for launch and restored afterward.

## Evidence and limits

Successful preparation proves installation and hash-checked local dependencies,
not mod discovery, title-screen behavior, conversion, assets, gameplay or restart.
The script never claims those runtime outcomes. Read the separate artifact-bound
production validation results for actual client/server evidence.

The scripts are intentionally Windows AMD64 and pinned-version validation
helpers. They are not a general launcher-version resolver. Asset-index changes,
another platform/version, authenticated launch, arbitrary third-party launcher
arguments and launcher-specific restart integrations require separate work.
Missing/hash-mismatched artifacts fail explicitly; unrelated files are not
overwritten or deleted to make preparation pass. Concurrent hostile replacement
of owned filesystem paths is outside this local validation script's sandbox.
