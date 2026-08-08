package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.scene.SceneWorkspace;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.reflection.ComponentFieldCodec;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;
import fr.epistudio.epysia.scripting.compile.ScriptLoadResult;
import fr.epistudio.epysia.scripting.compile.ScriptModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class ScriptService {

    private final ScriptLanguages languages = ScriptLanguages.discover();
    private final Project project;
    private final ComponentRegistry registry;
    private final SceneSerializer serializer;
    private final SceneWorkspace workspace;
    private final Consumer<String> log;
    private final Consumer<List<Class<? extends ProjectRenderSetup>>> renderSetupSink;
    private long lastSeenStamp;
    private long pendingSinceMillis;

    public ScriptService(Project project, ComponentRegistry registry, SceneSerializer serializer,
                         SceneWorkspace workspace, Consumer<String> log,
                         Consumer<List<Class<? extends ProjectRenderSetup>>> renderSetupSink) {
        this.project = project;
        this.registry = registry;
        this.serializer = serializer;
        this.workspace = workspace;
        this.log = log;
        this.renderSetupSink = renderSetupSink;
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

    private long latestModified(Path scriptsDirectory) {
        if (scriptsDirectory == null || !Files.isDirectory(scriptsDirectory)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(scriptsDirectory)) {
            return walk.filter(languages::isSource)
                    .mapToLong(path -> path.toFile().lastModified())
                    .max().orElse(0L);
        } catch (IOException exception) {
            return 0L;
        }
    }

    public void reload() {
        KotlinRuntimeInstaller.ensureStandardLibrary(project, languages).ifPresent(log);
        ScriptLoadResult result = ScriptModule.load(project.scriptsDirectory(),
                project.compiledScriptsDirectory(), project.libraries());
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
        renderSetupSink.accept(result.renderSetups());
        log.accept("Scripts compiled (" + result.components().size() + " components, "
                + result.renderSetups().size() + " render setups).");
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
