package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.loaders.TexturePathPrefixes;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.assets.TilemapDiskFile;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public final class TilePalettePanel {

    private static final float MAXIMUM_IMAGE_HEIGHT = 260.0f;
    private static final float SOLID_MARKER_INSET = 3.0f;
    private static final float SOLID_MARKER_SIZE = 8.0f;
    private static final int COLOR_GRID_LINE = 0x66FFFFFF;
    private static final int COLOR_SELECTED_FILL = 0x40CC7A00;
    private static final int COLOR_SELECTED_BORDER = 0xFFCC7A00;
    private static final int COLOR_SOLID_MARKER = 0xE03355FF;
    private static final int COLOR_SHAPE_MARKER = 0xE000CCFF;

    private final ImagePreviewTexture preview;
    private final EngineServices services;
    private final Supplier<SceneDocument> activeDocument;
    private final TileBrush brush = new TileBrush();
    private final TileToolBar toolBar;
    private final TileLayersSection layersSection;
    private final TileTerrainsSection terrainsSection;
    private final TileDataSection dataSection;
    private String saveError = "";

    public TilePalettePanel(OpenGlRenderBackend backend, EngineServices services,
                            Supplier<SceneDocument> activeDocument, IconWidgets icons) {
        this.preview = new ImagePreviewTexture(backend);
        this.services = services;
        this.activeDocument = activeDocument;
        this.toolBar = new TileToolBar(icons, brush);
        this.layersSection = new TileLayersSection(icons, brush);
        this.terrainsSection = new TileTerrainsSection(icons, brush);
        this.dataSection = new TileDataSection(brush);
    }

    public TileBrush brush() {
        return brush;
    }

    public void render(TilemapRenderer renderer) {
        renderer.refresh(services);
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<SpriteAtlas> atlas = renderer.atlasValue();
        if (tilemap.isEmpty() || atlas.isEmpty() || atlas.get().grid().isEmpty()) {
            ImGui.textDisabled("Assign a tilemap backed by a grid atlas to paint tiles.");
            return;
        }
        ImGui.spacing();
        ImGui.textDisabled("Tile Palette");
        ImGui.separator();
        renderControls(tilemap.get(), renderer);
        renderPalette(tilemap.get(), atlas.get());
    }

    private void renderControls(SpriteTilemap tilemap, TilemapRenderer renderer) {
        toolBar.render(tilemap);
        boolean changed = layersSection.render(tilemap);
        changed |= terrainsSection.render(tilemap);
        changed |= dataSection.render(tilemap);
        if (changed) {
            activeDocument.get().markDirty();
        }
        renderSaveRow(tilemap, renderer);
    }

    private void renderSaveRow(SpriteTilemap tilemap, TilemapRenderer renderer) {
        Optional<Path> file = tilemapFile(renderer);
        boolean dirty = file.map(path -> !TilemapDiskFile.matchesDisk(tilemap, path)).orElse(true);
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
            TilemapDiskFile.write(tilemap, file);
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
        drawSolidMarkers(canvas, tilemap, grid);
        drawShapeMarkers(canvas, tilemap, grid);
        drawBrushHighlight(canvas, grid);
        handlePick(canvas, grid);
    }

    private Optional<ImagePreviewTexture.PreviewImage> texturePreview(SpriteTilemap tilemap, SpriteAtlas atlas) {
        if (atlas.texturePath().isEmpty() || tilemap.atlasPath().isEmpty()) {
            return Optional.empty();
        }
        String stripped = TexturePathPrefixes.stripPrefixes(atlas.texturePath());
        Path texture = Path.of(stripped);
        if (!texture.isAbsolute()) {
            Path atlasParent = Path.of(TexturePathPrefixes.stripPrefixes(tilemap.atlasPath()))
                    .toAbsolutePath().getParent();
            texture = atlasParent.resolve(stripped).normalize();
        }
        if (!Files.isRegularFile(texture)) {
            return Optional.empty();
        }
        return preview.get(texture);
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

    private static void drawSolidMarkers(AtlasCanvas canvas, SpriteTilemap tilemap, SpriteAtlasGrid grid) {
        int columns = Math.max(1, grid.columns());
        int rows = Math.max(1, grid.rows());
        for (int tileIndex : tilemap.solidTiles()) {
            if (tileIndex < 0 || tileIndex >= columns * rows) {
                continue;
            }
            float cellWidth = canvas.width() / columns;
            float cellHeight = canvas.height() / rows;
            float x = canvas.originX() + (tileIndex % columns) * cellWidth + SOLID_MARKER_INSET;
            float y = canvas.originY() + (tileIndex / columns) * cellHeight + SOLID_MARKER_INSET;
            ImGui.getWindowDrawList().addRectFilled(x, y, x + SOLID_MARKER_SIZE,
                    y + SOLID_MARKER_SIZE, COLOR_SOLID_MARKER);
        }
    }

    private static void drawShapeMarkers(AtlasCanvas canvas, SpriteTilemap tilemap, SpriteAtlasGrid grid) {
        int columns = Math.max(1, grid.columns());
        int rows = Math.max(1, grid.rows());
        for (Integer tileIndex : tilemap.tileDataByIndex().keySet()) {
            if (tileIndex < 0 || tileIndex >= columns * rows || tilemap.collisionShapesOf(tileIndex).isEmpty()) {
                continue;
            }
            float cellWidth = canvas.width() / columns;
            float cellHeight = canvas.height() / rows;
            float x = canvas.originX() + (tileIndex % columns) * cellWidth + SOLID_MARKER_INSET;
            float y = canvas.originY() + (tileIndex / columns + 1) * cellHeight - SOLID_MARKER_INSET;
            ImGui.getWindowDrawList().addTriangleFilled(x, y, x + SOLID_MARKER_SIZE, y,
                    x + SOLID_MARKER_SIZE, y - SOLID_MARKER_SIZE, COLOR_SHAPE_MARKER);
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
