package fr.epistudio.epysia.editor.ui.files;

import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.ui.kit.Breadcrumb;
import fr.epistudio.epysia.editor.ui.kit.Dialogs;
import fr.epistudio.epysia.editor.ui.kit.EmptyStates;
import fr.epistudio.epysia.editor.ui.kit.SearchField;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class FileBrowser {

    private static final String POPUP_ID = "##epysia-file-browser";
    private static final float WIDTH = 720.0f;
    private static final float HEIGHT = 460.0f;
    private static final float PLACES_WIDTH = 170.0f;
    private static final float FOOTER_HEIGHT = 68.0f;
    private static final float MINIMUM_BODY_HEIGHT = 120.0f;
    private static final int SEARCH_CAPACITY = 128;

    private final IconWidgets icons;
    private final ImString search = new ImString(SEARCH_CAPACITY);

    private Optional<FileBrowserSession> session = Optional.empty();
    private FileListing listing = FileListing.read(Path.of("."));
    private List<FilePlace> places = List.of();
    private Optional<Path> selected = Optional.empty();
    private boolean showHidden;
    private boolean openRequested;

    public FileBrowser(IconWidgets icons) {
        this.icons = icons;
    }

    public void chooseFolder(String title, Path start, Consumer<Path> onChosen) {
        begin(FileBrowserSession.folder(title, onChosen), start);
    }

    public void chooseFile(String title, Path start, Set<String> extensions, Consumer<Path> onChosen) {
        begin(FileBrowserSession.file(title, extensions, onChosen), start);
    }

    private void begin(FileBrowserSession request, Path start) {
        session = Optional.of(request);
        search.set("");
        selected = Optional.empty();
        places = FilePlace.discover(Path.of(System.getProperty("user.home", ".")));
        navigateTo(start);
        openRequested = true;
    }

    private void navigateTo(Path directory) {
        listing = FileListing.read(directory);
        selected = Optional.empty();
    }

    public void render() {
        if (session.isEmpty()) {
            return;
        }
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        if (!Dialogs.begin(POPUP_ID, WIDTH, HEIGHT)) {
            return;
        }
        renderContents(session.orElseThrow());
        Dialogs.end();
    }

    private void renderContents(FileBrowserSession active) {
        Dialogs.title(active.title());
        renderNavigation();
        float bodyHeight = Math.max(EditorScale.of(MINIMUM_BODY_HEIGHT),
                EditorScale.of(HEIGHT - FOOTER_HEIGHT) - ImGui.getCursorPosY());
        renderPlaces(bodyHeight);
        ImGui.sameLine();
        renderListing(active, bodyHeight);
        renderFooter(active);
    }

    private void renderNavigation() {
        ImGui.beginDisabled(listing.parent().isEmpty());
        if (ImGui.button(I18n.label(TextKey.EDITOR_FILE_BROWSER_PARENT, "file-browser-parent"))) {
            listing.parent().ifPresent(this::navigateTo);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        renderBreadcrumb();
        ImGui.sameLine();
        ImGui.setCursorPosX(EditorScale.of(WIDTH - PLACES_WIDTH));
        SearchField.render("##file-browser-search",
                I18n.translate(TextKey.EDITOR_FILE_BROWSER_SEARCH), search,
                EditorScale.of(PLACES_WIDTH) - ImGui.getStyle().getWindowPaddingX());
    }

    private void renderBreadcrumb() {
        List<Path> trail = trailOf(listing.directory());
        OptionalInt clicked = Breadcrumb.render("file-browser",
                trail.stream().map(FileBrowser::segmentName).toList());
        if (clicked.isPresent()) {
            navigateTo(trail.get(clicked.getAsInt()));
        }
    }

    private static List<Path> trailOf(Path directory) {
        List<Path> trail = new ArrayList<>();
        for (Path current = directory; current != null; current = current.getParent()) {
            trail.add(current);
        }
        Collections.reverse(trail);
        return List.copyOf(trail);
    }

    private static String segmentName(Path path) {
        return path.getFileName() == null ? path.toString() : path.getFileName().toString();
    }

    private void renderPlaces(float height) {
        ImGui.beginChild("##file-browser-places", EditorScale.of(PLACES_WIDTH), height, true);
        for (FilePlace place : places) {
            if (ImGui.selectable(place.label() + "##place-" + place.path(),
                    place.path().equals(listing.directory()))) {
                navigateTo(place.path());
            }
        }
        ImGui.endChild();
    }

    private void renderListing(FileBrowserSession active, float height) {
        ImGui.beginChild("##file-browser-entries", 0.0f, height, true);
        List<FileEntry> visible = listing.visible(filterFor(active));
        if (listing.failure().isPresent()) {
            EmptyStates.centerText(I18n.translate(failureKey(listing.failure().orElseThrow())));
        } else if (visible.isEmpty()) {
            EmptyStates.centerText(I18n.translate(TextKey.EDITOR_FILE_BROWSER_EMPTY));
        }
        visible.forEach(entry -> renderEntry(active, entry));
        ImGui.endChild();
    }

    private static TextKey failureKey(FileListing.Failure failure) {
        return switch (failure) {
            case MISSING -> TextKey.EDITOR_FILE_BROWSER_MISSING;
            case UNREADABLE -> TextKey.EDITOR_FILE_BROWSER_UNREADABLE;
        };
    }

    private void renderEntry(FileBrowserSession active, FileEntry entry) {
        boolean chosen = selected.filter(entry.path()::equals).isPresent();
        float rowStart = ImGui.getCursorPosY();
        if (ImGui.selectable("##entry-" + entry.name(), chosen,
                ImGuiSelectableFlags.AllowDoubleClick)) {
            selected = Optional.of(entry.path());
            if (ImGui.isMouseDoubleClicked(0)) {
                activate(active, entry);
            }
        }
        renderEntryLabel(entry, rowStart);
    }

    private void renderEntryLabel(FileEntry entry, float rowStart) {
        float size = ImGui.getTextLineHeight();
        ImGui.setCursorPosY(rowStart);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getStyle().getItemSpacingX());
        icons.drawInline(entry.directory() ? EditorIcon.FOLDER : EditorIcon.FILE, size);
        ImGui.textUnformatted(entry.name());
    }

    private void activate(FileBrowserSession active, FileEntry entry) {
        if (entry.directory()) {
            navigateTo(entry.path());
            return;
        }
        if (filterFor(active).allows(entry)) {
            complete(active, entry.path());
        }
    }

    private FileFilter filterFor(FileBrowserSession active) {
        return active.foldersOnly()
                ? FileFilter.folders(showHidden, search.get())
                : FileFilter.files(active.extensions(), showHidden, search.get());
    }

    private void renderFooter(FileBrowserSession active) {
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_FILE_BROWSER_SHOW_HIDDEN,
                "file-browser-hidden"), showHidden)) {
            showHidden = !showHidden;
        }
        ImGui.sameLine();
        Texts.muted(chosenPath(active).map(Path::toString)
                .orElseGet(() -> I18n.translate(TextKey.EDITOR_FILE_BROWSER_NOTHING_SELECTED)));
        Dialogs.alignFooter(2);
        if (Dialogs.button(I18n.label(TextKey.EDITOR_FILE_BROWSER_CANCEL, "file-browser-cancel"))) {
            dismiss();
        }
        ImGui.sameLine();
        Optional<Path> choice = chosenPath(active);
        if (Dialogs.primaryButton(I18n.label(TextKey.EDITOR_FILE_BROWSER_CHOOSE, "file-browser-choose"),
                choice.isPresent())) {
            complete(active, choice.orElseThrow());
        }
    }

    private Optional<Path> chosenPath(FileBrowserSession active) {
        if (!active.foldersOnly()) {
            return selected.filter(path -> !java.nio.file.Files.isDirectory(path));
        }
        return Optional.of(selected.filter(java.nio.file.Files::isDirectory)
                .orElseGet(listing::directory));
    }

    private void complete(FileBrowserSession active, Path path) {
        dismiss();
        active.onChosen().accept(path);
    }

    private void dismiss() {
        session = Optional.empty();
        selected = Optional.empty();
        ImGui.closeCurrentPopup();
    }
}
