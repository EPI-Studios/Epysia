package fr.epistudio.epysia.assets.epytilemap;

public record CellBounds(int minX, int minY, int maxX, int maxY) {

    public static CellBounds empty() {
        return new CellBounds(0, 0, -1, -1);
    }

    public boolean isEmpty() {
        return maxX < minX || maxY < minY;
    }

    public int widthCells() {
        return isEmpty() ? 0 : maxX - minX + 1;
    }

    public int heightCells() {
        return isEmpty() ? 0 : maxY - minY + 1;
    }

    public CellBounds including(int cellX, int cellY) {
        if (isEmpty()) {
            return new CellBounds(cellX, cellY, cellX, cellY);
        }
        return new CellBounds(Math.min(minX, cellX), Math.min(minY, cellY),
                Math.max(maxX, cellX), Math.max(maxY, cellY));
    }

    public CellBounds union(CellBounds other) {
        if (other.isEmpty()) {
            return this;
        }
        return including(other.minX(), other.minY()).including(other.maxX(), other.maxY());
    }
}
