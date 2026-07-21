package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.editor.command.CompositeCommand;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddGameObjectCommand;
import fr.epistudio.epysia.editor.command.builtin.InstantiatePrefabCommand;
import fr.epistudio.epysia.editor.command.builtin.RemoveGameObjectCommand;
import fr.epistudio.epysia.editor.command.builtin.RenameCommand;
import fr.epistudio.epysia.editor.command.builtin.ReparentCommand;
import fr.epistudio.epysia.editor.icons.ComponentIcons;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.scene.UniqueObjectName;

public final class HierarchyView {

    public static final String WINDOW_TITLE = "Hierarchy";
    static final String PAYLOAD_GAMEOBJECT = "gameobject";

    private static final String DEFAULT_NAME = "GameObject";
    private static final int RENAME_CAPACITY = 256;
    private static final int FILTER_CAPACITY = 128;
    private static final String ELLIPSIS = "...";
    private static final float INDENT_GUIDE_OFFSET = 6.0f;
    private static final int INDENT_GUIDE_COLOR = EditorStyle.rgba(255, 255, 255, 26);

    private final Supplier<SceneDocument> activeDocument;
    private final ComponentRegistry componentRegistry;
    private final Notifier notifier;
    private final Consumer<GameObject> onSaveAsPrefab;
    private final Consumer<GameObject> onFrameRequested;
    private final GameObjectFactory objectFactory;
    private final Supplier<Vector3f> spawnPoint;
    private final ConfirmDialog deleteConfirm = new ConfirmDialog("Delete selected objects?", "Delete");
    private final ImString renameInput = new ImString(RENAME_CAPACITY);
    private final ImString filterInput = new ImString(FILTER_CAPACITY);
    private final IconWidgets icons;
    private final List<Row> rows = new ArrayList<>();
    private GameObject renameTarget;
    private boolean renameFocusRequested;
    private int newObjectCounter;

    public HierarchyView(Supplier<SceneDocument> activeDocument, ComponentRegistry componentRegistry,
                         Notifier notifier, IconWidgets icons, Consumer<GameObject> onSaveAsPrefab,
                         Consumer<GameObject> onFrameRequested,
                         GameObjectFactory objectFactory,
                         Supplier<Vector3f> spawnPoint) {
        this.activeDocument = activeDocument;
        this.componentRegistry = componentRegistry;
        this.notifier = notifier;
        this.icons = icons;
        this.onSaveAsPrefab = onSaveAsPrefab;
        this.onFrameRequested = onFrameRequested;
        this.objectFactory = objectFactory;
        this.spawnPoint = spawnPoint;
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
        renderHeader();
        ImGui.separator();
        rebuildRows();
        renderRows();
        renderBackgroundDropZone();
        handleShortcuts();
        deleteConfirm.render();
        ImGui.end();
    }

