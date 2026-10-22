# Third-party notices

The GPL grant for original LegacyBridge work does not replace these licenses.

- Gradle 8.8 wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`):
  Gradle contributors, Apache License 2.0. Existing file notices are retained;
  the full text is in `licenses/Apache-2.0.txt`. Upstream source:
  https://github.com/gradle/gradle/tree/v8.8.0 .
- SRG Utils 0.6.2: Forge Development LLC and contributors, LGPL-2.1-only.
  The public JAR contains the unchanged dependency, published source archive
  and full LGPL text. Build/replacement source and instructions are in
  `legacy-corpus` and `docs/SRGUTILS_RELINKING.md`.
- Included finite source contracts retain MCPConfig and Forge notices at
  `legacy-converter/src/main/resources/META-INF/legacybridge/`.
  This is not permission to redistribute full Mojang/MCP mapping inventories.
- Public corpus metadata retains the identified authors' licenses in
  `corpus/licenses`. No third-party mod binary is supplied.

See `legacy-product/src/main/resources/META-INF/legacybridge/legal/THIRD_PARTY_NOTICES.md`
for exact dependency coordinates, source hashes and platform-provided libraries.
Local developer caches, games, private fixtures, credentials and complete API
or mapping exports are not part of the public source snapshot.
