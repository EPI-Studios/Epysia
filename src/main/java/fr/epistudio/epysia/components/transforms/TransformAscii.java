package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import org.joml.Vector2f;
import org.joml.Vector2i;

public final class TransformAscii extends Component {

    private final Vector2i cellPosition = new Vector2i();
    private final Vector2f subCellOffset = new Vector2f();
    private final Vector2i size = new Vector2i(1, 1);
    private int rotationQuarterTurns;
    private int renderLayer;
    private boolean visible = true;
    private boolean flippedHorizontally;
    private boolean flippedVertically;

    public Vector2i cellPosition() {
        return cellPosition;
    }

    public TransformAscii setCellPosition(int x, int y) {
        cellPosition.set(x, y);
        return this;
    }

    public TransformAscii translateCells(int deltaX, int deltaY) {
        cellPosition.add(deltaX, deltaY);
        return this;
    }

    public Vector2f subCellOffset() {
        return subCellOffset;
    }

    public TransformAscii setSubCellOffset(float x, float y) {
        subCellOffset.set(x, y);
        return this;
    }

    public Vector2i size() {
        return size;
    }

    public TransformAscii setSize(int width, int height) {
        size.set(width, height);
        return this;
    }

    public int rotationQuarterTurns() {
        return rotationQuarterTurns;
    }

    public TransformAscii setRotationQuarterTurns(int quarterTurns) {
        this.rotationQuarterTurns = Math.floorMod(quarterTurns, 4);
        return this;
    }

    public TransformAscii rotateClockwise() {
        return setRotationQuarterTurns(rotationQuarterTurns + 1);
    }

    public TransformAscii rotateCounterClockwise() {
        return setRotationQuarterTurns(rotationQuarterTurns - 1);
    }

    public int renderLayer() {
        return renderLayer;
    }

    public TransformAscii setRenderLayer(int renderLayer) {
        this.renderLayer = renderLayer;
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public TransformAscii setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean flippedHorizontally() {
        return flippedHorizontally;
    }

    public TransformAscii setFlippedHorizontally(boolean flipped) {
        this.flippedHorizontally = flipped;
        return this;
    }

    public boolean flippedVertically() {
        return flippedVertically;
    }

    public TransformAscii setFlippedVertically(boolean flipped) {
        this.flippedVertically = flipped;
        return this;
    }

    public Vector2f worldPosition(Vector2f destination) {
        return destination.set(cellPosition.x + subCellOffset.x, cellPosition.y + subCellOffset.y);
    }
}
