package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
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
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.lang.model.SourceVersion;

public final class InspectorView {

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
    private final ConfirmDialog removeConfirm = new ConfirmDialog("Remove this component?", "Remove");
    private final ImString componentSearch = new ImString(SEARCH_CAPACITY);
    private final MaterialsSection materialsSection;
    private final NameDialog scriptNameDialog = new NameDialog("##new-script-name");
    private final BiConsumer<String, GameObject> onCreateScriptForObject;

    public InspectorView(Supplier<SceneDocument> activeDocument, ComponentRegistry componentRegistry,
                         Notifier notifier, IconWidgets icons, AssetPicker assetPicker,
                         ThumbnailCache thumbnails, BiConsumer<String, GameObject> onCreateScriptForObject) {
        this.activeDocument = activeDocument;
        this.componentRegistry = componentRegistry;
        this.notifier = notifier;
        this.icons = icons;
        this.assetPicker = assetPicker;
        this.propertyRows = new PropertyRows(activeDocument, assetPicker);
        this.materialsSection = new MaterialsSection(activeDocument, thumbnails);
        this.onCreateScriptForObject = onCreateScriptForObject;
    }

    private EditorSelection selection() {
        return activeDocument.get().selection();
    }

    private EditorHistory history() {
        return activeDocument.get().history();
    }

    public void render() {
        if (!ImGui.begin(WINDOW_TITLE)) {
            ImGui.end();
            return;
        }
        propertyRows.beginFrame();
        Optional<GameObject> selected = selection().get();
        if (selected.isEmpty()) {
            renderEmpty();
        } else {
            renderBody(selected.get());
        }
        propertyRows.pruneStaleKeys();
        assetPicker.render();
        removeConfirm.render();
        scriptNameDialog.render();
        ImGui.end();
    }

    private void renderEmpty() {
        ImGui.textDisabled("No selection");
        ImGui.textDisabled("Select an object in the hierarchy.");
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
        ImGui.textDisabled(componentCount + (componentCount <= 1 ? " component" : " components"));
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
            removeConfirm.open(displayNameOf(component) + " will be removed from " + gameObject.name() + ".",
                    () -> removeComponent(gameObject, component));
        }
    }

    private void removeComponent(GameObject gameObject, IComponent component) {
        history().execute(new RemoveComponentCommand(gameObject, asComponentClass(component), component));
        notifier.show("Component removed.");
    }

    private void renderComponentProperties(GameObject gameObject, IComponent component) {
        List<ExportedProperty> properties = Reflection.scan(component);
        if (properties.isEmpty() && !(component instanceof MeshRenderer)) {
            ImGui.textDisabled("No exported fields.");
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
    }

    private void renderAddComponentButton(GameObject gameObject) {
        if (ImGui.button("Add Component", ImGui.getContentRegionAvailX(), 0.0f)) {
            componentSearch.set("");
            ImGui.openPopup(ADD_COMPONENT_POPUP);
        }
        renderAddComponentPopup(gameObject);
    }

    private void renderAddComponentPopup(GameObject gameObject) {
        if (!ImGui.beginPopup(ADD_COMPONENT_POPUP)) {
            return;
        }
        ImGui.inputTextWithHint("##component-search", "Search components", componentSearch);
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
        if (ImGui.selectable("New Script...")) {
            scriptNameDialog.open("New script class name", "MyBehaviour",
                    name -> requestScriptCreation(name, gameObject));
            ImGui.closeCurrentPopup();
        }
    }

    private void requestScriptCreation(String requestedName, GameObject gameObject) {
        String className = pascalCase(requestedName);
        if (!SourceVersion.isName(className)) {
            notifier.show("Invalid class name: " + requestedName);
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
            notifier.show("This component already exists on the object.");
            return;
        }
        history().execute(new AddComponentCommand(gameObject, entry.componentClass()));
        notifier.show("Component added.");
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
