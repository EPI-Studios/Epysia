package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.shell.FileDialogs;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class ProjectSelectorView implements FrameView {

    private static final String WINDOW_TITLE = "Epysia Projects";
    private static final DateTimeFormatter ABSOLUTE_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final int HOST_WINDOW_FLAGS = ImGuiWindowFlags.NoDecoration
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoSavedSettings;
    private static final float CONTENT_MAX_WIDTH = 1080.0f;
    private static final float RECENTS_COLUMN_RATIO = 0.62f;
    private static final float ACTION_BUTTON_HEIGHT = 42.0f;
    private static final float CARD_HEIGHT = 56.0f;

    private final ProjectStore store;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final Consumer<Project> onProjectOpened;
    private final NewProjectDialog newProjectDialog;
    private List<RecentEntry> recents = List.of();
    private boolean confirmClearRequested;

    public ProjectSelectorView(ProjectStore store, Notifier notifier, IconWidgets icons,
                               Consumer<Project> onProjectOpened) {
        this.store = store;
        this.notifier = notifier;
        this.icons = icons;
        this.onProjectOpened = onProjectOpened;
        this.newProjectDialog = new NewProjectDialog(store, notifier, onProjectOpened);
        reloadRecents();
    }

    private void reloadRecents() {
        List<RecentEntry> entries = new ArrayList<>();
        for (Project project : store.loadRecents()) {
            entries.add(new RecentEntry(project, Files.isRegularFile(project.markerFile())));
        }
        recents = List.copyOf(entries);
    }

    @Override
    public void render(float deltaSeconds) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY(), ImGuiCond.Always);
        if (ImGui.begin(I18n.label(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TITLE, "project-selector"),
                HOST_WINDOW_FLAGS)) {
            renderContent();
        }
        newProjectDialog.render();
        renderClearConfirm();
        ImGui.end();
    }

    private void renderContent() {
        float contentWidth = Math.min(CONTENT_MAX_WIDTH, ImGui.getContentRegionAvailX());
        float indent = (ImGui.getContentRegionAvailX() - contentWidth) * 0.5f;
        if (indent > 0.0f) {
            ImGui.indent(indent);
        }
        float recentsWidth = contentWidth * RECENTS_COLUMN_RATIO;
        renderRecentsColumn(recentsWidth);
        ImGui.sameLine();
        renderActionsColumn(contentWidth - recentsWidth - EditorStyle.ITEM_SPACING_X);
        if (indent > 0.0f) {
            ImGui.unindent(indent);
        }
    }

    private void renderRecentsColumn(float width) {
        ImGui.beginChild("##recents", width, 0.0f, false);
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_RECENT_PROJECTS));
        ImGui.separator();
        if (recents.isEmpty()) {
            renderEmptyRecents();
        } else {
            renderRecentCards();
            renderClearLink();
        }
        ImGui.endChild();
    }

    private void renderEmptyRecents() {
        ImGui.spacing();
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_NO_RECENT_PROJECTS));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_FIRST_PROJECT_HELP));
    }

    private void renderRecentCards() {
        for (RecentEntry entry : recents) {
            renderCard(entry);
        }
    }

    private void renderCard(RecentEntry entry) {
        ImGui.beginDisabled(!entry.exists());
        ImGui.pushID(entry.project().rootDirectory().toString());
        boolean clicked = ImGui.button("##card", ImGui.getContentRegionAvailX(), CARD_HEIGHT);
        renderCardOverlay(entry);
        ImGui.popID();
        ImGui.endDisabled();
        if (clicked && entry.exists()) {
            openExistingProject(entry.project());
        }
    }

    private void renderCardOverlay(RecentEntry entry) {
        float cardMinX = ImGui.getItemRectMinX();
        float cardMinY = ImGui.getItemRectMinY();
        float cardMaxX = ImGui.getItemRectMaxX();
        var drawList = ImGui.getWindowDrawList();
        float iconSize = EditorStyle.ICON_SIZE_MEDIUM;
        float padding = EditorStyle.WINDOW_PADDING;
        float textX = cardMinX + padding + iconSize + padding;
        drawList.addImage(icons.atlasTextureId(EditorIcon.FOLDER), cardMinX + padding, cardMinY + (CARD_HEIGHT - iconSize) * 0.5f,
                cardMinX + padding + iconSize, cardMinY + (CARD_HEIGHT + iconSize) * 0.5f);
        drawList.addText(textX, cardMinY + padding, EditorStyle.COLOR_TEXT, entry.project().name());
        String pathLine = entry.exists()
                ? entry.project().rootDirectory().toString()
                : I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_FOLDER_NOT_FOUND);
        drawList.addText(textX, cardMinY + padding + ImGui.getTextLineHeight() + 2.0f,
                EditorStyle.COLOR_TEXT_MUTED, pathLine);
        String date = relativeDateText(entry.project().lastOpenedMillis());
        float dateWidth = ImGui.calcTextSize(date).x;
        drawList.addText(cardMaxX - padding - dateWidth, cardMinY + padding, EditorStyle.COLOR_TEXT_MUTED, date);
    }

    private void renderClearLink() {
        ImGui.spacing();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CLEAR_LIST,
                "project-selector-clear-list"))) {
            confirmClearRequested = true;
        }
    }

    private void renderClearConfirm() {
        if (confirmClearRequested) {
            ImGui.openPopup(I18n.label(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CLEAR_RECENT_TITLE,
                    "project-selector-clear-recent"));
            confirmClearRequested = false;
        }
        if (!ImGui.beginPopupModal(I18n.label(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CLEAR_RECENT_TITLE,
                "project-selector-clear-recent"), ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CLEAR_RECENT_MESSAGE));
        ImGui.separator();
        if (ImGui.button(I18n.label(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CLEAR,
                "project-selector-clear-confirm"))) {
            clearRecents();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CANCEL,
                "project-selector-clear-cancel"))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderActionsColumn(float width) {
        ImGui.beginChild("##actions", width, 0.0f, false);
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_GET_STARTED));
        ImGui.separator();
        if (actionButton("new-project", EditorIcon.ADD,
                I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_NEW_PROJECT))) {
            newProjectDialog.open();
        }
        if (actionButton("open-folder", EditorIcon.FOLDER,
                I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_OPEN_FOLDER))) {
            pickProjectFolder();
        }
        ImGui.endChild();
    }

    private boolean actionButton(String id, EditorIcon icon, String label) {
        ImGui.pushID(id);
        boolean clicked = ImGui.button("##" + id, ImGui.getContentRegionAvailX(), ACTION_BUTTON_HEIGHT);
        drawButtonContent(icon, label);
        ImGui.popID();
        return clicked;
    }

    private void drawButtonContent(EditorIcon icon, String label) {
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float width = ImGui.getItemRectMaxX() - minX;
        float iconSize = EditorStyle.ICON_SIZE_MEDIUM;
        float labelWidth = ImGui.calcTextSize(label).x;
        float startX = minX + (width - iconSize - EditorStyle.INNER_SPACING - labelWidth) * 0.5f;
        float iconY = minY + (ACTION_BUTTON_HEIGHT - iconSize) * 0.5f;
        var drawList = ImGui.getWindowDrawList();
        drawList.addImage(icons.atlasTextureId(icon), startX, iconY, startX + iconSize, iconY + iconSize);
        drawList.addText(startX + iconSize + EditorStyle.INNER_SPACING,
                minY + (ACTION_BUTTON_HEIGHT - ImGui.getTextLineHeight()) * 0.5f,
                EditorStyle.COLOR_TEXT, label);
    }

    private void pickProjectFolder() {
        Optional<Path> picked = FileDialogs.pickFolder(I18n.translate(
                        TextKey.EDITOR_PROJECT_SELECTOR_VIEW_OPEN_PROJECT_FOLDER),
                Path.of(System.getProperty("user.home")));
        picked.ifPresent(this::openFolder);
    }

    private void openFolder(Path folder) {
        Optional<Project> project = store.readProjectFromDisk(folder, System.currentTimeMillis());
        if (project.isEmpty()) {
            notifier.show(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TOAST_NOT_EPYSIA_PROJECT));
            return;
        }
        openExistingProject(project.get());
    }

    private void openExistingProject(Project project) {
        try {
            store.recordOpened(project);
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TOAST_COULD_NOT_RECORD,
                    error.getMessage()));
            return;
        }
        onProjectOpened.accept(project);
    }

    private void clearRecents() {
        try {
            store.clearRecents();
            reloadRecents();
            notifier.show(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TOAST_RECENT_CLEARED));
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TOAST_COULD_NOT_CLEAR,
                    error.getMessage()));
        }
    }

    private static String relativeDateText(long millis) {
        if (millis <= 0L) {
            return "";
        }
        LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        long daysBetween = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (daysBetween == 0L) {
            return I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TODAY);
        }
        if (daysBetween == 1L) {
            return I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_YESTERDAY);
        }
        return daysBetween < 7L
                ? I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_DAYS_AGO, daysBetween)
                : date.format(ABSOLUTE_DATE_FORMAT);
    }

    private record RecentEntry(Project project, boolean exists) {
    }
}
