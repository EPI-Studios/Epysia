package fr.epistudio.epysia.render.lighting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.LightProbeVolume;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;

import java.util.Optional;

public final class ProbeRefreshSystem implements GameSystem {

    private static final int FACE_SIZE = 32;

    private final float[][] faceRadiance = new float[CubeCaptureFace.COUNT][FACE_SIZE * FACE_SIZE * 3];
    private final Vector3f scratchProbePosition = new Vector3f();
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final ProbeOrder order = new ProbeOrder();

    private EpysiaEngine engine;
    private ProbeRadianceCapture capture;
    private int probeCursor;
    private int faceCursor;
    private long lastRefreshNanos;

    @Override
    public void initialize(EngineServices services) {
        engine = services instanceof EpysiaEngine actual ? actual : null;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        refresh(scene, cameraPosition(scene));
    }

    public boolean hasWork(Scene scene) {
        return engine != null && scene != null
                && refreshingVolume(scene).flatMap(LightProbeVolume::bakedProbes).isPresent();
    }

    public void refresh(Scene scene, Vector3f viewerPosition) {
        if (engine == null || scene == null) {
            return;
        }
        Optional<LightProbeVolume> volume = refreshingVolume(scene);
        if (volume.isEmpty()) {
            return;
        }
        Optional<BakedProbes> probes = volume.get().bakedProbes();
        if (probes.isEmpty()) {
            return;
        }
        if (!intervalElapsed(volume.get())) {
            return;
        }
        order.rebuildIfNeeded(probes.get(), viewerPosition);
        refreshFaces(scene, probes.get(), volume.get());
    }

    private boolean intervalElapsed(LightProbeVolume volume) {
        long now = System.nanoTime();
        if (now - lastRefreshNanos < volume.refreshIntervalNanos()) {
            return false;
        }
        lastRefreshNanos = now;
        return true;
    }

    private static Optional<LightProbeVolume> refreshingVolume(Scene scene) {
        for (LightProbeVolume candidate : scene.componentsOf(LightProbeVolume.class)) {
            if (candidate.realtimeRefresh()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Vector3f cameraPosition(Scene scene) {
        for (Camera3D camera : scene.componentsOf(Camera3D.class)) {
            if (camera.active()) {
                return camera.position(scratchCameraPosition);
            }
        }
        return scratchCameraPosition.set(0.0f, 0.0f, 0.0f);
    }

    private void refreshFaces(Scene scene, BakedProbes probes, LightProbeVolume volume) {
        ensureCapture();
        long deadline = System.nanoTime() + volume.refreshBudgetNanos();
        try {
            capture.bindStages(engine, scene.clearColor());
            for (int face = 0; face < volume.refreshFacesPerFrame(); face++) {
                captureNextFace(probes);
                if (System.nanoTime() >= deadline) {
                    return;
                }
            }
        } finally {
            restoreStages();
        }
    }

    private void captureNextFace(BakedProbes probes) {
        int probeIndex = order.probeAt(probeCursor, probes.probeCount());
        probePosition(probes, probeIndex, scratchProbePosition);
        capture.captureFace(engine, scratchProbePosition, faceCursor, faceRadiance[faceCursor]);
        faceCursor++;
        if (faceCursor < CubeCaptureFace.COUNT) {
            return;
        }
        faceCursor = 0;
        publishProbe(probeIndex);
        probeCursor = (probeCursor + 1) % Math.max(1, probes.probeCount());
    }

    private void publishProbe(int probeIndex) {
        float[] coefficients = SphericalHarmonics.project(faceRadiance, FACE_SIZE);
        engine.renderSystem(MeshRenderSystem.class).writeProbeCoefficients(probeIndex, coefficients);
    }

    private static void probePosition(BakedProbes probes, int probeIndex, Vector3f destination) {
        float[] positions = probes.positions();
        destination.set(positions[probeIndex * 3], positions[probeIndex * 3 + 1],
                positions[probeIndex * 3 + 2]);
    }

    private void ensureCapture() {
        if (capture == null) {
            capture = new ProbeRadianceCapture(engine.renderBackend(), FACE_SIZE);
        }
    }

    private void restoreStages() {
        if (engine.hasRenderSystem(PostProcessSystem.class)) {
            engine.renderSystem(PostProcessSystem.class).rebindStageTargets();
            return;
        }
        engine.bindStageTarget(RenderPasses.OPAQUE_3D, RenderTargetHandle.SCREEN,
                PassClear.color(0.10f, 0.12f, 0.18f));
        engine.bindStageTarget(RenderPasses.TRANSPARENT_3D, RenderTargetHandle.SCREEN, PassClear.none());
    }

    @Override
    public void shutdown() {
        if (capture != null) {
            capture.destroy();
            capture = null;
        }
    }
}
