# Original static half-shape self-drop fixture

Project-authored Forge **1.16.5 / 36.2.42**, version1.0.0, Java8.
Separate fixture from `static-shape-block-1.16.5`; same closed half-shape
code/model, two owned classes and eight methods. The registered id is
`legacybridge_static_shape_drop:bridge_block`.

The only behavior addition is an authored local block loot table with
one pool, rolls1, one self BlockItem entry, and survives_explosion.
No functions, NBT, custom context, GLM, nested tables or world access code.
bonus_rolls is omitted because both real source/target parsers default it to0.
The private converter must preserve the existing table byte-for-byte; it must
never invent this table for the separate no-loot fixture.

Exact condition semantics were checked in source SurvivesExplosion and target
ExplosionCondition: absent radius passes, supplied radius uses nextFloat<=1/r.
This does NOT promise seed-identical results or identical explosion context:
target RNG infrastructure and TNT drop-decay gamerule behavior differ.
No random_sequence is inserted. Native breaking, explosion contexts, reload,
drops and anti-duplication remain NOT_RUN.

One authored half-height element and vanilla stone reference avoid copying
game assets; no stacking, waterlogging, slab placement or stateful geometry
is implemented. Model and other resources remain the bounded base fixture.

Build serially using Java17 from project root:
`gradlew.bat -p fixtures/static-shape-drop-1.16.5 build --offline --max-workers=1`.
Five standalone tests inspect archive data only, never original class loading.
Private candidate only: no public profile promotion/Apply, and no DATA-LOOT
or prerequisite acceptance is asserted by the fixture.