    private void renderHeader() {
        if (icons.iconButton("hierarchy-add", EditorIcon.ADD,
                EditorStyle.ICON_SIZE_SMALL)) {
            createEmptyGameObject();
        }
        ImGui.sameLine();
        ImGui.beginDisabled(selection().count() == 0);
        if (icons.iconButton("hierarchy-remove", EditorIcon.REMOVE,
                EditorStyle.ICON_SIZE_SMALL)) {
            askDeleteSelected();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##hierarchy-filter", "Filter", filterInput);
    }

    private boolean matchesFilter(GameObject gameObject) {
        String query = filterInput.get().replace("\0", "").strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() || gameObject.name().toLowerCase(Locale.ROOT).contains(query);
    }

    private void renderRows() {
        for (int index = 0; index < rows.size(); index++) {
            renderRow(rows.get(index), index);
        }
        if (rows.isEmpty() && isFiltering()) {
            ImGui.textDisabled("No object matches the filter.");
        } else if (rows.isEmpty()) {
            ImGui.textDisabled("The scene is empty.");
            ImGui.textDisabled("Right-click here or use the GameObject");
            ImGui.textDisabled("menu to create your first object.");
        }
    }

    private void renderRow(Row row, int index) {
        ImGui.pushID(row.gameObject().id().toString());
        drawIndentGuides(row.depth());
        ImGui.indent(row.depth() * EditorStyle.INDENT_SPACING + 1.0f);
        icons.drawInline(ComponentIcons.forGameObject(row.gameObject()), EditorStyle.ICON_SIZE_SMALL);
        if (renameTarget == row.gameObject()) {
            renderRenameField();
        } else {
            renderSelectable(row, index);
        }
        ImGui.unindent(row.depth() * EditorStyle.INDENT_SPACING + 1.0f);
        ImGui.popID();
    }

    private void renderSelectable(Row row, int index) {
        boolean selected = selection().isSelected(row.gameObject());
        String name = row.gameObject().name();
        if (ImGui.selectable(elide(name, ImGui.getContentRegionAvailX()), selected,
                ImGuiSelectableFlags.AllowDoubleClick)) {
            handleRowClick(row, index);
        }
        if (ImGui.isItemHovered()) {
            showFullNameTooltip(name);
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            onFrameRequested.accept(row.gameObject());
        }
        renderRowDragSource(row);
        renderRowDropTarget(row);
        renderRowContextMenu(row);
    }

    private static void drawIndentGuides(int depth) {
        if (depth == 0) {
            return;
        }
        float startX = ImGui.getCursorScreenPosX();
        float startY = ImGui.getCursorScreenPosY();
        float height = ImGui.getTextLineHeightWithSpacing();
        for (int level = 0; level < depth; level++) {
            float x = startX + level * EditorStyle.INDENT_SPACING + INDENT_GUIDE_OFFSET;
            ImGui.getWindowDrawList().addLine(x, startY, x, startY + height, INDENT_GUIDE_COLOR);
        }
    }

    private static void showFullNameTooltip(String name) {
        if (ImGui.calcTextSize(name).x > ImGui.getContentRegionAvailX()) {
            ImGui.setTooltip(name);
        }
    }

    private static String elide(String name, float availableWidth) {
        if (ImGui.calcTextSize(name).x <= availableWidth) {
            return name;
        }
        String ellipsis = ELLIPSIS;
        int length = name.length();
        while (length > 1 && ImGui.calcTextSize(name.substring(0, length) + ellipsis).x > availableWidth) {
            length--;
        }
        return name.substring(0, length) + ellipsis;
    }

    private void handleRowClick(Row row, int index) {
        if (ImGui.getIO().getKeyShift()) {
            selectRangeTo(row, index);
        } else if (ImGui.getIO().getKeyCtrl()) {
            selection().toggle(row.gameObject());
        } else {
            selection().select(row.gameObject());
        }
    }

    private void renderRowDragSource(Row row) {
        if (!ImGui.beginDragDropSource()) {
            return;
        }
        ImGui.setDragDropPayload(PAYLOAD_GAMEOBJECT, row.gameObject());
        ImGui.textUnformatted(row.gameObject().name());
        ImGui.endDragDropSource();
    }

    private void renderRowDropTarget(Row row) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        GameObject dropped = ImGui.acceptDragDropPayload(PAYLOAD_GAMEOBJECT, GameObject.class);
        if (dropped != null) {
            reparentOnto(dropped, row.gameObject());
        }
        ImGui.endDragDropTarget();
    }

    private void renderRowContextMenu(Row row) {
        if (!ImGui.beginPopupContextItem("hierarchy-row-menu")) {
            return;
        }
        if (!selection().isSelected(row.gameObject())) {
            selection().select(row.gameObject());
        }
        renderContextMenuItems(row.gameObject());
        ImGui.endPopup();
    }

    private void renderContextMenuItems(GameObject target) {
        if (ImGui.menuItem("Rename", "F2")) {
            beginRename(target);
        }
        if (ImGui.menuItem("Duplicate", "Ctrl+D")) {
            duplicateSelected();
        }
        if (hasParent(target) && ImGui.menuItem("Unparent")) {
            unparent(target);
        }
        if (ImGui.menuItem("Save as Prefab")) {
            onSaveAsPrefab.accept(target);
        }
        ImGui.separator();
        if (ImGui.menuItem("Delete", "Del")) {
            askDeleteSelected();
        }
    }

