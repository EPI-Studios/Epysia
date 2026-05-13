package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.utils.Matrix3f;
import fr.epistudio.epysia.utils.Vector2f;
import fr.epistudio.epysia.utils.Vector2i;

/**
 * Component dedicated to transforms in an ASCII renderer.
 *
 * <p>The transform separates grid coordinates from sub-cell offsets. This makes it practical for
 * text-mode rendering systems where logical placement happens on a character grid, while still
 * allowing smooth interpolation.
 */
public class TransformAscii extends Component {

    private Vector2i cellPosition;
    private Vector2f offset;
    private Vector2i size;
    private Vector2f pivot;
    private int rotationQuarterTurns;
    private int layer;
    private boolean visible;
    private boolean flipX;
    private boolean flipY;

    /**
     * Creates a default ASCII transform.
     */
    public TransformAscii() {
        this(Vector2i.ZERO, Vector2f.ZERO, Vector2i.ONE, Vector2f.ZERO, 0, 0, true, false, false);
    }

    /**
     * Creates an ASCII transform with a grid position.
     *
     * @param cellPosition grid position in character cells
     */
    public TransformAscii(Vector2i cellPosition) {
        this(cellPosition, Vector2f.ZERO, Vector2i.ONE, Vector2f.ZERO, 0, 0, true, false, false);
    }

    /**
     * Creates an ASCII transform with all fields explicitly specified.
     *
     * @param cellPosition grid position in character cells
     * @param offset sub-cell offset
     * @param size logical size in character cells
     * @param pivot local pivot
     * @param rotationQuarterTurns discrete clockwise quarter turns
     * @param layer render layer
     * @param visible visibility flag
     * @param flipX horizontal mirror flag
     * @param flipY vertical mirror flag
     */
    public TransformAscii(
            Vector2i cellPosition,
            Vector2f offset,
            Vector2i size,
            Vector2f pivot,
            int rotationQuarterTurns,
            int layer,
            boolean visible,
            boolean flipX,
            boolean flipY
    ) {
        this.cellPosition = cellPosition;
        this.offset = offset;
        this.size = size;
        this.pivot = pivot;
        this.rotationQuarterTurns = normalizeQuarterTurns(rotationQuarterTurns);
        this.layer = layer;
        this.visible = visible;
        this.flipX = flipX;
        this.flipY = flipY;
    }

    /**
     * Returns the grid position.
     *
     * @return cell position
     */
    public Vector2i getCellPosition() {
        return cellPosition;
    }

    /**
     * Sets the grid position.
     *
     * @param cellPosition new cell position
     */
    public void setCellPosition(Vector2i cellPosition) {
        this.cellPosition = cellPosition;
    }

    /**
     * Moves the transform by a whole-cell offset.
     *
     * @param delta cell offset
     */
    public void translateCells(Vector2i delta) {
        cellPosition = cellPosition.add(delta);
    }

    /**
     * Moves the transform by cell offsets.
     *
     * @param dx x cell offset
     * @param dy y cell offset
     */
    public void translateCells(int dx, int dy) {
        cellPosition = cellPosition.add(new Vector2i(dx, dy));
    }

    /**
     * Returns the sub-cell offset.
     *
     * @return current offset
     */
    public Vector2f getOffset() {
        return offset;
    }

    /**
     * Sets the sub-cell offset.
     *
     * @param offset new offset
     */
    public void setOffset(Vector2f offset) {
        this.offset = offset;
    }

    /**
     * Moves the sub-cell offset.
     *
     * @param delta offset delta
     */
    public void translateOffset(Vector2f delta) {
        offset = offset.add(delta);
    }

    /**
     * Moves the sub-cell offset.
     *
     * @param dx x offset
     * @param dy y offset
     */
    public void translateOffset(float dx, float dy) {
        offset = offset.add(dx, dy);
    }

    /**
     * Returns the size expressed in cells.
     *
     * @return logical size
     */
    public Vector2i getSize() {
        return size;
    }

    /**
     * Sets the size expressed in cells.
     *
     * @param size new logical size
     */
    public void setSize(Vector2i size) {
        this.size = size;
    }

    /**
     * Returns the pivot.
     *
     * @return pivot
     */
    public Vector2f getPivot() {
        return pivot;
    }

    /**
     * Sets the pivot.
     *
     * @param pivot new pivot
     */
    public void setPivot(Vector2f pivot) {
        this.pivot = pivot;
    }

    /**
     * Returns the number of clockwise quarter turns.
     *
     * @return normalized quarter-turn value in {@code [0, 3]}
     */
    public int getRotationQuarterTurns() {
        return rotationQuarterTurns;
    }

