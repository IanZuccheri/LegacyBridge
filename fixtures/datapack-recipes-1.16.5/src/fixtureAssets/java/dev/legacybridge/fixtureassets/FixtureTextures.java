package dev.legacybridge.fixtureassets;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/** Original, deterministic test patterns; not copied from Minecraft or any mod. */
public final class FixtureTextures {
    private FixtureTextures() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected generated-resources directory");
        }
        Path root = Paths.get(args[0]).resolve("assets/legacybridge_datapack/textures");
        BufferedImage block = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        BufferedImage ingot = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = x == 0 || y == 0 || x == 15 || y == 15 ? 0xff124752
                        : ((x / 4 + y / 4) % 2 == 0 ? 0xff34a7ac : 0xff58c1bb);
                block.setRGB(x, y, color);
                if (x >= 2 && x <= 13 && y >= 5 && y <= 11) {
                    ingot.setRGB(x, y, y == 5 || x == 2 ? 0xffc6f5e7
                            : y == 11 || x == 13 ? 0xff21757f : 0xff64d9cc);
                }
            }
        }
        write(root.resolve("block/bridge_block.png"), block);
        write(root.resolve("item/bridge_ingot.png"), ingot);
    }

    private static void write(Path output, BufferedImage image) throws IOException {
        Files.createDirectories(output.getParent());
        if (!ImageIO.write(image, "PNG", output.toFile())) {
            throw new IOException("PNG ImageIO writer is unavailable");
        }
    }
}