    private void renderRenameField() {
        if (renameFocusRequested) {
            ImGui.setKeyboardFocusHere();
            renameFocusRequested = false;
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.inputText("##rename", renameInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
            commitRename();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renameTarget = null;
        } else if (!ImGui.isItemActive() && !renameFocusRequested && ImGui.isItemDeactivated()) {
            commitRename();
        }
    }

    private void renderBackgroundDropZone() {
        ImGui.invisibleButton("##hierarchy-background", Math.max(1.0f, ImGui.getContentRegionAvailX()),
                Math.max(24.0f, ImGui.getContentRegionAvailY()));
        renderBackgroundContextMenu();
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        GameObject dropped = ImGui.acceptDragDropPayload(PAYLOAD_GAMEOBJECT, GameObject.class);
        if (dropped != null) {
            unparent(dropped);
        }
        String prefabPath = ImGui.acceptDragDropPayload(AssetMimeTypes.PREFAB, String.class);
        if (prefabPath != null) {
            history().execute(new InstantiatePrefabCommand(Path.of(prefabPath), new Vector3f()));
        }
        ImGui.endDragDropTarget();
    }

    private void renderBackgroundContextMenu() {
        if (!ImGui.beginPopupContextItem("hierarchy-background-menu")) {
            return;
        }
        if (ImGui.beginMenu("Create")) {
            renderCreateItems();
            ImGui.endMenu();
        }
        ImGui.endPopup();
    }

    private void renderCreateItems() {
        if (ImGui.menuItem("Cube")) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CUBE, spawnPoint.get());
        }
        if (ImGui.menuItem("Plane")) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.PLANE, spawnPoint.get());
        }
        if (ImGui.menuItem("Capsule")) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CAPSULE, spawnPoint.get());
        }
        ImGui.separator();
        renderCreateLightAndCameraItems();
    }

    private void renderCreateLightAndCameraItems() {
        if (ImGui.menuItem("Point Light")) {
            objectFactory.createPointLight(spawnPoint.get());
        }
        if (ImGui.menuItem("Spot Light")) {
            objectFactory.createSpotLight(spawnPoint.get());
        }
        if (ImGui.menuItem("Directional Light")) {
            objectFactory.createDirectionalLight(spawnPoint.get());
        }
        ImGui.separator();
        if (ImGui.menuItem("Camera")) {
            objectFactory.createCamera(spawnPoint.get());
        }
        if (ImGui.menuItem("Empty")) {
            objectFactory.createEmpty(spawnPoint.get());
        }
    }

    private void handleShortcuts() {
        if (!ImGui.isWindowFocused() || ImGui.getIO().getWantTextInput()) {
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.F2)) {
            selection().get().ifPresent(this::beginRename);
        }
        if (ImGui.isKeyPressed(ImGuiKey.Delete) && selection().count() > 0) {
            askDeleteSelected();
        }
    }

    private void selectRangeTo(Row row, int rowIndex) {
        int anchorIndex = indexOfPrimary();
        if (anchorIndex < 0) {
            selection().select(row.gameObject());
            return;
        }
        int from = Math.min(anchorIndex, rowIndex);
        int to = Math.max(anchorIndex, rowIndex);
        List<GameObject> range = new ArrayList<>(to - from + 1);
        for (int i = from; i <= to; i++) {
            range.add(rows.get(i).gameObject());
        }
        selection().selectAll(range, row.gameObject());
    }

    private int indexOfPrimary() {
        GameObject primary = selection().get().orElse(null);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).gameObject() == primary) {
                return i;
            }
        }
        return -1;
    }

    private void reparentOnto(GameObject dropped, GameObject target) {
        if (dropped == target) {
            return;
        }
        Optional<Transform3D> droppedTransform = dropped.getComponent(Transform3D.class);
        Optional<Transform3D> targetTransform = target.getComponent(Transform3D.class);
        if (droppedTransform.isEmpty() || targetTransform.isEmpty()) {
            return;
        }
        if (isDescendantOf(targetTransform.get(), droppedTransform.get())) {
            notifier.show("Cannot parent an object to its own child");
            return;
        }
        if (targetTransform.get() != droppedTransform.get().parent().orElse(null)) {
            history().execute(new ReparentCommand(dropped, Optional.of(target)));
        }
    }

    private static boolean isDescendantOf(Transform3D candidate, Transform3D ancestor) {
        Transform3D walker = candidate;
        while (walker != null) {
            if (walker == ancestor) {
                return true;
            }
            walker = walker.parent().orElse(null);
        }
        return false;
    }

    private void unparent(GameObject target) {
        Optional<Transform3D> transform = target.getComponent(Transform3D.class);
        if (transform.isEmpty() || transform.get().parent().isEmpty()) {
            return;
        }
        history().execute(new ReparentCommand(target, Optional.empty()));
    }

    private static boolean hasParent(GameObject gameObject) {
        return gameObject.getComponent(Transform3D.class)
                .flatMap(Transform3D::parent)
                .isPresent();
    }

    private boolean isFiltering() {
        return !filterInput.get().replace("\0", "").strip().isEmpty();
    }

    private void rebuildRows() {
        rows.clear();
        if (isFiltering()) {
            rebuildFilteredRows();
            return;
        }
        for (GameObject gameObject : activeDocument.get().scene().gameObjects()) {
            Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
            if (transform.isEmpty()) {
                rows.add(new Row(gameObject, 0));
            } else if (transform.get().parent().isEmpty()) {
                appendDescendants(gameObject, transform.get(), 0);
            }
        }
    }

    private void rebuildFilteredRows() {
        for (GameObject gameObject : activeDocument.get().scene().gameObjects()) {
            if (matchesFilter(gameObject)) {
                rows.add(new Row(gameObject, 0));
            }
        }
    }

    private void appendDescendants(GameObject gameObject, Transform3D transform, int depth) {
        rows.add(new Row(gameObject, depth));
        for (Transform3D child : transform.children()) {
            child.owner().ifPresent(childOwner -> appendDescendants(childOwner, child, depth + 1));
        }
    }

    public void createEmptyGameObject() {
        newObjectCounter++;
        GameObject gameObject = new GameObject(DEFAULT_NAME + " " + newObjectCounter);
        gameObject.addComponent(new Transform3D());
        history().execute(new AddGameObjectCommand(gameObject, true));
        beginRename(gameObject);
    }

    public void duplicateSelected() {
        List<GameObject> targets = new ArrayList<>(selection().all());
        if (targets.isEmpty()) {
            return;
        }
        List<EditorCommand> commands = new ArrayList<>(targets.size());
        for (GameObject source : targets) {
            commands.add(new AddGameObjectCommand(buildCopy(source), targets.size() == 1));
        }
        history().execute(new CompositeCommand("Duplicate " + targets.size() + " object(s)", commands));
        notifier.show("Duplicated " + targets.size() + " object(s)");
    }

    private GameObject buildCopy(GameObject source) {
        GameObject copy = new GameObject(
                UniqueObjectName.in(activeDocument.get().scene(), source.name()));
        for (IComponent component : new ArrayList<>(source.components())) {
            copyComponentInto(copy, component);
        }
        return copy;
    }

    private void copyComponentInto(GameObject target, IComponent source) {
        Optional<Supplier<IComponent>> factory = componentRegistry.factoryFor(asComponentClass(source));
        if (factory.isEmpty()) {
            return;
        }
        IComponent fresh = factory.get().get();
        copyExportedProperties(source, fresh);
        target.addComponent(fresh);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends IComponent> asComponentClass(IComponent component) {
        return (Class<? extends IComponent>) component.getClass();
    }

    private void copyExportedProperties(IComponent source, IComponent destination) {
        List<ExportedProperty> sourceProperties = Reflection.scan(source);
        List<ExportedProperty> destinationProperties = Reflection.scan(destination);
        for (int i = 0; i < sourceProperties.size() && i < destinationProperties.size(); i++) {
            copyPropertyValue(sourceProperties.get(i), destinationProperties.get(i));
        }
    }

    private void copyPropertyValue(ExportedProperty source, ExportedProperty destination) {
        Object value = source.read();
        if (value == null) {
            return;
        }
        switch (source.kind()) {
            case FLOAT -> destination.writeFloat(((Number) value).floatValue());
            case INT -> destination.writeInt(((Number) value).intValue());
            case BOOLEAN -> destination.writeBoolean((Boolean) value);
            case STRING, ENUM -> destination.writeObject(value);
            case VECTOR3 -> copyVector(value, destination);
            case QUATERNION -> copyQuaternion(value, destination);
            case ASSET_REF -> copyAssetRef(value, destination);
            default -> {
            }
        }
    }

    private static void copyVector(Object value, ExportedProperty destination) {
        if (destination.read() instanceof Vector3f target) {
            target.set((Vector3f) value);
        }
    }

    private static void copyQuaternion(Object value, ExportedProperty destination) {
        if (destination.read() instanceof Quaternionf target) {
            target.set((Quaternionf) value);
        }
    }

    private static void copyAssetRef(Object value, ExportedProperty destination) {
        if (value instanceof AssetRef<?> sourceRef && destination.read() instanceof AssetRef<?> targetRef) {
            targetRef.setPath(sourceRef.path());
        }
    }

    private void beginRename(GameObject target) {
        renameTarget = target;
        renameInput.set(target.name());
        renameFocusRequested = true;
    }

    private void commitRename() {
        if (renameTarget == null) {
            return;
        }
        String text = renameInput.get().trim();
        if (!text.isEmpty() && !text.equals(renameTarget.name())) {
            history().execute(new RenameCommand(renameTarget, renameTarget.name(), text));
        }
        renameTarget = null;
    }

    public void askDeleteSelected() {
        if (selection().count() == 0) {
            return;
        }
        List<GameObject> targets = new ArrayList<>(selection().all());
        deleteConfirm.open("The objects and all their components will be removed. Undo with Ctrl+Z.",
                () -> deleteTargets(targets));
    }

    private void deleteTargets(List<GameObject> targets) {
        List<EditorCommand> commands = new ArrayList<>(targets.size());
        for (GameObject target : targets) {
            commands.add(new RemoveGameObjectCommand(target));
        }
        history().execute(new CompositeCommand("Delete " + targets.size() + " object(s)", commands));
        notifier.show("Deleted " + targets.size() + " object(s)");
    }

    private record Row(GameObject gameObject, int depth) {
    }
}
