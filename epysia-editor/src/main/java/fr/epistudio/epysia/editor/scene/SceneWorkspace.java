package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class SceneWorkspace {

    private final Project project;
    private final EditorScene3DHost host;
    private final SceneSerializer serializer;
    private final ComponentRegistry registry;
    private final Notifier notifier;
    private final List<SceneDocument> documents = new ArrayList<>();
    private int activeIndex = -1;

    public SceneWorkspace(Project project, EditorScene3DHost host, SceneSerializer serializer,
                          ComponentRegistry registry, Notifier notifier) {
        this.project = project;
        this.host = host;
        this.serializer = serializer;
        this.registry = registry;
        this.notifier = notifier;
    }

    public List<SceneDocument> documents() {
        return documents;
    }

    public int activeIndex() {
        return activeIndex;
    }

    public SceneDocument active() {
        return documents.get(activeIndex);
    }

    public void switchTo(int index) {
        if (index < 0 || index >= documents.size()) {
            return;
        }
        activeIndex = index;
        host.setActiveScene(active().scene());
    }

    public SceneDocument create() {
        return create("untitled", (scene, services) -> {
        });
    }

    public SceneDocument create(String baseName, BiConsumer<Scene, EngineServices> populate) {
        String name = uniqueName(baseName);
        Path filePath = project.scenesDirectory().resolve(name + Project.SCENE_EXTENSION);
        Scene scene = new Scene(name);
        populate.accept(scene, host.engine());
        ensureDefaultLight(scene);
        SceneDocument document = buildDocument(scene, filePath, name);
        registerAndActivate(document);
        save(document);
        return document;
    }

    public SceneDocument open(Path path) {
        Optional<Integer> existing = indexOfPath(path);
        if (existing.isPresent()) {
            switchTo(existing.get());
            return active();
        }
        String name = stripExtension(path);
        Scene scene = new Scene(name);
        try {
            serializer.load(scene, path, host.engine());
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCENE_WORKSPACE_TOAST_FAILED_TO_LOAD,
                    error.getMessage()));
            return active();
        }
        ensureDefaultLight(scene);
        SceneDocument document = buildDocument(scene, path, name);
        document.history().clear();
        document.markClean();
        registerAndActivate(document);
        return document;
    }

    public void close(SceneDocument document) {
        int index = documents.indexOf(document);
        if (index >= 0) {
            close(index);
        }
    }

    public void close(int index) {
        if (index < 0 || index >= documents.size()) {
            return;
        }
        SceneDocument document = documents.get(index);
        host.closeScene(document.scene());
        documents.remove(index);
        if (documents.isEmpty()) {
            create();
            return;
        }
        switchTo(Math.min(index, documents.size() - 1));
    }

    public void save(SceneDocument document) {
        try {
            Files.createDirectories(document.filePath().getParent());
            serializer.save(document.scene(), document.filePath(), gameObject -> true);
            document.markClean();
            notifier.show(I18n.translate(TextKey.EDITOR_SCENE_WORKSPACE_TOAST_SCENE_SAVED));
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCENE_WORKSPACE_TOAST_SAVE_FAILED,
                    error.getMessage()));
        }
    }

    public boolean rename(SceneDocument document, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\")) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCENE_WORKSPACE_TOAST_INVALID_NAME));
            return false;
        }
        Path target = project.scenesDirectory().resolve(trimmed + Project.SCENE_EXTENSION);
        if (nameTaken(target, document)) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCENE_WORKSPACE_TOAST_SCENE_EXISTS));
            return false;
        }
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(document.filePath())) {
                Files.move(document.filePath(), target);
            }
            document.renameTo(target, trimmed);
            return true;
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_SCENE_WORKSPACE_TOAST_RENAME_FAILED,
                    error.getMessage()));
            return false;
        }
    }

    private boolean nameTaken(Path target, SceneDocument self) {
        for (SceneDocument document : documents) {
            if (document != self && document.filePath().equals(target)) {
                return true;
            }
        }
        return Files.exists(target);
    }

    private SceneDocument buildDocument(Scene scene, Path filePath, String name) {
        EditorSelection selection = new EditorSelection();
        EngineServices services = host.engine();
        EditorHistory history = new EditorHistory(new CommandContext(scene, selection, services, registry));
        return new SceneDocument(scene, selection, history, filePath, name);
    }

    private void registerAndActivate(SceneDocument document) {
        host.openScene(document.scene());
        documents.add(document);
        switchTo(documents.size() - 1);
    }

    private Optional<Integer> indexOfPath(Path path) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).filePath().equals(path)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private String uniqueName(String base) {
        String candidate = base;
        int counter = 2;
        while (nameExists(candidate)) {
            candidate = base + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private boolean nameExists(String name) {
        Path path = project.scenesDirectory().resolve(name + Project.SCENE_EXTENSION);
        if (Files.exists(path)) {
            return true;
        }
        for (SceneDocument document : documents) {
            if (document.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String stripExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private void ensureDefaultLight(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject.getComponent(DirectionalLight.class).isPresent()) {
                return;
            }
        }
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D().lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f));
        sun.addComponent(new DirectionalLight()
                .setColor(1.0f, 0.95f, 0.85f)
                .setAmbient(0.22f, 0.24f, 0.28f)
                .setShadowExtent(8.0f, 0.5f, 30.0f));
        scene.addGameObject(sun);
        scene.advanceTick();
    }
}
