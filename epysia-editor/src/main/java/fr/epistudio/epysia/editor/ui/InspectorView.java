package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddComponentCommand;
import fr.epistudio.epysia.editor.command.builtin.RemoveComponentCommand;
import fr.epistudio.epysia.editor.icons.ComponentIcons;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.GraphComponent;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.vfx.ParticleEffect;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.lang.model.SourceVersion;

public final class InspectorView {

    private static final float EMPTY_STATE_LINE_GAP = 6.0f;
    private static final float EMPTY_STATE_BLOCK_HEIGHT = 70.0f;

    public static final String WINDOW_TITLE = "Inspector";

    private static final String ADD_COMPONENT_POPUP = "##add-component-popup";
    private static final int SEARCH_CAPACITY = 128;
    private static final int COMPONENT_HEADER_FLAGS = ImGuiTreeNodeFlags.DefaultOpen
            | ImGuiTreeNodeFlags.AllowItemOverlap;

    private final Supplier<SceneDocument> activeDocument;
    private final ComponentRegistry componentRegistry;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final PropertyRows propertyRows;
    private final AssetPicker assetPicker;
    private final ConfirmDialog removeConfirm = new ConfirmDialog(
            I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_REMOVE_COMPONENT_TITLE),
            I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_REMOVE_COMPONENT_CONFIRM));
    private final ImString componentSearch = new ImString(SEARCH_CAPACITY);
    private final MaterialsSection materialsSection;
    private final PopulateSection populateSection;
    private final AnimatorSection animatorSection;
    private final VfxSection vfxSection;
    private final PostEffectsSection postEffectsSection;
    private final GraphSection graphSection;
    private final NameDialog scriptNameDialog = new NameDialog("##new-script-name");
    private final BiConsumer<String, GameObject> onCreateScriptForObject;
    private final Supplier<Optional<Path>> selectedAssetPath;
    private final AtlasInspectorSection atlasSection;
    private final TextureInspectorSection textureSection;
    private final Runnable openTilemapDock;
    private final SpriteColliderFitSection spriteColliderFit = new SpriteColliderFitSection();

    public InspectorView(Supplier<SceneDocument> activeDocument, ComponentRegistry componentRegistry,
                         Notifier notifier, IconWidgets icons, AssetPicker assetPicker,
                         ThumbnailCache thumbnails, Project project,
                         BiConsumer<String, GameObject> onCreateScriptForObject,
                         Consumer<Path> onOpenGraph,
                         Supplier<Optional<Path>> selectedAssetPath,
                         AtlasInspectorSection atlasSection,
                         TextureInspectorSection textureSection,
                         Runnable openTilemapDock) {
        this.activeDocument = activeDocument;
        this.componentRegistry = componentRegistry;
        this.notifier = notifier;
        this.icons = icons;
        this.assetPicker = assetPicker;
        this.propertyRows = new PropertyRows(activeDocument, assetPicker);
        this.materialsSection = new MaterialsSection(activeDocument, thumbnails, project);
        this.populateSection = new PopulateSection(activeDocument, notifier, project);
        this.animatorSection = new AnimatorSection(activeDocument, project);
        this.vfxSection = new VfxSection(activeDocument, project);
        this.postEffectsSection = new PostEffectsSection(project, thumbnails);
        this.graphSection = new GraphSection(activeDocument, onOpenGraph);
        this.onCreateScriptForObject = onCreateScriptForObject;
        this.selectedAssetPath = selectedAssetPath;
        this.atlasSection = atlasSection;
        this.textureSection = textureSection;
        this.openTilemapDock = openTilemapDock;
    }

    private EditorSelection selection() {
        return activeDocument.get().selection();
    }

    private EditorHistory history() {
        return activeDocument.get().history();
    }

    public void render() {
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_TITLE, WINDOW_TITLE))) {
            ImGui.end();
            return;
        }
        propertyRows.beginFrame();
        Optional<GameObject> selected = selection().get();
        if (selected.isPresent()) {
            renderBody(selected.get());
        } else if (!renderAssetSections()) {
            renderEmpty();
        }
        propertyRows.pruneStaleKeys();
        assetPicker.render();
        removeConfirm.render();
        scriptNameDialog.render();
        ImGui.end();
    }

    private boolean renderAssetSections() {
        Optional<Path> selectedAsset = selectedAssetPath.get();
        return textureSection.render(selectedAsset) || atlasSection.render(selectedAsset);
    }

    private void renderEmpty() {
        centerVertically();
        centerText(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_NOTHING_SELECTED));
        ImGui.dummy(0.0f, EMPTY_STATE_LINE_GAP);
        centerText(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_PICK_OBJECT));
        centerText(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_EDIT_COMPONENTS));
    }

    private static void centerVertically() {
        float free = ImGui.getContentRegionAvailY() - EMPTY_STATE_BLOCK_HEIGHT;
        if (free > 0.0f) {
            ImGui.dummy(0.0f, free * 0.5f);
        }
    }

    private static void centerText(String text) {
        float indent = (ImGui.getContentRegionAvailX() - ImGui.calcTextSize(text).x) * 0.5f;
        if (indent > 0.0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        }
        ImGui.textDisabled(text);
    }

    private void renderBody(GameObject gameObject) {
        renderObjectHeader(gameObject);
        ImGui.separator();
        for (IComponent component : new ArrayList<>(gameObject.components())) {
            renderComponentBlock(gameObject, component);
        }
        ImGui.spacing();
        renderAddComponentButton(gameObject);
    }

    private void renderObjectHeader(GameObject gameObject) {
        boolean active = gameObject.active();
        if (ImGui.checkbox("##active", active)) {
            gameObject.setActive(!active);
        }
        ImGui.sameLine();
        ImGui.textUnformatted(gameObject.name());
        int componentCount = gameObject.components().size();
        ImGui.textDisabled(componentCount <= 1
                ? I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_COMPONENT_COUNT_SINGULAR, componentCount)
                : I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_COMPONENT_COUNT_PLURAL, componentCount));
    }

    private void renderComponentBlock(GameObject gameObject, IComponent component) {
        ImGui.pushID(gameObject.id() + "#" + component.getClass().getName());
        icons.drawInline(ComponentIcons.forComponent(component), EditorStyle.ICON_SIZE_SMALL);
        boolean expanded = ImGui.collapsingHeader(displayNameOf(component), COMPONENT_HEADER_FLAGS);
        renderRemoveButton(gameObject, component);
        if (expanded) {
            renderComponentProperties(gameObject, component);
        }
        ImGui.popID();
    }

    private void renderRemoveButton(GameObject gameObject, IComponent component) {
        if (component instanceof Transform3D) {
            return;
        }
        ImGui.sameLine(ImGui.getContentRegionMaxX() - EditorStyle.ICON_SIZE_SMALL * 2.0f);
        if (icons.iconButton("remove-component", EditorIcon.REMOVE, EditorStyle.ICON_SIZE_SMALL - 2.0f)) {
            removeConfirm.open(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_REMOVE_COMPONENT_MESSAGE,
                            displayNameOf(component), gameObject.name()),
                    () -> removeComponent(gameObject, component));
        }
    }

    private void removeComponent(GameObject gameObject, IComponent component) {
        history().execute(new RemoveComponentCommand(gameObject, asComponentClass(component), component));
            notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_TOAST_COMPONENT_REMOVED));
    }

    private void renderComponentProperties(GameObject gameObject, IComponent component) {
        List<ExportedProperty> properties = Reflection.scan(component);
        if (properties.isEmpty() && !(component instanceof MeshRenderer)) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_NO_EXPORTED_FIELDS));
            return;
        }
        String keyPrefix = gameObject.id() + "#" + component.getClass().getName()
                + "#" + System.identityHashCode(component);
        for (ExportedProperty property : properties) {
            propertyRows.renderProperty(component, property, keyPrefix + "." + property.fieldName());
        }
        if (component instanceof MeshRenderer renderer) {
            materialsSection.render(renderer);
        }
        if (component instanceof MultiMeshRenderer multiMesh) {
            materialsSection.render(multiMesh);
            populateSection.render(multiMesh);
        }
        if (component instanceof Animator animator) {
            animatorSection.render(gameObject, animator);
        }
        if (component instanceof ParticleEffect particleEffect) {
            vfxSection.render(particleEffect);
        }
        if (component instanceof Camera3D camera) {
            renderCameraPostEffects(camera);
        }
        if (component instanceof GraphComponent graph) {
            graphSection.render(graph);
        }
        if (spriteColliderFit.render(gameObject, component)) {
            activeDocument.get().markDirty();
        }
        if (component instanceof TilemapRenderer tilemapRenderer) {
            renderTilemapSummary(tilemapRenderer);
        }
    }

    private void renderTilemapSummary(TilemapRenderer renderer) {
        ImGui.spacing();
        ImGui.separator();
        renderer.tilemapValue().ifPresentOrElse(InspectorView::renderTilemapFacts,
                () -> ImGui.textDisabled("No tilemap asset assigned yet."));
        if (ImGui.button("Open the TileMap panel")) {
            openTilemapDock.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Tools, palette, layers, terrains and per tile settings live in the bottom dock.");
        }
    }

    private static void renderTilemapFacts(fr.epistudio.epysia.assets.epytilemap.SpriteTilemap tilemap) {
        ImGui.textDisabled(tilemap.width() + " x " + tilemap.height() + " cells");
        ImGui.textDisabled(tilemap.layerCount() + " layer(s), " + tilemap.terrains().size() + " terrain(s)");
        ImGui.textDisabled(tilemap.solidTiles().size() + " solid tile(s)");
    }

    private void renderCameraPostEffects(Camera3D camera) {
        ImGui.spacing();
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_POST_EFFECTS));
        ImGui.separator();
        boolean overrideActive = camera.postEffectStack().isPresent();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_OVERRIDE_POST_EFFECTS,
                "inspector-override-post-effects"), overrideActive)) {
            toggleCameraPostEffects(camera, overrideActive);
        }
        camera.postEffectStack().ifPresent(stack ->
                postEffectsSection.render(stack, () -> activeDocument.get().markDirty()));
    }

    private void toggleCameraPostEffects(Camera3D camera, boolean overrideActive) {
        if (overrideActive) {
            camera.disablePostEffectStack();
        } else {
            camera.enablePostEffectStack();
        }
        activeDocument.get().markDirty();
    }

    private void renderAddComponentButton(GameObject gameObject) {
        if (ImGui.button(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_ADD_COMPONENT,
                "inspector-add-component"), ImGui.getContentRegionAvailX(), 0.0f)) {
            componentSearch.set("");
            ImGui.openPopup(ADD_COMPONENT_POPUP);
        }
        renderAddComponentPopup(gameObject);
    }

    private void renderAddComponentPopup(GameObject gameObject) {
        if (!ImGui.beginPopup(ADD_COMPONENT_POPUP)) {
            return;
        }
        ImGui.inputTextWithHint("##component-search",
                I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_SEARCH_COMPONENTS), componentSearch);
        ImGui.separator();
        renderNewScriptOption(gameObject);
        ImGui.separator();
        String query = componentSearch.get().toLowerCase(Locale.ROOT);
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            renderComponentOption(gameObject, entry, query);
        }
        ImGui.endPopup();
    }

    private void renderComponentOption(GameObject gameObject, ComponentRegistry.Entry entry, String query) {
        String label = entry.category() + " / " + entry.displayName();
        if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)) {
            return;
        }
        if (ImGui.selectable(label)) {
            addComponent(gameObject, entry);
            ImGui.closeCurrentPopup();
        }
    }

    private void renderNewScriptOption(GameObject gameObject) {
        if (ImGui.selectable(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_NEW_SCRIPT,
                "inspector-new-script"))) {
            scriptNameDialog.open(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_NEW_SCRIPT_CLASS_NAME), "MyBehaviour",
                    name -> requestScriptCreation(name, gameObject));
            ImGui.closeCurrentPopup();
        }
    }

    private void requestScriptCreation(String requestedName, GameObject gameObject) {
        String className = pascalCase(requestedName);
        if (!SourceVersion.isName(className)) {
            notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_TOAST_INVALID_CLASS_NAME,
                    requestedName));
            return;
        }
        onCreateScriptForObject.accept(className, gameObject);
    }

    private static String pascalCase(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private void addComponent(GameObject gameObject, ComponentRegistry.Entry entry) {
        if (gameObject.getComponent(entry.componentClass()).isPresent()) {
            notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_TOAST_COMPONENT_EXISTS));
            return;
        }
        history().execute(new AddComponentCommand(gameObject, entry.componentClass()));
        notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_TOAST_COMPONENT_ADDED));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends IComponent> asComponentClass(IComponent component) {
        return (Class<? extends IComponent>) component.getClass();
    }

    private String displayNameOf(IComponent component) {
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            if (entry.componentClass().equals(component.getClass())) {
                return entry.displayName();
            }
        }
        return component.getClass().getSimpleName();
    }
}
