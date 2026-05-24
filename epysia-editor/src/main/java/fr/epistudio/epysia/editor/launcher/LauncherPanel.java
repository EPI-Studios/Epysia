package fr.epistudio.epysia.editor.launcher;

import com.miry.ui.PanelContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.widgets.Button;
import com.miry.ui.widgets.TextField;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.project.Project;
import fr.epistudio.epysia.editor.project.ProjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public final class LauncherPanel extends Panel {

    private static final String TITLE = "Launcher";
    private static final int HEADER_HEIGHT = 96;
    private static final int COLUMN_GAP = 24;
    private static final int PADDING = 28;
    private static final int RECENT_ROW_HEIGHT = 56;
    private static final int FIELD_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 34;
    private static final int FORM_LABEL_HEIGHT = 18;
    private static final int FORM_ROW_GAP = 14;
    private static final Path DEFAULT_PROJECTS_ROOT = Path.of(System.getProperty("user.home"), "EpysiaProjects");

    private final ProjectStore projectStore;
    private final Consumer<Project> onProjectChosen;
    private final Consumer<String> onError;
    private final TextField nameField = new TextField("My Game");
    private final TextField pathField = new TextField(DEFAULT_PROJECTS_ROOT.toString());
    private final Button createButton = new Button("Create Project");
    private List<Project> recents = List.of();
    private int hoveredRecentIndex = -1;

    public LauncherPanel(ProjectStore projectStore,
                         Consumer<Project> onProjectChosen,
                         Consumer<String> onError) {
        super(TITLE);
        this.projectStore = projectStore;
        this.onProjectChosen = onProjectChosen;
        this.onError = onError;
        this.recents = projectStore.loadRecents();
    }

    @Override
    public void render(PanelContext context) {
        UiRenderer renderer = context.renderer();
        renderBackground(renderer, context);
        renderHeader(renderer, context);
        int contentTop = context.y() + HEADER_HEIGHT;
        int contentHeight = context.height() - HEADER_HEIGHT - PADDING;
        int columnWidth = (context.width() - PADDING * 2 - COLUMN_GAP) / 2;
        int leftX = context.x() + PADDING;
        int rightX = leftX + columnWidth + COLUMN_GAP;
        renderRecentsColumn(context, leftX, contentTop, columnWidth, contentHeight);
        renderNewProjectColumn(context, rightX, contentTop, columnWidth, contentHeight);
    }

    private void renderBackground(UiRenderer renderer, PanelContext context) {
        renderer.drawRect(context.x(), context.y(), context.width(), context.height(), EditorStyle.COLOR_WINDOW_BG);
    }

    private void renderHeader(UiRenderer renderer, PanelContext context) {
        int x = context.x() + PADDING;
        int y = context.y() + PADDING;
        renderer.drawText("EPYSIA", x, y + 22, EditorStyle.LEAF_HEADER_ACCENT);
        renderer.drawText("Project Launcher", x + 96, y + 22, EditorStyle.COLOR_TEXT_HEADER);
        renderer.drawText("Pick an existing project or create a new one.",
                x, y + 50, EditorStyle.COLOR_TEXT_MUTED);
        renderer.drawRect(context.x() + PADDING, context.y() + HEADER_HEIGHT - 1,
                context.width() - PADDING * 2, 1, EditorStyle.COLOR_SEPARATOR);
    }

    private void renderRecentsColumn(PanelContext context, int x, int y, int width, int height) {
        UiRenderer renderer = context.renderer();
        renderer.drawText("Recent Projects", x, y + 18, EditorStyle.COLOR_TEXT_HEADER);
        int listTop = y + 36;
        if (recents.isEmpty()) {
            renderer.drawText("No recent projects yet.", x, listTop + 16, EditorStyle.COLOR_TEXT_DIM);
            return;
        }
        UiInput input = context.ui().input();
        float mouseX = input.mousePos().x;
        float mouseY = input.mousePos().y;
        hoveredRecentIndex = -1;
        int rowY = listTop;
        int rowsCap = Math.max(1, (height - 36) / (RECENT_ROW_HEIGHT + 6));
        int rowsToRender = Math.min(rowsCap, recents.size());
        for (int i = 0; i < rowsToRender; i++) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + RECENT_ROW_HEIGHT;
            renderRecentRow(renderer, recents.get(i), x, rowY, width, hovered);
            if (hovered) {
                hoveredRecentIndex = i;
            }
            rowY += RECENT_ROW_HEIGHT + 6;
        }
        if (hoveredRecentIndex >= 0 && input.mousePressed()) {
            chooseProject(recents.get(hoveredRecentIndex));
        }
    }

    private void renderRecentRow(UiRenderer renderer, Project project, int x, int y, int width, boolean hovered) {
        int background = hovered ? EditorStyle.COLOR_WIDGET_HOVER : EditorStyle.COLOR_WIDGET_BG;
        renderer.drawRoundedRect(x, y, width, RECENT_ROW_HEIGHT, 6, background);
        renderer.drawText(project.name(), x + 14, y + 20, EditorStyle.COLOR_TEXT_PRIMARY);
        renderer.drawText(project.rootDirectory().toString(), x + 14, y + 38, EditorStyle.COLOR_TEXT_MUTED);
        String age = formatRelativeAge(project.lastOpenedMillis());
        int ageWidth = Math.round(renderer.measureText(age));
        renderer.drawText(age, x + width - ageWidth - 14, y + 20, EditorStyle.COLOR_TEXT_DIM);
    }

    private void renderNewProjectColumn(PanelContext context, int x, int y, int width, int height) {
        UiRenderer renderer = context.renderer();
        renderer.drawText("Create New Project", x, y + 18, EditorStyle.COLOR_TEXT_HEADER);
        int rowY = y + 44;
        rowY = renderFormRow(context, "Project name", nameField, x, rowY, width);
        rowY = renderFormRow(context, "Parent folder", pathField, x, rowY, width);
        renderer.drawText("Will be created at: " + resolveTargetPath(),
                x, rowY + 14, EditorStyle.COLOR_TEXT_DIM);
        rowY += 32;
        if (createButton.render(renderer, context.uiContext(), context.ui().input(), context.ui().theme(),
                x, rowY, width, BUTTON_HEIGHT, true)) {
            handleCreate();
        }
    }

    private int renderFormRow(PanelContext context, String label, TextField field, int x, int y, int width) {
        UiRenderer renderer = context.renderer();
        renderer.drawText(label, x, y + 14, EditorStyle.COLOR_TEXT_MUTED);
        int fieldY = y + FORM_LABEL_HEIGHT + 6;
        field.render(renderer, context.uiContext(), context.ui().input(), context.ui().theme(),
                x, fieldY, width, FIELD_HEIGHT, true);
        return fieldY + FIELD_HEIGHT + FORM_ROW_GAP;
    }

    private Path resolveTargetPath() {
        String trimmed = nameField.text().trim();
        String safeName = trimmed.isEmpty() ? "Untitled" : trimmed.replaceAll("[^A-Za-z0-9_\\- ]", "_");
        return Path.of(pathField.text().trim().isEmpty()
                ? DEFAULT_PROJECTS_ROOT.toString() : pathField.text().trim()).resolve(safeName);
    }

    private void handleCreate() {
        String name = nameField.text().trim();
        if (name.isEmpty()) {
            onError.accept("Project name is required");
            return;
        }
        Path target = resolveTargetPath();
        try {
            Project project = projectStore.createProject(name, target);
            chooseProject(project);
        } catch (IOException exception) {
            onError.accept("Create failed: " + exception.getMessage());
        }
    }

    private void chooseProject(Project project) {
        try {
            projectStore.recordOpened(project);
        } catch (IOException ignored) {
        }
        onProjectChosen.accept(project.withLastOpenedNow());
    }

    private static String formatRelativeAge(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        Duration duration = Duration.between(Instant.ofEpochMilli(millis), Instant.now());
        long days = duration.toDays();
        if (days > 0) return days + "d ago";
        long hours = duration.toHours();
        if (hours > 0) return hours + "h ago";
        long minutes = Math.max(1, duration.toMinutes());
        return minutes + "m ago";
    }
}
