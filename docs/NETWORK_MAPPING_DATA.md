# Networking: explicit local mapping inputs

Prepared 2026-09-06 for `network-snapshot-v1`, source Minecraft 1.16.5 /
Forge 36.2.42 and target Minecraft 1.20.1 / Forge 47.4.23. The developer-only
profile is `build/network-snapshot-v1/mapping-profile.properties`.

This is a one-time explicit local input selection, not automatic Gradle-cache
discovery by the engine. It references files already present; preparation did
not download anything, execute mod classes, start a game or run a build.
The profile and its locally generated bundle must not be shipped as public
redistributable data. No Minecraft, Forge, fixture or dependency class bytes are
copied into the product by this operation. Existing
[portable mapping-data](PORTABLE_MAPPING_DATA.md) and
[rights](PACKAGING_RIGHTS.md) requirements remain in force. Netty declarations
do not grant additional networking functionality or redistribution clearance
for the complete mixed-origin mapping bundle.

## Selection and provenance

The profile retains the existing 13 inputs from
`build/logic-preserving-v1/mapping-profile.properties`: six official/MCP mapping
inputs, two legacy API JARs and five target API JARs. It appends one source Netty
JAR and three target Netty JARs, for **17 input artifacts total**, with three
legacy API JARs and eight target API JARs. Both are below the loader's 16-JAR
per-side limit. All explicitly selected files existed and were hashed during
this preparation.

Source Netty is **4.1.25.Final**, not the unrelated 4.1.68.Final also present in
the developer's cache. The exact cached Mojang `versions/1.16.5/version.json`
declares `io.netty:netty-all:4.1.25.Final`, SHA-1
`d0626cd3108294d1d58c05859add27b4ef21f83b`, size 3,823,147. The normal source
installation at
`build/datapack-recipes-v1/source-default-comparison1-server/minecraft_server.1.16.5.jar`
contains `META-INF/io.netty.versions.properties` identifying the same version.
The following class-entry SHA-256 values are identical in that installed server
JAR and the selected standalone Netty JAR:

| Class entry | Matching SHA-256 |
| --- | --- |
| `io/netty/buffer/ByteBuf.class` | `be21c80d927338f300822a1725f0255c43405c3943bf332446a1323143087c93` |
| `io/netty/channel/SimpleChannelInboundHandler.class` | `1dd8b3590b4e47ba12ba883396584319264d87e04fbec192b78a2950bdb6c97c` |
| `io/netty/util/ReferenceCounted.class` | `61636ebb1c5e77d971446891eacb1bbb18b88eb5ab1c825920dcac9642eb66f6` |

The source installed-server JAR used for that read-only comparison has SHA-256
`58f329c7d2696526f948470aa6fd0b45545039b64cb75015e64c12194b373da6`.
It is **not** an additional mapping-profile input.

Target Netty is **4.1.82.Final** in both the cached Mojang
`versions/1.20.1/version.json` and the ordinary Forge 47.4.23 installation's
`build/p6-public/server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt`.
Only artifact coordinates were inspected from the latter; no account or launch
arguments are copied into this document. The profile selects that installation's
unchanged `netty-common`, `netty-buffer` and `netty-transport` JARs. Their metadata
SHA-1 values are respectively `022d148e85c3f5ebdacc0ce1f5aabb1d420f73f3`,
`a544270cf1ae8b8077082f5036436a9a9971ea71`, and
`e431a218d91acb6476ccad5f5aafde50aa3945ca`. Local SHA-256 values are below.

The source and target cached version-metadata files themselves have SHA-256
`3eeeab7b3165cc5263dc26ff8fe114fdb718b75a0244efead9d19635d090ba72` and
`a071414699e9cb476eddf9c54850eb6711a7c7d2eb92b5ec010321a8dd9f3f94`.
Metadata/artifact hashes record the exact inspected bytes, not publisher
authentication or proof of a newly executed installation.

## Input hashes

The indexes are zero-based positions in each semicolon-separated API list.
Existing Forge development JARs use the official development namespace; the
engine must perform its existing source/target namespace normalization before
offline linkage. This table is provenance, not a manual symbol mapping.

