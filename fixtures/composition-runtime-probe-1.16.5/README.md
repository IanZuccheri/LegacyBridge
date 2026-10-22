# Native SOURCE composition probe

Developer-only Forge 36.2.42 / Minecraft 1.16.5 QA artifact, never a public/player
dependency. Compiled to Java 8 classfiles; the reviewed QA environment is Java 17.
See [guarded capture contract and launch limitations](../../docs/COMPOSITION_PROBES.md).

Build/test from the repository root, with no game launch:

```powershell
.\gradlew.bat -p fixtures/composition-runtime-probe-1.16.5 build --no-daemon --max-workers=1
```

Native execution remains NOT_RUN and needs separate authorization and the pending
disposable launch/exit collector. Never install this probe in a player profile.
