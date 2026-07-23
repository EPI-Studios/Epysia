package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.assets.loaders.TexturePathPrefixes;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImInt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class AtlasInspectorSection {

    public static final String EXTENSION = ".epyatlas";

    private static final int COLOR_GRID_LINE = 0x66FFFFFF;
    private static final int COLOR_EXPLICIT_REGION = 0xC050C878;
    private static final int COLOR_SELECTED_FILL = 0x40CC7A00;
    private static final int COLOR_SELECTED_BORDER = 0xFFCC7A00;
    private static final float PREVIEW_MAX_HEIGHT = 320.0f;

    private record Canvas(float originX, float originY, float width, float height) {

        float uvToScreenX(float u) {
            return originX + u * width;
        }

        float uvToScreenY(float v) {
            return originY + (1.0f - v) * height;
        }
    }

    private final SpriteAtlasJsonCodec codec = new SpriteAtlasJsonCodec();
    private final ImagePreviewTexture preview;
    private final Consumer<Path> onAtlasSaved;
    private final ImInt editCellWidth = new ImInt(32);
    private final ImInt editCellHeight = new ImInt(32);
    private final ImInt editColumns = new ImInt(1);
    private final ImInt editRows = new ImInt(1);
    private Optional<SpriteAtlas> cachedAtlas = Optional.empty();
    private String cachedPath = "";
    private long cachedModifiedMillis;
    private String cachedError = "";
    private int selectedIndex = -1;
    private int textureWidth = 1;
    private int textureHeight = 1;

    public AtlasInspectorSection(ImagePreviewTexture preview, Consumer<Path> onAtlasSaved) {
        this.preview = preview;
        this.onAtlasSaved = onAtlasSaved;
    }

    public boolean render(Optional<Path> selectedAsset) {
        Optional<Path> atlasPath = selectedAsset.filter(path ->
                path.getFileName().toString().endsWith(EXTENSION) && Files.isRegularFile(path));
        if (atlasPath.isEmpty()) {
            return false;
        }
        Path path = atlasPath.get();
        refreshCache(path);
        renderContent(path);
        return true;
    }

    private void refreshCache(Path path) {
        long modifiedMillis = modifiedMillisOf(path);
        if (path.toString().equals(cachedPath) && modifiedMillis == cachedModifiedMillis) {
            return;
        }
        cachedPath = path.toString();
        cachedModifiedMillis = modifiedMillis;
        selectedIndex = -1;
        try {
            cachedAtlas = Optional.of(codec.read(Files.readString(path)));
            cachedError = "";
            cachedAtlas.flatMap(SpriteAtlas::grid).ifPresent(this::syncEditFields);
        } catch (IOException | RuntimeException unreadable) {
            cachedAtlas = Optional.empty();
            cachedError = unreadable.getMessage() == null ? "unreadable atlas" : unreadable.getMessage();
        }
    }

    private void syncEditFields(SpriteAtlasGrid grid) {
        editCellWidth.set(Math.max(1, grid.cellWidth()));
        editCellHeight.set(Math.max(1, grid.cellHeight()));
        editColumns.set(Math.max(1, grid.columns()));
        editRows.set(Math.max(1, grid.rows()));
    }

    private void renderContent(Path path) {
        ImGui.textUnformatted(path.getFileName().toString());
        ImGui.separator();
        if (cachedAtlas.isEmpty()) {
            ImGui.textDisabled("Could not parse atlas: " + cachedError);
            return;
        }
        SpriteAtlas atlas = cachedAtlas.get();
        ImGui.textDisabled("Texture");
        ImGui.textUnformatted(atlas.texturePath().isEmpty() ? "(none)" : atlas.texturePath());
        renderSlicer(path, atlas);
        renderRegions(atlas);
    }

    private void renderSlicer(Path path, SpriteAtlas atlas) {
        Optional<ImagePreviewTexture.PreviewImage> image = texturePreview(path, atlas);
        if (image.isEmpty()) {
            ImGui.textDisabled("Texture preview unavailable");
            return;
        }
        textureWidth = image.get().width();
        textureHeight = image.get().height();
        ImGui.separator();
        renderGridFields();
        Canvas canvas = drawTextureImage(image.get());
        drawGridOverlay(canvas);
        drawExplicitRegions(canvas, atlas);
        drawSelectedCell(canvas);
        handleCellClick(canvas);
        renderSelectedCellInfo();
        renderSaveButton(path, atlas);
    }

    private Optional<ImagePreviewTexture.PreviewImage> texturePreview(Path path, SpriteAtlas atlas) {
        if (atlas.texturePath().isEmpty()) {
            return Optional.empty();
        }
        String stripped = TexturePathPrefixes.stripPrefixes(atlas.texturePath());
        Path texture = Path.of(stripped);
        if (!texture.isAbsolute()) {
            texture = path.toAbsolutePath().getParent().resolve(stripped).normalize();
        }
        if (!Files.isRegularFile(texture)) {
            return Optional.empty();
        }
        return preview.get(texture);
    }

    private void renderGridFields() {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.4f);
        if (ImGui.inputInt("Cell W", editCellWidth)) {
            clampCellFields();
            editColumns.set(Math.max(1, textureWidth / editCellWidth.get()));
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.6f);
        if (ImGui.inputInt("Cell H", editCellHeight)) {
            clampCellFields();
            editRows.set(Math.max(1, textureHeight / editCellHeight.get()));
        }
        renderCountFields();
    }

    private void renderCountFields() {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.4f);
        if (ImGui.inputInt("Columns", editColumns)) {
            clampCellFields();
            editCellWidth.set(Math.max(1, textureWidth / editColumns.get()));
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.6f);
        if (ImGui.inputInt("Rows", editRows)) {
            clampCellFields();
            editCellHeight.set(Math.max(1, textureHeight / editRows.get()));
        }
    }

    private void clampCellFields() {
        editCellWidth.set(Math.clamp(editCellWidth.get(), 1, Math.max(1, textureWidth)));
        editCellHeight.set(Math.clamp(editCellHeight.get(), 1, Math.max(1, textureHeight)));
        editColumns.set(Math.clamp(editColumns.get(), 1, Math.max(1, textureWidth)));
        editRows.set(Math.clamp(editRows.get(), 1, Math.max(1, textureHeight)));
        selectedIndex = -1;
    }

    private Canvas drawTextureImage(ImagePreviewTexture.PreviewImage image) {
        float width = Math.max(32.0f, ImGui.getContentRegionAvailX());
        float height = width * image.height() / image.width();
        if (height > PREVIEW_MAX_HEIGHT) {
            width = width * PREVIEW_MAX_HEIGHT / height;
            height = PREVIEW_MAX_HEIGHT;
        }
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.image(image.textureId(), width, height);
        return new Canvas(originX, originY, width, height);
    }

    private void drawGridOverlay(Canvas canvas) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        int columns = Math.max(1, editColumns.get());
        int rows = Math.max(1, editRows.get());
        for (int column = 0; column <= columns; column++) {
            float x = canvas.originX() + column * canvas.width() / columns;
            drawList.addLine(x, canvas.originY(), x, canvas.originY() + canvas.height(), COLOR_GRID_LINE);
        }
        for (int row = 0; row <= rows; row++) {
            float y = canvas.originY() + row * canvas.height() / rows;
            drawList.addLine(canvas.originX(), y, canvas.originX() + canvas.width(), y, COLOR_GRID_LINE);
        }
    }

    private void drawExplicitRegions(Canvas canvas, SpriteAtlas atlas) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        for (SpriteAtlasRegion region : atlas.explicitRegions()) {
            drawList.addRect(canvas.uvToScreenX(region.minU()), canvas.uvToScreenY(region.maxV()),
                    canvas.uvToScreenX(region.maxU()), canvas.uvToScreenY(region.minV()),
                    COLOR_EXPLICIT_REGION);
        }
    }

    private void drawSelectedCell(Canvas canvas) {
        if (selectedIndex < 0) {
            return;
        }
        Optional<SpriteAtlasRegion> region = selectedRegion();
        if (region.isEmpty()) {
            return;
        }
        float left = canvas.uvToScreenX(region.get().minU());
        float top = canvas.uvToScreenY(region.get().maxV());
        float right = canvas.uvToScreenX(region.get().maxU());
        float bottom = canvas.uvToScreenY(region.get().minV());
        ImGui.getWindowDrawList().addRectFilled(left, top, right, bottom, COLOR_SELECTED_FILL);
        ImGui.getWindowDrawList().addRect(left, top, right, bottom, COLOR_SELECTED_BORDER);
    }

    private void handleCellClick(Canvas canvas) {
        if (!ImGui.isItemHovered() || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        int columns = Math.max(1, editColumns.get());
        int rows = Math.max(1, editRows.get());
        int column = (int) ((ImGui.getMousePosX() - canvas.originX()) / (canvas.width() / columns));
        int row = (int) ((ImGui.getMousePosY() - canvas.originY()) / (canvas.height() / rows));
        column = Math.clamp(column, 0, columns - 1);
        row = Math.clamp(row, 0, rows - 1);
        selectedIndex = row * columns + column;
    }

    private Optional<SpriteAtlasRegion> selectedRegion() {
        int columns = Math.max(1, editColumns.get());
        int rows = Math.max(1, editRows.get());
        if (selectedIndex < 0 || selectedIndex >= columns * rows) {
            return Optional.empty();
        }
        int row = selectedIndex / columns;
        int column = selectedIndex % columns;
        float minU = (float) column / columns;
        float maxU = (float) (column + 1) / columns;
        float minV = (float) (rows - row - 1) / rows;
        float maxV = (float) (rows - row) / rows;
        return Optional.of(new SpriteAtlasRegion(Integer.toString(selectedIndex), minU, minV, maxU, maxV));
    }

    private void renderSelectedCellInfo() {
        Optional<SpriteAtlasRegion> region = selectedRegion();
        if (region.isEmpty()) {
            ImGui.textDisabled("Click a cell to inspect it");
            return;
        }
        ImGui.textUnformatted("Region \"" + region.get().name() + "\"");
        ImGui.sameLine();
        ImGui.textDisabled(String.format("uv [%.3f, %.3f] - [%.3f, %.3f]",
                region.get().minU(), region.get().minV(), region.get().maxU(), region.get().maxV()));
    }

    private void renderSaveButton(Path path, SpriteAtlas atlas) {
        if (!ImGui.button("Save Atlas", ImGui.getContentRegionAvailX(), 0.0f)) {
            return;
        }
        SpriteAtlasGrid grid = new SpriteAtlasGrid(editCellWidth.get(), editCellHeight.get(),
                editColumns.get(), editRows.get());
        SpriteAtlas updated = SpriteAtlas.gridAtlas(atlas.texturePath(), grid, atlas.explicitRegions());
        try {
            Files.writeString(path, codec.write(updated));
            onAtlasSaved.accept(path);
        } catch (IOException unwritable) {
            cachedError = unwritable.getMessage() == null ? "write failed" : unwritable.getMessage();
        }
    }

    private void renderRegions(SpriteAtlas atlas) {
        ImGui.textDisabled(atlas.regionCount() + " regions");
        ImGui.beginChild("##atlas-regions", 0.0f, 0.0f, true);
        for (String name : atlas.regionNames()) {
            atlas.region(name).ifPresent(AtlasInspectorSection::renderRegion);
        }
        ImGui.endChild();
    }

    private static void renderRegion(SpriteAtlasRegion region) {
        ImGui.textUnformatted(region.name());
        ImGui.sameLine();
        ImGui.textDisabled(String.format("uv [%.3f, %.3f] - [%.3f, %.3f]",
                region.minU(), region.minV(), region.maxU(), region.maxV()));
    }

    private static long modifiedMillisOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }
}