| Profile input | Bytes | SHA-256 |
| --- | ---: | --- |
| `legacy.clientMappings` | 5746047 | `7931ed6d723eceb1d621d05a76e10ddf643bf468c6ecf1c4ecf377bd72cf8b8c` |
| `legacy.serverMappings` | 4400926 | `b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7` |
| `legacy.srg` | 777652 | `80216a0f4ae79d81a5e11ceccaa97582a1f6a2cf079b7703b88a99a26547b342` |
| `target.clientMappings` | 8001795 | `67bfc33ef0c7103f002d6ac134f8eddd3d67e2ce45d266e83816d1a2e2c9b01d` |
| `target.serverMappings` | 6154195 | `dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72` |
| `target.srg` | 1740711 | `f46d1050d8bed9046886b90a0d2fb80b4c6b5120fc35d32e5d37498dbe0a6d2a` |
| `legacy.apiJars[0]`: mapped Forge/game | 14784630 | `1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03` |
| `legacy.apiJars[1]`: EventBus 4.0.0 | 42873 | `8754a57ff4d15dffcab15f50b691f4a9c1108b057fbcfff5c915ce928c41f84e` |
| `legacy.apiJars[2]`: Netty all 4.1.25.Final | 3823147 | `972642d0037706485747d34ce50fd67a15f0ed3d8a781882d683824c9977bc86` |
| `target.apiJars[0]`: mapped Forge/game | 19237052 | `b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee` |
| `target.apiJars[1]`: javafmllanguage 47.4.23 | 16851 | `c11e456e4486152f77d7f34af37ce795e9abbf3a100663ed62abdd6ff5cd3661` |
| `target.apiJars[2]`: fmlcore 47.4.23 | 118909 | `cb7467136d76ed8cdbf1d6cecca40e0034cd77ce78f64a72105346deaa184daa` |
| `target.apiJars[3]`: fmlloader 47.4.23 | 270956 | `a2a86db8b83b4a8ec53fe82a4412e94103101e6d337064f2075f974819ce185a` |
| `target.apiJars[4]`: EventBus 6.2.33 | 64014 | `c9c9aab0979e08a0489fc497ba433276648d5192608e53535bad881dfe14d712` |
| `target.apiJars[5]`: Netty common 4.1.82.Final | 653880 | `d5923db1cc68b26baf5368f1f76ec2c6100d12db8406c6de00eb6dbc08052550` |
| `target.apiJars[6]`: Netty buffer 4.1.82.Final | 304664 | `a7ce7f6d8c1e82b044cd56765dab4338251596416de03b1f9a05d7402429c29d` |
| `target.apiJars[7]`: Netty transport 4.1.82.Final | 485752 | `c210069be4ab4120f5ac7cb5e4a126d3a007e78a64fd44c9971220e013dbaef1` |

## Closure and validation boundary

The additional declarations address the finite fixture's buffer return types,
`ByteBuf`/`ReferenceCounted`, and the `NetworkManager`/`Connection` superclass
chain through Netty channel handlers. The four selected Netty JARs contain no
`META-INF/versions/*.class` entries; no ad-hoc multi-release flattening or API
class rewriting was performed. The source aggregate is read as declarations
only. Target codec/handler/resolver/native-transport libraries are not silently
added just because the installation includes them.

`ApiIndex` reads declarations with ASM while skipping method bodies, debug data
and frames; it rejects conflicting declarations and unsupported multi-release
API inputs. File existence, class-entry inspection and these hashes do **not**
prove complete linkage. The root validation still must load this explicit
profile, prepare/verify its private bundle, then run source and target offline
linkage on the actual fixture. Missing additional owners must fail closed, not
be replaced by reflection, downloaded implicitly or fabricated. Any subsequent
input addition requires a new explicit provenance record and validation run.

No engine code, recipes, mappings, admission limits, public packaging or runtime
tests were changed by this data preparation. See the separate
[native API audit](NETWORK_API_BASELINES.md) for semantic differences and
networking execution gates.
