package fr.epistudio.epysia.assets.epytilemap;

import java.util.ArrayList;
import java.util.List;

public final class TilemapSolidRectangles {

    public record TileRectangle(int cellX, int cellY, int widthCells, int heightCells) {
    }

    private record Scan(SpriteTilemap tilemap, CellBounds bounds, boolean[] consumed) {

        boolean available(int cellX, int cellY) {
            return within(cellX, cellY) && tilemap.isCellSolid(cellX, cellY) && !consumed[indexOf(cellX, cellY)];
        }

        boolean within(int cellX, int cellY) {
            return cellX >= bounds.minX() && cellX <= bounds.maxX()
                    && cellY >= bounds.minY() && cellY <= bounds.maxY();
        }

        int indexOf(int cellX, int cellY) {
            return (cellY - bounds.minY()) * bounds.widthCells() + (cellX - bounds.minX());
        }

        void consume(int cellX, int cellY) {
            consumed[indexOf(cellX, cellY)] = true;
        }
    }

    private TilemapSolidRectangles() {
    }

    public static List<TileRectangle> merge(SpriteTilemap tilemap) {
        CellBounds bounds = tilemap.collisionBounds();
        List<TileRectangle> rectangles = new ArrayList<>();
        if (bounds.isEmpty()) {
            return rectangles;
        }
        Scan scan = new Scan(tilemap, bounds, new boolean[bounds.widthCells() * bounds.heightCells()]);
        for (int cellY = bounds.minY(); cellY <= bounds.maxY(); cellY++) {
            for (int cellX = bounds.minX(); cellX <= bounds.maxX(); cellX++) {
                if (scan.available(cellX, cellY)) {
                    rectangles.add(growRectangle(scan, cellX, cellY));
                }
            }
        }
        return rectangles;
    }

    private static TileRectangle growRectangle(Scan scan, int cellX, int cellY) {
        int widthCells = rowRunWidth(scan, cellX, cellY);
        int heightCells = columnRunHeight(scan, cellX, cellY, widthCells);
        consumeCells(scan, cellX, cellY, widthCells, heightCells);
        return new TileRectangle(cellX, cellY, widthCells, heightCells);
    }

    private static int rowRunWidth(Scan scan, int cellX, int cellY) {
        int runEnd = cellX;
        while (scan.available(runEnd, cellY)) {
            runEnd++;
        }
        return runEnd - cellX;
    }

    private static int columnRunHeight(Scan scan, int cellX, int cellY, int widthCells) {
        int rowAbove = cellY + 1;
        while (rowMatches(scan, cellX, rowAbove, widthCells)) {
            rowAbove++;
        }
        return rowAbove - cellY;
    }

    private static boolean rowMatches(Scan scan, int cellX, int cellY, int widthCells) {
        for (int step = 0; step < widthCells; step++) {
            if (!scan.available(cellX + step, cellY)) {
                return false;
            }
        }
        return true;
    }

    private static void consumeCells(Scan scan, int cellX, int cellY, int widthCells, int heightCells) {
        for (int rowStep = 0; rowStep < heightCells; rowStep++) {
            for (int columnStep = 0; columnStep < widthCells; columnStep++) {
                scan.consume(cellX + columnStep, cellY + rowStep);
            }
        }
    }
}
