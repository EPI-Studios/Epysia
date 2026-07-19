package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.scene.SceneWorkspace;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.reflection.ComponentFieldCodec;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.scripting.compile.ScriptLoadResult;
import fr.epistudio.epysia.scripting.compile.ScriptModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ScriptService {

    private final Project project;
    private final ComponentRegistry registry;
    private final SceneSerializer serializer;
    private final SceneWorkspace workspace;
    private final Consumer<String> log;
    private long lastSeenStamp;
    private long pendingSinceMillis;

    public ScriptService(Project project, ComponentRegistry registry, SceneSerializer serializer,
                         SceneWorkspace workspace, Consumer<String> log) {
        this.project = project;
        this.registry = registry;
        this.serializer = serializer;
        this.workspace = workspace;
        this.log = log;
        this.lastSeenStamp = latestModified(project.scriptsDirectory());
    }

    public void poll(long nowMillis) {
        long stamp = latestModified(project.scriptsDirectory());
        if (stamp != lastSeenStamp) {
            lastSeenStamp = stamp;
            pendingSinceMillis = nowMillis;
            return;
        }
        if (pendingSinceMillis != 0 && nowMillis - pendingSinceMillis > 300) {
            pendingSinceMillis = 0;
            reload();
        }
    }

    private long latestModified(java.nio.file.Path scriptsDirectory) {
        if (scriptsDirectory == null || !java.nio.file.Files.isDirectory(scriptsDirectory)) {
            return 0L;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(scriptsDirectory)) {
            return walk.filter(path -> path.toString().endsWith(".java"))
                    .mapToLong(path -> path.toFile().lastModified())
                    .max().orElse(0L);
        } catch (java.io.IOException exception) {
            return 0L;
        }
    }

    public void reload() {
        ScriptLoadResult result = ScriptModule.load(
                project.scriptsDirectory(), project.rootDirectory().resolve(".epysia/scripts-out"));
        for (String message : result.messages()) {
            log.accept(message);
        }
        if (!result.ok()) {
            log.accept("Script reload failed; keeping last compiled scripts.");
            return;
        }
        registry.setUserComponents(result.components());
        Set<String> userTypes = new HashSet<>();
        result.components().forEach(component -> userTypes.add(component.componentClass().getName()));
        reinstantiate(userTypes);
        log.accept("Scripts compiled (" + result.components().size() + " components).");
    }

    private void reinstantiate(Set<String> userTypes) {
        if (workspace.documents().isEmpty()) {
            return;
        }
        SceneDocument document = workspace.active();
        for (GameObject gameObject : document.scene().gameObjects()) {
            List<IComponent> snapshot = new ArrayList<>(gameObject.components());
            for (IComponent component : snapshot) {
                if (!userTypes.contains(component.getClass().getName())) {
                    continue;
                }
                Map<String, Object> fields = ComponentFieldCodec.capture(component);
                IComponent fresh = freshInstance(component.getClass().getName());
                if (fresh == null) {
                    continue;
                }
                serializer.applyFields(fresh, fields);
                gameObject.replaceComponent(component, fresh);
            }
        }
    }

    private IComponent freshInstance(String typeName) {
        for (ComponentRegistry.Entry entry : registry.entries()) {
            if (entry.componentClass().getName().equals(typeName)) {
                return entry.factory().get();
            }
        }
        return null;
    }
}
