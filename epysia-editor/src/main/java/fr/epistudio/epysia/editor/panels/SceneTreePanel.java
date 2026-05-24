package fr.epistudio.epysia.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.SearchBox;
import com.miry.ui.widgets.TextField;
import com.miry.ui.widgets.TreeNode;
import com.miry.ui.widgets.TreeView;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.editor.EditorPrimitiveRegistry;
import fr.epistudio.epysia.editor.EditorSceneHost;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.EditorWorld;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.List;

public final class SceneTreePanel extends Panel {

    private static final String TITLE = "Scene";
    private static final int ROW_HEIGHT = 22;
    private static final int SEARCH_HEIGHT = 24;
    private static final int SEARCH_PADDING = 6;
    private static final int HEADER_BLOCK_HEIGHT = SEARCH_HEIGHT + SEARCH_PADDING * 2;

    private final EditorWorld world;
    private final EditorSceneHost sceneHost;
    private final TreeNode<GameObject> rootNode;
    private final TreeView<GameObject> treeView;
    private final ContextMenu rowContextMenu = new ContextMenu();
    private final ContextMenu addMenu = new ContextMenu();
    private final SearchBox searchBox = new SearchBox();
    private final TextField renameField = new TextField();
    private boolean addMenuOpenedThisFrame;
    private static final int ADD_BUTTON_WIDTH = 26;
    private static final int ADD_BUTTON_GAP = 4;
    private static final int ADD_ICON_SIZE = 16;
    private List<GameObject> lastObjectsSnapshot = List.of();
    private List<GameObject> filteredObjects = List.of();
    private String lastSearchQuery = "";
    private float scrollOffset;
    private boolean rowMenuOpenedThisFrame;
    private GameObject currentlyRenamingObject;

    public SceneTreePanel(EditorWorld world, EditorSceneHost sceneHost) {
        super(TITLE);
        this.world = world;
        this.sceneHost = sceneHost;
        this.rootNode = new TreeNode<>(null);
        this.treeView = new TreeView<>(rootNode, ROW_HEIGHT);
        searchBox.setPlaceholder("Search objects...");
        configureTreeView();
        rebuildAddMenu();
    }

    private void configureTreeView() {
        treeView.setLabelFunction(gameObject -> gameObject == null ? "(scene)" : gameObject.name());
        treeView.setIconFunction(this::iconFor);
        treeView.setCustomIconColorFunction(this::iconColorFor);
        treeView.setOnRightClick((gameObject, coords) -> openRowMenu(gameObject, coords));
        treeView.setOnDoubleClick(this::beginRename);
        treeView.setMultiSelect(true);
    }

    private Icon iconFor(GameObject gameObject) {
        if (gameObject == null) {
            return Icon.FOLDER;
        }
        if (gameObject.getComponent(Camera3D.class).isPresent()) {
            return Icon.EYE;
        }
        if (gameObject.getComponent(DirectionalLight.class).isPresent()
                || gameObject.getComponent(PointLight.class).isPresent()
                || gameObject.getComponent(SpotLight.class).isPresent()) {
            return Icon.VISIBLE;
        }
        if (gameObject.getComponent(MeshRenderer.class).isPresent()) {
            return Icon.IMAGE;
        }
        return Icon.SETTINGS;
    }

    private int iconColorFor(GameObject gameObject) {
        if (gameObject == null) {
            return 0xFF8A92A2;
        }
        if (gameObject.getComponent(Camera3D.class).isPresent()) {
            return 0xFF6FB3E0;
        }
        if (gameObject.getComponent(DirectionalLight.class).isPresent()
                || gameObject.getComponent(PointLight.class).isPresent()
                || gameObject.getComponent(SpotLight.class).isPresent()) {
            return 0xFFE3C167;
        }
        if (gameObject.getComponent(MeshRenderer.class).isPresent()) {
            return 0xFF8CBADC;
        }
        return 0xFF8A92A2;
    }

    private void openRowMenu(GameObject gameObject, int[] coords) {
        rowContextMenu.clear();
        rowContextMenu.addItem("Rename", () -> beginRename(gameObject));
        rowContextMenu.addItem("Duplicate", () -> duplicateObject(gameObject));
        rowContextMenu.addSeparator();
        rowContextMenu.addItem("Delete", () -> deleteObject(gameObject));
        rowContextMenu.open(coords[0], coords[1]);
        rowMenuOpenedThisFrame = true;
    }

