package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;

public final class CameraPostEffectsSection {

    private final PostEffectsSection postEffects;
    private final Runnable markDirty;

    public CameraPostEffectsSection(PostEffectsSection postEffects, Runnable markDirty) {
        this.postEffects = postEffects;
        this.markDirty = markDirty;
    }

    public void render(Camera3D camera) {
        Sections.divider();
        Sections.caption(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_POST_EFFECTS));
        boolean overrideActive = camera.postEffectStack().isPresent();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_OVERRIDE_POST_EFFECTS,
                "inspector-override-post-effects"), overrideActive)) {
            toggle(camera, overrideActive);
        }
        camera.postEffectStack().ifPresent(stack -> postEffects.render(stack, markDirty));
    }

    private void toggle(Camera3D camera, boolean overrideActive) {
        if (overrideActive) {
            camera.disablePostEffectStack();
        } else {
            camera.enablePostEffectStack();
        }
        markDirty.run();
    }
}
