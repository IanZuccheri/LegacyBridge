package dev.legacybridge.runtime.compat.world;

import dev.legacybridge.runtime.SimpleContentPlan;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import java.util.Objects;

/**
 * Legacy API: {@code AbstractBlock.Properties.of(Material.STONE).strength(float,float)} and default stone sound.
 * Target API: {@code BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(float,float)}.
 * Transformation: recreates plain stone properties and the legacy stone note-block instrument on the target.
 * Behavior differences: target pickaxe speed uses mineable/pickaxe tags; the offline BlockMiningAdapter generates that tag.
 * Known limitations: STONE material/sound, finite nonnegative strength, default occlusion and no correct-tool requirement only.
 */
public final class BlockPropertiesAdapter {
    private BlockPropertiesAdapter() { }

    public static BlockBehaviour.Properties create(SimpleContentPlan.BlockProperties source) {
        Objects.requireNonNull(source, "source");
        if (!"STONE".equals(source.material()) || !"STONE".equals(source.sound())
                || !Float.isFinite(source.hardness()) || source.hardness() < 0
                || !Float.isFinite(source.resistance()) || source.resistance() < 0
                || source.requiresCorrectTool() || source.noOcclusion()) {
            throw new IllegalArgumentException("Block properties are outside the verified stone adapter subset");
        }
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(source.hardness(), source.resistance()).sound(SoundType.STONE);
    }
}
