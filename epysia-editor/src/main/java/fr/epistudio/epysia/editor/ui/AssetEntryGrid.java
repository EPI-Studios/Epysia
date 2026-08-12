package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.ui.kit.ExtensionBadge;
import fr.epistudio.epysia.editor.assets.AssetEntry;
import fr.epistudio.epysia.editor.assets.AssetType;
import fr.epistudio.epysia.editor.assets.MeshThumbnailer;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.icons.AssetTypeIcons;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.callback.ImListClipperCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class AssetEntryGrid {

    public enum Mode { GRID, LIST }

    private static final String ELLIPSIS = "...";
    private static final int MAXIMUM_KEPT_EXTENSION = 12;
    private static final float CELL_PADDING_DESIGN = 10.0f;
    private static final float LABEL_HEIGHT_DESIGN = 20.0f;
    private static final float LIST_ROW_HEIGHT_DESIGN = 22.0f;
    private static final float LIST_THUMBNAIL_SIZE_DESIGN = 16.0f;
    private static final float SIZE_COLUMN_WIDTH_DESIGN = 90.0f;
    private static final float ELLIPSIS_MINIMUM = 1;

    private final IconWidgets icons;
    private final ThumbnailCache thumbnails;
    private final MeshThumbnailer meshThumbnails;
    private final Set<String> selection = new LinkedHashSet<>();

    private Mode mode = Mode.GRID;
    private float cellSize = 72.0f;

    public AssetEntryGrid(IconWidgets icons, ThumbnailCache thumbnails, MeshThumbnailer meshThumbnails) {
        this.icons = icons;
        this.thumbnails = thumbnails;
        this.meshThumbnails = meshThumbnails;
    }

    private static float cellPadding() {
        return EditorScale.of(CELL_PADDING_DESIGN);
    }

    private static float labelHeight() {
        return EditorScale.of(LABEL_HEIGHT_DESIGN);
    }

    private static float listRowHeight() {
        return EditorScale.of(LIST_ROW_HEIGHT_DESIGN);
    }

    private static float listThumbnailSize() {
        return EditorScale.of(LIST_THUMBNAIL_SIZE_DESIGN);
    }

    private static float sizeColumnWidth() {
        return EditorScale.of(SIZE_COLUMN_WIDTH_DESIGN);
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode value) {
        this.mode = value;
    }

    public float cellSize() {
        return cellSize;
    }

    public void setCellSize(float value) {
        this.cellSize = value;
    }

    public Set<String> selection() {
        return Set.copyOf(selection);
    }

    public Optional<String> primarySelection() {
        return selection.stream().reduce((first, second) -> second);
    }

    public void clearSelection() {
        selection.clear();
    }

    public void render(List<AssetEntry> entries, Consumer<AssetEntry> onActivate,
                       Consumer<AssetEntry> onDecorate) {
        if (mode == Mode.GRID) {
            renderGrid(entries, onActivate, onDecorate);
        } else {
            renderList(entries, onActivate, onDecorate);
        }
    }

    private void renderGrid(List<AssetEntry> entries, Consumer<AssetEntry> onActivate,
                            Consumer<AssetEntry> onDecorate) {
        float cellWidth = cellSize + cellPadding();
        int columns = Math.max(1, (int) (ImGui.getContentRegionAvailX() / cellWidth));
        int rowCount = (entries.size() + columns - 1) / columns;
        int rowHeight = Math.round(cellSize + labelHeight() + ImGui.getStyle().getItemSpacingY());
        ImGuiListClipper.forEach(rowCount, rowHeight, new ImListClipperCallback() {
            @Override
            public void accept(int row) {
                renderGridRow(entries, row, columns, onActivate, onDecorate);
            }
        });
    }

    private void renderGridRow(List<AssetEntry> entries, int row, int columns,
                               Consumer<AssetEntry> onActivate, Consumer<AssetEntry> onDecorate) {
        int start = row * columns;
        int end = Math.min(start + columns, entries.size());
        for (int index = start; index < end; index++) {
            if (index != start) {
                ImGui.sameLine();
            }
            renderGridCell(entries.get(index), onActivate, onDecorate);
        }
    }

    private void renderGridCell(AssetEntry entry, Consumer<AssetEntry> onActivate,
                                Consumer<AssetEntry> onDecorate) {
        ImGui.pushID(entry.assetPath());
        ImGui.beginGroup();
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        boolean selected = selection.contains(entry.assetPath());
        if (ImGui.selectable("##cell", selected, ImGuiSelectableFlags.AllowDoubleClick,
                cellSize, cellSize + labelHeight())) {
            applyClick(entry);
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                onActivate.accept(entry);
            }
        }
        onDecorate.accept(entry);
        drawCellContent(entry, startX, startY);
        ImGui.endGroup();
        ImGui.popID();
    }

    private void drawCellContent(AssetEntry entry, float startX, float startY) {
        ImGui.setCursorPos(startX, startY);
        drawThumbnail(entry, cellSize);
        ImGui.setCursorPos(startX, startY + cellSize);
        drawCenteredLabel(entry.displayName(), cellSize);
        ImGui.setCursorPos(startX, startY + cellSize + labelHeight());
    }

    private void drawCenteredLabel(String name, float width) {
        String label = elide(name, width);
        float indent = (width - ImGui.calcTextSize(label).x) * 0.5f;
        if (indent > 0.0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        }
        ImGui.textUnformatted(label);
    }

    private void renderList(List<AssetEntry> entries, Consumer<AssetEntry> onActivate,
                            Consumer<AssetEntry> onDecorate) {
        ImGuiListClipper.forEach(entries.size(), Math.round(listRowHeight()), new ImListClipperCallback() {
            @Override
            public void accept(int index) {
                renderListRow(entries.get(index), onActivate, onDecorate);
            }
        });
    }

    private void renderListRow(AssetEntry entry, Consumer<AssetEntry> onActivate,
                               Consumer<AssetEntry> onDecorate) {
        ImGui.pushID(entry.assetPath());
        float startY = ImGui.getCursorPosY();
        boolean selected = selection.contains(entry.assetPath());
        if (ImGui.selectable("##row", selected, ImGuiSelectableFlags.AllowDoubleClick,
                0.0f, listRowHeight())) {
            applyClick(entry);
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                onActivate.accept(entry);
            }
        }
        onDecorate.accept(entry);
        drawListRowContent(entry, startY);
        ImGui.popID();
    }

    private void drawListRowContent(AssetEntry entry, float startY) {
        ImGui.setCursorPosY(startY + (listRowHeight() - listThumbnailSize()) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + cellPadding() * 0.5f);
        drawThumbnail(entry, listThumbnailSize());
        ImGui.sameLine();
        ImGui.setCursorPosY(startY + (listRowHeight() - ImGui.getTextLineHeight()) * 0.5f);
        ImGui.textUnformatted(entry.displayName());
        drawSizeColumn(entry, startY);
        ImGui.setCursorPosY(startY + listRowHeight());
    }

    private static void drawSizeColumn(AssetEntry entry, float startY) {
        if (entry.isBuiltin()) {
            return;
        }
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - sizeColumnWidth());
        ImGui.setCursorPosY(startY + (listRowHeight() - ImGui.getTextLineHeight()) * 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(entry.formattedSize());
        ImGui.popStyleColor();
    }

    private void applyClick(AssetEntry entry) {
        if (ImGui.getIO().getKeyCtrl()) {
            toggle(entry.assetPath());
            return;
        }
        selection.clear();
        selection.add(entry.assetPath());
    }

    private void toggle(String assetPath) {
        if (!selection.remove(assetPath)) {
            selection.add(assetPath);
        }
    }

    private void drawThumbnail(AssetEntry entry, float size) {
        OptionalInt texture = thumbnailFor(entry);
        if (texture.isEmpty()) {
            float iconX = ImGui.getCursorScreenPosX();
            float iconY = ImGui.getCursorScreenPosY();
            icons.draw(AssetTypeIcons.iconFor(entry.type()), size);
            if (entry.type() == AssetType.OTHER) {
                ExtensionBadge.draw(entry.assetPath(), iconX, iconY, size);
            }
            return;
        }
        if (entry.type() == AssetType.TEXTURE) {
            ImGui.image(texture.getAsInt(), size, size);
        } else {
            ImGui.image(texture.getAsInt(), size, size, 0.0f, 1.0f, 1.0f, 0.0f);
        }
    }

    private OptionalInt thumbnailFor(AssetEntry entry) {
        if (entry.type() == AssetType.TEXTURE) {
            return thumbnails.get(entry.assetPath());
        }
        if ((entry.type() == AssetType.MESH || entry.type() == AssetType.PRESET)
                && !isImportSource(entry.assetPath())) {
            return meshThumbnails.get(entry.assetPath());
        }
        return OptionalInt.empty();
    }

    private static boolean isImportSource(String assetPath) {
        String lowerCasePath = assetPath.toLowerCase(Locale.ROOT);
        return lowerCasePath.endsWith(".gltf") || lowerCasePath.endsWith(".glb");
    }

    private static String elide(String name, float availableWidth) {
        if (ImGui.calcTextSize(name).x <= availableWidth) {
            return name;
        }
        int extensionStart = name.lastIndexOf('.');
        if (extensionStart <= 0 || name.length() - extensionStart > MAXIMUM_KEPT_EXTENSION) {
            return elideTail(name, availableWidth);
        }
        return elideMiddle(name.substring(0, extensionStart), name.substring(extensionStart), availableWidth);
    }

    private static String elideMiddle(String stem, String extension, float availableWidth) {
        int length = stem.length();
        while (length > ELLIPSIS_MINIMUM) {
            String candidate = stem.substring(0, length) + ELLIPSIS + extension;
            if (ImGui.calcTextSize(candidate).x <= availableWidth) {
                return candidate;
            }
            length--;
        }
        return elideTail(stem + extension, availableWidth);
    }

    private static String elideTail(String name, float availableWidth) {
        int length = name.length();
        while (length > ELLIPSIS_MINIMUM
                && ImGui.calcTextSize(name.substring(0, length) + ELLIPSIS).x > availableWidth) {
            length--;
        }
        return name.substring(0, length) + ELLIPSIS;
    }
}