    /**
     * Sets the number of clockwise quarter turns.
     *
     * @param rotationQuarterTurns new quarter-turn value
     */
    public void setRotationQuarterTurns(int rotationQuarterTurns) {
        this.rotationQuarterTurns = normalizeQuarterTurns(rotationQuarterTurns);
    }

    /**
     * Rotates one quarter turn clockwise.
     */
    public void rotateClockwise() {
        rotationQuarterTurns = normalizeQuarterTurns(rotationQuarterTurns + 1);
    }

    /**
     * Rotates one quarter turn counter-clockwise.
     */
    public void rotateCounterClockwise() {
        rotationQuarterTurns = normalizeQuarterTurns(rotationQuarterTurns - 1);
    }

    /**
     * Returns the rotation in radians.
     *
     * @return clockwise rotation in radians
     */
    public float getRotationRadians() {
        return rotationQuarterTurns * ((float) Math.PI / 2.0f);
    }

    /**
     * Returns the render layer.
     *
     * @return render layer
     */
    public int getLayer() {
        return layer;
    }

    /**
     * Sets the render layer.
     *
     * @param layer new render layer
     */
    public void setLayer(int layer) {
        this.layer = layer;
    }

    /**
     * Returns whether the transform is visible.
     *
     * @return visibility flag
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets the visibility flag.
     *
     * @param visible new visibility
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Toggles visibility.
     */
    public void toggleVisible() {
        visible = !visible;
    }

    /**
     * Returns whether the transform is flipped horizontally.
     *
     * @return horizontal flip flag
     */
    public boolean isFlipX() {
        return flipX;
    }

    /**
     * Sets the horizontal flip flag.
     *
     * @param flipX new horizontal flip flag
     */
    public void setFlipX(boolean flipX) {
        this.flipX = flipX;
    }

    /**
     * Returns whether the transform is flipped vertically.
     *
     * @return vertical flip flag
     */
    public boolean isFlipY() {
        return flipY;
    }

    /**
     * Sets the vertical flip flag.
     *
     * @param flipY new vertical flip flag
     */
    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
    }

    /**
     * Toggles the horizontal flip flag.
     */
    public void toggleFlipX() {
        flipX = !flipX;
    }

    /**
     * Toggles the vertical flip flag.
     */
    public void toggleFlipY() {
        flipY = !flipY;
    }

    /**
     * Returns the position expressed in continuous world coordinates.
     *
     * @return continuous position
     */
    public Vector2f getWorldPosition() {
        return cellPosition.toVector2f().add(offset);
    }

    /**
     * Sets the world position and splits it into cell and offset parts.
     *
     * @param worldPosition continuous world position
     */
    public void setWorldPosition(Vector2f worldPosition) {
        int cellX = (int) Math.floor(worldPosition.getX());
        int cellY = (int) Math.floor(worldPosition.getY());
        cellPosition = new Vector2i(cellX, cellY);
        offset = worldPosition.subtract(cellPosition.toVector2f());
    }

    /**
     * Builds the transform matrix used by the ASCII renderer.
     *
     * @return homogeneous transform matrix
     */
    public Matrix3f toMatrix() {
        float scaleX = size.getX() * (flipX ? -1.0f : 1.0f);
        float scaleY = size.getY() * (flipY ? -1.0f : 1.0f);
        return Matrix3f.translation(getWorldPosition())
                .multiply(Matrix3f.translation(pivot))
                .multiply(Matrix3f.rotation(getRotationRadians()))
                .multiply(Matrix3f.scale(new Vector2f(scaleX, scaleY)))
                .multiply(Matrix3f.translation(pivot.negate()));
    }

    /**
     * Transforms a local point into ASCII world space.
     *
     * @param localPoint local point
     * @return transformed point
     */
    public Vector2f transformPoint(Vector2f localPoint) {
        return toMatrix().transformPoint(localPoint);
    }

    /**
     * Transforms a local point and rounds it to character-cell coordinates.
     *
     * @param localPoint local point
     * @return transformed cell coordinate
     */
    public Vector2i transformCell(Vector2f localPoint) {
        return transformPoint(localPoint).toVector2i();
    }

    /**
     * Resets the transform to its default values.
     */
    public void reset() {
        cellPosition = Vector2i.ZERO;
        offset = Vector2f.ZERO;
        size = Vector2i.ONE;
        pivot = Vector2f.ZERO;
        rotationQuarterTurns = 0;
        layer = 0;
        visible = true;
        flipX = false;
        flipY = false;
    }

    private static int normalizeQuarterTurns(int rotationQuarterTurns) {
        int normalized = rotationQuarterTurns % 4;
        return normalized < 0 ? normalized + 4 : normalized;
    }
}
