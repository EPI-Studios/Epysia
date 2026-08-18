package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.icons.ComponentIcons;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.ui.kit.Disabled;
import fr.epistudio.epysia.editor.ui.kit.FuzzyScore;
import fr.epistudio.epysia.editor.ui.kit.SearchField;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.editor.ui.kit.Toolbars;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class AddComponentBrowser {

    private static final String STABLE_ID = "add-component-browser";
    private static final String EVERY_CATEGORY = "";
    private static final int QUERY_CAPACITY = 128;
    private static final int ACCENT_COLOR_COUNT = 3;
    private static final float DIALOG_WIDTH = 780.0f;
    private static final float DIALOG_HEIGHT = 560.0f;
    private static final float DIALOG_PADDING = 14.0f;
    private static final float BODY_PADDING = 10.0f;
    private static final float CATEGORY_PANE_WIDTH = 186.0f;
    private static final float PREVIEW_HEIGHT = 92.0f;
    private static final float SEARCH_WIDTH = 260.0f;
    private static final float HEADER_GAP = 8.0f;
    private static final float ROW_PADDING_Y = 5.0f;
    private static final float ROW_INSET = 10.0f;
    private static final float ICON_GAP = 8.0f;
    private static final float MARKER_WIDTH = 2.0f;
    private static final float MARKER_INSET = 5.0f;
    private static final float SELECTED_ALPHA = 0.16f;
    private static final float HOVER_ALPHA = 0.5f;
    private static final float FOOTER_BUTTON_WIDTH = 96.0f;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoTitleBar;

    private final ComponentRegistry componentRegistry;
    private final IconWidgets icons;
    private final Consumer<ComponentRegistry.Entry> onAdd;
    private final Runnable onNewScript;
    private final ImString query = new ImString(QUERY_CAPACITY);
    private String category = EVERY_CATEGORY;
    private int highlightedIndex;
    private boolean openRequested;
    private boolean focusRequested;
    private boolean scrollRequested;

    public AddComponentBrowser(ComponentRegistry componentRegistry, IconWidgets icons,
                               Consumer<ComponentRegistry.Entry> onAdd, Runnable onNewScript) {
        this.componentRegistry = componentRegistry;
        this.icons = icons;
        this.onAdd = onAdd;
        this.onNewScript = onNewScript;
    }

    public void open() {
        query.set("");
        category = EVERY_CATEGORY;
        highlightedIndex = 0;
        openRequested = true;
        focusRequested = true;
    }

    public void render(GameObject target) {
        if (openRequested) {
            ImGui.openPopup(STABLE_ID);
            openRequested = false;
        }
        centerNextWindow();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(DIALOG_PADDING),
                EditorScale.of(DIALOG_PADDING));
        boolean open = ImGui.beginPopupModal(STABLE_ID, WINDOW_FLAGS);
        ImGui.popStyleVar();
        if (!open) {
            return;
        }
        renderBody(target);
        ImGui.endPopup();
    }

    private void renderBody(GameObject target) {
        renderHeader();
        List<ComponentRegistry.Entry> results = results();
        moveHighlight(results.size());
        renderPanes(target, results);
        renderPreview(results);
        renderFooter(target, results);
    }

    private static void centerNextWindow() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing,
                0.5f, 0.5f);
        ImGui.setNextWindowSize(EditorScale.of(DIALOG_WIDTH), EditorScale.of(DIALOG_HEIGHT),
                ImGuiCond.Appearing);
    }

    private void renderHeader() {
        Sections.title(I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_TITLE));
        float searchWidth = EditorScale.of(SEARCH_WIDTH);
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), ImGui.getContentRegionMaxX() - searchWidth));
        if (focusRequested) {
            ImGui.setKeyboardFocusHere();
            focusRequested = false;
        }
        if (SearchField.render("##add-component-search",
                I18n.translate(TextKey.EDITOR_INSPECTOR_VIEW_SEARCH_COMPONENTS), query, searchWidth)) {
            category = EVERY_CATEGORY;
            highlightedIndex = 0;
        }
        ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
    }

    private void renderPanes(GameObject target, List<ComponentRegistry.Entry> results) {
        float height = panesHeight();
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        beginPane("##add-component-categories", EditorScale.of(CATEGORY_PANE_WIDTH), height);
        renderCategoryList();
        endPane();
        ImGui.sameLine();
        beginPane("##add-component-results", 0.0f, height);
        renderResults(target, results);
        endPane();
        ImGui.popStyleColor();
    }

    private static void beginPane(String identifier, float width, float height) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(BODY_PADDING),
                EditorScale.of(BODY_PADDING));
        ImGui.beginChild(identifier, width, height, ImGuiChildFlags.AlwaysUseWindowPadding);
    }

    private static void endPane() {
        ImGui.endChild();
        ImGui.popStyleVar();
    }

    private static float panesHeight() {
        return -(EditorScale.of(PREVIEW_HEIGHT) + EditorScale.of(HEADER_GAP)
                + Toolbars.buttonHeight() + ImGui.getStyle().getItemSpacingY() * 2.0f);
    }

    private void renderCategoryList() {
        renderCategoryItem(EVERY_CATEGORY, I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_ALL),
                componentRegistry.entries().size());
        for (Map.Entry<String, Integer> counted : categoryCounts().entrySet()) {
            renderCategoryItem(counted.getKey(), counted.getKey(), counted.getValue());
        }
    }

    private void renderCategoryItem(String value, String label, int count) {
        boolean active = category.equals(value) && searchText().isEmpty();
        if (ImGui.invisibleButton("##add-component-category-" + label,
                ImGui.getContentRegionAvailX(), rowHeight())) {
            selectCategory(value);
        }
        paintRowBackground(active, ImGui.isItemHovered());
        paintRowText(label, EditorScale.of(ROW_INSET),
                active ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED);
        paintRowTrailing(String.valueOf(count), EditorStyle.COLOR_TEXT_FAINT);
    }

    private void selectCategory(String value) {
        category = value;
        query.set("");
        highlightedIndex = 0;
    }

    private Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            counts.merge(entry.category(), 1, Integer::sum);
        }
        return counts;
    }

    private void renderResults(GameObject target, List<ComponentRegistry.Entry> results) {
        if (results.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_NO_MATCH, searchText()));
        }
        String group = "";
        boolean grouped = searchText().isEmpty() && category.isEmpty();
        for (int index = 0; index < results.size(); index++) {
            ComponentRegistry.Entry entry = results.get(index);
            group = grouped ? renderGroupCaption(entry.category(), group) : group;
            renderResult(target, entry, index);
        }
    }

    private static String renderGroupCaption(String group, String previous) {
        if (group.equals(previous)) {
            return previous;
        }
        if (!previous.isEmpty()) {
            ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
        }
        Sections.caption(group.toUpperCase(Locale.ROOT));
        return group;
    }

    private void renderResult(GameObject target, ComponentRegistry.Entry entry, int index) {
        boolean present = isOn(target, entry);
        boolean clicked = ImGui.invisibleButton("##add-component-result-" + index,
                ImGui.getContentRegionAvailX(), rowHeight());
        if (ImGui.isItemHovered()) {
            highlightedIndex = index;
        }
        boolean active = index == highlightedIndex;
        paintResult(entry, active, present);
        scrollToActive(active);
        if (clicked && !present) {
            commit(entry);
        }
    }

    private void paintResult(ComponentRegistry.Entry entry, boolean active, boolean present) {
        paintRowBackground(active && !present, ImGui.isItemHovered());
        paintRowIcon(ComponentIcons.forComponentClass(entry.componentClass()));
        float inset = EditorScale.of(ROW_INSET) + EditorStyle.iconSizeSmall() + EditorScale.of(ICON_GAP);
        paintRowText(entry.displayName(), inset, resultColor(active, present));
        if (present) {
            paintRowTrailing(I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_ALREADY_ADDED),
                    EditorStyle.COLOR_TEXT_FAINT);
        }
    }

    private static int resultColor(boolean active, boolean present) {
        if (present) {
            return EditorStyle.COLOR_TEXT_FAINT;
        }
        return active ? EditorStyle.COLOR_TEXT_FOCUS : EditorStyle.COLOR_TEXT;
    }

    private void scrollToActive(boolean active) {
        if (active && scrollRequested) {
            ImGui.setScrollHereY();
            scrollRequested = false;
        }
    }

    private static float rowHeight() {
        return Math.max(ImGui.getTextLineHeight(), EditorStyle.iconSizeSmall())
                + EditorScale.of(ROW_PADDING_Y) * 2.0f;
    }

    private static void paintRowBackground(boolean active, boolean hovered) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float left = ImGui.getItemRectMinX();
        float top = ImGui.getItemRectMinY();
        float right = ImGui.getItemRectMaxX();
        float bottom = ImGui.getItemRectMaxY();
        int fill = active
                ? EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTED_ALPHA)
                : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, hovered ? HOVER_ALPHA : 0.0f);
        drawList.addRectFilled(left, top, right, bottom, fill, EditorStyle.frameRounding());
        if (active) {
            drawList.addRectFilled(left, top + EditorScale.of(MARKER_INSET),
                    left + EditorScale.ofAtLeastOne(MARKER_WIDTH),
                    bottom - EditorScale.of(MARKER_INSET), EditorStyle.COLOR_ACCENT);
        }
    }

    private static void paintRowText(String label, float inset, int color) {
        ImGui.getWindowDrawList().addText(ImGui.getItemRectMinX() + inset, textTop(), color, label);
    }

    private static void paintRowTrailing(String label, int color) {
        float left = ImGui.getItemRectMaxX() - ImGui.calcTextSizeX(label) - EditorScale.of(ROW_INSET);
        ImGui.getWindowDrawList().addText(left, textTop(), color, label);
    }

    private void paintRowIcon(EditorIcon icon) {
        float size = EditorStyle.iconSizeSmall();
        float left = ImGui.getItemRectMinX() + EditorScale.of(ROW_INSET);
        float top = ImGui.getItemRectMinY()
                + (ImGui.getItemRectMaxY() - ImGui.getItemRectMinY() - size) * 0.5f;
        ImGui.getWindowDrawList().addImage(icons.atlasTextureId(icon), left, top,
                left + size, top + size);
    }

    private static float textTop() {
        float height = ImGui.getItemRectMaxY() - ImGui.getItemRectMinY();
        return ImGui.getItemRectMinY() + (height - ImGui.getTextLineHeight()) * 0.5f;
    }

    private void renderPreview(List<ComponentRegistry.Entry> results) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        beginPane("##add-component-preview", 0.0f, EditorScale.of(PREVIEW_HEIGHT));
        highlighted(results).ifPresent(this::renderPreviewBody);
        endPane();
        ImGui.popStyleColor();
    }

    private void renderPreviewBody(ComponentRegistry.Entry entry) {
        Sections.caption(entry.category().toUpperCase(Locale.ROOT));
        icons.drawInline(ComponentIcons.forComponentClass(entry.componentClass()),
                EditorStyle.iconSizeSmall());
        Texts.plain(entry.displayName());
        Texts.wrapped(descriptionOf(entry));
    }

    private static String descriptionOf(ComponentRegistry.Entry entry) {
        return entry.description().isBlank()
                ? I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_NO_DESCRIPTION)
                : entry.description();
    }

    private void renderFooter(GameObject target, List<ComponentRegistry.Entry> results) {
        ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
        renderNewScriptButton();
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + footerIndent());
        if (ImGui.button(I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_CANCEL),
                footerButtonWidth(), Toolbars.buttonHeight())) {
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        renderAddButton(target, results);
    }

    private void renderNewScriptButton() {
        if (ImGui.button(I18n.label(TextKey.EDITOR_INSPECTOR_VIEW_NEW_SCRIPT, "add-component-new-script"),
                0.0f, Toolbars.buttonHeight())) {
            ImGui.closeCurrentPopup();
            onNewScript.run();
        }
    }

    private void renderAddButton(GameObject target, List<ComponentRegistry.Entry> results) {
        Optional<ComponentRegistry.Entry> choice = highlighted(results)
                .filter(entry -> !isOn(target, entry));
        pushAccent();
        Disabled.push(choice.isEmpty());
        boolean pressed = ImGui.button(I18n.translate(TextKey.EDITOR_ADD_COMPONENT_BROWSER_ADD),
                footerButtonWidth(), Toolbars.buttonHeight());
        Disabled.pop(choice.isEmpty());
        ImGui.popStyleColor(ACCENT_COLOR_COUNT);
        if (pressed || submitted()) {
            choice.ifPresent(this::commit);
        }
    }

    private static void pushAccent() {
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_ON_ACCENT);
    }

    private static float footerButtonWidth() {
        return EditorScale.of(FOOTER_BUTTON_WIDTH);
    }

    private static float footerIndent() {
        float buttons = footerButtonWidth() * 2.0f + ImGui.getStyle().getItemSpacingX();
        return Math.max(0.0f, ImGui.getContentRegionAvailX() - buttons);
    }

    private static boolean submitted() {
        return ImGui.isKeyPressed(ImGuiKey.Enter) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter);
    }

    private void commit(ComponentRegistry.Entry entry) {
        ImGui.closeCurrentPopup();
        onAdd.accept(entry);
    }

    private static boolean isOn(GameObject target, ComponentRegistry.Entry entry) {
        return target.getComponent(entry.componentClass()).isPresent();
    }

    private void moveHighlight(int count) {
        if (count == 0) {
            highlightedIndex = 0;
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            highlightedIndex = Math.floorMod(highlightedIndex + 1, count);
            scrollRequested = true;
        }
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
            highlightedIndex = Math.floorMod(highlightedIndex - 1, count);
            scrollRequested = true;
        }
        highlightedIndex = Math.clamp(highlightedIndex, 0, count - 1);
    }

    private Optional<ComponentRegistry.Entry> highlighted(List<ComponentRegistry.Entry> results) {
        return highlightedIndex >= 0 && highlightedIndex < results.size()
                ? Optional.of(results.get(highlightedIndex))
                : Optional.empty();
    }

    private String searchText() {
        return query.get().replace("\0", "").strip();
    }

    private List<ComponentRegistry.Entry> results() {
        String search = searchText();
        return search.isEmpty() ? inCategory() : ranked(search);
    }

    private List<ComponentRegistry.Entry> inCategory() {
        List<ComponentRegistry.Entry> matching = new ArrayList<>();
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            if (category.isEmpty() || entry.category().equals(category)) {
                matching.add(entry);
            }
        }
        return matching;
    }

    private List<ComponentRegistry.Entry> ranked(String search) {
        List<Ranked> scored = new ArrayList<>();
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            int score = FuzzyScore.of(entry.displayName() + " " + entry.category(), search);
            if (score != FuzzyScore.NO_MATCH) {
                scored.add(new Ranked(entry, score));
            }
        }
        scored.sort(Comparator.comparingInt(Ranked::score).reversed()
                .thenComparing(ranked -> ranked.entry().displayName()));
        return scored.stream().map(Ranked::entry).toList();
    }

    private record Ranked(ComponentRegistry.Entry entry, int score) {
    }
}
