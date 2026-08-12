package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.ui.kit.Notices;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.ReparentCommand;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.ui.UiCanvas;
import fr.epistudio.epysia.ui.UiElement;
import imgui.ImGui;

import java.util.Optional;
import java.util.function.Supplier;

public final class UiElementSection {

    private final GameObjectFactory objectFactory;
    private final Supplier<Scene> scene;
    private final Supplier<EditorHistory> history;

    public UiElementSection(GameObjectFactory objectFactory, Supplier<Scene> scene,
                            Supplier<EditorHistory> history) {
        this.objectFactory = objectFactory;
        this.scene = scene;
        this.history = history;
    }

    public void render(GameObject gameObject, UiElement element) {
        if (hasCanvasAncestor(element)) {
            return;
        }
        ImGui.separator();
        Notices.warning(I18n.translate(TextKey.EDITOR_UI_ELEMENT_SECTION_NO_CANVAS));
        if (ImGui.button(I18n.translate(TextKey.EDITOR_UI_ELEMENT_SECTION_MOVE_UNDER_CANVAS))) {
            attachToCanvas(gameObject);
        }
        ImGui.separator();
    }

    private void attachToCanvas(GameObject gameObject) {
        GameObject canvas = findCanvas().orElseGet(objectFactory::createUiCanvas);
        history.get().execute(new ReparentCommand(gameObject, Optional.of(canvas)));
    }

    private Optional<GameObject> findCanvas() {
        for (GameObject candidate : scene.get().gameObjects()) {
            if (candidate.getComponent(UiCanvas.class).isPresent()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean hasCanvasAncestor(UiElement element) {
        Optional<Transform3D> walker = element.owner()
                .flatMap(owner -> owner.getComponent(Transform3D.class))
                .flatMap(Transform3D::parent);
        while (walker.isPresent()) {
            boolean isCanvas = walker.flatMap(Transform3D::owner)
                    .flatMap(owner -> owner.getComponent(UiCanvas.class)).isPresent();
            if (isCanvas) {
                return true;
            }
            walker = walker.flatMap(Transform3D::parent);
        }
        return false;
    }
}
