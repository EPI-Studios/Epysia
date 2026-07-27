package fr.epistudio.epysia.editor.play;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.audio.AudioSystem;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.log.EditorConsole;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.GraphSystem;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.project.ProjectQuality;
import fr.epistudio.epysia.physics.api.CollisionLayers;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.scripting.ScriptDispatcherSystem;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class EmbeddedPlaySession {

    public enum State { STOPPED, PLAYING, PAUSED }

    private double fixedTimestepSeconds = 1.0 / 60.0;
    private static final double MAX_FRAME_SECONDS = 0.25;

    private final EditorScene3DHost sceneHost;
    private final SceneSerializer serializer;
    private final Project project;
    private final ProjectStore projectStore;
    private final Supplier<SceneDocument> activeDocument;
    private final Notifier notifier;
    private final EditorConsole console;
    private final PlayInputSampler inputSampler = new PlayInputSampler();

    private State state = State.STOPPED;
    private SceneDocument playingDocument;
    private String snapshot;
    private Optional<UUID> selectedId = Optional.empty();
    private double accumulator;
    private long tickCount;

    public EmbeddedPlaySession(EditorScene3DHost sceneHost, SceneSerializer serializer, Project project,
                               ProjectStore projectStore, Supplier<SceneDocument> activeDocument,
                               Notifier notifier, EditorConsole console) {
        this.sceneHost = sceneHost;
        this.serializer = serializer;
        this.project = project;
        this.projectStore = projectStore;
        this.activeDocument = activeDocument;
        this.notifier = notifier;
        this.console = console;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state != State.STOPPED;
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    public long tickCount() {
        return tickCount;
    }

    public float interpolationAlpha() {
        return (float) (accumulator / fixedTimestepSeconds);
    }

    public PlayInputSampler inputSampler() {
        return inputSampler;
    }

    public Optional<SceneDocument> playingDocument() {
        return Optional.ofNullable(playingDocument);
    }

    public void start() {
        if (state != State.STOPPED) {
            return;
        }
        playingDocument = activeDocument.get();
        snapshot = serializer.serialize(playingDocument.scene(), gameObject -> true);
        selectedId = playingDocument.selection().get().map(GameObject::id);
        accumulator = 0.0;
        tickCount = 0L;
        applyCollisionLayers();
        warnIfNoActiveCamera();
        dispatchLifecycle("onPlayStart", component -> component.onPlayStart(engine()));
        state = State.PLAYING;
        console.system("[play] Embedded play started");
    }

    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    public void togglePause() {
        if (state == State.PLAYING) {
            pause();
        } else {
            resume();
        }
    }

    public void step() {
        if (state != State.PAUSED) {
            return;
        }
        runTicks(1);
    }

    public void stop() {
        if (state == State.STOPPED) {
            return;
        }
        dispatchLifecycle("onPlayStop", component -> component.onPlayStop(engine()));
        restoreEditingState();
        console.system("[play] Embedded play stopped");
    }

    public void frame(float deltaSeconds) {
        if (state != State.PLAYING) {
            return;
        }
        accumulator = Math.min(accumulator + deltaSeconds, MAX_FRAME_SECONDS);
        int pending = (int) (accumulator / fixedTimestepSeconds);
        accumulator -= pending * fixedTimestepSeconds;
        runTicks(pending);
    }

    public Optional<Camera3D> gameCamera() {
        if (playingDocument == null) {
            return Optional.empty();
        }
        for (GameObject gameObject : playingDocument.scene().gameObjects()) {
            Optional<Camera3D> camera = gameObject.getComponent(Camera3D.class).filter(Camera3D::active);
            if (camera.isPresent()) {
                return camera;
            }
        }
        return Optional.empty();
    }

    private void runTicks(int count) {
        try {
            for (int index = 0; index < count; index++) {
                engine().tick(inputSampler.inputState(), (float) fixedTimestepSeconds);
                inputSampler.inputState().advanceFrame();
                tickCount++;
            }
        } catch (RuntimeException error) {
            failAndStop(error);
        }
    }

    private void failAndStop(RuntimeException error) {
        console.error("[play] Runtime error, stopping play mode: " + error);
            notifier.show(I18n.translate(TextKey.EDITOR_EMBEDDED_PLAY_SESSION_TOAST_PLAY_STOPPED,
                    error.getClass().getSimpleName()));
        dispatchLifecycle("onPlayStop", component -> component.onPlayStop(engine()));
        restoreEditingState();
    }

    private void restoreEditingState() {
        resetSystems();
        serializer.deserialize(playingDocument.scene(), snapshot, engine());
        applyCollisionLayers();
        playingDocument.history().clear();
        reselectByUuid(playingDocument);
        snapshot = null;
        playingDocument = null;
        selectedId = Optional.empty();
        state = State.STOPPED;
    }

    private void reselectByUuid(SceneDocument document) {
        selectedId.flatMap(document.scene()::findById)
                .ifPresentOrElse(document.selection()::select, document.selection()::clear);
    }

    private void resetSystems() {
        Optional.ofNullable(engine().systems().get(ScriptDispatcherSystem.class))
                .ifPresent(ScriptDispatcherSystem::resetForPlaySession);
        Optional.ofNullable(engine().systems().get(PhysicsSystem.class))
                .ifPresent(PhysicsSystem::resetForPlaySession);
        Optional.ofNullable(engine().systems().get(AudioSystem.class))
                .ifPresent(AudioSystem::resetForPlaySession);
        Optional.ofNullable(engine().systems().get(GraphSystem.class))
                .ifPresent(GraphSystem::resetForPlaySession);
    }

    private void applyCollisionLayers() {
        Optional.ofNullable(engine().systems().get(PhysicsSystem.class)).ifPresent(physics ->
                physics.setCollisionLayers(CollisionLayers.from(projectStore.readSettings(project).collisionMatrix())));
        applyProjectQuality();
    }

    private void applyProjectQuality() {
        ProjectQuality quality = projectStore.readQuality(project);
        fixedTimestepSeconds = quality.fixedTimestepSeconds();
        Optional.ofNullable(engine().systems().get(PhysicsSystem.class)).ifPresent(physics ->
                physics.setGravity(quality.gravityX(), quality.gravityY(), quality.gravityZ()));
        engine().inputActions().replaceAll(projectStore.readInputActions(project));
    }

    private void warnIfNoActiveCamera() {
        Scene scene = playingDocument.scene();
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject.getComponent(Camera3D.class).filter(Camera3D::active).isPresent()) {
                return;
            }
        }
            notifier.show(I18n.translate(TextKey.EDITOR_EMBEDDED_PLAY_SESSION_TOAST_NO_ACTIVE_CAMERA));
    }

    private void dispatchLifecycle(String hookName, Consumer<IComponent> hook) {
        for (GameObject gameObject : new ArrayList<>(playingDocument.scene().gameObjects())) {
            for (IComponent component : new ArrayList<>(gameObject.components())) {
                dispatchSafely(hookName, hook, component);
            }
        }
    }

    private void dispatchSafely(String hookName, Consumer<IComponent> hook, IComponent component) {
        try {
            hook.accept(component);
        } catch (RuntimeException error) {
            console.error("[play] " + hookName + " failed for " + component.getClass().getName() + ": " + error);
        }
    }

    private EpysiaEngine engine() {
        return sceneHost.engine();
    }
}
