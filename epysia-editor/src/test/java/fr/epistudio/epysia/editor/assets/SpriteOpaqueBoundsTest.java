package fr.epistudio.epysia.editor.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteOpaqueBoundsTest {

    private static final int CELL_SIZE = 16;
    private static final int OPAQUE_ROWS = 9;
    private static final float TOLERANCE = 1.0e-4f;

    @Test
    void artAtTheTopOfACellReportsBoundsAtTheTopInUnitSpace() throws IOException {
        Path sheet = writeSheetWithTopBand();
        SpriteOpaqueBounds bounds = new SpriteOpaqueBounds();
        Optional<SpriteOpaqueBounds.UnitBounds> measured = bounds.boundsOf(sheet, 2, 2, 0);
        assertTrue(measured.isPresent());
        assertEquals(0.0f, measured.get().minX(), TOLERANCE);
        assertEquals(1.0f, measured.get().maxX(), TOLERANCE);
        assertEquals(1.0f - (float) OPAQUE_ROWS / CELL_SIZE, measured.get().minY(), TOLERANCE);
        assertEquals(1.0f, measured.get().maxY(), TOLERANCE);
    }

    @Test
    void fullyTransparentCellReportsNothing() throws IOException {
        Path sheet = writeSheetWithTopBand();
        SpriteOpaqueBounds bounds = new SpriteOpaqueBounds();
        assertTrue(bounds.boundsOf(sheet, 2, 2, 3).isEmpty());
    }

    private static Path writeSheetWithTopBand() throws IOException {
        BufferedImage image = new BufferedImage(CELL_SIZE * 2, CELL_SIZE * 2, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < OPAQUE_ROWS; row++) {
            for (int column = 0; column < CELL_SIZE; column++) {
                image.setRGB(column, row, 0xFFFFFFFF);
            }
        }
        Path file = Files.createTempFile("opaque-bounds", ".png");
        ImageIO.write(image, "png", file.toFile());
        file.toFile().deleteOnExit();
        return file;
    }
}
