package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.editor.ui.AnimatorSection;
import fr.epistudio.epysia.editor.ui.CameraPostEffectsSection;
import fr.epistudio.epysia.editor.ui.GraphSection;
import fr.epistudio.epysia.editor.ui.MaterialsSection;
import fr.epistudio.epysia.editor.ui.NavMeshSurfaceSection;
import fr.epistudio.epysia.editor.ui.PopulateSection;
import fr.epistudio.epysia.editor.ui.RigidBodyLiveSection;
import fr.epistudio.epysia.editor.ui.TilemapSummarySection;
import fr.epistudio.epysia.editor.ui.UiElementSection;
import fr.epistudio.epysia.editor.ui.VfxSection;

import java.util.function.BooleanSupplier;

public record InspectorSectionBundle(UiElementSection uiElement,
                                     MaterialsSection materials,
                                     PopulateSection populate,
                                     AnimatorSection animator,
                                     VfxSection vfx,
                                     CameraPostEffectsSection cameraPostEffects,
                                     GraphSection graph,
                                     ColliderFitSection colliderFit,
                                     TilemapSummarySection tilemapSummary,
                                     RigidBodyLiveSection rigidBodyLive,
                                     NavMeshSurfaceSection navMeshSurface,
                                     BooleanSupplier playModeActive) {
}
