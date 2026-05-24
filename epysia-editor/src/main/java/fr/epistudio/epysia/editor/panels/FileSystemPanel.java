package fr.epistudio.epysia.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.widgets.AssetBrowser;
import com.miry.ui.widgets.Button;
import com.miry.ui.widgets.ToastManager;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class FileSystemPanel extends Panel {

    private static final String TITLE = "FileSystem";
    private static final int HEADER_HEIGHT = 26;
    private static final int REFRESH_BUTTON_WIDTH = 70;
    private static final int REFRESH_BUTTON_HEIGHT = 18;
    private static final int CONTENT_PADDING = 6;
    private static final long RESCAN_INTERVAL_NANOS = 2_000_000_000L;

    private final Project project;
    private final ToastManager toasts;
    private final AssetBrowser<Path> browser = new AssetBrowser<>();
    private final Button refreshButton = new Button("Refresh");
    private long lastScanNanos;
    private int lastSelectedIndex = -1;

    public FileSystemPanel(Project project, ToastManager toasts) {
        super(TITLE);
        this.project = project;
        this.toasts = toasts;
        browser.setViewMode(AssetBrowser.ViewMode.LIST);
        scanProjectFiles();
    }

    @Override
    public void render(PanelContext context) {
        autoRescanIfStale();
        renderHeader(context);
        int contentTop = context.y() + HEADER_HEIGHT + CONTENT_PADDING;
        int contentHeight = Math.max(1, context.height() - HEADER_HEIGHT - CONTENT_PADDING * 2);
        browser.render(context.renderer(), context.ui().input(), context.ui().theme(),
                context.x() + CONTENT_PADDING, contentTop,
                context.width() - CONTENT_PADDING * 2, contentHeight);
        notifyIfSelectionChanged();
    }

    private void renderHeader(PanelContext context) {
        UiRenderer renderer = context.renderer();
        renderer.drawRect(context.x(), context.y(), context.width(), HEADER_HEIGHT, EditorStyle.LEAF_HEADER_BG);
        renderer.drawText(project.rootDirectory().getFileName().toString(),
                context.x() + 10, context.y() + 17, EditorStyle.COLOR_TEXT_HEADER);
        int buttonX = context.x() + context.width() - REFRESH_BUTTON_WIDTH - 8;
        int buttonY = context.y() + (HEADER_HEIGHT - REFRESH_BUTTON_HEIGHT) / 2;
        if (refreshButton.render(renderer, context.uiContext(), context.ui().input(), context.ui().theme(),
                buttonX, buttonY, REFRESH_BUTTON_WIDTH, REFRESH_BUTTON_HEIGHT, true)) {
            scanProjectFiles();
            toasts.show("Refreshed file list", 1.2f);
        }
    }

    private void autoRescanIfStale() {
        long now = System.nanoTime();
        if (now - lastScanNanos > RESCAN_INTERVAL_NANOS) {
            scanProjectFiles();
        }
    }

    private void scanProjectFiles() {
        lastScanNanos = System.nanoTime();
        browser.clear();
        Path root = project.rootDirectory();
        List<Path> all = collectFiles(root);
        all.sort(Comparator.comparing(Path::toString));
        for (Path file : all) {
            AssetBrowser.AssetItem<Path> item = new AssetBrowser.AssetItem<>(file, root.relativize(file).toString());
            item.icon = iconForPath(file);
            item.type = typeLabelForPath(file);
            browser.addItem(item);
        }
    }

    private List<Path> collectFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Path> collected = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 4)) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().startsWith("."))
                    .forEach(collected::add);
        } catch (IOException ignored) {
        }
        return collected;
    }

    private Icon iconForPath(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".epyscene")) return Icon.FOLDER;
        if (name.endsWith(".java")) return Icon.CODE;
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) return Icon.IMAGE;
        if (name.endsWith(".vert") || name.endsWith(".frag") || name.endsWith(".glsl")) return Icon.SHADER;
        return Icon.FILE;
    }

    private String typeLabelForPath(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toUpperCase();
    }

    private void notifyIfSelectionChanged() {
        AssetBrowser.AssetItem<Path> selected = browser.selectedItem();
        if (selected == null) {
            lastSelectedIndex = -1;
            return;
        }
        int currentIndex = System.identityHashCode(selected);
        if (currentIndex != lastSelectedIndex) {
            lastSelectedIndex = currentIndex;
            toasts.show(project.rootDirectory().relativize(selected.data).toString(), 1.2f);
        }
    }
}
