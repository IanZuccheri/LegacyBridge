# Original horizontal orientation-transform fixture

Project-authored Forge **1.16.5 / 36.2.42**, version 1.0.0, Java 8 bytecode.
Separate second-stage fixture from `horizontal-block-1.16.5`; it does not
replace or silently broaden that first placement-only fixture.

Two owned classes register `legacybridge_horizontal_transforms:bridge_block`
and its BlockItem. The native Block subclass retains its NORTH default,
single horizontal property, and opposite-player placement. Additionally, both
native rotate and mirror overrides return the input state with only FACING
changed by the supplied native Rotation or Mirror enum. Neither ignores its
argument nor invokes hooks, worlds, packets or client-only APIs.

The source overloads Rotation.rotate(Direction) and Mirror.mirror(Direction)
were read from the actual 1.16.5 Forge36.2.42 source archive. State.getValue/setValue
retain their generic casts; transformation must preserve virtual dispatch and
both complete overrides, not infer arbitrary block callbacks.

One shapeless recipe, four facing variants, cube_all stone-texture reference,
item model, and en_us/pt_br are unchanged in shape from the first fixture.
No game assets or code are copied. Isotropic stone is not visual proof of
orientation; native QA must inspect state across all four rotations and all
three mirrors, including round-trips. No loot/self-drop behavior is claimed.

Build serially with Java17 from repository root:
`gradlew.bat -p fixtures/horizontal-transforms-1.16.5 build --offline --max-workers=1`.
Four standalone tests read the JAR archive only, without loading original
classes. Conversion uses the private horizontal candidate pathway, never
public export/Apply. Native/client/server/multiplayer comparisons remain
NOT_RUN; no world migration is asserted.