    private void beginRename(GameObject gameObject) {
        if (gameObject == null) {
            return;
        }
        currentlyRenamingObject = gameObject;
        renameField.setText(gameObject.name());
        renameField.setCursorPos(gameObject.name().length());
    }

    private void commitRename() {
        if (currentlyRenamingObject == null) {
            return;
        }
        String newName = renameField.text().trim();
        if (!newName.isEmpty() && !newName.equals(currentlyRenamingObject.name())) {
            world.history().execute(new fr.epistudio.epysia.editor.command.builtin.RenameCommand(
                    currentlyRenamingObject, newName));
        }
        currentlyRenamingObject = null;
    }

    private void duplicateObject(GameObject source) {
        if (source == null) {
            return;
        }
        GameObject copy = new GameObject(source.name() + " copy");
        source.getComponent(fr.epistudio.epysia.components.transforms.Transform3D.class).ifPresent(transform -> {
            fr.epistudio.epysia.components.transforms.Transform3D copyTransform = new fr.epistudio.epysia.components.transforms.Transform3D();
            copyTransform.setPosition(transform.position().x + 1.0f, transform.position().y, transform.position().z);
            copy.addComponent(copyTransform);
        });
        source.getComponent(MeshRenderer.class).ifPresent(meshRenderer -> meshRenderer.materialForSlot(0).ifPresent(material ->
                copy.addComponent(new MeshRenderer().setMesh(meshRenderer.mesh()).setMaterial(material))));
        world.history().execute(new fr.epistudio.epysia.editor.command.builtin.AddGameObjectCommand(copy));
        rebuildTreeIfNeeded(true);
    }

    private void deleteObject(GameObject target) {
        if (target == null) {
            return;
        }
        world.history().execute(new fr.epistudio.epysia.editor.command.builtin.RemoveGameObjectCommand(target));
        rebuildTreeIfNeeded(true);
    }

    private void rebuildAddMenu() {
        addMenu.clear();
        for (EditorPrimitiveRegistry.Entry entry : sceneHost.primitives().entries()) {
            addMenu.addItem(entry.displayName(), Icon.ADD, () -> {
                world.history().execute(new fr.epistudio.epysia.editor.command.builtin.AddGameObjectCommand(
                        entry.factory().get()));
                rebuildTreeIfNeeded(true);
            });
        }
    }

    public void openAddMenu(int screenX, int screenY) {
        addMenu.open(screenX, screenY);
    }

    @Override
    public void render(PanelContext context) {
        rowMenuOpenedThisFrame = false;
        addMenuOpenedThisFrame = false;
        renderSearchBox(context);
        rebuildTreeIfNeeded(false);
        int treeY = context.y() + HEADER_BLOCK_HEIGHT;
        int treeHeight = context.height() - HEADER_BLOCK_HEIGHT;
        if (filteredObjects.isEmpty()) {
            renderEmptyState(context, treeY, treeHeight);
            return;
        }
        routeSelectionInput(context, treeY, treeHeight);
        syncSelectionFromWorld();
        treeView.render(context.renderer(), context.uiContext(), context.ui().input(),
                context.ui().theme(),
                context.x(), treeY, context.width(), treeHeight,
                Math.round(scrollOffset), true);
        renderRenameOverlay(context, treeY, treeHeight);
        updateRowMenuInput(context.ui().input(), context.ui().theme());
        updateAddMenuInput(context.ui().input(), context.ui().theme());
    }

    private void updateAddMenuInput(UiInput input, Theme theme) {
        if (!addMenu.isOpen()) {
            return;
        }
        int itemHeight = Math.max(22, theme.tokens.itemHeight);
        addMenu.updateFromInput(input, theme, itemHeight);
        if (input.mousePressed() && !addMenuOpenedThisFrame) {
            int mouseX = Math.round(input.mousePos().x);
            int mouseY = Math.round(input.mousePos().y);
            int menuWidth = Math.max(1, addMenu.lastWidth());
            int menuHeight = Math.max(1, addMenu.lastHeight());
            boolean insideMenu = mouseX >= addMenu.x() && mouseY >= addMenu.y()
                    && mouseX < addMenu.x() + menuWidth && mouseY < addMenu.y() + menuHeight;
            if (insideMenu) {
                addMenu.handleClick(mouseX, mouseY, itemHeight);
            } else {
                addMenu.close();
            }
        }
    }

