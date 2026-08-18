package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
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
import fr.epistudio.epysia.editor.ui.LabelFitCache.LabelFit;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;
import imgui.ImDrawList;
import imgui.flag.ImGuiCol;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.callback.ImListClipperCallback;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.scene.UniqueObjectName;
import fr.epistudio.epysia.editor.ui.kit.SearchField;
import fr.epistudio.epysia.editor.ui.kit.Texts;

public final class HierarchyView {

    private static final int TRANSPARENT = 0;
    private static final int SELECTION_COLOR_COUNT = 3;
    private static final float SELECTED_ALPHA = 0.16f;
    private static final float HOVER_ALPHA = 0.5f;
    private static final float MARKER_WIDTH = 2.0f;
    private static final float MARKER_INSET = 2.0f;
    private static final float LABEL_INSET = 6.0f;


    public static final String WINDOW_TITLE = "Hierarchy";
    static final String PAYLOAD_GAMEOBJECT = "gameobject";

    private static final String DEFAULT_NAME = "GameObject";
    private static final int RENAME_CAPACITY = 256;
    private static final int FILTER_CAPACITY = 128;
    private static final String ELLIPSIS = "...";
    private static final String NETWORK_BADGE = "[net]";
    private static final String NETWORK_BADGE_OWNED = "[net*]";
    private static final int BADGE_NETWORKED_COLOR = 0xFFB0A060;
    private static final int BADGE_OWNED_COLOR = 0xFF70D0A0;
    private static final int MAXIMUM_CACHED_WIDGET_IDS = 8192;
    private static final float INDENT_GUIDE_OFFSET = 6.0f;
    private static final int INDENT_GUIDE_COLOR = EditorStyle.rgba(255, 255, 255, 26);

