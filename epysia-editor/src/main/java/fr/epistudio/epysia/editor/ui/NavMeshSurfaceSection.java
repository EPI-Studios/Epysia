package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.editor.ui.kit.Notices;
import fr.epistudio.epysia.editor.ui.kit.Rows;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.navigation.NavMeshSettings;
import fr.epistudio.epysia.navigation.NavMeshSurface;
import fr.epistudio.epysia.navigation.NavigationService;

import java.util.function.Supplier;

public final class NavMeshSurfaceSection {

    private final Supplier<EngineServices> services;

    public NavMeshSurfaceSection(Supplier<EngineServices> services) {
        this.services = services;
    }

    public void render(NavMeshSurface surface) {
        NavigationService navigation = services.get().navigation();
        Sections.divider();
        Sections.caption(I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_STATUS));
        if (!navigation.baked()) {
            Notices.info(I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_NOT_BAKED));
            renderAgentSummary(surface.settings());
            return;
        }
        Rows.readOnly(I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_TRIANGLES),
                String.valueOf(navigation.bakedTriangleCount()));
        Rows.readOnly(I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_TILES),
                String.valueOf(navigation.loadedTileCount()));
        renderAgentSummary(surface.settings());
    }

    private static void renderAgentSummary(NavMeshSettings settings) {
        Rows.readOnly(I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_AGENT),
                I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_AGENT_VALUE,
                        settings.agentRadius(), settings.agentHeight()));
        Rows.readOnly(I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_TILE_SIZE),
                I18n.translate(TextKey.EDITOR_NAVMESH_SECTION_TILE_SIZE_VALUE, settings.tileWorldSize()));
    }
}
