package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scripteditor.CompletionEngine;
import fr.epistudio.epysia.editor.scripteditor.CompletionKind;
import fr.epistudio.epysia.editor.scripteditor.CompletionPopup;
import fr.epistudio.epysia.editor.scripteditor.CompletionSymbol;
import fr.epistudio.epysia.editor.scripteditor.ImportPlanner;
import fr.epistudio.epysia.editor.scripteditor.ImportStyle;
import fr.epistudio.epysia.editor.scripteditor.JavaSymbols;
import fr.epistudio.epysia.editor.scripteditor.ScriptSyntaxes;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.ui.kit.Chips;
import fr.epistudio.epysia.editor.ui.kit.DocumentTabs;
import fr.epistudio.epysia.editor.ui.kit.Toggles;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.extension.texteditor.TextDiff;
import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.TextEditorCursorPosition;
import imgui.extension.texteditor.TextEditorLanguage;
import imgui.extension.texteditor.flag.TextEditorColor;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptEditorView {

    public static final String WINDOW_TITLE = "Script Editor";

    private static final float DIAGNOSTICS_HEIGHT = 96.0f;
    private static final float DIAGNOSTICS_INNER_HEIGHT = DIAGNOSTICS_HEIGHT - 8.0f;
    private static final float MINIMUM_EDITOR_HEIGHT = 80.0f;
    private static final int TAB_SIZE = 4;
    private static final ImportStyle PLAIN_IMPORT_STYLE =
            ImportStyle.of("", Set.of(), List.of("class"));
    private static final int MARGIN_GLYPHS_BEFORE_TEXT = 3;
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
    private static final int ERROR_MARGIN_COLOR_ABGR = 0x804B4BFF;
    private static final int ERROR_LINE_COLOR_ABGR = 0x304B4BFF;
    private static final int DIFF_ADDED_COLOR_ABGR = 0x5A20A020;
    private static final int DIFF_DELETED_COLOR_ABGR = 0x5A2020C0;
    private static final String[] DROPPABLE_MIME_TYPES = {
            AssetMimeTypes.MESH, AssetMimeTypes.TEXTURE, AssetMimeTypes.AUDIO,
            AssetMimeTypes.PREFAB, AssetMimeTypes.SHADER, AssetMimeTypes.SCENE
    };

    private final Notifier notifier;
    private final Consumer<Path> onSaved;
    private final int scriptIconTextureId;
    private final ComponentRegistry componentRegistry;
    private CompletionEngine completionEngine;
    private final CompletionPopup completionPopup = new CompletionPopup();
    private ScriptSyntaxes syntaxes = ScriptSyntaxes.discover();
    private Pattern diagnosticPattern = diagnosticPatternFor(syntaxes);
    private final Map<Path, OpenScript> openScripts = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final TextEditorCursorPosition cursorPosition = new TextEditorCursorPosition();
    private Path focusRequest;
    private boolean windowFocusRequest;
    private boolean markersDirty;
    private boolean windowFocused;

    public ScriptEditorView(ComponentRegistry componentRegistry, IconWidgets icons, Notifier notifier,
                            Consumer<Path> onSaved) {
        this.componentRegistry = componentRegistry;
        this.scriptIconTextureId = icons.textureId(EditorIcon.SCRIPT);
        this.notifier = notifier;
        this.onSaved = onSaved;
        applySymbols(new JavaSymbols(componentRegistry, ProjectLibraries.none(), Path.of("")));
    }

    public void refreshSymbols(ProjectLibraries libraries, Path compiledScriptsDirectory) {
        adoptSyntaxes(ScriptSyntaxes.discover(ScriptLanguages.discover(libraries)));
        applySymbols(new JavaSymbols(componentRegistry, libraries, compiledScriptsDirectory));
        openScripts.forEach((path, script) -> script.editor().setLanguage(syntaxes.definitionFor(path)));
    }

    private void adoptSyntaxes(ScriptSyntaxes discovered) {
        syntaxes.release();
        syntaxes = discovered;
        diagnosticPattern = diagnosticPatternFor(syntaxes);
    }

    private void applySymbols(JavaSymbols javaSymbols) {
        this.completionEngine = new CompletionEngine(javaSymbols);
        syntaxes.rebuild(javaSymbols);
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
            openScripts.put(path, new OpenScript(createEditor(text, path), text,
                    syntaxes.definitionFor(path)));
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TOAST_COULD_NOT_OPEN_SCRIPT,
                    error.getMessage()));
        }
    }

    private TextEditor createEditor(String text, Path path) {
        TextEditor editor = new TextEditor();
        editor.setLanguage(syntaxes.definitionFor(path));
        applyPalette(editor);
        editor.setTabSize(TAB_SIZE);
        editor.setShowWhitespacesEnabled(false);
        editor.setAutoIndentEnabled(true);
        editor.setCompletePairedGlyphs(true);
        editor.setText(text);
        return editor;
    }

    private static void applyPalette(TextEditor editor) {
        editor.setDarkPalette();
        editor.setPaletteColor(TextEditorColor.background, BACKGROUND_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.text, FOREGROUND_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.identifier, FOREGROUND_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.keyword, KEYWORD_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.preprocessor, KEYWORD_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.declaration, KEYWORD_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.knownIdentifier, TYPE_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.number, NUMBER_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.string, STRING_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.punctuation, PUNCTUATION_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.comment, COMMENT_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.lineNumber, LINE_NUMBER_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.currentLineNumber, FOREGROUND_COLOR_ABGR);
        editor.setPaletteColor(TextEditorColor.cursor, CURSOR_COLOR_ABGR);
    }

    public boolean hasOpenScripts() {
        return !openScripts.isEmpty();
    }

    private static Pattern diagnosticPatternFor(ScriptSyntaxes syntaxes) {
        String extensions = syntaxes.syntaxes().stream()
                .flatMap(syntax -> syntax.sourceExtensions().stream())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("([\\w./\\\\-]+(?:" + extensions + ")):(\\d+):\\s*(error|warning)?:?\\s*(.*)");
    }

    public void clearDiagnostics() {
        diagnostics.clear();
        markersDirty = true;
    }

    public void acceptCompilerMessage(String message) {
        Matcher matcher = diagnosticPattern.matcher(message);
        if (matcher.find()) {
            diagnostics.add(new Diagnostic(Path.of(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), severityOf(matcher.group(3)),
                    matcher.group(4)));
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
            Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_NO_SCRIPT_OPEN));
            Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_OPEN_HELP));
        }
        ImGui.end();
    }

    private void applyErrorMarkers() {
        for (Map.Entry<Path, OpenScript> entry : openScripts.entrySet()) {
            Map<Integer, String> messagesByLine = new HashMap<>();
            for (Diagnostic diagnostic : fileDiagnostics(entry.getKey())) {
                messagesByLine.merge(diagnostic.line(),
                        diagnostic.severity().name() + ": " + diagnostic.message(),
                        (first, second) -> first + "\n" + second);
            }
            applyMarkers(entry.getValue().editor(), messagesByLine);
        }
        markersDirty = false;
    }

    private static void applyMarkers(TextEditor editor, Map<Integer, String> messagesByLine) {
        editor.clearMarkers();
        messagesByLine.forEach((line, message) -> editor.addMarker(line - 1,
                ERROR_MARGIN_COLOR_ABGR, ERROR_LINE_COLOR_ABGR, message, message));
    }

    private void renderTabs() {
        if (!ImGui.beginTabBar("##script-tabs", ImGuiTabBarFlags.Reorderable | ImGuiTabBarFlags.DrawSelectedOverline)) {
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
        String label = DocumentTabs.reserveIconSpace(path.getFileName().toString()) + "##" + path;
        boolean selected = ImGui.beginTabItem(label, keepOpen, flags);
        DocumentTabs.decorate(scriptIconTextureId);
        boolean middleClicked = DocumentTabs.closeRequestedByMiddleClick();
        if (selected) {
            renderEditor(path, script);
            ImGui.endTabItem();
        }
        if (!keepOpen.get() || middleClicked) {
            closeTab(path);
        }
    }

    private void closeTab(Path path) {
        Optional.ofNullable(openScripts.remove(path)).ifPresent(OpenScript::dispose);
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
        float surfaceHeight = Math.max(EditorScale.of(MINIMUM_EDITOR_HEIGHT),
                ImGui.getContentRegionAvailY() - diagnosticsHeightFor(path));
        if (script.isShowingDiff()) {
            script.diff().render("##diff-" + path, ImGui.getContentRegionAvailX(), surfaceHeight, true);
            renderDiagnostics(path);
            return;
        }
        handleCompletionKeys(path, script);
        renderEditorSurface(path, script, surfaceHeight, forceCompletion);
        completionPopup.render().ifPresent(symbol -> acceptCompletion(path, script, symbol));
        renderDiagnostics(path);
    }

    private void renderDiffToggle(Path path, OpenScript script) {
        String label = I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_DIFF);
        if (Toggles.text("script-diff-" + path, label, script.isShowingDiff())) {
            script.toggleDiff();
            completionPopup.hide();
        }
        if (script.isShowingDiff()) {
            script.refreshDiff();
        }
    }

    private void handleCompletionKeys(Path path, OpenScript script) {
        CompletionPopup.KeyAction action = completionPopup.handleKeys();
        if (action == CompletionPopup.KeyAction.NONE) {
            return;
        }
        ImGui.getIO().clearInputKeys();
        if (action == CompletionPopup.KeyAction.ACCEPT) {
            completionPopup.selected().ifPresent(symbol -> acceptCompletion(path, script, symbol));
        }
        if (action == CompletionPopup.KeyAction.CLOSE) {
            completionPopup.hide();
        }
    }

    private void renderEditorSurface(Path path, OpenScript script, float height, boolean forceCompletion) {
        TextEditor editor = script.editor();
        script.consumePendingLine().ifPresent(line -> editor.setCursor(line - 1, 0));
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        EditorStyle.monospaceFont().ifPresent(font ->
                ImGui.pushFont(font, EditorStyle.monospaceFontPixelHeight()));
        editor.render("##texteditor-" + path, ImGui.getContentRegionAvailX(), height);
        EditorStyle.monospaceFont().ifPresent(ignored -> ImGui.popFont());
        acceptAssetDrop(script);
        boolean textChanged = script.consumeTextChange();
        if (textChanged) {
            script.markDirty();
        }
        if (isWordDeleteShortcutPressed()) {
            deletePreviousWord(script);
            textChanged = true;
        }
        updateCompletion(path, editor, new EditorOrigin(originX, originY),
                new CompletionTrigger(textChanged, script.consumeCursorMove(), forceCompletion));
    }

    private void acceptAssetDrop(OpenScript script) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        for (String mimeType : DROPPABLE_MIME_TYPES) {
            String droppedPath = ImGui.acceptDragDropPayload(mimeType, String.class);
            if (droppedPath != null) {
                script.editor().replaceTextInCurrentCursor("\"" + droppedPath + "\"");
                script.markEdited();
                break;
            }
        }
        ImGui.endDragDropTarget();
    }

    private static boolean isWordDeleteShortcutPressed() {
        return ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Backspace, false)
                && ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
    }

    private void deletePreviousWord(OpenScript script) {
        TextEditor editor = script.editor();
        editor.getMainCursorPosition(cursorPosition);
        String lineText = editor.getLineText(cursorPosition.line);
        int cursorIndex = characterIndexForColumn(lineText, cursorPosition.column);
        int wordStart = wordStartBefore(lineText, cursorIndex);
        if (wordStart == cursorIndex) {
            return;
        }
        editor.selectRegion(cursorPosition.line, columnForCharacterIndex(lineText, wordStart),
                cursorPosition.line, cursorPosition.column);
        editor.replaceTextInCurrentCursor("");
        script.markEdited();
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

    private float completionAnchorX(TextEditor editor, float originX) {
        float glyphWidth = editor.getGlyphWidth();
        int lineNumberDigits = String.valueOf(editor.getLineCount() + 1).length();
        float textOffset = (lineNumberDigits + MARGIN_GLYPHS_BEFORE_TEXT) * glyphWidth;
        return originX + textOffset + (cursorPosition.column - editor.getFirstVisibleColumn()) * glyphWidth;
    }

    private float completionAnchorY(TextEditor editor, float originY) {
        return originY + (cursorPosition.line + 1 - editor.getFirstVisibleLine()) * editor.getLineHeight();
    }

    private void updateCompletion(Path path, TextEditor editor, EditorOrigin origin, CompletionTrigger trigger) {
        if (!trigger.shouldRecompute()) {
            if (trigger.cursorMoved()) {
                completionPopup.hide();
            }
            return;
        }
        editor.getMainCursorPosition(cursorPosition);
        String lineText = editor.getLineText(cursorPosition.line);
        int cursorIndex = characterIndexForColumn(lineText, cursorPosition.column);
        CompletionEngine.Context context = completionEngine.contextAt(lineText, cursorIndex);
        if (!trigger.forced() && !completionEngine.shouldTrigger(context)) {
            completionPopup.hide();
            return;
        }
        List<CompletionSymbol> candidates = completionEngine.candidates(context, editor.getText(),
                syntaxes.importStyleFor(path).orElse(PLAIN_IMPORT_STYLE));
        completionPopup.show(candidates, completionAnchorX(editor, origin.x()),
                completionAnchorY(editor, origin.y()));
    }

    private void acceptCompletion(Path path, OpenScript script, CompletionSymbol symbol) {
        TextEditor editor = script.editor();
        editor.getMainCursorPosition(cursorPosition);
        String lineText = editor.getLineText(cursorPosition.line);
        int cursorIndex = characterIndexForColumn(lineText, cursorPosition.column);
        CompletionEngine.Context context = completionEngine.contextAt(lineText, cursorIndex);
        int replacedLength = context.importPath().orElse(context.prefix()).length();
        if (replacedLength > 0) {
            editor.selectRegion(cursorPosition.line, cursorPosition.column - replacedLength,
                    cursorPosition.line, cursorPosition.column);
        }
        editor.replaceTextInCurrentCursor(symbol.insertText());
        if (!context.isImport() && symbol.kind() == CompletionKind.TYPE) {
            symbol.qualifiedName().ifPresent(qualifiedName -> ensureImport(path, script, qualifiedName));
        }
        script.markEdited();
        completionPopup.hide();
    }

    private void ensureImport(Path path, OpenScript script, String qualifiedName) {
        syntaxes.importStyleFor(path)
                .flatMap(style -> ImportPlanner.plan(script.editor().getText(), qualifiedName, style))
                .ifPresent(plan -> insertImport(script, plan));
    }

    private void insertImport(OpenScript script, ImportPlanner.ImportPlan plan) {
        TextEditor editor = script.editor();
        editor.getMainCursorPosition(cursorPosition);
        int restoredLine = cursorPosition.line;
        int restoredColumn = cursorPosition.column;
        editor.setCursor(plan.lineIndex(), 0);
        editor.replaceTextInCurrentCursor(plan.insertionText());
        int lineOffset = plan.lineIndex() <= restoredLine ? plan.addedLines() : 0;
        editor.setCursor(restoredLine + lineOffset, restoredColumn);
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
        return fileDiagnostics(path).isEmpty() ? 0.0f : EditorScale.of(DIAGNOSTICS_HEIGHT);
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
        int color = severityColor(diagnostic.severity());
        Chips.drawInline(diagnostic.severity().name(), color);
        Texts.colored(color,
                I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_DIAGNOSTIC,
                        diagnostic.line(), diagnostic.message()));
        if (ImGui.isItemClicked()) {
            open(path, diagnostic.line());
        }
    }

    private void save(Path path, OpenScript script) {
        try {
            String text = script.editor().getText();
            Files.writeString(path, text);
            script.adoptSavedText(text);
            script.markSaved();
            notifier.show(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TOAST_SAVED, path.getFileName()));
            onSaved.accept(path);
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCRIPT_EDITOR_VIEW_TOAST_SAVE_FAILED,
                    error.getMessage()));
        }
    }

    private record Diagnostic(Path file, int line, Severity severity, String message) {
    }

    private enum Severity {
        ERROR, WARNING
    }

    private static Severity severityOf(String captured) {
        return captured != null && captured.equalsIgnoreCase("warning") ? Severity.WARNING : Severity.ERROR;
    }

    private static int severityColor(Severity severity) {
        return severity == Severity.WARNING ? EditorStyle.COLOR_WARNING : EditorStyle.COLOR_DANGER;
    }

    private record EditorOrigin(float x, float y) {
    }

    private record CompletionTrigger(boolean textChanged, boolean cursorMoved, boolean forced) {

        boolean shouldRecompute() {
            return textChanged || forced;
        }
    }

    private static final class OpenScript {

        private final TextEditor editor;
        private final TextEditorCursorPosition lastCursorPosition = new TextEditorCursorPosition();
        private final TextEditorCursorPosition currentCursorPosition = new TextEditorCursorPosition();
        private final TextDiff diff = new TextDiff();
        private String savedText;
        private long textVersion;
        private boolean dirty;
        private boolean showingDiff;
        private OptionalInt pendingLine = OptionalInt.empty();

        OpenScript(TextEditor editor, String savedText, TextEditorLanguage language) {
            this.editor = editor;
            this.savedText = savedText;
            this.textVersion = editor.getUndoIndex();
            this.diff.setLanguage(language);
            this.diff.setColors(DIFF_ADDED_COLOR_ABGR, DIFF_DELETED_COLOR_ABGR);
        }

        TextDiff diff() {
            return diff;
        }

        boolean isShowingDiff() {
            return showingDiff;
        }

        void toggleDiff() {
            showingDiff = !showingDiff;
            if (showingDiff) {
                diff.setText(savedText, editor.getText());
            }
        }

        void refreshDiff() {
            diff.setText(savedText, editor.getText());
        }

        void adoptSavedText(String text) {
            savedText = text;
        }

        void dispose() {
            editor.destroy();
            diff.destroy();
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

        void markEdited() {
            dirty = true;
            consumeTextChange();
        }

        void markSaved() {
            dirty = false;
        }

        boolean consumeTextChange() {
            long version = editor.getUndoIndex();
            boolean changed = version != textVersion;
            textVersion = version;
            return changed;
        }

        boolean consumeCursorMove() {
            editor.getMainCursorPosition(currentCursorPosition);
            boolean moved = !currentCursorPosition.equals(lastCursorPosition);
            lastCursorPosition.set(currentCursorPosition);
            return moved;
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
