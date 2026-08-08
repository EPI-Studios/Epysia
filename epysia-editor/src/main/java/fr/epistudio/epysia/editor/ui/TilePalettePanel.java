package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import fr.epistudio.epysia.assets.NestedAssetPaths;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.assets.TilemapDiskFile;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;

public final class TilePalettePanel {

    private static final float MAXIMUM_IMAGE_HEIGHT = 260.0f;
    private static final int COLOR_GRID_LINE = 0x66FFFFFF;
    private static final int COLOR_SELECTED_FILL = 0x40CC7A00;
    private static final int COLOR_SELECTED_BORDER = 0xFFCC7A00;

    private final ImagePreviewTexture preview;
    private final EngineServices services;
    private final Supplier<SceneDocument> activeDocument;
    private final TileBrush brush;
    private String saveError = "";

    public TilePalettePanel(OpenGlRenderBackend backend, EngineServices services,
                            Supplier<SceneDocument> activeDocument, TileBrush brush) {
        this.preview = new ImagePreviewTexture(backend);
        this.services = services;
        this.activeDocument = activeDocument;
        this.brush = brush;
    }

    public TileBrush brush() {
        return brush;
    }

    public Optional<ImagePreviewTexture.PreviewImage> atlasImage(SpriteTilemap tilemap, SpriteAtlas atlas) {
        return texturePreview(tilemap, atlas);
    }

    public void render(TilemapRenderer renderer) {
        renderer.refresh(services);
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<SpriteAtlas> atlas = renderer.atlasValue();
        if (tilemap.isEmpty() || atlas.isEmpty() || atlas.get().grid().isEmpty()) {
            ImGui.textDisabled("Assign a tilemap backed by a grid atlas to paint tiles.");
            return;
        }
        renderPalette(tilemap.get(), atlas.get());
    }

