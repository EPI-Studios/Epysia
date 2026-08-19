package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.inspector.ColliderFitSection;
import fr.epistudio.epysia.editor.inspector.InspectorDependencies;
import fr.epistudio.epysia.editor.inspector.ComponentSections;
import fr.epistudio.epysia.editor.inspector.InspectorSectionBundle;
import fr.epistudio.epysia.editor.inspector.PropertyKeyCache;
import fr.epistudio.epysia.editor.ui.kit.EmptyStates;
import fr.epistudio.epysia.editor.ui.kit.Category;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.editor.ui.kit.Switches;
import fr.epistudio.epysia.editor.ui.kit.Notices;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.editor.assets.SpriteTextureLookup;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddComponentCommand;
import fr.epistudio.epysia.editor.command.builtin.MergeIntoMultiMeshCommand;
import fr.epistudio.epysia.editor.command.builtin.RemoveComponentCommand;
import fr.epistudio.epysia.editor.command.builtin.SetComponentEnabledCommand;
import fr.epistudio.epysia.editor.command.builtin.SetGameObjectFlagCommand;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.editor.command.builtin.ApplyInstanceToPrefabCommand;
import fr.epistudio.epysia.editor.command.builtin.RevertPrefabOverridesCommand;
import fr.epistudio.epysia.prefab.PrefabApplier;
import fr.epistudio.epysia.prefab.PrefabFieldApplier;
import fr.epistudio.epysia.prefab.PrefabRefresher;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.editor.icons.ComponentIcons;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.reflection.ComponentAction;
import fr.epistudio.epysia.reflection.Reflection;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.ui.kit.Fields;
import fr.epistudio.epysia.editor.ui.kit.Texts;

public final class InspectorView {


    public static final String WINDOW_TITLE = "Inspector";

    private static final String QUICK_ADD_POPUP = "##quick-add-component";
    private static final int SEARCH_CAPACITY = 128;


