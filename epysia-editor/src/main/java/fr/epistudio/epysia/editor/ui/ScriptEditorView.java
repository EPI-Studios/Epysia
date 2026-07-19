package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scripteditor.CompletionEngine;
import fr.epistudio.epysia.editor.scripteditor.CompletionPopup;
import fr.epistudio.epysia.editor.scripteditor.CompletionSymbol;
import fr.epistudio.epysia.editor.scripteditor.JavaLanguageDefinition;
import fr.epistudio.epysia.editor.scripteditor.JavaSymbols;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import imgui.ImGui;
import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.TextEditorCoordinates;
import imgui.extension.texteditor.TextEditorLanguageDefinition;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptEditorView {

    public static final String WINDOW_TITLE = "Script Editor";

    private static final float DIAGNOSTICS_HEIGHT = 96.0f;
    private static final float DIAGNOSTICS_INNER_HEIGHT = DIAGNOSTICS_HEIGHT - 8.0f;
    private static final float MINIMUM_EDITOR_HEIGHT = 80.0f;
    private static final int TAB_SIZE = 4;
    private static final float LINE_NUMBER_MARGIN = 10.0f;
    private static final float CHARACTER_SPACING = 1.0f;
    private static final int BACKGROUND_COLOR_ABGR = 0xFF1E1E1E;
    private static final Pattern DIAGNOSTIC_PATTERN =
            Pattern.compile("([\\w./\\\\-]+\\.java):(\\d+):\\s*(?:error|warning)?:?\\s*(.*)");

    private final Notifier notifier;
    private final Runnable onSaved;
    private final CompletionEngine completionEngine;
    private final CompletionPopup completionPopup = new CompletionPopup();
    private final TextEditorLanguageDefinition languageDefinition;
    private final Map<Path, OpenScript> openScripts = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final TextEditorCoordinates cursorCoordinates = new TextEditorCoordinates();
    private Path focusRequest;
    private boolean windowFocusRequest;
    private boolean markersDirty;

    public ScriptEditorView(ComponentRegistry componentRegistry, Notifier notifier, Runnable onSaved) {
        this.notifier = notifier;
        this.onSaved = onSaved;
        JavaSymbols javaSymbols = new JavaSymbols(componentRegistry);
        this.completionEngine = new CompletionEngine(javaSymbols);
        this.languageDefinition = JavaLanguageDefinition.create(javaSymbols);
    }

    public void open(Path path) {
        if (!openScripts.containsKey(path)) {
            loadScript(path);
        }
        focusRequest = path;
        windowFocusRequest = true;
    }

    public void open(Path path, int line) {
        open(path);
        Optional.ofNullable(openScripts.get(path)).ifPresent(script -> script.requestLine(line));
    }

    private void loadScript(Path path) {
        try {
            String text = Files.readString(path);
            openScripts.put(path, new OpenScript(createEditor(text)));
        } catch (IOException error) {
            notifier.show("Could not open script: " + error.getMessage());
        }
    }

    private TextEditor createEditor(String text) {
        TextEditor editor = new TextEditor();
        editor.setLanguageDefinition(languageDefinition);
        editor.setPalette(themedPalette(editor));
        editor.setTabSize(TAB_SIZE);
        editor.setShowWhitespaces(false);
        editor.setImGuiChildIgnored(true);
        editor.setText(text);
        return editor;
    }

    private static int[] themedPalette(TextEditor editor) {
        int[] palette = editor.getDarkPalette();
        palette[TextEditorPaletteIndex.Background] = BACKGROUND_COLOR_ABGR;
        return palette;
    }

    public boolean hasOpenScripts() {
        return !openScripts.isEmpty();
    }

    public void clearDiagnostics() {
        diagnostics.clear();
        markersDirty = true;
    }

    public void acceptCompilerMessage(String message) {
        Matcher matcher = DIAGNOSTIC_PATTERN.matcher(message);
        if (matcher.find()) {
            diagnostics.add(new Diagnostic(Path.of(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), matcher.group(3)));
            markersDirty = true;
        }
    }

    public void render() {
        if (openScripts.isEmpty()) {
            renderEmptyWindow();
            return;
        }
        if (markersDirty) {
            applyErrorMarkers();
        }
        if (windowFocusRequest) {
            ImGui.setNextWindowFocus();
            windowFocusRequest = false;
        }
        if (!ImGui.begin(WINDOW_TITLE)) {
            ImGui.end();
            completionPopup.hide();
            return;
        }
        renderTabs();
        ImGui.end();
    }

    private void renderEmptyWindow() {
        if (ImGui.begin(WINDOW_TITLE)) {
            ImGui.textDisabled("No script open.");
            ImGui.textDisabled("Double-click a script in the asset browser.");
        }
        ImGui.end();
    }

    private void applyErrorMarkers() {
        for (Map.Entry<Path, OpenScript> entry : openScripts.entrySet()) {
            Map<Integer, String> markers = new HashMap<>();
            for (Diagnostic diagnostic : fileDiagnostics(entry.getKey())) {
                markers.merge(diagnostic.line(), diagnostic.message(),
                        (first, second) -> first + "\n" + second);
            }
            entry.getValue().editor().setErrorMarkers(markers);
        }
        markersDirty = false;
    }

    private void renderTabs() {
        if (!ImGui.beginTabBar("##script-tabs", ImGuiTabBarFlags.Reorderable)) {
            return;
        }
        for (Map.Entry<Path, OpenScript> entry : new ArrayList<>(openScripts.entrySet())) {
            renderTab(entry.getKey(), entry.getValue());
        }
        ImGui.endTabBar();
    }

    private void renderTab(Path path, OpenScript script) {
        ImBoolean keepOpen = new ImBoolean(true);
        int flags = tabFlags(path, script);
        boolean selected = ImGui.beginTabItem(path.getFileName().toString() + "##" + path, keepOpen, flags);
        if (selected) {
            renderEditor(path, script);
            ImGui.endTabItem();
        }
        if (!keepOpen.get()) {
            closeTab(path);
        }
    }

    private void closeTab(Path path) {
        Optional.ofNullable(openScripts.remove(path))
                .ifPresent(script -> script.editor().destroy());
        completionPopup.hide();
    }

    private int tabFlags(Path path, OpenScript script) {
        int flags = script.isDirty() ? ImGuiTabItemFlags.UnsavedDocument : ImGuiTabItemFlags.None;
        if (path.equals(focusRequest)) {
            flags |= ImGuiTabItemFlags.SetSelected;
            focusRequest = null;
        }
        return flags;
    }

    private void renderEditor(Path path, OpenScript script) {
        handleSaveShortcut(path, script);
        boolean forceCompletion = isCompletionShortcutPressed();
        gateEditorKeyboard(script);
        float editorHeight = Math.max(MINIMUM_EDITOR_HEIGHT,
                ImGui.getContentRegionAvailY() - diagnosticsHeightFor(path));
        renderEditorChild(path, script, editorHeight, forceCompletion);
        completionPopup.render().ifPresent(symbol -> acceptCompletion(script, symbol));
        renderDiagnostics(path);
    }

    private void gateEditorKeyboard(OpenScript script) {
        CompletionPopup.KeyAction action = completionPopup.handleKeys();
        script.editor().setHandleKeyboardInputs(action == CompletionPopup.KeyAction.NONE);
        if (action == CompletionPopup.KeyAction.ACCEPT) {
            completionPopup.selected().ifPresent(symbol -> acceptCompletion(script, symbol));
        }
        if (action == CompletionPopup.KeyAction.CLOSE) {
            completionPopup.hide();
        }
    }

    private void renderEditorChild(Path path, OpenScript script, float height, boolean forceCompletion) {
        TextEditor editor = script.editor();
        ImGui.beginChild("##script-" + path, ImGui.getContentRegionAvailX(), height, false,
                ImGuiWindowFlags.HorizontalScrollbar);
        script.consumePendingLine().ifPresent(line -> editor.setCursorPosition(line - 1, 0));
        editor.render("##texteditor-" + path);
        boolean textChanged = editor.isTextChanged();
        boolean cursorMoved = editor.isCursorPositionChanged();
        if (textChanged) {
            script.markDirty();
        }
        updateCompletion(editor, textChanged, cursorMoved, forceCompletion);
        ImGui.endChild();
    }

    private void updateCompletion(TextEditor editor, boolean textChanged, boolean cursorMoved,
                                  boolean forceCompletion) {
        if (!textChanged && !forceCompletion) {
            if (cursorMoved) {
                completionPopup.hide();
            }
            return;
        }
        editor.getCursorPosition(cursorCoordinates);
        String lineText = editor.getCurrentLineText();
        int cursorIndex = characterIndexForColumn(lineText, cursorCoordinates.mColumn);
        CompletionEngine.Context context = completionEngine.contextAt(lineText, cursorIndex);
        if (!forceCompletion && !completionEngine.shouldTrigger(context)) {
            completionPopup.hide();
            return;
        }
        List<CompletionSymbol> candidates = completionEngine.candidates(context, editor.getText());
        completionPopup.show(candidates,
                completionAnchorX(editor, cursorCoordinates.mColumn),
                completionAnchorY(cursorCoordinates.mLine));
    }

    private static float completionAnchorX(TextEditor editor, int column) {
        float characterAdvance = ImGui.calcTextSizeX("#") + CHARACTER_SPACING;
        float textStart = ImGui.calcTextSizeX(" " + editor.getTotalLines() + " ") + LINE_NUMBER_MARGIN;
        return ImGui.getWindowPosX() + textStart + column * characterAdvance - ImGui.getScrollX();
    }

    private static float completionAnchorY(int line) {
        return ImGui.getWindowPosY() + (line + 1) * ImGui.getTextLineHeightWithSpacing()
                - ImGui.getScrollY();
    }

    private void acceptCompletion(OpenScript script, CompletionSymbol symbol) {
        TextEditor editor = script.editor();
        editor.getCursorPosition(cursorCoordinates);
        String lineText = editor.getCurrentLineText();
        int cursorIndex = characterIndexForColumn(lineText, cursorCoordinates.mColumn);
        int prefixLength = completionEngine.contextAt(lineText, cursorIndex).prefix().length();
        if (prefixLength > 0) {
            editor.setSelection(cursorCoordinates.mLine, cursorCoordinates.mColumn - prefixLength,
                    cursorCoordinates.mLine, cursorCoordinates.mColumn);
            editor.delete();
        }
        editor.insertText(symbol.insertText());
        script.markDirty();
        completionPopup.hide();
    }

    private static int characterIndexForColumn(String lineText, int column) {
        int currentColumn = 0;
        int index = 0;
        while (index < lineText.length() && currentColumn < column) {
            if (lineText.charAt(index) == '\t') {
                currentColumn = (currentColumn / TAB_SIZE + 1) * TAB_SIZE;
            } else {
                currentColumn++;
            }
            index++;
        }
        return index;
    }

    private void handleSaveShortcut(Path path, OpenScript script) {
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.S)
                && ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            save(path, script);
        }
    }

    private static boolean isCompletionShortcutPressed() {
        return ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Space);
    }

    private float diagnosticsHeightFor(Path path) {
        return fileDiagnostics(path).isEmpty() ? 0.0f : DIAGNOSTICS_HEIGHT;
    }

    private List<Diagnostic> fileDiagnostics(Path path) {
        List<Diagnostic> matching = new ArrayList<>();
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.file().getFileName().equals(path.getFileName())) {
                matching.add(diagnostic);
            }
        }
        return matching;
    }

    private void renderDiagnostics(Path path) {
        List<Diagnostic> matching = fileDiagnostics(path);
        if (matching.isEmpty()) {
            return;
        }
        ImGui.beginChild("##diagnostics-" + path, 0.0f, DIAGNOSTICS_INNER_HEIGHT, true);
        for (Diagnostic diagnostic : matching) {
            renderDiagnosticLine(path, diagnostic);
        }
        ImGui.endChild();
    }

    private void renderDiagnosticLine(Path path, Diagnostic diagnostic) {
        ImGui.textColored(EditorStyle.COLOR_DANGER,
                "Line " + diagnostic.line() + ": " + diagnostic.message());
        if (ImGui.isItemClicked()) {
            open(path, diagnostic.line());
        }
    }

    private void save(Path path, OpenScript script) {
        try {
            Files.writeString(path, script.editor().getText());
            script.markSaved();
            notifier.show("Saved " + path.getFileName());
            onSaved.run();
        } catch (IOException error) {
            notifier.show("Save failed: " + error.getMessage());
        }
    }

    private record Diagnostic(Path file, int line, String message) {
    }

    private static final class OpenScript {

        private final TextEditor editor;
        private boolean dirty;
        private OptionalInt pendingLine = OptionalInt.empty();

        OpenScript(TextEditor editor) {
            this.editor = editor;
        }

        TextEditor editor() {
            return editor;
        }

        boolean isDirty() {
            return dirty;
        }

        void markDirty() {
            dirty = true;
        }

        void markSaved() {
            dirty = false;
        }

        void requestLine(int line) {
            pendingLine = OptionalInt.of(line);
        }

        OptionalInt consumePendingLine() {
            OptionalInt line = pendingLine;
            pendingLine = OptionalInt.empty();
            return line;
        }
    }
}
