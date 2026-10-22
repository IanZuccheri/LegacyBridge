# Native TARGET composition probe

Developer-only Forge 47.4.23 / Minecraft 1.20.1 / Java 17 QA artifact, never a
public/player dependency. Requires the exact reviewed candidate and runtime for
future native QA, not during static tests. See the
[guarded capture contract and launch limitations](../../docs/COMPOSITION_PROBES.md).

Build/test from the repository root, after the source build finishes:

```powershell
.\gradlew.bat -p fixtures/composition-runtime-probe-1.20.1 build --no-daemon --max-workers=1
```

Native execution remains NOT_RUN and needs separate authorization and the pending
disposable launch/exit collector. Never install this probe in a player profile.
