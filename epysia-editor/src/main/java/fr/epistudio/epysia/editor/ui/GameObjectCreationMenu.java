package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.scene.EditorPrimitives;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;
import org.joml.Vector3f;

import java.util.List;
import fr.epistudio.epysia.ui.UiButton;
import fr.epistudio.epysia.ui.UiImage;
import fr.epistudio.epysia.ui.UiLabel;
import fr.epistudio.epysia.ui.UiPanel;
import fr.epistudio.epysia.ui.UiTextField;

public final class GameObjectCreationMenu {

    private final GameObjectFactory objectFactory;

    public GameObjectCreationMenu(GameObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public void renderItems(Vector3f spawnPoint) {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CREATE_EMPTY,
                "create-empty"))) {
            objectFactory.createEmpty(spawnPoint);
        }
        ImGui.separator();
        renderPrimitiveItems(spawnPoint);
        ImGui.separator();
        renderPlanarItems(spawnPoint);
        ImGui.separator();
        renderLightAndCameraItems(spawnPoint);
        ImGui.separator();
        renderUiItems();
        renderModulePrimitives(spawnPoint);
    }

    private void renderModulePrimitives(Vector3f spawnPoint) {
        List<EditorPrimitives.Entry> entries = objectFactory.modulePrimitives();
        if (entries.isEmpty()) {
            return;
        }
        ImGui.separator();
        for (EditorPrimitives.Entry entry : entries) {
            if (ImGui.menuItem(entry.displayName())) {
                objectFactory.createModulePrimitive(entry, spawnPoint);
            }
        }
    }

    private void renderUiItems() {
        if (ImGui.menuItem(I18n.translate(TextKey.EDITOR_GAME_OBJECT_MENU_UI_CANVAS))) {
            objectFactory.createUiCanvas();
        }
        if (ImGui.menuItem(I18n.translate(TextKey.EDITOR_GAME_OBJECT_MENU_UI_PANEL))) {
            objectFactory.createUiElement("Panel", new UiPanel());
        }
        if (ImGui.menuItem(I18n.translate(TextKey.EDITOR_GAME_OBJECT_MENU_UI_LABEL))) {
            objectFactory.createUiElement("Label", new UiLabel());
        }
        if (ImGui.menuItem(I18n.translate(TextKey.EDITOR_GAME_OBJECT_MENU_UI_BUTTON))) {
            objectFactory.createUiElement("Button", new UiButton());
        }
        if (ImGui.menuItem(I18n.translate(TextKey.EDITOR_GAME_OBJECT_MENU_UI_IMAGE))) {
            objectFactory.createUiElement("Image", new UiImage());
        }
        if (ImGui.menuItem(I18n.translate(TextKey.EDITOR_GAME_OBJECT_MENU_UI_TEXT_FIELD))) {
            objectFactory.createUiElement("Text Field", new UiTextField());
        }
    }

    private void renderPrimitiveItems(Vector3f spawnPoint) {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CUBE, "create-cube"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CUBE, spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_PLANE, "create-plane"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.PLANE, spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CAPSULE, "create-capsule"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CAPSULE, spawnPoint);
        }
    }

    private void renderPlanarItems(Vector3f spawnPoint) {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_GAME_OBJECT_MENU_SPRITE_2D, "create-sprite-2d"))) {
            objectFactory.createSprite(spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_GAME_OBJECT_MENU_TILEMAP, "create-tilemap"))) {
            objectFactory.createTilemap(spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_GAME_OBJECT_MENU_POINT_LIGHT_2D, "create-point-light-2d"))) {
            objectFactory.createPointLight2D(spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_GAME_OBJECT_MENU_SPOT_LIGHT_2D, "create-spot-light-2d"))) {
            objectFactory.createSpotLight2D(spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_GAME_OBJECT_MENU_GLOBAL_LIGHT_2D, "create-global-light-2d"))) {
            objectFactory.createGlobalLight2D(spawnPoint);
        }
    }

    private void renderLightAndCameraItems(Vector3f spawnPoint) {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_DIRECTIONAL_LIGHT,
                "create-directional-light"))) {
            objectFactory.createDirectionalLight(spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_POINT_LIGHT,
                "create-point-light"))) {
            objectFactory.createPointLight(spawnPoint);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_SPOT_LIGHT,
                "create-spot-light"))) {
            objectFactory.createSpotLight(spawnPoint);
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CAMERA, "create-camera"))) {
            objectFactory.createCamera(spawnPoint);
        }
    }
}