    private final Supplier<SceneDocument> activeDocument;
    private final ComponentRegistry componentRegistry;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final PropertyRows propertyRows;
    private final AssetPicker assetPicker;
    private final ConfirmDialog removeConfirm = new ConfirmDialog(
            I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_REMOVE_COMPONENT_TITLE),
            I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_REMOVE_COMPONENT_CONFIRM));
    private boolean playModeActive;
    private final ComponentSections componentSections;
    private final PropertyKeyCache propertyKeys = new PropertyKeyCache();
    private final ImString componentSearch = new ImString(SEARCH_CAPACITY);
    private final AddComponentBrowser addComponentBrowser;
    private Optional<GameObject> addComponentTarget = Optional.empty();
    private final Consumer<GameObject> onRequestNewScript;
    private final Supplier<Optional<Path>> selectedAssetPath;
    private final AtlasInspectorSection atlasSection;
    private final TextureInspectorSection textureSection;
    private final ProceduralTextureSection proceduralSection;
    private final EngineServices engineServices;
    private PrefabRefresher prefabRefresher;
    private PrefabFieldApplier fieldApplier;
    private final SpriteTextureLookup spriteTextures;
    private final SurfaceUniformRows spriteUniformRows;

    public InspectorView(InspectorDependencies dependencies, AssetPicker assetPicker,
                         Consumer<GameObject> onRequestNewScript,
                         Consumer<Path> onOpenGraph,
                         Supplier<Optional<Path>> selectedAssetPath,
                         AtlasInspectorSection atlasSection,
                         TextureInspectorSection textureSection,
                         ProceduralTextureSection proceduralSection,
                         Runnable openTilemapDock) {
        Supplier<SceneDocument> activeDocument = dependencies.activeDocument();
        Project project = dependencies.project();
        ThumbnailCache thumbnails = dependencies.thumbnails();
        Notifier notifier = dependencies.notifier();
        this.activeDocument = activeDocument;
        this.componentRegistry = dependencies.componentRegistry();
        this.notifier = notifier;
        this.icons = dependencies.icons();
        this.assetPicker = assetPicker;
        this.propertyRows = new PropertyRows(activeDocument, assetPicker);
        this.spriteTextures = new SpriteTextureLookup(project.locator());
        this.spriteUniformRows = new SurfaceUniformRows(projectShaderLoader(project), this::history,
                new AssetFilePicker(project, thumbnails), project.locator());
        this.componentSections = ComponentSections.of(new InspectorSectionBundle(
                new UiElementSection(dependencies.objectFactory(),
                        () -> activeDocument.get().scene(), () -> activeDocument.get().history()),
                new MaterialsSection(activeDocument, thumbnails, project, notifier),
                new PopulateSection(activeDocument, notifier, project),
                new AnimatorSection(activeDocument, project),
                new VfxSection(activeDocument, project),
                new CameraPostEffectsSection(new PostEffectsSection(project, thumbnails),
                        () -> activeDocument.get().markDirty()),
                new GraphSection(activeDocument, onOpenGraph, project.locator()),
                new ColliderFitSection(new SpriteColliderFitSection(project.locator()),
                        new MeshColliderFitSection(),
                        () -> activeDocument.get().markDirty(),
                        command -> activeDocument.get().history().execute(command)),
                new TilemapSummarySection(openTilemapDock),
                new RigidBodyLiveSection(),
                new NavMeshSurfaceSection(dependencies::engineServices),
                () -> playModeActive));
        this.onRequestNewScript = onRequestNewScript;
        this.selectedAssetPath = selectedAssetPath;
        this.atlasSection = atlasSection;
        this.textureSection = textureSection;
        this.proceduralSection = proceduralSection;
        this.engineServices = dependencies.engineServices();
        this.addComponentBrowser = new AddComponentBrowser(this.componentRegistry, this.icons,
                this::addComponentToTarget, this::requestNewScript);
    }

    private static ShaderLoader projectShaderLoader(Project project) {
        ShaderLoader loader = ShaderLoader.autoDetect();
        loader.useProject(project::locator);
        return loader;
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
        renderPlayModeNotice();
        propertyRows.beginFrame();
        Optional<GameObject> selected = selection().get();
        if (selected.isPresent()) {
            renderMultiSelectionTools();
            renderBody(selected.get());
            renderSpriteUniforms(selected.get());
            renderSelectedAssetFooter(selected.get());
        } else if (!renderAssetSections()) {
            renderEmpty();
        }
        propertyRows.pruneStaleKeys();
        assetPicker.render();
        removeConfirm.render();
        addComponentTarget.ifPresent(addComponentBrowser::render);
        ImGui.end();
    }

    private void renderMultiSelectionTools() {
        List<GameObject> all = selection().all();
        if (all.size() < 2) {
            return;
        }
        long mergeable = all.stream().filter(MergeIntoMultiMeshCommand::mergeable).count();
        if (mergeable < 2) {
            return;
        }
        if (ImGui.button(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_MERGE_MULTIMESH, mergeable))) {
            history().execute(new MergeIntoMultiMeshCommand(all));
            return;
        }
        Sections.caption(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_MERGE_MULTIMESH_HINT));
        Sections.divider();
    }

    private void renderSpriteUniforms(GameObject gameObject) {
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        if (sprite == null || sprite.surfaceShaderPath().isEmpty()) {
            return;
        }
        Sections.divider();
        if (Sections.header(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_SPRITE_SHADER_UNIFORMS))) {
            spriteUniformRows.render(sprite, List.of(sprite.surfaceShaderPath()));
        }
    }

    private void renderSelectedAssetFooter(GameObject gameObject) {
        Optional<Path> asset = selectedAssetPath.get()
                .or(() -> spriteTextures.textureFileOf(gameObject));
        if (asset.isEmpty()) {
            return;
        }
        Sections.divider();
        if (Sections.header(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_TEXTURE_IMPORT))
                && !textureSection.render(asset)) {
            atlasSection.render(asset);
        }
    }

    public void setPlayModeActive(boolean value) {
        playModeActive = value;
    }

    private void renderPlayModeNotice() {
        if (!playModeActive) {
            return;
        }
        Notices.warning(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_PLAY_MODE_NOTICE));
        ImGui.separator();
    }

    private boolean renderAssetSections() {
        Optional<Path> selectedAsset = selectedAssetPath.get();
        return proceduralSection.render(selectedAsset)
                || textureSection.render(selectedAsset)
                || atlasSection.render(selectedAsset);
    }

    private void renderEmpty() {
        EmptyStates.centered(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_NOTHING_SELECTED),
                List.of(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_PICK_OBJECT),
                        I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_EDIT_COMPONENTS)));
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
        if (Switches.draw("##active", active) != active) {
            history().execute(new SetGameObjectFlagCommand(gameObject,
                    SetGameObjectFlagCommand.Flag.ACTIVE, !active));
        }
        ImGui.sameLine();
        int componentCount = gameObject.components().size();
        Texts.muted(I18n.plural(TextKey.EDITOR_INSPECTOR_VIEW_COMPONENT_COUNT, componentCount));
        Category.draw(gameObject.name(), 0);
        renderKeepOnSceneChange(gameObject);
        renderPrefabSection(gameObject);
    }

    private void renderKeepOnSceneChange(GameObject gameObject) {
        boolean keep = gameObject.keepOnSceneChange();
        ImBoolean flag = new ImBoolean(keep);
        if (ImGui.checkbox("##keep-on-scene-change", flag)) {
            history().execute(new SetGameObjectFlagCommand(gameObject,
                    SetGameObjectFlagCommand.Flag.KEEP_ON_SCENE_CHANGE, flag.get()));
        }
        ImGui.sameLine();
        Texts.muted("Keep on scene change");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Survives load and unload, so the object carries state between levels."
                    + "\nThis is not autoload: nothing creates it for you, it has to exist already.");
        }
    }

    private void renderPrefabSection(GameObject gameObject) {
        if (!gameObject.isPrefabInstance()) {
            return;
        }
        Texts.muted("Prefab " + gameObject.prefabSource()
                + " (" + gameObject.overriddenProperties().size() + " overridden)");
        if (ImGui.button("Revert all")) {
            history().execute(new RevertPrefabOverridesCommand(gameObject, prefabRefresher(),
                    fieldApplier()));
            notifier.show("Reverted to " + gameObject.prefabSource());
        }
        ImGui.sameLine();
        if (ImGui.button("Apply to prefab")) {
            applyInstanceToPrefab(gameObject);
        }
        ImGui.spacing();
    }

    private void applyInstanceToPrefab(GameObject gameObject) {
        Optional<Path> file = resolvePrefabFile(gameObject.prefabSource());
        if (file.isEmpty()) {
            notifier.show("Prefab not found: " + gameObject.prefabSource());
            return;
        }
        history().execute(new ApplyInstanceToPrefabCommand(gameObject, file.get(),
                new PrefabApplier(componentRegistry)));
        prefabRefresher().refresh(activeDocument.get().scene());
        notifier.show("Applied to " + gameObject.prefabSource());
    }

    private PrefabFieldApplier fieldApplier() {
        if (fieldApplier == null) {
            fieldApplier = new SceneSerializer(componentRegistry)::applyFields;
        }
        return fieldApplier;
    }

    private PrefabRefresher prefabRefresher() {
        if (prefabRefresher == null) {
            prefabRefresher = new PrefabRefresher(this::readPrefabText, fieldApplier());
        }
        return prefabRefresher;
    }

    private Optional<String> readPrefabText(String prefabSource) {
        return resolvePrefabFile(prefabSource).flatMap(file -> {
            try {
                return Optional.of(Files.readString(file));
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        });
    }

    private Optional<Path> resolvePrefabFile(String prefabSource) {
        return engineServices.assets().locator().file(prefabSource);
    }

    private void renderComponentBlock(GameObject gameObject, IComponent component) {
        ImGui.pushID(gameObject.id().hashCode());
        ImGui.pushID(component.getClass().getName());
        boolean expanded = Sections.header(displayNameOf(component),
                icons.textureId(ComponentIcons.forComponent(component)));
        renderRemoveButton(gameObject, component);
        renderEnabledToggle(component);
        if (expanded) {
            renderComponentProperties(gameObject, component);
        }
        ImGui.popID();
        ImGui.popID();
    }

    private void renderEnabledToggle(IComponent component) {
        if (component instanceof Transform3D || component instanceof Transform2D) {
            return;
        }
        ImGui.sameLine(ImGui.getContentRegionMaxX() - EditorStyle.iconSizeSmall() * 4.0f);
        ImBoolean enabled = new ImBoolean(component.enabled());
        if (ImGui.checkbox("##component-enabled", enabled)) {
            history().execute(new SetComponentEnabledCommand(component,
                    component.enabled(), enabled.get()));
        }
    }

    private void renderRemoveButton(GameObject gameObject, IComponent component) {
        if (component instanceof Transform3D) {
            return;
        }
        ImGui.sameLine(ImGui.getContentRegionMaxX() - EditorStyle.iconSizeSmall() * 2.0f);
        if (icons.iconButton("remove-component", EditorIcon.REMOVE, EditorStyle.iconSizeSmall() - 2.0f)) {
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
            Texts.muted(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_NO_EXPORTED_FIELDS));
            return;
        }
        String keyPrefix = propertyKeys.prefixFor(gameObject, component);
        for (ExportedProperty property : properties) {
            if (property.isHiddenInEditor()) {
                continue;
            }
            propertyRows.renderProperty(component, property,
                    propertyKeys.keyFor(keyPrefix, property.fieldName()));
        }
        componentSections.render(gameObject, component);
        renderComponentActions(component);
    }

    private void renderComponentActions(IComponent component) {
        List<ComponentAction> actions = Reflection.actionsOf(component);
        if (actions.isEmpty()) {
            return;
        }
        ImGui.separator();
        for (ComponentAction action : actions) {
            if (ImGui.button(action.label())) {
                runComponentAction(component, action);
            }
            if (!action.tooltip().isBlank() && ImGui.isItemHovered()) {
                ImGui.setTooltip(action.tooltip());
            }
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private void runComponentAction(IComponent component, ComponentAction action) {
        try {
            action.invoke(component, engineServices);
            activeDocument.get().markDirty();
            notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_ACTION_RAN, action.label()));
        } catch (RuntimeException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_ACTION_FAILED,
                    action.label(), error.getMessage()));
        }
    }

    private void renderAddComponentButton(GameObject gameObject) {
        if (ImGui.button(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_ADD_COMPONENT,
                "inspector-add-component"), ImGui.getContentRegionAvailX(), 0.0f)) {
            addComponentTarget = Optional.of(gameObject);
            addComponentBrowser.open();
        }
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            addComponentTarget = Optional.of(gameObject);
            componentSearch.set("");
        }
        renderQuickAddMenu(gameObject);
    }

    private void renderQuickAddMenu(GameObject gameObject) {
        if (!ImGui.beginPopupContextItem(QUICK_ADD_POPUP)) {
            return;
        }
        Fields.underlined("##component-search",
                I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_SEARCH_COMPONENTS), componentSearch,
                ImGui.getContentRegionAvailX());
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
            ImGui.closeCurrentPopup();
            onRequestNewScript.accept(gameObject);
        }
    }

    private void requestNewScript() {
        addComponentTarget.ifPresent(onRequestNewScript);
    }

    private void addComponentToTarget(ComponentRegistry.Entry entry) {
        addComponentTarget.ifPresent(target -> addComponent(target, entry));
    }

    private void addComponent(GameObject gameObject, ComponentRegistry.Entry entry) {
        if (gameObject.getComponent(entry.componentClass()).isPresent()) {
            notifier.show(I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_TOAST_COMPONENT_EXISTS));
            return;
        }
        Optional<String> failure = history().execute(new AddComponentCommand(gameObject, entry.componentClass()));
        if (failure.isPresent()) {
            notifier.show("Could not add " + entry.displayName() + ": " + failure.get());
            return;
        }
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
