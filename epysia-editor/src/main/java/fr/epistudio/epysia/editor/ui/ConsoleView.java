package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.log.EditorConsole;
import fr.epistudio.epysia.editor.play.PlayController;
import fr.epistudio.epysia.editor.play.PlayLogLine;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;
import imgui.type.ImString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsoleView {

    public static final String WINDOW_TITLE = "Console";

    private static final int MAX_LINES = 2000;
    private static final int SEARCH_CAPACITY = 128;
    private static final float LEVEL_MARKER_RADIUS = 4.0f;
    private static final Pattern JAVA_FILE_PATTERN = Pattern.compile("([\\w./\\\\-]+\\.java)(?::(\\d+))?");

    private enum Filter { ALL, INFO, WARN, ERROR }

    private final PlayController playController;
    private final EditorConsole editorConsole;
    private final Path scriptsDirectory;
    private final Consumer<ScriptLocation> onOpenScript;
    private final Deque<PlayLogLine> lines = new ArrayDeque<>();
    private final ImString searchInput = new ImString(SEARCH_CAPACITY);
    private Filter filter = Filter.ALL;
    private boolean stickToBottom = true;

    public record ScriptLocation(Path file, int line) {
    }

    public ConsoleView(PlayController playController, EditorConsole editorConsole,
                       Path scriptsDirectory, Consumer<ScriptLocation> onOpenScript) {
        this.playController = playController;
        this.editorConsole = editorConsole;
        this.scriptsDirectory = scriptsDirectory;
        this.onOpenScript = onOpenScript;
    }

    public Optional<String> lastLine() {
        return lines.isEmpty() ? Optional.empty() : Optional.of(lines.peekLast().message());
    }

    public void render() {
        drainPendingLines();
        if (!ImGui.begin(WINDOW_TITLE)) {
            ImGui.end();
            return;
        }
        renderHeader();
        ImGui.separator();
        renderLines();
        ImGui.end();
    }

    private void renderHeader() {
        for (Filter candidate : Filter.values()) {
            renderFilterButton(candidate);
            ImGui.sameLine();
        }
        ImGui.setNextItemWidth(180.0f);
        ImGui.inputTextWithHint("##console-search", "Filter text", searchInput);
        ImGui.sameLine();
        if (ImGui.smallButton("Clear")) {
            lines.clear();
            stickToBottom = true;
        }
    }

    private void renderFilterButton(Filter candidate) {
        boolean active = filter == candidate;
        if (active) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, EditorStyle.COLOR_WIDGET_ACTIVE);
        }
        String label = candidate.name().charAt(0) + candidate.name().substring(1).toLowerCase(Locale.ROOT);
        if (ImGui.smallButton(label)) {
            filter = candidate;
        }
        if (active) {
            ImGui.popStyleColor();
        }
    }

    private void renderLines() {
        ImGui.beginChild("##console-lines", 0.0f, 0.0f, false);
        String query = searchInput.get().trim().toLowerCase(Locale.ROOT);
        for (PlayLogLine line : lines) {
            if (matchesFilter(line.level()) && matchesQuery(line, query)) {
                renderLine(line);
            }
        }
        applyStickToBottom();
        ImGui.endChild();
    }

    private void applyStickToBottom() {
        if (stickToBottom && ImGui.getScrollY() < ImGui.getScrollMaxY()) {
            ImGui.setScrollHereY(1.0f);
        }
        if (ImGui.getIO().getMouseWheel() != 0.0f && ImGui.isWindowHovered()) {
            stickToBottom = ImGui.getScrollY() >= ImGui.getScrollMaxY() - 1.0f;
        }
    }

    private static boolean matchesQuery(PlayLogLine line, String query) {
        return query.isEmpty() || line.message().toLowerCase(Locale.ROOT).contains(query);
    }

    private void renderLine(PlayLogLine line) {
        drawLevelMarker(line.level());
        Optional<ScriptLocation> link = scriptLinkFor(line.message());
        if (link.isPresent()) {
            renderLinkedLine(line, link.get());
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, colorForLevel(line.level()));
            ImGui.textUnformatted(line.message());
            ImGui.popStyleColor();
        }
    }

    private void drawLevelMarker(PlayLogLine.Level level) {
        float centerX = ImGui.getCursorScreenPosX() + LEVEL_MARKER_RADIUS;
        float centerY = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
        ImGui.getWindowDrawList().addCircleFilled(centerX, centerY, LEVEL_MARKER_RADIUS, colorForLevel(level));
        ImGui.dummy(LEVEL_MARKER_RADIUS * 2.0f + 2.0f, 1.0f);
        ImGui.sameLine();
    }

    private void renderLinkedLine(PlayLogLine line, ScriptLocation location) {
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, EditorStyle.COLOR_ACCENT);
        if (ImGui.selectable(line.message())) {
            onOpenScript.accept(location);
        }
        ImGui.popStyleColor();
    }

    private Optional<ScriptLocation> scriptLinkFor(String message) {
        Matcher matcher = JAVA_FILE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int lineNumber = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
        Path candidate = Path.of(matcher.group(1));
        if (Files.isRegularFile(candidate)) {
            return Optional.of(new ScriptLocation(candidate, lineNumber));
        }
        Path inScripts = scriptsDirectory.resolve(candidate.getFileName().toString());
        return Files.isRegularFile(inScripts)
                ? Optional.of(new ScriptLocation(inScripts, lineNumber))
                : Optional.empty();
    }

    private boolean matchesFilter(PlayLogLine.Level level) {
        return switch (filter) {
            case ALL -> true;
            case INFO -> level == PlayLogLine.Level.INFO || level == PlayLogLine.Level.SYSTEM;
            case WARN -> level == PlayLogLine.Level.WARN;
            case ERROR -> level == PlayLogLine.Level.ERROR;
        };
    }

    private int colorForLevel(PlayLogLine.Level level) {
        return switch (level) {
            case ERROR -> EditorStyle.COLOR_DANGER;
            case WARN -> EditorStyle.COLOR_WARNING;
            case SYSTEM -> EditorStyle.COLOR_SYSTEM;
            case INFO -> EditorStyle.COLOR_TEXT_MUTED;
        };
    }

    private void drainPendingLines() {
        appendFrom(playController::pollLine);
        appendFrom(editorConsole::pollLine);
    }

    private void appendFrom(Supplier<PlayLogLine> source) {
        PlayLogLine line;
        while ((line = source.get()) != null) {
            lines.add(line);
            while (lines.size() > MAX_LINES) {
                lines.pollFirst();
            }
        }
    }
}
