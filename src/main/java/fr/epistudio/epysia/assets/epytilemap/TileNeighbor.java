package fr.epistudio.epysia.assets.epytilemap;

public enum TileNeighbor {

    RIGHT(1, 0),
    TOP_RIGHT(1, 1),
    TOP(0, 1),
    TOP_LEFT(-1, 1),
    LEFT(-1, 0),
    BOTTOM_LEFT(-1, -1),
    BOTTOM(0, -1),
    BOTTOM_RIGHT(1, -1);

    private final int cellOffsetX;
    private final int cellOffsetY;

    TileNeighbor(int cellOffsetX, int cellOffsetY) {
        this.cellOffsetX = cellOffsetX;
        this.cellOffsetY = cellOffsetY;
    }

    public int cellOffsetX() {
        return cellOffsetX;
    }

    public int cellOffsetY() {
        return cellOffsetY;
    }

    public boolean side() {
        return cellOffsetX == 0 || cellOffsetY == 0;
    }

    public boolean corner() {
        return !side();
    }

    public TileNeighbor opposite() {
        return values()[(ordinal() + 4) % values().length];
    }

    public boolean matches(TerrainMatchMode mode) {
        return switch (mode) {
            case SIDES -> side();
            case CORNERS -> corner();
            case CORNERS_AND_SIDES -> true;
        };
    }
}