    public int saveDirtyTilemaps(Scene scene) {
        int written = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            TilemapRenderer renderer = gameObject.getComponentOrNull(TilemapRenderer.class);
            if (renderer != null && saveIfDirty(renderer)) {
                written++;
            }
        }
        return written;
    }

    private boolean saveIfDirty(TilemapRenderer renderer) {
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<Path> file = tilemapFile(renderer);
        if (tilemap.isEmpty() || file.isEmpty() || !dirty(renderer)) {
            return false;
        }
        save(tilemap.get(), file.get());
        return saveError.isEmpty();
    }

    public boolean dirty(TilemapRenderer renderer) {
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<Path> file = tilemapFile(renderer);
        if (tilemap.isEmpty() || file.isEmpty()) {
            return false;
        }
        return !TilemapDiskFile.matchesDisk(tilemap.get(), file.get(), services.assets().locator());
    }

    public void renderSaveBar(TilemapRenderer renderer) {
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<Path> file = tilemapFile(renderer);
        if (tilemap.isEmpty()) {
            return;
        }
        boolean dirty = dirty(renderer);
        renderSaveButton(tilemap.get(), file, dirty);
        ImGui.sameLine();
        renderSaveState(file, dirty);
    }

    private void renderSaveButton(SpriteTilemap tilemap, Optional<Path> file, boolean dirty) {
        if (dirty) {
            ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        }
        ImGui.beginDisabled(file.isEmpty());
        boolean clicked = ImGui.button(dirty ? "Save Tilemap *" : "Save Tilemap");
        ImGui.endDisabled();
        if (dirty) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Tiles, collision and terrains only reach the game once this file is written."
                    + "\nCtrl+S while this panel is focused does the same.");
        }
        if (clicked || saveShortcutPressed()) {
            file.ifPresent(path -> save(tilemap, path));
        }
    }

    private static boolean saveShortcutPressed() {
        return ImGui.isWindowFocused(imgui.flag.ImGuiFocusedFlags.RootAndChildWindows)
                && ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(imgui.flag.ImGuiKey.S);
    }

    private void renderSaveState(Optional<Path> file, boolean dirty) {
        if (file.isEmpty()) {
            ImGui.textColored(EditorStyle.COLOR_DANGER, "This tilemap has no file on disk yet.");
            return;
        }
        if (!saveError.isEmpty()) {
            ImGui.textColored(EditorStyle.COLOR_DANGER, "Save failed: " + saveError);
            return;
        }
        if (dirty) {
            ImGui.textColored(EditorStyle.COLOR_DANGER,
                    "Unsaved changes. The running game still loads the old file.");
            return;
        }
        ImGui.textDisabled("Saved  " + file.get().getFileName());
    }

    private void renderSaveRow(SpriteTilemap tilemap, TilemapRenderer renderer) {
        Optional<Path> file = tilemapFile(renderer);
        boolean dirty = file.map(path -> !TilemapDiskFile.matchesDisk(tilemap, path, services.assets().locator())).orElse(true);
        ImGui.textDisabled(dirty ? "Unsaved changes" : "Saved");
        ImGui.sameLine();
        ImGui.beginDisabled(file.isEmpty());
        if (ImGui.button("Save Tilemap")) {
            file.ifPresent(path -> save(tilemap, path));
        }
        ImGui.endDisabled();
        if (!saveError.isEmpty()) {
            ImGui.textColored(EditorStyle.COLOR_DANGER, "Save failed: " + saveError);
        }
    }

    private void save(SpriteTilemap tilemap, Path file) {
        try {
            TilemapDiskFile.write(tilemap, file, services.assets().locator());
            saveError = "";
        } catch (IOException unwritable) {
            saveError = unwritable.getMessage() == null ? "write failed" : unwritable.getMessage();
        }
    }

    private static Optional<Path> tilemapFile(TilemapRenderer renderer) {
        String path = renderer.tilemapRef().path();
        if (path.isEmpty()) {
            return Optional.empty();
        }
        Path file = Path.of(path);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    private void renderPalette(SpriteTilemap tilemap, SpriteAtlas atlas) {
        Optional<ImagePreviewTexture.PreviewImage> image = texturePreview(tilemap, atlas);
        if (image.isEmpty()) {
            ImGui.textDisabled("Atlas texture preview unavailable");
            return;
        }
        SpriteAtlasGrid grid = atlas.grid().get();
        AtlasCanvas canvas = drawImage(image.get());
        drawGrid(canvas, grid);
        drawTileMarkers(canvas, tilemap, grid);
        drawBrushHighlight(canvas, grid);
        describeHoveredTile(canvas, tilemap, grid);
        handlePick(canvas, grid);
    }

    public Optional<Path> atlasTextureFile(SpriteTilemap tilemap, SpriteAtlas atlas) {
        if (atlas.texturePath().isEmpty() || tilemap.atlasPath().isEmpty()) {
            return Optional.empty();
        }
        String rebased = NestedAssetPaths.rebase(
                LegacyAssetReferences.interpret(tilemap.atlasPath(), services.assets()),
                atlas.texturePath());
        return services.assets().locator()
                .file(LegacyAssetReferences.interpret(rebased, services.assets()))
                .filter(Files::isRegularFile);
    }

    private Optional<ImagePreviewTexture.PreviewImage> texturePreview(SpriteTilemap tilemap, SpriteAtlas atlas) {
        return atlasTextureFile(tilemap, atlas).flatMap(preview::get);
    }

    private static AtlasCanvas drawImage(ImagePreviewTexture.PreviewImage image) {
        float width = Math.max(32.0f, ImGui.getContentRegionAvailX());
        float height = width * image.height() / image.width();
        if (height > MAXIMUM_IMAGE_HEIGHT) {
            width = width * MAXIMUM_IMAGE_HEIGHT / height;
            height = MAXIMUM_IMAGE_HEIGHT;
        }
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.image(image.textureId(), width, height);
        return new AtlasCanvas(originX, originY, width, height);
    }

    private static void drawGrid(AtlasCanvas canvas, SpriteAtlasGrid grid) {
        int columns = Math.max(1, grid.columns());
        int rows = Math.max(1, grid.rows());
        for (int column = 0; column <= columns; column++) {
            float x = canvas.originX() + column * canvas.width() / columns;
            ImGui.getWindowDrawList().addLine(x, canvas.originY(), x,
                    canvas.originY() + canvas.height(), COLOR_GRID_LINE);
        }
        for (int row = 0; row <= rows; row++) {
            float y = canvas.originY() + row * canvas.height() / rows;
            ImGui.getWindowDrawList().addLine(canvas.originX(), y,
                    canvas.originX() + canvas.width(), y, COLOR_GRID_LINE);
        }
    }

    private static void drawTileMarkers(AtlasCanvas canvas, SpriteTilemap tilemap, SpriteAtlasGrid grid) {
        int columns = Math.max(1, grid.columns());
        int rows = Math.max(1, grid.rows());
        for (int tileIndex = 0; tileIndex < columns * rows; tileIndex++) {
            drawMarkersForTile(canvas, tilemap, tileIndex, columns, rows);
        }
    }

    private static void drawMarkersForTile(AtlasCanvas canvas, SpriteTilemap tilemap, int tileIndex,
                                           int columns, int rows) {
        float cellWidth = canvas.width() / columns;
        float cellHeight = canvas.height() / rows;
        float minX = canvas.originX() + (tileIndex % columns) * cellWidth;
        float minY = canvas.originY() + (tileIndex / columns) * cellHeight;
        float maxX = minX + cellWidth;
        float maxY = minY + cellHeight;
        ImDrawList drawList = ImGui.getWindowDrawList();
        List<TileCollisionShape> shapes = tilemap.collisionShapesOf(tileIndex);
        if (!shapes.isEmpty()) {
            TileMarkerPainter.drawCollisionShapes(drawList, shapes, minX, minY, maxX, maxY);
        } else if (tilemap.isSolidTile(tileIndex)) {
            TileMarkerPainter.drawSolidMarker(drawList, minX, minY, maxX, maxY);
        }
        drawStatusMarkers(drawList, tilemap, tileIndex, minX, minY, maxX, maxY);
    }

    private static void drawStatusMarkers(ImDrawList drawList, SpriteTilemap tilemap, int tileIndex,
                                          float minX, float minY, float maxX, float maxY) {
        if (tilemap.existingTileData(tileIndex).map(TileData::participatesInTerrain).orElse(false)) {
            TileMarkerPainter.drawTerrainDot(drawList, minX, minY, maxX, maxY);
        }
        if (tilemap.sceneForTile(tileIndex).isPresent()) {
            TileMarkerPainter.drawSceneMarker(drawList, minX, minY, maxX, maxY);
        }
    }

    private void drawBrushHighlight(AtlasCanvas canvas, SpriteAtlasGrid grid) {
        int columns = Math.max(1, grid.columns());
        int rows = Math.max(1, grid.rows());
        if (brush.tileIndex() < 0 || brush.tileIndex() >= columns * rows) {
            return;
        }
        float cellWidth = canvas.width() / columns;
        float cellHeight = canvas.height() / rows;
        float x = canvas.originX() + (brush.tileIndex() % columns) * cellWidth;
        float y = canvas.originY() + (brush.tileIndex() / columns) * cellHeight;
        ImGui.getWindowDrawList().addRectFilled(x, y, x + cellWidth, y + cellHeight, COLOR_SELECTED_FILL);
        ImGui.getWindowDrawList().addRect(x, y, x + cellWidth, y + cellHeight, COLOR_SELECTED_BORDER);
    }

    private static void describeHoveredTile(AtlasCanvas canvas, SpriteTilemap tilemap, SpriteAtlasGrid grid) {
        if (!ImGui.isItemHovered()) {
            return;
        }
        int tileIndex = canvas.cellIndexAt(ImGui.getMousePosX(), ImGui.getMousePosY(),
                Math.max(1, grid.columns()), Math.max(1, grid.rows()));
        ImGui.setTooltip("Tile " + tileIndex + "\n" + describeTile(tilemap, tileIndex));
    }

    private static String describeTile(SpriteTilemap tilemap, int tileIndex) {
        StringBuilder description = new StringBuilder();
        if (!tilemap.collisionShapesOf(tileIndex).isEmpty()) {
            description.append("cyan outline: custom collision shape\n");
        } else if (tilemap.isSolidTile(tileIndex)) {
            description.append("red outline: whole cell is solid\n");
        }
        if (tilemap.existingTileData(tileIndex).map(TileData::participatesInTerrain).orElse(false)) {
            description.append("green dot: belongs to a terrain\n");
        }
        tilemap.sceneForTile(tileIndex).ifPresent(path ->
                description.append("magenta diamond: spawns ").append(path).append('\n'));
        return description.isEmpty() ? "no collision, no terrain" : description.toString().strip();
    }

    private void handlePick(AtlasCanvas canvas, SpriteAtlasGrid grid) {
        if (!ImGui.isItemHovered() || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        brush.setTileIndex(canvas.cellIndexAt(ImGui.getMousePosX(), ImGui.getMousePosY(),
                Math.max(1, grid.columns()), Math.max(1, grid.rows())));
    }

    public void dispose() {
        preview.dispose();
    }
}
