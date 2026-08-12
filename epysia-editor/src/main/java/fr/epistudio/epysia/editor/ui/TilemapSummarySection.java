package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;

public final class TilemapSummarySection {

    private final Runnable openTilemapDock;

    public TilemapSummarySection(Runnable openTilemapDock) {
        this.openTilemapDock = openTilemapDock;
    }

    public void render(TilemapRenderer renderer) {
        Sections.divider();
        renderer.tilemapValue().ifPresentOrElse(TilemapSummarySection::renderFacts,
                TilemapSummarySection::renderMissingAsset);
        renderOpenButton();
    }

    private void renderOpenButton() {
        if (ImGui.button(I18n.translate(TextKey.EDITOR_TILEMAP_SUMMARY_OPEN_PANEL))) {
            openTilemapDock.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_TILEMAP_SUMMARY_OPEN_PANEL_TOOLTIP));
        }
    }

    private static void renderMissingAsset() {
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_SUMMARY_NO_ASSET));
    }

    private static void renderFacts(SpriteTilemap tilemap) {
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_SUMMARY_SIZE,
                tilemap.width(), tilemap.height()));
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_SUMMARY_LAYERS,
                tilemap.layerCount(), tilemap.terrains().size()));
        Texts.muted(I18n.translate(TextKey.EDITOR_TILEMAP_SUMMARY_SOLID_TILES,
                tilemap.solidTiles().size()));
    }
}
