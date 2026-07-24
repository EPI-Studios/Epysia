package fr.epistudio.epysia.assets.epytilemap;

import java.util.ArrayList;
import java.util.List;

public final class TilemapSolidRectangles {

    public record TileRectangle(int cellX, int cellY, int widthCells, int heightCells) {
    }

    private TilemapSolidRectangles() {
    }

    public static List<TileRectangle> merge(SpriteTilemap tilemap) {
        boolean[] consumed = new boolean[tilemap.width() * tilemap.height()];
        List<TileRectangle> rectangles = new ArrayList<>();
        for (int cellY = 0; cellY < tilemap.height(); cellY++) {
            for (int cellX = 0; cellX < tilemap.width(); cellX++) {
                if (tilemap.isCellSolid(cellX, cellY) && !consumed[cellY * tilemap.width() + cellX]) {
                    rectangles.add(growRectangle(tilemap, consumed, cellX, cellY));
                }
            }
        }
        return rectangles;
    }

    private static TileRectangle growRectangle(SpriteTilemap tilemap, boolean[] consumed, int cellX, int cellY) {
        int widthCells = rowRunWidth(tilemap, consumed, cellX, cellY);
        int heightCells = columnRunHeight(tilemap, consumed, cellX, cellY, widthCells);
        consumeCells(tilemap, consumed, cellX, cellY, widthCells, heightCells);
        return new TileRectangle(cellX, cellY, widthCells, heightCells);
    }

    private static int rowRunWidth(SpriteTilemap tilemap, boolean[] consumed, int cellX, int cellY) {
        int runEnd = cellX;
        while (runEnd < tilemap.width() && tilemap.isCellSolid(runEnd, cellY)
                && !consumed[cellY * tilemap.width() + runEnd]) {
            runEnd++;
        }
        return runEnd - cellX;
    }

    private static int columnRunHeight(SpriteTilemap tilemap, boolean[] consumed, int cellX, int cellY, int widthCells) {
        int rowAbove = cellY + 1;
        while (rowAbove < tilemap.height() && rowMatches(tilemap, consumed, cellX, rowAbove, widthCells)) {
            rowAbove++;
        }
        return rowAbove - cellY;
    }

    private static boolean rowMatches(SpriteTilemap tilemap, boolean[] consumed, int cellX, int cellY, int widthCells) {
        for (int step = 0; step < widthCells; step++) {
            if (!tilemap.isCellSolid(cellX + step, cellY) || consumed[cellY * tilemap.width() + cellX + step]) {
                return false;
            }
        }
        return true;
    }

    private static void consumeCells(SpriteTilemap tilemap, boolean[] consumed, int cellX, int cellY,
                                     int widthCells, int heightCells) {
        for (int rowStep = 0; rowStep < heightCells; rowStep++) {
            for (int columnStep = 0; columnStep < widthCells; columnStep++) {
                consumed[(cellY + rowStep) * tilemap.width() + cellX + columnStep] = true;
            }
        }
    }
}
