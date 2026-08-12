package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TilemapDockView {

    public static final String WINDOW_TITLE = "TileMap";

    private static final float TOOL_COLUMN_WIDTH = 92.0f;
    private static final float SIDE_COLUMN_WIDTH = 230.0f;
    private static final float SETUP_LEFT_WIDTH = 260.0f;

    private final Supplier<SceneDocument> activeDocument;
    private final EngineServices services;
    private final TileBrush brush;
    private final TilePalettePanel palette;
    private final TileToolBar toolBar;
    private final TileLayersSection layersSection;
    private final TileTerrainsSection terrainsSection;
    private final TileDataSection dataSection;
    private final TileSetupTab setupTab;
    private final Runnable enablePainting;
    private final BooleanSupplier twoDimensionalView;
    private boolean visible = true;
    private boolean setupRequested;

    public TilemapDockView(Supplier<SceneDocument> activeDocument, EngineServices services,
                           IconWidgets icons, TilePalettePanel palette, Runnable enablePainting,
                           BooleanSupplier twoDimensionalView) {
        this.activeDocument = activeDocument;
        this.services = services;
        this.palette = palette;
        this.brush = palette.brush();
        this.enablePainting = enablePainting;
        this.twoDimensionalView = twoDimensionalView;
        this.toolBar = new TileToolBar(icons, brush);
        this.layersSection = new TileLayersSection(icons, brush);
        this.terrainsSection = new TileTerrainsSection(icons, brush);
        this.dataSection = new TileDataSection(brush);
        this.setupTab = new TileSetupTab(brush, palette, dataSection, terrainsSection);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean value) {
        visible = value;
    }

    public void focus() {
        visible = true;
        ImGui.setWindowFocus(WINDOW_TITLE);
    }

    public void render() {
        if (!visible) {
            return;
        }
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_TILEMAP_DOCK_VIEW_TITLE, WINDOW_TITLE), ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }
        selectedRenderer().ifPresentOrElse(this::renderTabs, TilemapDockView::renderNoSelection);
        ImGui.end();
    }

    private Optional<TilemapRenderer> selectedRenderer() {
        Optional<TilemapRenderer> renderer = activeDocument.get().selection().get()
                .flatMap(gameObject -> gameObject.getComponent(TilemapRenderer.class));
        renderer.ifPresent(found -> found.refresh(services));
        return renderer;
    }

    private static void renderNoSelection() {
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_SELECT_OBJECT));
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_CREATE_HINT));
    }

    private void renderTabs(TilemapRenderer renderer) {
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        Optional<SpriteAtlas> atlas = renderer.atlasValue();
        if (tilemap.isEmpty() || atlas.isEmpty() || atlas.get().grid().isEmpty()) {
            renderMissingSetup(tilemap, atlas);
            return;
        }
        palette.renderSaveBar(renderer);
        ImGui.separator();
        if (!ImGui.beginTabBar("tilemapTabs")) {
            return;
        }
        renderPaintTab(renderer, tilemap.get(), atlas.get());
        renderSetupTab(renderer, tilemap.get(), atlas.get());
        ImGui.endTabBar();
    }

    private void renderMissingSetup(Optional<SpriteTilemap> tilemap, Optional<SpriteAtlas> atlas) {
        if (tilemap.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_NO_ASSET));
            Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_NO_ASSET_HINT));
            return;
        }
        if (atlas.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_NO_ATLAS));
            Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_NO_ATLAS_HINT));
            return;
        }
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_NO_GRID));
    }

    private void renderPaintTab(TilemapRenderer renderer, SpriteTilemap tilemap, SpriteAtlas atlas) {
        if (!ImGui.beginTabItem(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_PAINT))) {
            return;
        }
        renderPaintReadiness();
        renderToolColumn();
        ImGui.sameLine();
        renderPaletteColumn(renderer, tilemap);
        ImGui.sameLine();
        renderSideColumn(tilemap);
        ImGui.endTabItem();
    }

    private void renderToolColumn() {
        ImGui.beginChild("tileTools", EditorScale.of(TOOL_COLUMN_WIDTH), 0.0f, true);
        if (toolBar.renderTools()) {
            enablePainting.run();
        }
        ImGui.separator();
        toolBar.renderClipboardState();
        ImGui.endChild();
    }

    private void renderPaintReadiness() {
        if (twoDimensionalView.getAsBoolean()) {
            return;
        }
        Texts.colored(EditorStyle.COLOR_DANGER,
                I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_NEEDS_2D));
    }

    private void renderPaletteColumn(TilemapRenderer renderer, SpriteTilemap tilemap) {
        float width = Math.max(120.0f, ImGui.getContentRegionAvailX() - EditorScale.of(SIDE_COLUMN_WIDTH));
        ImGui.beginChild("tilePalette", width, 0.0f, true);
        toolBar.renderLayerSelector(tilemap);
        palette.render(renderer);
        ImGui.endChild();
    }

    private void renderSideColumn(SpriteTilemap tilemap) {
        ImGui.beginChild("tileSide", 0.0f, 0.0f, true);
        boolean changed = layersSection.render(tilemap);
        changed |= terrainsSection.render(tilemap);
        ImGui.separator();
        if (ImGui.button(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_CONFIGURE_HINT))) {
            setupRequested = true;
        }
        ImGui.endChild();
        markDirtyIfNeeded(tilemap, changed);
    }

    private void renderSetupTab(TilemapRenderer renderer, SpriteTilemap tilemap, SpriteAtlas atlas) {
        int flags = setupRequested ? imgui.flag.ImGuiTabItemFlags.SetSelected : 0;
        setupRequested = false;
        if (!ImGui.beginTabItem(I18n.translate(TextKey.EDITOR_TILEMAP_DOCK_VIEW_SETUP), flags)) {
            return;
        }
        ImGui.beginChild("tileSetupLeft", EditorScale.of(SETUP_LEFT_WIDTH), 0.0f, true);
        palette.render(renderer);
        ImGui.endChild();
        ImGui.sameLine();
        boolean changed = setupTab.render(tilemap, atlas);
        ImGui.endTabItem();
        markDirtyIfNeeded(tilemap, changed);
    }

    private void markDirtyIfNeeded(SpriteTilemap tilemap, boolean changed) {
        if (changed) {
            tilemap.touch();
            activeDocument.get().markDirty();
        }
    }
}