    private void renderSearchBox(PanelContext context) {
        int searchX = context.x() + SEARCH_PADDING;
        int searchY = context.y() + SEARCH_PADDING;
        int searchWidth = context.width() - SEARCH_PADDING * 2 - ADD_BUTTON_WIDTH - ADD_BUTTON_GAP;
        searchBox.render(context.uiContext(), context.ui().input(), context.renderer(),
                context.ui().theme(), searchX, searchY, searchWidth, SEARCH_HEIGHT);
        int addX = searchX + searchWidth + ADD_BUTTON_GAP;
        renderAddObjectButton(context, addX, searchY);
        String query = searchBox.field().text();
        if (!query.equals(lastSearchQuery)) {
            lastSearchQuery = query;
            rebuildTreeIfNeeded(true);
        }
    }

    private void renderAddObjectButton(PanelContext context, int x, int y) {
        UiRenderer renderer = context.renderer();
        UiInput input = context.ui().input();
        float mouseX = input.mousePos().x;
        float mouseY = input.mousePos().y;
        boolean hovered = mouseX >= x && mouseX < x + ADD_BUTTON_WIDTH
                && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
        int background = hovered
                ? withAlphaByte(EditorStyle.COLOR_WIDGET_HOVER, 0xA0)
                : EditorStyle.COLOR_WIDGET_BG;
        renderer.drawRoundedRect(x, y, ADD_BUTTON_WIDTH, SEARCH_HEIGHT, 4, background);
        int iconX = x + (ADD_BUTTON_WIDTH - ADD_ICON_SIZE) / 2;
        int iconY = y + (SEARCH_HEIGHT - ADD_ICON_SIZE) / 2;
        context.ui().theme().icons.draw(renderer, Icon.ADD, iconX, iconY, ADD_ICON_SIZE,
                hovered ? EditorStyle.COLOR_TEXT_PRIMARY : EditorStyle.COLOR_TEXT_MUTED);
        if (hovered && input.mousePressed()) {
            addMenu.open(x, y + SEARCH_HEIGHT + 2);
            addMenuOpenedThisFrame = true;
        }
    }

