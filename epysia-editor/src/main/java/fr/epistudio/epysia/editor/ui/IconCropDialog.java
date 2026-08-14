package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.assets.IconComposer;
import fr.epistudio.epysia.editor.assets.LoadedImage;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.ui.kit.IconButtons;
import fr.epistudio.epysia.editor.ui.kit.SegmentedControl;
import fr.epistudio.epysia.editor.ui.kit.Sliders;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class IconCropDialog {

    private static final String POPUP_ID = "###icon-crop";
    private static final float FRAME_SIZE = 300.0f;
    private static final float RESULT_SIZE = 96.0f;
    private static final float SIDE_WIDTH = 250.0f;
    private static final float SECTION_GAP = 12.0f;
    private static final float TOOL_ICON_SIZE = 18.0f;
    private static final float BUTTON_HEIGHT = 34.0f;
    private static final float CHECKER_CELL = 12.0f;
    private static final float GUIDE_THIRD = 3.0f;
    private static final float MINIMUM_ZOOM = 1.0f;
    private static final float MAXIMUM_ZOOM = 8.0f;
    private static final int CHECKER_LIGHT = 0xFF3A3A3A;
    private static final int CHECKER_DARK = 0xFF2E2E2E;
    private static final int GUIDE_COLOR = 0x30FFFFFF;
    private static final int CORNER_COUNT = 4;
    private static final int DEFAULT_OUTPUT_SIZE = 256;
    private static final List<Integer> OUTPUT_SIZES = List.of(64, 128, 256, 512);

    private final IconWidgets icons;
    private final Consumer<Path> onWritten;
    private final Consumer<String> onFailed;
    private final ImFloat zoom = new ImFloat(MINIMUM_ZOOM);
    private final float[] cornerU = new float[CORNER_COUNT];
    private final float[] cornerV = new float[CORNER_COUNT];
    private Optional<LoadedImage> source = Optional.empty();
    private Path target = Path.of("");
    private float panX;
    private float panY;
    private int quarterTurns;
    private int outputSizeIndex = OUTPUT_SIZES.indexOf(DEFAULT_OUTPUT_SIZE);
    private boolean openRequested;

    public IconCropDialog(IconWidgets icons, Consumer<Path> onWritten, Consumer<String> onFailed) {
        this.icons = icons;
        this.onWritten = onWritten;
        this.onFailed = onFailed;
    }

    public void open(Path imageFile, Path targetFile) {
        Optional<LoadedImage> loaded = LoadedImage.load(imageFile);
        if (loaded.isEmpty()) {
            onFailed.accept(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_UNREADABLE, imageFile));
            return;
        }
        close();
        source = loaded;
        target = targetFile;
        reset();
        openRequested = true;
    }

    public void dispose() {
        close();
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        String title = I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_TITLE) + POPUP_ID;
        if (source.isEmpty() || !ImGui.beginPopupModal(title, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        LoadedImage image = source.get();
        fillCorners(image);
        renderFrame(image);
        ImGui.sameLine(0.0f, EditorScale.of(SECTION_GAP));
        renderSideColumn(image);
        ImGui.endPopup();
    }

    private void renderFrame(LoadedImage image) {
        float size = EditorScale.of(FRAME_SIZE);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImGui.invisibleButton("##icon-crop-frame", size, size);
        applyDrag(size);
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(origin.x, origin.y, origin.x + size, origin.y + size, true);
        paintChecker(drawList, origin, size);
        paintImage(drawList, image, origin, size);
        paintGuides(drawList, origin, size);
        drawList.popClipRect();
        drawList.addRect(origin.x, origin.y, origin.x + size, origin.y + size,
                EditorStyle.COLOR_WIDGET_OUTLINE, EditorStyle.frameRounding());
    }

    private void renderSideColumn(LoadedImage image) {
        ImGui.beginGroup();
        renderResult(image);
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        renderTools();
        renderZoom();
        renderSizes();
        ImGui.dummy(0.0f, EditorScale.of(SECTION_GAP));
        renderConfirmation();
        ImGui.endGroup();
    }

    private void renderResult(LoadedImage image) {
        Texts.muted(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_RESULT));
        float size = EditorScale.of(RESULT_SIZE);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImGui.dummy(size, size);
        ImDrawList drawList = ImGui.getWindowDrawList();
        paintChecker(drawList, origin, size);
        paintImage(drawList, image, origin, size);
        drawList.addRect(origin.x, origin.y, origin.x + size, origin.y + size,
                EditorStyle.COLOR_WIDGET_OUTLINE, EditorStyle.frameRounding());
        ImGui.sameLine();
        Texts.muted(image.width() + " x " + image.height());
    }

    private void renderTools() {
        float size = EditorScale.of(TOOL_ICON_SIZE);
        if (IconButtons.mirrored(icons, "##rotate-left", EditorIcon.REDO, size)) {
            quarterTurns = (quarterTurns + 3) % CORNER_COUNT;
        }
        IconButtons.tooltip(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_ROTATE_LEFT));
        ImGui.sameLine();
        if (icons.iconButton("##rotate-right", EditorIcon.REDO, size)) {
            quarterTurns = (quarterTurns + 1) % CORNER_COUNT;
        }
        IconButtons.tooltip(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_ROTATE_RIGHT));
        ImGui.sameLine();
        if (icons.iconButton("##reset-crop", EditorIcon.ERASER, size)) {
            reset();
        }
        IconButtons.tooltip(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_RESET));
    }

    private void renderZoom() {
        icons.drawInline(EditorIcon.TOOL_SCALE, EditorStyle.iconSizeSmall());
        Sliders.filled("##icon-crop-zoom", zoom, MINIMUM_ZOOM, MAXIMUM_ZOOM,
                EditorScale.of(SIDE_WIDTH) - EditorStyle.iconSizeSmall() - EditorStyle.itemSpacingX());
        IconButtons.tooltip(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_ZOOM));
    }

    private void renderSizes() {
        outputSizeIndex = SegmentedControl.render("##icon-crop-size",
                OUTPUT_SIZES.stream().map(size -> size + " px").toList(), outputSizeIndex);
    }

    private void renderConfirmation() {
        float width = (EditorScale.of(SIDE_WIDTH) - EditorStyle.itemSpacingX()) * 0.5f;
        float height = EditorScale.of(BUTTON_HEIGHT);
        if (IconButtons.withLabel(icons, "icon-crop-apply", EditorIcon.SAVE,
                I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_APPLY), width, height)) {
            apply();
        }
        ImGui.sameLine();
        if (IconButtons.withLabel(icons, "icon-crop-cancel", EditorIcon.REMOVE,
                I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CANCEL), width, height)) {
            close();
            ImGui.closeCurrentPopup();
        }
        Texts.muted(target.getFileName() + " " + OUTPUT_SIZES.get(outputSizeIndex) + " px");
    }

    private static void paintChecker(ImDrawList drawList, ImVec2 origin, float size) {
        float cell = EditorScale.of(CHECKER_CELL);
        int cells = (int) Math.ceil(size / cell);
        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                float left = origin.x + column * cell;
                float top = origin.y + row * cell;
                drawList.addRectFilled(left, top, Math.min(left + cell, origin.x + size),
                        Math.min(top + cell, origin.y + size),
                        (row + column) % 2 == 0 ? CHECKER_LIGHT : CHECKER_DARK);
            }
        }
    }

    private void paintImage(ImDrawList drawList, LoadedImage image, ImVec2 origin, float size) {
        drawList.addImageQuad(image.textureId(),
                origin.x, origin.y, origin.x + size, origin.y,
                origin.x + size, origin.y + size, origin.x, origin.y + size,
                cornerU[0], cornerV[0], cornerU[1], cornerV[1],
                cornerU[2], cornerV[2], cornerU[3], cornerV[3]);
    }

    private static void paintGuides(ImDrawList drawList, ImVec2 origin, float size) {
        for (int line = 1; line < GUIDE_THIRD; line++) {
            float offset = size * line / GUIDE_THIRD;
            drawList.addLine(origin.x + offset, origin.y, origin.x + offset, origin.y + size, GUIDE_COLOR);
            drawList.addLine(origin.x, origin.y + offset, origin.x + size, origin.y + offset, GUIDE_COLOR);
        }
    }

    private void applyDrag(float frameSize) {
        if (!ImGui.isItemActive()) {
            return;
        }
        float alongX = -ImGui.getIO().getMouseDeltaX() / frameSize / zoom.get();
        float alongY = -ImGui.getIO().getMouseDeltaY() / frameSize / zoom.get();
        panX += rotatedX(alongX, alongY);
        panY += rotatedY(alongX, alongY);
    }

    private float rotatedX(float alongX, float alongY) {
        return switch (quarterTurns) {
            case 1 -> alongY;
            case 2 -> -alongX;
            case 3 -> -alongY;
            default -> alongX;
        };
    }

    private float rotatedY(float alongX, float alongY) {
        return switch (quarterTurns) {
            case 1 -> -alongX;
            case 2 -> -alongY;
            case 3 -> alongX;
            default -> alongY;
        };
    }

    private void fillCorners(LoadedImage image) {
        float shortSide = Math.min(image.width(), image.height());
        float extent = shortSide / zoom.get();
        float centerX = image.width() * 0.5f + panX * shortSide;
        float centerY = image.height() * 0.5f + panY * shortSide;
        float left = (centerX - extent * 0.5f) / image.width();
        float right = (centerX + extent * 0.5f) / image.width();
        float top = (centerY - extent * 0.5f) / image.height();
        float bottom = (centerY + extent * 0.5f) / image.height();
        float[] baseU = {left, right, right, left};
        float[] baseV = {top, top, bottom, bottom};
        for (int corner = 0; corner < CORNER_COUNT; corner++) {
            cornerU[corner] = baseU[(corner + quarterTurns) % CORNER_COUNT];
            cornerV[corner] = baseV[(corner + quarterTurns) % CORNER_COUNT];
        }
    }

    private void apply() {
        try {
            IconComposer.write(source.orElseThrow(), cornerU, cornerV,
                    OUTPUT_SIZES.get(outputSizeIndex), target);
            onWritten.accept(target);
        } catch (IOException failure) {
            onFailed.accept(I18n.translate(TextKey.EDITOR_ICON_CROP_DIALOG_FAILED, failure.getMessage()));
        }
        close();
        ImGui.closeCurrentPopup();
    }

    private void reset() {
        zoom.set(MINIMUM_ZOOM);
        panX = 0.0f;
        panY = 0.0f;
        quarterTurns = 0;
        outputSizeIndex = OUTPUT_SIZES.indexOf(DEFAULT_OUTPUT_SIZE);
    }

    private void close() {
        source.ifPresent(LoadedImage::dispose);
        source = Optional.empty();
    }
}
