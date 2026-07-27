package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scripteditor.CompletionEngine;
import fr.epistudio.epysia.editor.scripteditor.CompletionKind;
import fr.epistudio.epysia.editor.scripteditor.CompletionPopup;
import fr.epistudio.epysia.editor.scripteditor.CompletionSymbol;
import fr.epistudio.epysia.editor.scripteditor.DelimiterAutoClose;
import fr.epistudio.epysia.editor.scripteditor.SourceIndent;
import fr.epistudio.epysia.editor.scripteditor.ImportPlanner;
import fr.epistudio.epysia.editor.scripteditor.JavaLanguageDefinition;
import fr.epistudio.epysia.editor.scripteditor.JavaSymbols;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
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
import imgui.flag.ImGuiMouseButton;
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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptEditorView {

    public static final String WINDOW_TITLE = "Script Editor";

    private static final float DIAGNOSTICS_HEIGHT = 96.0f;
    private static final float DIAGNOSTICS_INNER_HEIGHT = DIAGNOSTICS_HEIGHT - 8.0f;
    private static final float MINIMUM_EDITOR_HEIGHT = 80.0f;
    private static final int TAB_SIZE = 4;
    private static final float MAXIMUM_AUTOSCROLL_OVERSHOOT = 120.0f;
    private static final float AUTOSCROLL_SPEED = 0.35f;
    private static final float LINE_NUMBER_MARGIN = 10.0f;
    private static final float CHARACTER_SPACING = 1.0f;
    private static final int BACKGROUND_COLOR_ABGR = 0xFF14120F;
    private static final int FOREGROUND_COLOR_ABGR = 0xFFD6D6D6;
    private static final int KEYWORD_COLOR_ABGR = 0xFFD69C56;
    private static final int TYPE_COLOR_ABGR = 0xFFB0B856;
    private static final int NUMBER_COLOR_ABGR = 0xFF9CDCB8;
    private static final int STRING_COLOR_ABGR = 0xFF8CA0CE;
    private static final int PUNCTUATION_COLOR_ABGR = 0xFFB4B4B4;
    private static final int COMMENT_COLOR_ABGR = 0xFF6A9955;
    private static final int LINE_NUMBER_COLOR_ABGR = 0xFF5A5A5A;
    private static final int CURSOR_COLOR_ABGR = 0xFFE0E0E0;
    private static final int CURRENT_LINE_FILL_ABGR = 0x1AFFFFFF;
    private static final int CURRENT_LINE_FILL_INACTIVE_ABGR = 0x0FFFFFFF;
    private static final int CURRENT_LINE_EDGE_ABGR = 0x00000000;
    private static final int ERROR_MARKER_COLOR_ABGR = 0x804B4BFF;
    private static final Pattern DIAGNOSTIC_PATTERN =
            Pattern.compile("([\\w./\\\\-]+\\.java):(\\d+):\\s*(?:error|warning)?:?\\s*(.*)");
    private static final String[] DROPPABLE_MIME_TYPES = {
            AssetMimeTypes.MESH, AssetMimeTypes.TEXTURE, AssetMimeTypes.AUDIO,
            AssetMimeTypes.PREFAB, AssetMimeTypes.SHADER, AssetMimeTypes.SCENE
    };

    private final Notifier notifier;
    private final Consumer<Path> onSaved;
    private final CompletionEngine completionEngine;
    private final CompletionPopup completionPopup = new CompletionPopup();
    private final TextEditorLanguageDefinition languageDefinition;
    private final Map<Path, OpenScript> openScripts = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final TextEditorCoordinates cursorCoordinates = new TextEditorCoordinates();
    private final DelimiterAutoClose autoClose = new DelimiterAutoClose();
    private Path focusRequest;
    private boolean windowFocusRequest;
    private boolean markersDirty;
    private boolean selectionDragActive;
    private boolean keyboardGateOpen = true;
    private boolean pendingEnter;
    private boolean pendingWordDelete;
    private boolean windowFocused;

    public ScriptEditorView(ComponentRegistry componentRegistry, Notifier notifier, Consumer<Path> onSaved) {
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
            notifier.show(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TOAST_COULD_NOT_OPEN_SCRIPT,
                    error.getMessage()));
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
        palette[TextEditorPaletteIndex.Default] = FOREGROUND_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Identifier] = FOREGROUND_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Keyword] = KEYWORD_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Preprocessor] = KEYWORD_COLOR_ABGR;
        palette[TextEditorPaletteIndex.KnownIdentifier] = TYPE_COLOR_ABGR;
        palette[TextEditorPaletteIndex.PreprocIdentifier] = TYPE_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Number] = NUMBER_COLOR_ABGR;
        palette[TextEditorPaletteIndex.String] = STRING_COLOR_ABGR;
        palette[TextEditorPaletteIndex.CharLiteral] = STRING_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Punctuation] = PUNCTUATION_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Comment] = COMMENT_COLOR_ABGR;
        palette[TextEditorPaletteIndex.MultiLineComment] = COMMENT_COLOR_ABGR;
        palette[TextEditorPaletteIndex.LineNumber] = LINE_NUMBER_COLOR_ABGR;
        palette[TextEditorPaletteIndex.Cursor] = CURSOR_COLOR_ABGR;
        palette[TextEditorPaletteIndex.CurrentLineFill] = CURRENT_LINE_FILL_ABGR;
        palette[TextEditorPaletteIndex.CurrentLineFillInactive] = CURRENT_LINE_FILL_INACTIVE_ABGR;
        palette[TextEditorPaletteIndex.CurrentLineEdge] = CURRENT_LINE_EDGE_ABGR;
        palette[TextEditorPaletteIndex.ErrorMarker] = ERROR_MARKER_COLOR_ABGR;
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

    public boolean isFocused() {
        return windowFocused;
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
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TITLE, WINDOW_TITLE))) {
            windowFocused = false;
            ImGui.end();
            completionPopup.hide();
            return;
        }
        windowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
        renderTabs();
        ImGui.end();
    }

    private void renderEmptyWindow() {
        windowFocused = false;
        if (ImGui.begin(I18n.label(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TITLE, WINDOW_TITLE))) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_NO_SCRIPT_OPEN));
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_OPEN_HELP));
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
        pendingEnter = claimSmartEnter(script);
        pendingWordDelete = !pendingEnter && claimWordDelete(script);
        float editorHeight = Math.max(MINIMUM_EDITOR_HEIGHT,
                ImGui.getContentRegionAvailY() - diagnosticsHeightFor(path));
        renderEditorChild(path, script, editorHeight, forceCompletion);
        completionPopup.render().ifPresent(symbol -> acceptCompletion(script, symbol));
        renderDiagnostics(path);
    }

    private void gateEditorKeyboard(OpenScript script) {
        CompletionPopup.KeyAction action = completionPopup.handleKeys();
        keyboardGateOpen = action == CompletionPopup.KeyAction.NONE;
        script.editor().setHandleKeyboardInputs(keyboardGateOpen);
        if (action == CompletionPopup.KeyAction.ACCEPT) {
            completionPopup.selected().ifPresent(symbol -> acceptCompletion(script, symbol));
        }
        if (action == CompletionPopup.KeyAction.CLOSE) {
            completionPopup.hide();
        }
    }

    private boolean claimSmartEnter(OpenScript script) {
        if (!keyboardGateOpen || ImGui.getIO().getKeyCtrl() || !isEnterPressed()
                || !ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            return false;
        }
        script.editor().setHandleKeyboardInputs(false);
        return true;
    }

    private boolean claimWordDelete(OpenScript script) {
        if (!keyboardGateOpen || !ImGui.getIO().getKeyCtrl()
                || !ImGui.isKeyPressed(ImGuiKey.Backspace, false)
                || !ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            return false;
        }
        script.editor().setHandleKeyboardInputs(false);
        return true;
    }

    private static boolean isEnterPressed() {
        return ImGui.isKeyPressed(ImGuiKey.Enter, false)
                || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false);
    }

    private void renderEditorChild(Path path, OpenScript script, float height, boolean forceCompletion) {
        TextEditor editor = script.editor();
        ImGui.beginChild("##script-" + path, ImGui.getContentRegionAvailX(), height, false,
                ImGuiWindowFlags.HorizontalScrollbar);
        float editorOriginX = ImGui.getCursorScreenPosX();
        float editorOriginY = ImGui.getCursorScreenPosY();
        float editorWidth = ImGui.getContentRegionAvailX();
        claimKeyboardFocusOnClick();
        script.consumePendingLine().ifPresent(line -> editor.setCursorPosition(line - 1, 0));
        EditorStyle.monospaceFont().ifPresent(ImGui::pushFont);
        editor.render("##texteditor-" + path);
        EditorStyle.monospaceFont().ifPresent(ignored -> ImGui.popFont());
        autoScrollWhileSelecting();
        handleEditorShortcuts(script);
        boolean textChanged = editor.isTextChanged();
        boolean cursorMoved = editor.isCursorPositionChanged();
        if (textChanged) {
            script.markDirty();
        }
        if (pendingEnter) {
            insertSmartNewLine(script);
            textChanged = true;
        } else if (pendingWordDelete) {
            deletePreviousWord(script);
            textChanged = true;
        } else {
            applyAutoClose(script);
        }
        updateCompletion(editor, textChanged, cursorMoved, forceCompletion);
        renderAssetDropOverlay(script, editorOriginX, editorOriginY, editorWidth, height);
        ImGui.endChild();
    }

    private void insertSmartNewLine(OpenScript script) {
        TextEditor editor = script.editor();
        editor.getCursorPosition(cursorCoordinates);
        String lineText = editor.getCurrentLineText();
        int cursorIndex = characterIndexForColumn(lineText, cursorCoordinates.mColumn);
        String indent = SourceIndent.forNewLineAfter(
                textBeforeCursor(editor, cursorCoordinates.mLine, lineText, cursorIndex));
        editor.insertText("\n" + indent);
        expandBlockIfClosingFollows(editor, lineText, cursorIndex, indent);
        autoClose.forget();
        script.markDirty();
    }

    private void expandBlockIfClosingFollows(TextEditor editor, String lineText, int cursorIndex,
                                             String indent) {
        if (cursorIndex >= lineText.length() || lineText.charAt(cursorIndex) != '}') {
            return;
        }
        editor.getCursorPosition(cursorCoordinates);
        int openedLine = cursorCoordinates.mLine;
        int openedColumn = cursorCoordinates.mColumn;
        editor.insertText("\n" + SourceIndent.indentOf(indent.length() - 1));
        editor.setCursorPosition(openedLine, openedColumn);
    }

    private static String textBeforeCursor(TextEditor editor, int line, String lineText, int cursorIndex) {
        StringBuilder builder = new StringBuilder();
        String[] lines = editor.getTextLines();
        for (int index = 0; index < line && index < lines.length; index++) {
            builder.append(lines[index]).append('\n');
        }
        builder.append(lineText, 0, Math.min(cursorIndex, lineText.length()));
        return builder.toString();
    }

    private void applyAutoClose(OpenScript script) {
        TextEditor editor = script.editor();
        editor.getCursorPosition(cursorCoordinates);
        String lineText = editor.getCurrentLineText();
        int cursorIndex = characterIndexForColumn(lineText, cursorCoordinates.mColumn);
        DelimiterAutoClose.Decision decision =
                autoClose.observe(cursorCoordinates.mLine, cursorIndex, lineText);
        switch (decision.action()) {
            case INSERT_CLOSER -> insertCloser(editor, decision.closer());
            case SKIP_CLOSER -> skipCloser(editor);
            case NONE -> {
            }
        }
    }

    private void insertCloser(TextEditor editor, String closer) {
        int line = cursorCoordinates.mLine;
        int column = cursorCoordinates.mColumn;
        editor.insertText(closer);
        editor.setCursorPosition(line, column);
    }

    private void skipCloser(TextEditor editor) {
        int line = cursorCoordinates.mLine;
        editor.setSelection(line, cursorCoordinates.mColumn, line, cursorCoordinates.mColumn + 1);
        editor.delete();
        editor.setCursorPosition(line, cursorCoordinates.mColumn);
    }

    private void handleEditorShortcuts(OpenScript script) {
        if (!ImGui.isWindowFocused() || !ImGui.getIO().getKeyCtrl()) {
            return;
        }
        TextEditor editor = script.editor();
        if (ImGui.getIO().getKeyShift()) {
            if (ImGui.isKeyPressed(ImGuiKey.Z)) {
                editor.redo();
                script.markDirty();
            }
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.A)) {
            editor.selectAll();
        }
        if (ImGui.isKeyPressed(ImGuiKey.X)) {
            editor.cut();
            script.markDirty();
        }
        if (ImGui.isKeyPressed(ImGuiKey.V)) {
            editor.paste();
            script.markDirty();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Z)) {
            editor.undo();
            script.markDirty();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Y)) {
            editor.redo();
            script.markDirty();
        }
    }

    private void deletePreviousWord(OpenScript script) {
        TextEditor editor = script.editor();
        editor.getCursorPosition(cursorCoordinates);
        String lineText = editor.getCurrentLineText();
        int cursorIndex = characterIndexForColumn(lineText, cursorCoordinates.mColumn);
        int wordStart = wordStartBefore(lineText, cursorIndex);
        if (wordStart == cursorIndex) {
            return;
        }
        editor.setSelection(cursorCoordinates.mLine, columnForCharacterIndex(lineText, wordStart),
                cursorCoordinates.mLine, cursorCoordinates.mColumn);
        editor.delete();
        autoClose.forget();
        script.markDirty();
    }

    private static int wordStartBefore(String lineText, int cursorIndex) {
        int index = Math.min(cursorIndex, lineText.length());
        while (index > 0 && Character.isWhitespace(lineText.charAt(index - 1))) {
            index--;
        }
        if (index > 0 && !CompletionEngine.isWordCharacter(lineText.charAt(index - 1))) {
            return index - 1;
        }
        while (index > 0 && CompletionEngine.isWordCharacter(lineText.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static int columnForCharacterIndex(String lineText, int characterIndex) {
        int column = 0;
        for (int index = 0; index < characterIndex && index < lineText.length(); index++) {
            column = lineText.charAt(index) == '\t' ? (column / TAB_SIZE + 1) * TAB_SIZE : column + 1;
        }
        return column;
    }

    private void claimKeyboardFocusOnClick() {
        if (ImGui.isWindowHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            ImGui.setWindowFocus();
            selectionDragActive = true;
        }
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            selectionDragActive = false;
        }
    }

    private void autoScrollWhileSelecting() {
        if (!selectionDragActive || !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            return;
        }
        float overshoot = verticalOvershoot(ImGui.getMousePosY(), ImGui.getWindowPosY(),
                ImGui.getWindowPosY() + ImGui.getWindowSizeY());
        if (overshoot == 0.0f) {
            return;
        }
        float clamped = Math.copySign(Math.min(Math.abs(overshoot), MAXIMUM_AUTOSCROLL_OVERSHOOT), overshoot);
        ImGui.setScrollY(Math.max(0.0f, ImGui.getScrollY() + clamped * AUTOSCROLL_SPEED));
    }

    private static float verticalOvershoot(float mouseY, float top, float bottom) {
        if (mouseY < top) {
            return mouseY - top;
        }
        if (mouseY > bottom) {
            return mouseY - bottom;
        }
        return 0.0f;
    }

    private void renderAssetDropOverlay(OpenScript script, float originX, float originY,
                                        float width, float height) {
        if (ImGui.getDragDropPayload() == null) {
            return;
        }
        ImGui.setCursorScreenPos(originX, originY);
        ImGui.invisibleButton("##script-asset-drop", Math.max(1.0f, width), Math.max(1.0f, height));
        if (ImGui.beginDragDropTarget()) {
            insertDroppedAssetPath(script.editor());
            ImGui.endDragDropTarget();
        }
    }

    private void insertDroppedAssetPath(TextEditor editor) {
        for (String mimeType : DROPPABLE_MIME_TYPES) {
            String droppedPath = ImGui.acceptDragDropPayload(mimeType, String.class);
            if (droppedPath != null) {
                editor.insertText("\"" + droppedPath + "\"");
                return;
            }
        }
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
        return ImGui.getWindowPosY() + ImGui.getStyle().getWindowPaddingY()
                + (line + 1) * ImGui.getTextLineHeight()
                - ImGui.getScrollY();
    }

    private void acceptCompletion(OpenScript script, CompletionSymbol symbol) {
        TextEditor editor = script.editor();
        editor.getCursorPosition(cursorCoordinates);
        String lineText = editor.getCurrentLineText();
        int cursorIndex = characterIndexForColumn(lineText, cursorCoordinates.mColumn);
        CompletionEngine.Context context = completionEngine.contextAt(lineText, cursorIndex);
        int replacedLength = context.importPath().orElse(context.prefix()).length();
        if (replacedLength > 0) {
            editor.setSelection(cursorCoordinates.mLine, cursorCoordinates.mColumn - replacedLength,
                    cursorCoordinates.mLine, cursorCoordinates.mColumn);
            editor.delete();
        }
        editor.insertText(symbol.insertText());
        if (!context.isImport() && symbol.kind() == CompletionKind.TYPE) {
            symbol.qualifiedName().ifPresent(qualifiedName -> ensureImport(editor, qualifiedName));
        }
        script.markDirty();
        completionPopup.hide();
    }

    private void ensureImport(TextEditor editor, String qualifiedName) {
        ImportPlanner.plan(editor.getText(), qualifiedName)
                .ifPresent(plan -> insertImport(editor, plan));
    }

    private void insertImport(TextEditor editor, ImportPlanner.ImportPlan plan) {
        editor.getCursorPosition(cursorCoordinates);
        int restoredLine = cursorCoordinates.mLine;
        int restoredColumn = cursorCoordinates.mColumn;
        editor.setCursorPosition(plan.lineIndex(), 0);
        editor.insertText(plan.insertionText());
        int lineOffset = plan.lineIndex() <= restoredLine ? plan.addedLines() : 0;
        editor.setCursorPosition(restoredLine + lineOffset, restoredColumn);
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
                I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_DIAGNOSTIC,
                        diagnostic.line(), diagnostic.message()));
        if (ImGui.isItemClicked()) {
            open(path, diagnostic.line());
        }
    }

    private void save(Path path, OpenScript script) {
        try {
            Files.writeString(path, script.editor().getText());
            script.markSaved();
            notifier.show(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TOAST_SAVED, path.getFileName()));
            onSaved.accept(path);
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TOAST_SAVE_FAILED,
                    error.getMessage()));
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