    private static int withAlphaByte(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void renderEmptyState(PanelContext context, int treeY, int treeHeight) {
        UiRenderer renderer = context.renderer();
        String primary = lastSearchQuery.isEmpty() ? "No objects in scene" : "No matches found";
        String secondary = lastSearchQuery.isEmpty() ? "Right-click or use + Add" : "Try a different search";
        float primaryWidth = renderer.measureText(primary);
        float secondaryWidth = renderer.measureText(secondary);
        int centerX = context.x() + context.width() / 2;
        int centerY = treeY + treeHeight / 2;
        renderer.drawText(primary, centerX - Math.round(primaryWidth / 2.0f), centerY - 4,
                EditorStyle.COLOR_TEXT_MUTED);
        renderer.drawText(secondary, centerX - Math.round(secondaryWidth / 2.0f), centerY + 16,
                EditorStyle.COLOR_TEXT_DIM);
    }

    private void renderRenameOverlay(PanelContext context, int treeY, int treeHeight) {
        if (currentlyRenamingObject == null) {
            return;
        }
        int index = filteredObjects.indexOf(currentlyRenamingObject);
        if (index < 0) {
            currentlyRenamingObject = null;
            return;
        }
        int rowY = treeY + index * ROW_HEIGHT - Math.round(scrollOffset);
        int rowX = context.x() + 4;
        int rowWidth = context.width() - 8;
        UiInput input = context.ui().input();
        boolean wasFocused = renameField.isFocused(context.uiContext());
        renameField.render(context.renderer(), context.uiContext(), input,
                context.ui().theme(), rowX, rowY, rowWidth, ROW_HEIGHT, true);
        if (input != null && input.mousePressed() && !isMouseInsideRect(input, rowX, rowY, rowWidth, ROW_HEIGHT)) {
            commitRename();
        } else if (wasFocused && !renameField.isFocused(context.uiContext())) {
            commitRename();
        }
    }

    private boolean isMouseInsideRect(UiInput input, int x, int y, int width, int height) {
        float mouseX = input.mousePos().x;
        float mouseY = input.mousePos().y;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void routeSelectionInput(PanelContext context, int treeY, int treeHeight) {
        if (currentlyRenamingObject != null) {
            return;
        }
        UiInput input = context.ui().input();
        if (input == null || !input.mousePressed()) {
            return;
        }
        int mouseX = Math.round(input.mousePos().x);
        int mouseY = Math.round(input.mousePos().y);
        if (!isInsideTreeArea(context, treeY, treeHeight, mouseX, mouseY)) {
            return;
        }
        int rowIndex = rowIndexAt(treeY, mouseY);
        if (rowIndex < 0 || rowIndex >= filteredObjects.size()) {
            return;
        }
        applySelectionForClick(input, filteredObjects.get(rowIndex));
    }

    private boolean isInsideTreeArea(PanelContext context, int treeY, int treeHeight, int mouseX, int mouseY) {
        return mouseX >= context.x() && mouseX < context.x() + context.width()
                && mouseY >= treeY && mouseY < treeY + treeHeight;
    }

    private int rowIndexAt(int treeY, int mouseY) {
        return (mouseY - treeY + Math.round(scrollOffset)) / ROW_HEIGHT;
    }

    private void applySelectionForClick(UiInput input, GameObject target) {
        int absoluteIndex = world.objects().indexOf(target);
        if (absoluteIndex < 0) {
            return;
        }
        if (input.shiftDown()) {
            world.selectRange(world.selectedIndex(), absoluteIndex);
        } else if (input.ctrlDown()) {
            world.toggleSelection(absoluteIndex);
        } else {
            world.selectIndex(absoluteIndex);
        }
    }

    private void updateRowMenuInput(UiInput input, Theme theme) {
        if (!rowContextMenu.isOpen()) {
            return;
        }
        int itemHeight = Math.max(22, theme.tokens.itemHeight);
        rowContextMenu.updateFromInput(input, theme, itemHeight);
        if (input.mousePressed() && !rowMenuOpenedThisFrame) {
            boolean handled = rowContextMenu.handleClick(
                    Math.round(input.mousePos().x), Math.round(input.mousePos().y), itemHeight);
            if (!handled) {
                rowContextMenu.close();
            }
        }
    }

    public void renderOverlayMenus(UiRenderer renderer, Theme theme) {
        if (addMenu.isOpen()) {
            int itemHeight = Math.max(22, theme.tokens.itemHeight);
            addMenu.render(renderer, theme, itemHeight,
                    EditorStyle.COLOR_PANEL_BG,
                    EditorStyle.COLOR_SELECTION,
                    EditorStyle.COLOR_TEXT_PRIMARY,
                    addMenu.hoverIndex());
        }
        if (rowContextMenu.isOpen()) {
            int itemHeight = Math.max(22, theme.tokens.itemHeight);
            rowContextMenu.render(renderer, theme, itemHeight,
                    EditorStyle.COLOR_PANEL_BG,
                    EditorStyle.COLOR_SELECTION,
                    EditorStyle.COLOR_TEXT_PRIMARY,
                    rowContextMenu.hoverIndex());
        }
    }

    private void rebuildTreeIfNeeded(boolean force) {
        List<GameObject> current = world.objects();
        List<GameObject> nextFiltered = filterObjects(current);
        boolean sameSnapshot = current == lastObjectsSnapshot;
        boolean sameSize = rootNode.children().size() == nextFiltered.size();
        if (!force && sameSnapshot && sameSize && nextFiltered.equals(filteredObjects)) {
            return;
        }
        rootNode.children().clear();
        for (GameObject gameObject : nextFiltered) {
            TreeNode<GameObject> node = new TreeNode<>(gameObject);
            rootNode.children().add(node);
        }
        rootNode.setExpanded(true);
        lastObjectsSnapshot = current;
        filteredObjects = nextFiltered;
    }

    private List<GameObject> filterObjects(List<GameObject> source) {
        String query = lastSearchQuery.trim().toLowerCase();
        if (query.isEmpty()) {
            return new ArrayList<>(source);
        }
        List<GameObject> result = new ArrayList<>();
        for (GameObject gameObject : source) {
            if (gameObject.name().toLowerCase().contains(query)) {
                result.add(gameObject);
            }
        }
        return result;
    }

    private void syncSelectionFromWorld() {
        List<Integer> selected = world.selectedIndicesView();
        List<GameObject> worldObjects = world.objects();
        List<TreeNode<GameObject>> children = rootNode.children();
        for (TreeNode<GameObject> child : children) {
            int absoluteIndex = worldObjects.indexOf(child.data());
            child.setSelected(absoluteIndex >= 0 && selected.contains(absoluteIndex));
        }
    }
}