    private final Supplier<SceneDocument> activeDocument;
    private final ComponentRegistry componentRegistry;
    private final LabelFitCache labelFits = new LabelFitCache();
    private final Map<GameObject, String> widgetIds = new IdentityHashMap<>();
    private final Notifier notifier;
    private final Consumer<GameObject> onSaveAsPrefab;
    private final Consumer<GameObject> onFrameRequested;
    private final GameObjectFactory objectFactory;
    private final GameObjectCreationMenu creationMenu;
    private final Supplier<Vector3f> spawnPoint;
    private final ConfirmDialog deleteConfirm = new ConfirmDialog(
            I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_DELETE_SELECTED_TITLE),
            I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_DELETE_SELECTED_CONFIRM));
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
        this.creationMenu = new GameObjectCreationMenu(objectFactory);
        this.spawnPoint = spawnPoint;
    }

    private EditorSelection selection() {
        return activeDocument.get().selection();
    }

    private EditorHistory history() {
        return activeDocument.get().history();
    }

    public void render() {
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_TITLE, WINDOW_TITLE))) {
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
                EditorStyle.iconSizeSmall())) {
            createEmptyGameObject();
        }
        ImGui.sameLine();
        ImGui.beginDisabled(selection().count() == 0);
        if (icons.iconButton("hierarchy-remove", EditorIcon.REMOVE,
                EditorStyle.iconSizeSmall())) {
            askDeleteSelected();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        SearchField.render("##hierarchy-filter",
                I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_FILTER), filterInput,
                ImGui.getContentRegionAvailX());
    }

    private boolean matchesFilter(GameObject gameObject) {
        String query = filterInput.get().replace("\0", "").strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() || gameObject.name().toLowerCase(Locale.ROOT).contains(query);
    }

    private void renderRows() {
        if (rows.isEmpty()) {
            renderEmptyState();
            return;
        }
        ImGuiListClipper.forEach(rows.size(), new ImListClipperCallback() {
            @Override
            public void accept(int index) {
                renderRow(rows.get(index), index);
            }
        });
    }

    private void renderEmptyState() {
        if (isFiltering()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_NO_FILTER_MATCH));
            return;
        }
        Texts.muted(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_EMPTY));
        Texts.muted(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_EMPTY_HELP_1));
        Texts.muted(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_EMPTY_HELP_2));
    }

    private void renderRow(Row row, int index) {
        ImGui.pushID(widgetIdOf(row.gameObject()));
        drawIndentGuides(row.depth());
        ImGui.indent(row.depth() * EditorStyle.indentSpacing() + 1.0f);
        icons.drawInline(ComponentIcons.forGameObject(row.gameObject()), EditorStyle.iconSizeSmall());
        if (renameTarget == row.gameObject()) {
            renderRenameField();
        } else {
            renderSelectable(row, index);
        }
        ImGui.unindent(row.depth() * EditorStyle.indentSpacing() + 1.0f);
        ImGui.popID();
    }

    private String widgetIdOf(GameObject gameObject) {
        if (widgetIds.size() >= MAXIMUM_CACHED_WIDGET_IDS) {
            widgetIds.clear();
        }
        return widgetIds.computeIfAbsent(gameObject, target -> target.id().toString());
    }

    private void renderSelectable(Row row, int index) {
        boolean selected = selection().isSelected(row.gameObject());
        String name = row.gameObject().name();
        float availableWidth = ImGui.getContentRegionAvailX();
        LabelFit fit = labelFits.fitFor(name, availableWidth);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        pushTransparentSelection();
        boolean activated = ImGui.selectable("##" + widgetIdOf(row.gameObject()), selected,
                ImGuiSelectableFlags.AllowDoubleClick);
        ImGui.popStyleColor(SELECTION_COLOR_COUNT);
        paintRow(left, top, fit.label(), selected, ImGui.isItemHovered());
        if (activated) {
            handleRowClick(row, index);
        }
        if (fit.truncated() && ImGui.isItemHovered()) {
            ImGui.setTooltip(name);
        }
        renderNetworkBadge(row.gameObject());
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            onFrameRequested.accept(row.gameObject());
        }
        renderRowDragSource(row);
        renderRowDropTarget(row);
        renderRowContextMenu(row);
    }

    private static void pushTransparentSelection() {
        ImGui.pushStyleColor(ImGuiCol.Header, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, TRANSPARENT);
    }

    private static void paintRow(float left, float top, String label, boolean selected,
                                 boolean hovered) {
        float height = ImGui.getTextLineHeightWithSpacing();
        float right = left + ImGui.getContentRegionAvailX();
        ImDrawList drawList = ImGui.getWindowDrawList();
        int fill = selected
                ? EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTED_ALPHA)
                : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, hovered ? HOVER_ALPHA : 0.0f);
        drawList.addRectFilled(left, top, right, top + height, fill, EditorStyle.frameRounding());
        if (selected) {
            drawList.addRectFilled(left, top + MARKER_INSET,
                    left + EditorScale.ofAtLeastOne(MARKER_WIDTH), top + height - MARKER_INSET,
                    EditorStyle.COLOR_ACCENT);
        }
        drawList.addText(left + EditorScale.of(LABEL_INSET),
                top + (height - ImGui.getTextLineHeight()) * 0.5f,
                selected ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED, label);
    }

    private void renderNetworkBadge(GameObject gameObject) {
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (networkObject == null) {
            return;
        }
        ImGui.sameLine();
        boolean ownedHere = networkObject.ownerPeer() != NetworkObject.SERVER_PEER;
        Texts.colored(ownedHere ? BADGE_OWNED_COLOR : BADGE_NETWORKED_COLOR,
                ownedHere ? NETWORK_BADGE_OWNED : NETWORK_BADGE);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("network id " + networkObject.networkId()
                    + ", owner " + networkObject.ownerPeer());
        }
    }

    private static void drawIndentGuides(int depth) {
        if (depth == 0) {
            return;
        }
        float startX = ImGui.getCursorScreenPosX();
        float startY = ImGui.getCursorScreenPosY();
        float height = ImGui.getTextLineHeightWithSpacing();
        for (int level = 0; level < depth; level++) {
            float x = startX + level * EditorStyle.indentSpacing() + EditorScale.of(INDENT_GUIDE_OFFSET);
            ImGui.getWindowDrawList().addLine(x, startY, x, startY + height, INDENT_GUIDE_COLOR);
        }
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
        String prefabPath = ImGui.acceptDragDropPayload(AssetMimeTypes.PREFAB, String.class);
        if (prefabPath != null) {
            history().execute(new InstantiatePrefabCommand(Path.of(prefabPath), row.gameObject()));
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
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_RENAME,
                "hierarchy-rename"), "F2")) {
            beginRename(target);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_DUPLICATE,
                "hierarchy-duplicate"), "Ctrl+D")) {
            duplicateSelected();
        }
        if (hasParent(target) && ImGui.menuItem(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_UNPARENT,
                "hierarchy-unparent"))) {
            unparent(target);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_SAVE_AS_PREFAB,
                "hierarchy-save-as-prefab"))) {
            onSaveAsPrefab.accept(target);
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_DELETE,
                "hierarchy-delete"), "Del")) {
            askDeleteSelected();
        }
    }

    private void renderRenameField() {
        if (renameFocusRequested) {
            ImGui.setKeyboardFocusHere();
            renameFocusRequested = false;
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (TextFields.inputSubmitted("##rename", renameInput)) {
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
        if (ImGui.beginMenu(I18n.label(TextKey.EDITOR_HIERARCHY_VIEW_CREATE,
                "hierarchy-create"))) {
            renderCreateItems();
            ImGui.endMenu();
        }
        ImGui.endPopup();
    }

    private void renderCreateItems() {
        creationMenu.renderItems(spawnPoint.get());
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
        Optional<Transform3D> droppedSpatial = dropped.getComponent(Transform3D.class);
        Optional<Transform3D> targetSpatial = target.getComponent(Transform3D.class);
        if (droppedSpatial.isPresent() && targetSpatial.isPresent()) {
            reparentSpatialOnto(dropped, target, droppedSpatial.get(), targetSpatial.get());
            return;
        }
        Optional<Transform2D> droppedPlanar = dropped.getComponent(Transform2D.class);
        Optional<Transform2D> targetPlanar = target.getComponent(Transform2D.class);
        if (droppedPlanar.isPresent() && targetPlanar.isPresent()) {
            reparentPlanarOnto(dropped, target, droppedPlanar.get(), targetPlanar.get());
            return;
        }
        notifier.show(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_TOAST_CANNOT_PARENT_MIXED));
    }

    private void reparentSpatialOnto(GameObject dropped, GameObject target,
                                     Transform3D droppedTransform, Transform3D targetTransform) {
        if (isDescendantOf(targetTransform, droppedTransform)) {
            notifier.show(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_TOAST_CANNOT_PARENT_TO_CHILD));
            return;
        }
        if (targetTransform != droppedTransform.parent().orElse(null)) {
            history().execute(new ReparentCommand(dropped, Optional.of(target)));
        }
    }

    private void reparentPlanarOnto(GameObject dropped, GameObject target,
                                    Transform2D droppedTransform, Transform2D targetTransform) {
        if (isPlanarDescendantOf(targetTransform, droppedTransform)) {
            notifier.show(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_TOAST_CANNOT_PARENT_TO_CHILD));
            return;
        }
        if (targetTransform != droppedTransform.parent().orElse(null)) {
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
        if (!hasParent(target)) {
            return;
        }
        history().execute(new ReparentCommand(target, Optional.empty()));
    }

    private static boolean hasParent(GameObject gameObject) {
        if (gameObject.getComponent(Transform3D.class).flatMap(Transform3D::parent).isPresent()) {
            return true;
        }
        return gameObject.getComponent(Transform2D.class).flatMap(Transform2D::parent).isPresent();
    }

    private static boolean isPlanarDescendantOf(Transform2D candidate, Transform2D ancestor) {
        Transform2D walker = candidate;
        while (walker != null) {
            if (walker == ancestor) {
                return true;
            }
            walker = walker.parent().orElse(null);
        }
        return false;
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
            appendRootRow(gameObject);
        }
    }

    private void appendRootRow(GameObject gameObject) {
        Optional<Transform3D> spatial = gameObject.getComponent(Transform3D.class);
        if (spatial.isPresent()) {
            if (spatial.get().parent().isEmpty()) {
                appendDescendants(gameObject, spatial.get(), 0);
            }
            return;
        }
        Optional<Transform2D> planar = gameObject.getComponent(Transform2D.class);
        if (planar.isEmpty()) {
            rows.add(new Row(gameObject, 0));
            return;
        }
        if (planar.get().parent().isEmpty()) {
            appendPlanarDescendants(gameObject, planar.get(), 0);
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

    private void appendPlanarDescendants(GameObject gameObject, Transform2D transform, int depth) {
        rows.add(new Row(gameObject, depth));
        for (Transform2D child : transform.children()) {
            child.owner().ifPresent(childOwner -> appendPlanarDescendants(childOwner, child, depth + 1));
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
        notifier.show(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_TOAST_DUPLICATED, targets.size()));
    }

    private GameObject buildCopy(GameObject source) {
        GameObject copy = copyWithName(source,
                UniqueObjectName.in(activeDocument.get().scene(), source.name()));
        source.parent().ifPresent(copy::setParent);
        return copy;
    }

    private GameObject copyWithName(GameObject source, String name) {
        GameObject copy = new GameObject(name);
        for (IComponent component : new ArrayList<>(source.components())) {
            copyComponentInto(copy, component);
        }
        for (GameObject child : source.children()) {
            copyWithName(child, child.name()).setParent(copy);
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
        fresh.copyStateFrom(source);
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
            case VECTOR2 -> copyVector2(value, destination);
            case VECTOR3 -> copyVector(value, destination);
            case QUATERNION -> copyQuaternion(value, destination);
            case ASSET_REF -> copyAssetRef(value, destination);
            default -> {
            }
        }
    }

    private static void copyVector2(Object value, ExportedProperty destination) {
        if (destination.read() instanceof Vector2f target) {
            target.set((Vector2f) value);
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
        notifier.show(I18n.translate(TextKey.EDITOR_HIERARCHY_VIEW_TOAST_DELETED, targets.size()));
    }

    private record Row(GameObject gameObject, int depth) {
    }
}
