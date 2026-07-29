package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epyatlas.SpriteAnimation;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class SpriteEditorWindow {

    public static final String WINDOW_TITLE = "Sprite Editor";

    private static final float DEFAULT_WINDOW_WIDTH = 960.0f;
    private static final float DEFAULT_WINDOW_HEIGHT = 640.0f;
    private static final float LIST_PANEL_WIDTH = 230.0f;
    private static final float STRIP_HEIGHT = 132.0f;
    private static final float PREVIEW_PANEL_WIDTH = 170.0f;
    private static final float THUMBNAIL_SIZE = 72.0f;
    private static final float PREVIEW_IMAGE_SIZE = 72.0f;
    private static final float ZOOM_MINIMUM = 0.25f;
    private static final float ZOOM_MAXIMUM = 12.0f;
    private static final float ZOOM_NOTCH = 1.15f;
    private static final float DRAG_THRESHOLD = 4.0f;
    private static final float FPS_MINIMUM = 1.0f;
    private static final float FPS_MAXIMUM = 60.0f;
    private static final float SLICE_INFO_ROWS_HEIGHT = 96.0f;
    private static final int COLOR_GRID_LINE = 0x66FFFFFF;
    private static final int COLOR_EXPLICIT_REGION = 0xC050C878;
    private static final int COLOR_SELECTED_FILL = 0x40CC7A00;
    private static final int COLOR_SELECTED_BORDER = 0xFFCC7A00;
    private static final int COLOR_USED_CELL_FILL = 0x3300CC66;
    private static final int COLOR_BADGE_BACKGROUND = 0xCC202020;
    private static final int COLOR_BADGE_TEXT = 0xFFFFFFFF;
    private static final int COLOR_RUBBER_BAND_FILL = 0x330077CC;
    private static final int COLOR_RUBBER_BAND_BORDER = 0xFF3399DD;
    private static final int COLOR_ORPHAN_TINT = 0x60FF3333;
    private static final String FRAME_PAYLOAD = "sprite-editor-frame-index";
    private static final String DEFAULT_ANIMATION_NAME = "new animation";

    private static final class AnimationDraft {

        private String name;
        private float framesPerSecond;
        private boolean loop;
        private final List<String> frames = new ArrayList<>();

        private AnimationDraft(String name, float framesPerSecond, boolean loop, List<String> frames) {
            this.name = name;
            this.framesPerSecond = framesPerSecond;
            this.loop = loop;
            this.frames.addAll(frames);
        }
    }

    private record FrameUsage(int firstIndex, int count) {
    }

    private final SpriteAtlasJsonCodec codec = new SpriteAtlasJsonCodec();
    private final ImagePreviewTexture preview;
    private final AssetLocator locator;
    private final Consumer<Path> onAtlasSaved;
    private final ConfirmDialog discardConfirm = new ConfirmDialog("Unsaved sprite changes", "Discard");
    private final ImInt editCellWidth = new ImInt(32);
    private final ImInt editCellHeight = new ImInt(32);
    private final ImInt editColumns = new ImInt(1);
    private final ImInt editRows = new ImInt(1);
    private final ImString nameInput = new ImString(128);
    private final List<AnimationDraft> animations = new ArrayList<>();
    private final List<SpriteAtlasRegion> explicitRegions = new ArrayList<>();
    private Optional<Path> atlasPath = Optional.empty();
    private String storedTexturePath = "";
    private String loadError = "";
    private String saveError = "";
    private String nameError = "";
    private boolean visible;
    private boolean dirty;
    private int textureWidth = 1;
    private int textureHeight = 1;
    private int sliceSelectedCell = -1;
    private int selectedAnimation = -1;
    private int selectedFrame = -1;
    private float zoom = 1.0f;
    private float panX;
    private float panY;
    private boolean rubberBandActive;
    private float rubberBandStartX;
    private float rubberBandStartY;
    private boolean previewPlaying = true;
    private float previewTimeSeconds;

    public SpriteEditorWindow(ImagePreviewTexture preview, AssetLocator locator, Consumer<Path> onAtlasSaved) {
        this.preview = preview;
        this.locator = locator;
        this.onAtlasSaved = onAtlasSaved;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean value) {
        visible = value;
    }

    public void open(Path path) {
        visible = true;
        if (atlasPath.filter(current -> current.equals(path)).isPresent()) {
            return;
        }
        if (dirty) {
            discardConfirm.open("Discard unsaved changes to " + atlasFileName() + "?", () -> load(path));
            return;
        }
        load(path);
    }

    private String atlasFileName() {
        return atlasPath.map(path -> path.getFileName().toString()).orElse("(none)");
    }

    private void load(Path path) {
        atlasPath = Optional.of(path);
        resetSelectionState();
        try {
            applyLoadedAtlas(codec.read(Files.readString(path)));
            loadError = "";
        } catch (IOException | RuntimeException unreadable) {
            loadError = unreadable.getMessage() == null ? "unreadable atlas" : unreadable.getMessage();
        }
    }

    private void resetSelectionState() {
        dirty = false;
        saveError = "";
        nameError = "";
        sliceSelectedCell = -1;
        selectedAnimation = -1;
        selectedFrame = -1;
        zoom = 1.0f;
        panX = 0.0f;
        panY = 0.0f;
        rubberBandActive = false;
        previewTimeSeconds = 0.0f;
        animations.clear();
        explicitRegions.clear();
    }

    private void applyLoadedAtlas(SpriteAtlas atlas) {
        storedTexturePath = atlas.texturePath();
        explicitRegions.addAll(atlas.explicitRegions());
        atlas.grid().ifPresent(this::syncEditFields);
        for (SpriteAnimation animation : atlas.animations()) {
            animations.add(new AnimationDraft(animation.name(), animation.framesPerSecond(),
                    animation.loop(), animation.frames()));
        }
        if (!animations.isEmpty()) {
            selectAnimation(0);
        }
    }

    private void syncEditFields(SpriteAtlasGrid grid) {
        editCellWidth.set(Math.max(1, grid.cellWidth()));
        editCellHeight.set(Math.max(1, grid.cellHeight()));
        editColumns.set(Math.max(1, grid.columns()));
        editRows.set(Math.max(1, grid.rows()));
    }

    public void render() {
        discardConfirm.render();
        if (!visible) {
            return;
        }
        ImGui.setNextWindowSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, ImGuiCond.FirstUseEver);
        ImBoolean keepOpen = new ImBoolean(true);
        if (ImGui.begin(WINDOW_TITLE, keepOpen)) {
            renderBody();
        }
        ImGui.end();
        handleCloseRequest(keepOpen);
    }

    private void handleCloseRequest(ImBoolean keepOpen) {
        if (keepOpen.get()) {
            return;
        }
        if (!dirty) {
            visible = false;
            return;
        }
        discardConfirm.open("Discard unsaved changes to " + atlasFileName() + "?", () -> {
            dirty = false;
            visible = false;
        });
    }

    private void renderBody() {
        if (atlasPath.isEmpty()) {
            ImGui.textDisabled("Double-click a .epyatlas asset to edit it here.");
            return;
        }
        if (!loadError.isEmpty()) {
            ImGui.textDisabled("Could not parse atlas: " + loadError);
            return;
        }
        renderHeader();
        if (!ImGui.beginTabBar("##sprite-editor-tabs")) {
            return;
        }
        renderSliceTabItem();
        renderAnimationsTabItem();
        ImGui.endTabBar();
    }

    private void renderHeader() {
        ImGui.textUnformatted(atlasFileName() + (dirty ? " *" : ""));
        ImGui.sameLine();
        ImGui.textDisabled(storedTexturePath.isEmpty() ? "(no texture)" : storedTexturePath);
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 60.0f);
        if (ImGui.button("Save", 60.0f, 0.0f)) {
            save();
        }
        if (!saveError.isEmpty()) {
            ImGui.textColored(EditorStyle.COLOR_DANGER, "Save failed: " + saveError);
        }
        ImGui.separator();
    }

    private void renderSliceTabItem() {
        if (!ImGui.beginTabItem("Slice")) {
            return;
        }
        renderSliceTab();
        ImGui.endTabItem();
    }

    private void renderAnimationsTabItem() {
        if (!ImGui.beginTabItem("Animations")) {
            return;
        }
        renderAnimationsTab();
        ImGui.endTabItem();
    }

    private void save() {
        atlasPath.ifPresent(this::saveTo);
    }

    private void saveTo(Path path) {
        SpriteAtlas updated = SpriteAtlas.gridAtlas(storedTexturePath, currentGrid(),
                List.copyOf(explicitRegions), builtAnimations());
        try {
            Files.writeString(path, codec.write(updated));
            dirty = false;
            saveError = "";
            onAtlasSaved.accept(path);
        } catch (IOException unwritable) {
            saveError = unwritable.getMessage() == null ? "write failed" : unwritable.getMessage();
        }
    }

    private SpriteAtlasGrid currentGrid() {
        return new SpriteAtlasGrid(editCellWidth.get(), editCellHeight.get(),
                editColumns.get(), editRows.get());
    }

    private List<SpriteAnimation> builtAnimations() {
        List<SpriteAnimation> built = new ArrayList<>();
        for (AnimationDraft draft : animations) {
            built.add(new SpriteAnimation(draft.name, draft.framesPerSecond, draft.loop,
                    List.copyOf(draft.frames)));
        }
        return built;
    }

    private Optional<ImagePreviewTexture.PreviewImage> texturePreview() {
        if (storedTexturePath.isEmpty() || atlasPath.isEmpty()) {
            return Optional.empty();
        }
        Optional<Path> texture = locator
                .file(LegacyAssetReferences.interpretWithoutMigration(storedTexturePath, locator))
                .filter(Files::isRegularFile);
        if (texture.isEmpty()) {
            return Optional.empty();
        }
        Optional<ImagePreviewTexture.PreviewImage> image = preview.get(texture.get());
        image.ifPresent(this::rememberTextureSize);
        return image;
    }

    private void rememberTextureSize(ImagePreviewTexture.PreviewImage image) {
        textureWidth = image.width();
        textureHeight = image.height();
    }

    private void renderSliceTab() {
        Optional<ImagePreviewTexture.PreviewImage> image = texturePreview();
        if (image.isEmpty()) {
            ImGui.textDisabled("Texture preview unavailable");
            return;
        }
        renderGridFields();
        AtlasCanvas canvas = drawSliceImage(image.get());
        drawGridOverlay(canvas);
        drawExplicitRegionOutlines(canvas);
        drawSliceSelectedCell(canvas);
        handleSliceCellClick(canvas);
        renderSliceSelectedCellInfo();
    }

    private void renderGridFields() {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.2f);
        if (ImGui.inputInt("Cell W", editCellWidth)) {
            clampCellFields();
            editColumns.set(Math.max(1, textureWidth / editCellWidth.get()));
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.25f);
        if (ImGui.inputInt("Cell H", editCellHeight)) {
            clampCellFields();
            editRows.set(Math.max(1, textureHeight / editCellHeight.get()));
        }
        renderCountFields();
    }

    private void renderCountFields() {
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.33f);
        if (ImGui.inputInt("Columns", editColumns)) {
            clampCellFields();
            editCellWidth.set(Math.max(1, textureWidth / editColumns.get()));
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f);
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
        sliceSelectedCell = -1;
        dirty = true;
    }

    private AtlasCanvas drawSliceImage(ImagePreviewTexture.PreviewImage image) {
        float maxHeight = Math.max(64.0f, ImGui.getContentRegionAvailY() - SLICE_INFO_ROWS_HEIGHT);
        float width = Math.max(32.0f, ImGui.getContentRegionAvailX());
        float height = width * image.height() / image.width();
        if (height > maxHeight) {
            width = width * maxHeight / height;
            height = maxHeight;
        }
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.image(image.textureId(), width, height);
        return new AtlasCanvas(originX, originY, width, height);
    }

    private void drawGridOverlay(AtlasCanvas canvas) {
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

    private void drawExplicitRegionOutlines(AtlasCanvas canvas) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        for (SpriteAtlasRegion region : explicitRegions) {
            drawList.addRect(canvas.uvToScreenX(region.minU()), canvas.uvToScreenY(region.maxV()),
                    canvas.uvToScreenX(region.maxU()), canvas.uvToScreenY(region.minV()),
                    COLOR_EXPLICIT_REGION);
        }
    }

    private void drawSliceSelectedCell(AtlasCanvas canvas) {
        Optional<SpriteAtlasRegion> region = gridRegionFor(sliceSelectedCell);
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

    private void handleSliceCellClick(AtlasCanvas canvas) {
        if (!ImGui.isItemHovered() || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        sliceSelectedCell = canvas.cellIndexAt(ImGui.getMousePosX(), ImGui.getMousePosY(),
                Math.max(1, editColumns.get()), Math.max(1, editRows.get()));
    }

    private void renderSliceSelectedCellInfo() {
        Optional<SpriteAtlasRegion> region = gridRegionFor(sliceSelectedCell);
        if (region.isEmpty()) {
            ImGui.textDisabled("Click a cell to inspect it");
            return;
        }
        ImGui.textUnformatted("Region \"" + region.get().name() + "\"");
        ImGui.sameLine();
        ImGui.textDisabled(String.format("uv [%.3f, %.3f] - [%.3f, %.3f]",
                region.get().minU(), region.get().minV(), region.get().maxU(), region.get().maxV()));
    }

    private Optional<SpriteAtlasRegion> gridRegionFor(int index) {
        int columns = Math.max(1, editColumns.get());
        int rows = Math.max(1, editRows.get());
        if (index < 0 || index >= columns * rows) {
            return Optional.empty();
        }
        int row = index / columns;
        int column = index % columns;
        float minU = (float) column / columns;
        float maxU = (float) (column + 1) / columns;
        float minV = (float) (rows - row - 1) / rows;
        float maxV = (float) (rows - row) / rows;
        return Optional.of(new SpriteAtlasRegion(Integer.toString(index), minU, minV, maxU, maxV));
    }

    private Optional<SpriteAtlasRegion> resolveRegion(String name) {
        Optional<SpriteAtlasRegion> gridRegion = parseGridIndex(name).flatMap(this::gridRegionFor);
        if (gridRegion.isPresent()) {
            return gridRegion;
        }
        return explicitRegions.stream().filter(region -> region.name().equals(name)).findFirst();
    }

    private static Optional<Integer> parseGridIndex(String name) {
        try {
            return Optional.of(Integer.parseInt(name));
        } catch (NumberFormatException notNumeric) {
            return Optional.empty();
        }
    }

    private void renderAnimationsTab() {
        renderAnimationListPanel();
        ImGui.sameLine();
        ImGui.beginGroup();
        renderSheetPanel();
        renderFrameStripRow();
        ImGui.endGroup();
    }

    private Optional<AnimationDraft> selectedDraft() {
        if (selectedAnimation < 0 || selectedAnimation >= animations.size()) {
            return Optional.empty();
        }
        return Optional.of(animations.get(selectedAnimation));
    }

    private void selectAnimation(int index) {
        selectedAnimation = index;
        selectedFrame = -1;
        previewTimeSeconds = 0.0f;
        nameError = "";
        selectedDraft().ifPresent(draft -> nameInput.set(draft.name));
    }

    private void renderAnimationListPanel() {
        ImGui.beginChild("##animation-list", LIST_PANEL_WIDTH, 0.0f, true);
        renderAnimationListButtons();
        ImGui.separator();
        for (int index = 0; index < animations.size(); index++) {
            if (ImGui.selectable(animations.get(index).name + "##animation-" + index,
                    index == selectedAnimation)) {
                selectAnimation(index);
            }
        }
        selectedDraft().ifPresent(this::renderAnimationProperties);
        ImGui.endChild();
    }

    private void renderAnimationListButtons() {
        if (ImGui.button("Add")) {
            addAnimation(new AnimationDraft(uniqueAnimationName(DEFAULT_ANIMATION_NAME),
                    10.0f, true, List.of()));
        }
        ImGui.sameLine();
        Optional<AnimationDraft> selected = selectedDraft();
        ImGui.beginDisabled(selected.isEmpty());
        if (ImGui.button("Duplicate") && selected.isPresent()) {
            AnimationDraft source = selected.get();
            addAnimation(new AnimationDraft(uniqueAnimationName(source.name),
                    source.framesPerSecond, source.loop, source.frames));
        }
        ImGui.sameLine();
        if (ImGui.button("Delete") && selected.isPresent()) {
            deleteSelectedAnimation();
        }
        ImGui.endDisabled();
    }

    private void addAnimation(AnimationDraft draft) {
        animations.add(draft);
        selectAnimation(animations.size() - 1);
        dirty = true;
    }

    private void deleteSelectedAnimation() {
        animations.remove(selectedAnimation);
        dirty = true;
        selectAnimation(Math.min(selectedAnimation, animations.size() - 1));
    }

    private String uniqueAnimationName(String base) {
        if (isAnimationNameFree(base)) {
            return base;
        }
        int suffix = 2;
        while (!isAnimationNameFree(base + " " + suffix)) {
            suffix++;
        }
        return base + " " + suffix;
    }

    private boolean isAnimationNameFree(String candidate) {
        return animations.stream().noneMatch(draft -> draft.name.equals(candidate));
    }

    private void renderAnimationProperties(AnimationDraft draft) {
        ImGui.separator();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.inputText("##animation-name", nameInput)) {
            renameSelected(draft, nameInput.get().replace("\0", "").strip());
        }
        if (!nameError.isEmpty()) {
            ImGui.textColored(EditorStyle.COLOR_DANGER, nameError);
        }
        renderFpsAndLoop(draft);
    }

    private void renameSelected(AnimationDraft draft, String candidate) {
        if (candidate.isEmpty()) {
            nameError = "Name cannot be empty";
            return;
        }
        if (!candidate.equals(draft.name) && !isAnimationNameFree(candidate)) {
            nameError = "Name already used";
            return;
        }
        nameError = "";
        if (!candidate.equals(draft.name)) {
            draft.name = candidate;
            dirty = true;
        }
    }

    private void renderFpsAndLoop(AnimationDraft draft) {
        float[] framesPerSecond = {draft.framesPerSecond};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.6f);
        if (ImGui.dragFloat("FPS", framesPerSecond, 0.1f, FPS_MINIMUM, FPS_MAXIMUM)) {
            draft.framesPerSecond = Math.clamp(framesPerSecond[0], FPS_MINIMUM, FPS_MAXIMUM);
            dirty = true;
        }
        if (ImGui.checkbox("Loop", draft.loop)) {
            draft.loop = !draft.loop;
            dirty = true;
        }
    }

    private void renderSheetPanel() {
        float height = Math.max(64.0f, ImGui.getContentRegionAvailY() - STRIP_HEIGHT);
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ImGui.beginChild("##sheet-panel", 0.0f, height, true, flags);
        Optional<ImagePreviewTexture.PreviewImage> image = texturePreview();
        if (image.isEmpty()) {
            ImGui.textDisabled("Texture preview unavailable");
        } else {
            renderSheetCanvas(image.get());
        }
        ImGui.endChild();
    }

    private void renderSheetCanvas(ImagePreviewTexture.PreviewImage image) {
        float areaX = ImGui.getCursorScreenPosX();
        float areaY = ImGui.getCursorScreenPosY();
        float areaWidth = Math.max(32.0f, ImGui.getContentRegionAvailX());
        float areaHeight = Math.max(32.0f, ImGui.getContentRegionAvailY());
        ImGui.invisibleButton("##sheet-input", areaWidth, areaHeight);
        AtlasCanvas canvas = sheetCanvas(image, areaX, areaY, areaWidth, areaHeight);
        ImGui.getWindowDrawList().addImage(image.textureId(), canvas.originX(), canvas.originY(),
                canvas.originX() + canvas.width(), canvas.originY() + canvas.height());
        drawGridOverlay(canvas);
        drawUsedCellBadges(canvas);
        drawRubberBand(canvas);
        handleSheetInput(canvas);
    }

    private AtlasCanvas sheetCanvas(ImagePreviewTexture.PreviewImage image,
                                    float areaX, float areaY, float areaWidth, float areaHeight) {
        float fit = Math.min(areaWidth / image.width(), areaHeight / image.height());
        float width = image.width() * fit * zoom;
        float height = image.height() * fit * zoom;
        float originX = areaX + (areaWidth - width) * 0.5f + panX;
        float originY = areaY + (areaHeight - height) * 0.5f + panY;
        return new AtlasCanvas(originX, originY, width, height);
    }

    private void handleSheetInput(AtlasCanvas canvas) {
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            applyZoomWheel(canvas);
            applyMiddleDragPan();
        }
        handleRubberBand(canvas, hovered);
    }

    private void applyZoomWheel(AtlasCanvas canvas) {
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel == 0.0f) {
            return;
        }
        float previous = zoom;
        zoom = Math.clamp(zoom * (float) Math.pow(ZOOM_NOTCH, wheel), ZOOM_MINIMUM, ZOOM_MAXIMUM);
        float scale = zoom / previous;
        float centerX = canvas.originX() + canvas.width() * 0.5f;
        float centerY = canvas.originY() + canvas.height() * 0.5f;
        panX += (ImGui.getMousePosX() - centerX) * (1.0f - scale);
        panY += (ImGui.getMousePosY() - centerY) * (1.0f - scale);
    }

    private void applyMiddleDragPan() {
        if (!ImGui.isMouseDown(ImGuiMouseButton.Middle)) {
            return;
        }
        panX += ImGui.getIO().getMouseDeltaX();
        panY += ImGui.getIO().getMouseDeltaY();
    }

    private void handleRubberBand(AtlasCanvas canvas, boolean hovered) {
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left) && selectedDraft().isPresent()) {
            rubberBandActive = true;
            rubberBandStartX = ImGui.getMousePosX();
            rubberBandStartY = ImGui.getMousePosY();
        }
        if (rubberBandActive && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            rubberBandActive = false;
            commitRubberBand(canvas);
        }
    }

    private void commitRubberBand(AtlasCanvas canvas) {
        float endX = ImGui.getMousePosX();
        float endY = ImGui.getMousePosY();
        float travel = Math.abs(endX - rubberBandStartX) + Math.abs(endY - rubberBandStartY);
        int columns = Math.max(1, editColumns.get());
        int rows = Math.max(1, editRows.get());
        if (travel < DRAG_THRESHOLD) {
            appendFrame(canvas.cellIndexAt(endX, endY, columns, rows));
            return;
        }
        appendCoveredCells(canvas, endX, endY, columns, rows);
    }

    private void appendCoveredCells(AtlasCanvas canvas, float endX, float endY, int columns, int rows) {
        List<Integer> covered = coveredCells(canvas, endX, endY, columns, rows);
        if (covered.isEmpty()) {
            appendFrame(canvas.cellIndexAt(endX, endY, columns, rows));
            return;
        }
        covered.forEach(this::appendFrame);
    }

    private List<Integer> coveredCells(AtlasCanvas canvas, float endX, float endY, int columns, int rows) {
        float minX = Math.min(rubberBandStartX, endX);
        float maxX = Math.max(rubberBandStartX, endX);
        float minY = Math.min(rubberBandStartY, endY);
        float maxY = Math.max(rubberBandStartY, endY);
        List<Integer> covered = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float centerX = canvas.originX() + canvas.width() * (column + 0.5f) / columns;
                float centerY = canvas.originY() + canvas.height() * (row + 0.5f) / rows;
                if (centerX >= minX && centerX <= maxX && centerY >= minY && centerY <= maxY) {
                    covered.add(row * columns + column);
                }
            }
        }
        return covered;
    }

    private void appendFrame(int cellIndex) {
        selectedDraft().ifPresent(draft -> {
            draft.frames.add(Integer.toString(cellIndex));
            selectedFrame = draft.frames.size() - 1;
            dirty = true;
        });
    }

    private void drawRubberBand(AtlasCanvas canvas) {
        if (!rubberBandActive || !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            return;
        }
        int columns = Math.max(1, editColumns.get());
        int rows = Math.max(1, editRows.get());
        for (int cell : coveredCells(canvas, ImGui.getMousePosX(), ImGui.getMousePosY(), columns, rows)) {
            drawCoveredCellHighlight(canvas, cell, columns, rows);
        }
        float minX = Math.min(rubberBandStartX, ImGui.getMousePosX());
        float minY = Math.min(rubberBandStartY, ImGui.getMousePosY());
        float maxX = Math.max(rubberBandStartX, ImGui.getMousePosX());
        float maxY = Math.max(rubberBandStartY, ImGui.getMousePosY());
        ImGui.getWindowDrawList().addRect(minX, minY, maxX, maxY, COLOR_RUBBER_BAND_BORDER);
    }

    private void drawCoveredCellHighlight(AtlasCanvas canvas, int cell, int columns, int rows) {
        int column = cell % columns;
        int row = cell / columns;
        float cellWidth = canvas.width() / columns;
        float cellHeight = canvas.height() / rows;
        float minX = canvas.originX() + column * cellWidth;
        float minY = canvas.originY() + row * cellHeight;
        ImGui.getWindowDrawList().addRectFilled(minX, minY, minX + cellWidth, minY + cellHeight, COLOR_RUBBER_BAND_FILL);
        ImGui.getWindowDrawList().addRect(minX, minY, minX + cellWidth, minY + cellHeight, COLOR_RUBBER_BAND_BORDER);
    }

    private void drawUsedCellBadges(AtlasCanvas canvas) {
        Optional<AnimationDraft> selected = selectedDraft();
        if (selected.isEmpty()) {
            return;
        }
        for (Map.Entry<String, FrameUsage> entry : frameUsage(selected.get()).entrySet()) {
            resolveRegion(entry.getKey()).ifPresent(region ->
                    drawCellBadge(canvas, region, entry.getValue()));
        }
    }

    private Map<String, FrameUsage> frameUsage(AnimationDraft draft) {
        Map<String, FrameUsage> usage = new LinkedHashMap<>();
        for (int index = 0; index < draft.frames.size(); index++) {
            usage.merge(draft.frames.get(index), new FrameUsage(index, 1),
                    (existing, added) -> new FrameUsage(existing.firstIndex(), existing.count() + 1));
        }
        return usage;
    }

    private void drawCellBadge(AtlasCanvas canvas, SpriteAtlasRegion region, FrameUsage usage) {
        float left = canvas.uvToScreenX(region.minU());
        float top = canvas.uvToScreenY(region.maxV());
        float right = canvas.uvToScreenX(region.maxU());
        float bottom = canvas.uvToScreenY(region.minV());
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(left, top, right, bottom, COLOR_USED_CELL_FILL);
        String label = (usage.firstIndex() + 1) + (usage.count() > 1 ? "+" : "");
        float textWidth = ImGui.calcTextSize(label).x;
        drawList.addRectFilled(left, top, left + textWidth + 8.0f, top + ImGui.getTextLineHeight() + 4.0f,
                COLOR_BADGE_BACKGROUND);
        drawList.addText(left + 4.0f, top + 2.0f, COLOR_BADGE_TEXT, label);
    }

    private void renderFrameStripRow() {
        float stripWidth = Math.max(64.0f, ImGui.getContentRegionAvailX() - PREVIEW_PANEL_WIDTH);
        ImGui.beginChild("##frame-strip", stripWidth, STRIP_HEIGHT, true,
                ImGuiWindowFlags.HorizontalScrollbar);
        selectedDraft().ifPresentOrElse(this::renderFrameThumbnails,
                () -> ImGui.textDisabled("Select an animation to edit its frames"));
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("##frame-preview", 0.0f, STRIP_HEIGHT, true);
        selectedDraft().ifPresent(this::renderPreview);
        ImGui.endChild();
    }

    private void renderFrameThumbnails(AnimationDraft draft) {
        Optional<ImagePreviewTexture.PreviewImage> image = texturePreview();
        for (int index = 0; index < draft.frames.size(); index++) {
            if (index > 0) {
                ImGui.sameLine();
            }
            renderFrameThumbnail(draft, index, image);
        }
        handleFrameDeleteKey(draft);
    }

    private void renderFrameThumbnail(AnimationDraft draft, int index,
                                      Optional<ImagePreviewTexture.PreviewImage> image) {
        ImGui.pushID(index);
        ImGui.beginGroup();
        Optional<SpriteAtlasRegion> region = resolveRegion(draft.frames.get(index));
        if (frameThumbnailButton(image, region)) {
            selectedFrame = index;
        }
        handleThumbnailInteractions(draft, index);
        markThumbnail(index == selectedFrame, region.isEmpty());
        ImGui.textDisabled(Integer.toString(index + 1));
        ImGui.endGroup();
        ImGui.popID();
    }

    private boolean frameThumbnailButton(Optional<ImagePreviewTexture.PreviewImage> image,
                                         Optional<SpriteAtlasRegion> region) {
        if (image.isEmpty() || region.isEmpty()) {
            return ImGui.button("?", THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        }
        SpriteAtlasRegion uv = region.get();
        return ImGui.imageButton(image.get().textureId(), THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                uv.minU(), 1.0f - uv.maxV(), uv.maxU(), 1.0f - uv.minV());
    }

    private void markThumbnail(boolean selected, boolean orphaned) {
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX();
        float maxY = ImGui.getItemRectMaxY();
        if (orphaned) {
            ImGui.getWindowDrawList().addRectFilled(minX, minY, maxX, maxY, COLOR_ORPHAN_TINT);
        }
        if (selected) {
            ImGui.getWindowDrawList().addRect(minX, minY, maxX, maxY, COLOR_SELECTED_BORDER);
        }
    }

    private void handleThumbnailInteractions(AnimationDraft draft, int index) {
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            removeFrame(draft, index);
            return;
        }
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(FRAME_PAYLOAD, Integer.valueOf(index));
            ImGui.textUnformatted("Frame " + (index + 1));
            ImGui.endDragDropSource();
        }
        handleThumbnailDropTarget(draft, index);
    }

    private void handleThumbnailDropTarget(AnimationDraft draft, int index) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        Integer dragged = ImGui.acceptDragDropPayload(FRAME_PAYLOAD, Integer.class);
        if (dragged != null && dragged != index && dragged < draft.frames.size()) {
            String moved = draft.frames.remove((int) dragged);
            draft.frames.add(index, moved);
            selectedFrame = index;
            dirty = true;
        }
        ImGui.endDragDropTarget();
    }

    private void handleFrameDeleteKey(AnimationDraft draft) {
        if (ImGui.isWindowFocused() && ImGui.isKeyPressed(ImGuiKey.Delete)
                && selectedFrame >= 0 && selectedFrame < draft.frames.size()) {
            removeFrame(draft, selectedFrame);
        }
    }

    private void removeFrame(AnimationDraft draft, int index) {
        draft.frames.remove(index);
        selectedFrame = Math.min(selectedFrame, draft.frames.size() - 1);
        dirty = true;
    }

    private void renderPreview(AnimationDraft draft) {
        if (previewPlaying) {
            previewTimeSeconds += ImGui.getIO().getDeltaTime();
        }
        int frameIndex = previewFrameIndex(draft);
        renderPreviewImage(draft, frameIndex);
        if (ImGui.button(previewPlaying ? "Pause" : "Play")) {
            previewPlaying = !previewPlaying;
        }
        ImGui.sameLine();
        ImGui.textDisabled(draft.frames.isEmpty() ? "no frames"
                : (frameIndex + 1) + " / " + draft.frames.size());
    }

    private int previewFrameIndex(AnimationDraft draft) {
        if (draft.frames.isEmpty()) {
            return 0;
        }
        int frame = (int) Math.floor(previewTimeSeconds * Math.max(FPS_MINIMUM, draft.framesPerSecond));
        if (draft.loop) {
            return Math.floorMod(frame, draft.frames.size());
        }
        return Math.min(frame, draft.frames.size() - 1);
    }

    private void renderPreviewImage(AnimationDraft draft, int frameIndex) {
        Optional<ImagePreviewTexture.PreviewImage> image = texturePreview();
        Optional<SpriteAtlasRegion> region = draft.frames.isEmpty() ? Optional.empty()
                : resolveRegion(draft.frames.get(frameIndex));
        if (image.isEmpty() || region.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
            ImGui.button("no frame", PREVIEW_IMAGE_SIZE, PREVIEW_IMAGE_SIZE);
            ImGui.popStyleColor();
            return;
        }
        SpriteAtlasRegion uv = region.get();
        ImGui.image(image.get().textureId(), PREVIEW_IMAGE_SIZE, PREVIEW_IMAGE_SIZE,
                uv.minU(), 1.0f - uv.maxV(), uv.maxU(), 1.0f - uv.minV());
    }

    public void dispose() {
        preview.dispose();
    }
}
