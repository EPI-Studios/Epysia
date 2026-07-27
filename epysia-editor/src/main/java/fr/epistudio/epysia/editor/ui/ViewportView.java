package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.builtin.InstantiatePrefabCommand;
import fr.epistudio.epysia.editor.command.builtin.TransformDragCommand;
import fr.epistudio.epysia.editor.gizmo.ColliderWireframeOverlay;
import fr.epistudio.epysia.editor.gizmo.GridOverlay;
import fr.epistudio.epysia.editor.gizmo.SelectionOutlineOverlay;
import fr.epistudio.epysia.editor.gl.GlStateSnapshot;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.importer.AssetImportPipeline;
import fr.epistudio.epysia.editor.importer.ImportOutcome;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.play.EmbeddedPlaySession;
import fr.epistudio.epysia.editor.runtime.EditorCamera;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.physics.PhysicsSystem;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import java.util.List;

public final class ViewportView {

    public static final String WINDOW_TITLE = "Viewport";

    public enum ViewMode { SCENE, GAME }

    private static final int DEFAULT_SUPERSAMPLE_FACTOR = 2;
    private static final int MINIMUM_SUPERSAMPLE_FACTOR = 1;
    private static final int MAXIMUM_SUPERSAMPLE_FACTOR = 2;
    private static final float FALLBACK_SPAWN_DISTANCE = 4.0f;
    private static final float RAYCAST_MAX_DISTANCE = 1000.0f;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    private static final float PLAY_BORDER_THICKNESS = 2.0f;
    private static final float PREVIEW_WIDTH_FRACTION = 0.28f;
    private static final float PREVIEW_ASPECT = 16.0f / 9.0f;
    private static final float PREVIEW_MARGIN = 12.0f;
    private static final float PREVIEW_ROUNDING = 6.0f;
    private static final float BILLBOARD_HALF_SIZE = 11.0f;
    private static final float BILLBOARD_CLICK_RADIUS = 14.0f;
    private static final float BILLBOARD_SHADOW_RADIUS = 14.0f;
    private static final int BILLBOARD_SHADOW_COLOR = 0x78000000;
    private static final float GIZMO_SIZE_CLIP_SPACE = 0.14f;
    private static final float MINIMUM_THICKNESS_MULTIPLIER = 0.5f;
    private static final float MAXIMUM_THICKNESS_MULTIPLIER = 3.0f;
    private static final float MINIMUM_GRID_FADE_DISTANCE = 10.0f;
    private static final float MAXIMUM_GRID_FADE_DISTANCE = 200.0f;
    private static final float AXIS_INDICATOR_SIZE = 96.0f;
    private static final float AXIS_INDICATOR_MARGIN = 8.0f;
    private static final float FRAME_MINIMUM_RADIUS = 1.0f;
    private static final String CONTEXT_POPUP = "##viewport-context";
    private static final float CONTEXT_MENU_DRAG_TOLERANCE = 4.0f;

    private final EditorScene3DHost sceneHost;
    private final EditorCamera editorCamera;
    private final Supplier<SceneDocument> activeDocument;
    private final GizmoState gizmoState;
    private final long windowHandle;
    private final EmbeddedPlaySession playSession;
    private final IconWidgets icons;
    private final GameObjectFactory objectFactory;
    private final AssetImportPipeline importPipeline;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final float[] viewArray = new float[16];
    private final float[] manipulatedViewArray = new float[16];
    private final float[] projectionArray = new float[16];
    private final float[] modelArray = new float[16];
    private final float[] snapArray = new float[3];
    private final Vector3f dragStartPosition = new Vector3f();
    private final Quaternionf dragStartRotation = new Quaternionf();
    private final Vector3f dragStartScale = new Vector3f();
    private GridOverlay gridOverlay;
    private ColliderWireframeOverlay colliderOverlay;
    private SelectionOutlineOverlay selectionOverlay;
    private Transform3D dragTransform;
    private boolean showGrid = true;
    private boolean showColliderWireframes;
    private float overlayThicknessMultiplier = 1.0f;
    private float gridFadeDistance = GridOverlay.DEFAULT_MINOR_FADE_DISTANCE;
    private int supersampleFactor = DEFAULT_SUPERSAMPLE_FACTOR;
    private boolean viewportHoveredThisFrame;
    private boolean billboardClickConsumed;
    private ViewMode viewMode = ViewMode.SCENE;

    public ViewportView(EditorScene3DHost sceneHost, EditorCamera editorCamera,
                        Supplier<SceneDocument> activeDocument, GizmoState gizmoState, long windowHandle,
                        EmbeddedPlaySession playSession, IconWidgets icons, GameObjectFactory objectFactory,
                        AssetImportPipeline importPipeline) {
        this.sceneHost = sceneHost;
        this.editorCamera = editorCamera;
        this.activeDocument = activeDocument;
        this.gizmoState = gizmoState;
        this.windowHandle = windowHandle;
        this.playSession = playSession;
        this.icons = icons;
        this.objectFactory = objectFactory;
        this.importPipeline = importPipeline;
    }

    public boolean showGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean show) {
        showGrid = show;
    }

    public boolean showColliderWireframes() {
        return showColliderWireframes;
    }

    public void setShowColliderWireframes(boolean show) {
        showColliderWireframes = show;
    }

    public void setOverlayThicknessMultiplier(float multiplier) {
        overlayThicknessMultiplier = Math.clamp(multiplier,
                MINIMUM_THICKNESS_MULTIPLIER, MAXIMUM_THICKNESS_MULTIPLIER);
    }

    public void setGridFadeDistance(float distance) {
        gridFadeDistance = Math.clamp(distance, MINIMUM_GRID_FADE_DISTANCE, MAXIMUM_GRID_FADE_DISTANCE);
    }

    public int supersampleFactor() {
        return supersampleFactor;
    }

    public void setSupersampleFactor(int factor) {
        supersampleFactor = Math.clamp(factor, MINIMUM_SUPERSAMPLE_FACTOR, MAXIMUM_SUPERSAMPLE_FACTOR);
    }

    public boolean isHovered() {
        return viewportHoveredThisFrame;
    }

    public ViewMode viewMode() {
        return viewMode;
    }

    public void render(float deltaSeconds) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        boolean visible = ImGui.begin(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_TITLE, WINDOW_TITLE), WINDOW_FLAGS);
        ImGui.popStyleVar();
        if (!visible) {
            samplePlayInput(0.0f, 0.0f, false);
            ImGui.end();
            return;
        }
        renderContent(deltaSeconds);
        ImGui.end();
    }

    private void renderContent(float deltaSeconds) {
        int width = Math.max(1, (int) ImGui.getContentRegionAvailX());
        int height = Math.max(1, (int) ImGui.getContentRegionAvailY());
        float imageX = ImGui.getCursorScreenPosX();
        float imageY = ImGui.getCursorScreenPosY();
        boolean gameView = playSession.isActive() && viewMode == ViewMode.GAME;
        billboardClickConsumed = false;
        drawSceneImage(width, height, gameView);
        viewportHoveredThisFrame = ImGui.isItemHovered();
        acceptAssetDrops(imageX, imageY, width, height);
        renderSceneModeContent(deltaSeconds, imageX, imageY, width, height, gameView);
        renderPlayDecorations(imageX, imageY, width, height);
        renderCameraPreview(imageX, imageY, width, height);
        renderContextMenu(imageX, imageY, width, height);
        samplePlayInput(imageX, imageY, gameView || viewportHoveredThisFrame);
    }

    private void renderSceneModeContent(float deltaSeconds, float imageX, float imageY,
                                        int width, int height, boolean gameView) {
        if (gameView) {
            return;
        }
        drawOverlays(imageX, imageY, width, height);
        boolean gizmoBusy = playSession.isActive() ? false : renderGizmo(imageX, imageY, width, height);
        renderAxisIndicator(imageX, imageY, width);
        renderBillboards(imageX, imageY, width, height);
        updateCamera(deltaSeconds, imageX, imageY, width, height);
        handleFrameShortcut();
        handlePicking(gizmoBusy, imageX, imageY, width, height);
    }

    private void drawSceneImage(int width, int height, boolean gameView) {
        int renderWidth = width * supersampleFactor;
        int renderHeight = height * supersampleFactor;
        float alpha = renderAlpha();
        int textureId = gameView
                ? renderGameViewTexture(renderWidth, renderHeight, alpha)
                : sceneHost.renderFrame(editorCamera, renderWidth, renderHeight, alpha);
        ImGui.image(textureId, width, height, 0.0f, 1.0f, 1.0f, 0.0f);
    }

    private float renderAlpha() {
        return playSession.isPlaying() ? playSession.interpolationAlpha() : Camera3D.CURRENT_STATE_ALPHA;
    }

    private int renderGameViewTexture(int renderWidth, int renderHeight, float alpha) {
        Optional<Camera3D> gameCamera = playSession.gameCamera();
        if (gameCamera.isPresent()) {
            try {
                return sceneHost.renderFrameFrom(gameCamera.get(), renderWidth, renderHeight, alpha);
            } catch (RuntimeException cameraUnusable) {
                return sceneHost.renderFrame(editorCamera, renderWidth, renderHeight, alpha);
            }
        }
        return sceneHost.renderFrame(editorCamera, renderWidth, renderHeight, alpha);
    }

    private void drawOverlays(float imageX, float imageY, int width, int height) {
        if (showGrid) {
            drawGridOverlay(imageX, imageY, width, height);
        }
        if (showColliderWireframes) {
            drawColliderOverlay(imageX, imageY, width, height);
        }
        drawSelectionOutline(imageX, imageY, width, height);
    }

    private float overlayThicknessScale() {
        return supersampleFactor * overlayThicknessMultiplier;
    }

    private void drawGridOverlay(float imageX, float imageY, int width, int height) {
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            if (gridOverlay == null) {
                gridOverlay = new GridOverlay();
            }
            gridOverlay.render(editorCamera.camera().viewProjection(),
                    editorCamera.camera().position(new Vector3f()),
                    width * supersampleFactor, height * supersampleFactor,
                    overlayThicknessScale(), gridFadeDistance);
        } finally {
            snapshot.restore();
        }
        addOverlayImage(gridOverlay.textureId(), imageX, imageY, width, height);
    }

    private void drawColliderOverlay(float imageX, float imageY, int width, int height) {
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            if (colliderOverlay == null) {
                colliderOverlay = new ColliderWireframeOverlay();
            }
            colliderOverlay.render(activeDocument.get().scene(), editorCamera.camera().viewProjection(),
                    editorCamera.camera().position(new Vector3f()),
                    width * supersampleFactor, height * supersampleFactor, overlayThicknessScale());
        } finally {
            snapshot.restore();
        }
        addOverlayImage(colliderOverlay.textureId(), imageX, imageY, width, height);
    }

    private void drawSelectionOutline(float imageX, float imageY, int width, int height) {
        if (activeDocument.get().selection().count() == 0) {
            return;
        }
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            if (selectionOverlay == null) {
                selectionOverlay = new SelectionOutlineOverlay();
            }
            selectionOverlay.render(activeDocument.get().selection().all(),
                    editorCamera.camera().viewProjection(),
                    editorCamera.camera().position(new Vector3f()),
                    width * supersampleFactor, height * supersampleFactor, overlayThicknessScale());
        } finally {
            snapshot.restore();
        }
        addOverlayImage(selectionOverlay.textureId(), imageX, imageY, width, height);
    }

    private void addOverlayImage(int textureId, float imageX, float imageY, int width, int height) {
        ImGui.getWindowDrawList().addImage(textureId, imageX, imageY, imageX + width, imageY + height,
                0.0f, 1.0f, 1.0f, 0.0f);
    }

    private void acceptAssetDrops(float imageX, float imageY, int width, int height) {
        if (playSession.isActive() || !ImGui.beginDragDropTarget()) {
            return;
        }
        try {
            handleAssetDrops(imageX, imageY, width, height);
        } catch (RuntimeException error) {
            sceneHost.engine().logger().error("[ViewportView] Asset drop failed", error);
        } finally {
            ImGui.endDragDropTarget();
        }
    }

    private void handleAssetDrops(float imageX, float imageY, int width, int height) {
        Vector3f dropPoint = dropPointAtMouse(imageX, imageY, width, height);
        String prefabPath = ImGui.acceptDragDropPayload(AssetMimeTypes.PREFAB, String.class);
        if (prefabPath != null) {
            activeDocument.get().history().execute(new InstantiatePrefabCommand(Path.of(prefabPath), dropPoint));
        }
        String meshPath = ImGui.acceptDragDropPayload(AssetMimeTypes.MESH, String.class);
        if (meshPath == null) {
            return;
        }
        Path meshFile = Path.of(meshPath);
        if (importPipeline.importerFor(meshFile).isPresent()) {
            instantiateImported(meshFile, dropPoint);
            return;
        }
        objectFactory.createMesh(meshPath, meshBaseName(meshPath), dropPoint);
    }

    private void instantiateImported(Path source, Vector3f dropPoint) {
        importPipeline.ensureImported(source)
                .flatMap(ImportOutcome::instantiable)
                .ifPresent(prefab -> activeDocument.get().history()
                        .execute(new InstantiatePrefabCommand(prefab, dropPoint)));
    }

    private static String meshBaseName(String meshPath) {
        if (meshPath.startsWith("preset:")) {
            String preset = meshPath.substring("preset:".length());
            return preset.isEmpty() ? "Mesh" : Character.toUpperCase(preset.charAt(0)) + preset.substring(1);
        }
        String fileName = Path.of(meshPath).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private Vector3f dropPointAtMouse(float imageX, float imageY, int width, int height) {
        Vector3f origin = editorCamera.camera().position(new Vector3f());
        Vector3f direction = mouseRayDirection(imageX, imageY, width, height);
        Optional<Vector3f> physicsHit = raycastPhysics(origin, direction);
        if (physicsHit.isPresent()) {
            return physicsHit.get();
        }
        return groundPlaneIntersection(origin, direction)
                .orElseGet(() -> origin.add(direction.mul(FALLBACK_SPAWN_DISTANCE, new Vector3f()), new Vector3f()));
    }

    private Optional<Vector3f> raycastPhysics(Vector3f origin, Vector3f direction) {
        return Optional.ofNullable(sceneHost.engine().systems().get(PhysicsSystem.class))
                .flatMap(physics -> physics.raycast(origin, direction, RAYCAST_MAX_DISTANCE))
                .map(hit -> new Vector3f(hit.point()));
    }

    private static Optional<Vector3f> groundPlaneIntersection(Vector3f origin, Vector3f direction) {
        if (Math.abs(direction.y) < 1.0e-6f) {
            return Optional.empty();
        }
        float t = -origin.y / direction.y;
        if (t <= 0.0f || t > RAYCAST_MAX_DISTANCE) {
            return Optional.empty();
        }
        return Optional.of(new Vector3f(direction).mul(t).add(origin));
    }

    private Vector3f mouseRayDirection(float imageX, float imageY, int width, int height) {
        float ndcX = ((ImGui.getMousePosX() - imageX) / width) * 2.0f - 1.0f;
        float ndcY = 1.0f - ((ImGui.getMousePosY() - imageY) / height) * 2.0f;
        Matrix4f inverse = new Matrix4f(editorCamera.camera().viewProjection()).invert();
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        Vector3f nearPoint = new Vector3f(near.x / near.w, near.y / near.w, near.z / near.w);
        Vector3f farPoint = new Vector3f(far.x / far.w, far.y / far.w, far.z / far.w);
        return farPoint.sub(nearPoint).normalize();
    }

    private boolean renderGizmo(float imageX, float imageY, int width, int height) {
        Optional<Integer> operation = gizmoState.operation();
        Optional<Transform3D> transform = selectedTransform();
        if (operation.isEmpty() || transform.isEmpty()) {
            finishDragIfReleased();
            return false;
        }
        prepareGizmoFrame(imageX, imageY, width, height);
        manipulate(transform.get(), operation.get());
        return ImGuizmo.isUsing() || ImGuizmo.isOver();
    }

    private Optional<Transform3D> selectedTransform() {
        return activeDocument.get().selection().get()
                .flatMap(gameObject -> gameObject.getComponent(Transform3D.class));
    }

    private void prepareGizmoFrame(float imageX, float imageY, int width, int height) {
        ImGuizmo.setOrthographic(false);
        ImGuizmo.setDrawList();
        ImGuizmo.setGizmoSizeClipSpace(GIZMO_SIZE_CLIP_SPACE);
        ImGuizmo.setRect(imageX, imageY, width, height);
        editorCamera.viewMatrix(viewMatrix).get(viewArray);
        editorCamera.projectionMatrix(projectionMatrix).get(projectionArray);
    }

    private void manipulate(Transform3D transform, int operation) {
        new Matrix4f(transform.worldMatrix()).get(modelArray);
        boolean wasUsing = dragTransform != null;
        if (snapActive()) {
            fillSnapArray();
            ImGuizmo.manipulate(viewArray, projectionArray, operation, gizmoState.mode(), modelArray, null, snapArray);
        } else {
            ImGuizmo.manipulate(viewArray, projectionArray, operation, gizmoState.mode(), modelArray, null);
        }
        applyManipulation(transform, wasUsing);
    }

    private void applyManipulation(Transform3D transform, boolean wasUsing) {
        boolean usingNow = ImGuizmo.isUsing();
        if (usingNow && !wasUsing) {
            captureDragStart(transform);
        }
        if (usingNow) {
            writeWorldMatrix(transform, new Matrix4f().set(modelArray));
        }
        if (!usingNow && wasUsing) {
            commitDrag(transform);
        }
    }

    private void finishDragIfReleased() {
        if (dragTransform != null && !ImGuizmo.isUsing()) {
            commitDrag(dragTransform);
        }
    }

    private boolean snapActive() {
        return gizmoState.snapEnabled() != ImGui.getIO().getKeyCtrl();
    }

    private void fillSnapArray() {
        float step = gizmoState.snapStep();
        snapArray[0] = step;
        snapArray[1] = step;
        snapArray[2] = step;
    }

    private void captureDragStart(Transform3D transform) {
        dragTransform = transform;
        dragStartPosition.set(transform.position());
        dragStartRotation.set(transform.rotation());
        dragStartScale.set(transform.scale());
    }

    private void writeWorldMatrix(Transform3D transform, Matrix4f world) {
        Matrix4f local = transform.parent()
                .map(parent -> new Matrix4f(parent.worldMatrix()).invert().mul(world))
                .orElse(world);
        Vector3f position = local.getTranslation(new Vector3f());
        Quaternionf rotation = local.getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f scale = local.getScale(new Vector3f());
        transform.setPosition(position.x, position.y, position.z);
        transform.setRotation(rotation);
        transform.setScale(scale.x, scale.y, scale.z);
        transform.markDirty();
    }

    private void commitDrag(Transform3D transform) {
        if (dragTransform != transform) {
            dragTransform = null;
            return;
        }
        Vector3f afterPosition = new Vector3f(transform.position());
        Quaternionf afterRotation = new Quaternionf(transform.rotation());
        Vector3f afterScale = new Vector3f(transform.scale());
        dragTransform = null;
        if (afterPosition.equals(dragStartPosition) && afterRotation.equals(dragStartRotation)
                && afterScale.equals(dragStartScale)) {
            return;
        }
        rewindAndExecute(transform, afterPosition, afterRotation, afterScale);
    }

    private void rewindAndExecute(Transform3D transform, Vector3f afterPosition,
                                  Quaternionf afterRotation, Vector3f afterScale) {
        TransformDragCommand command = new TransformDragCommand(transform,
                dragStartPosition, dragStartRotation, dragStartScale,
                afterPosition, afterRotation, afterScale);
        transform.setPosition(dragStartPosition.x, dragStartPosition.y, dragStartPosition.z);
        transform.setRotation(dragStartRotation);
        transform.setScale(dragStartScale.x, dragStartScale.y, dragStartScale.z);
        transform.markDirty();
        activeDocument.get().history().execute(command);
    }

    private void renderAxisIndicator(float imageX, float imageY, int width) {
        ImGuizmo.setOrthographic(false);
        ImGuizmo.setDrawList();
        editorCamera.viewMatrix(viewMatrix).get(viewArray);
        System.arraycopy(viewArray, 0, manipulatedViewArray, 0, viewArray.length);
        float indicatorX = imageX + width - AXIS_INDICATOR_SIZE - AXIS_INDICATOR_MARGIN;
        float indicatorY = imageY + AXIS_INDICATOR_MARGIN;
        ImGuizmo.viewManipulate(manipulatedViewArray, editorCamera.focusDistance(),
                indicatorX, indicatorY, AXIS_INDICATOR_SIZE, AXIS_INDICATOR_SIZE, 0x00000000);
        if (mouseOverIndicator(indicatorX, indicatorY) && viewChanged()) {
            editorCamera.applyViewMatrix(new Matrix4f().set(manipulatedViewArray));
        }
    }

    private static boolean mouseOverIndicator(float indicatorX, float indicatorY) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        return mouseX >= indicatorX && mouseX <= indicatorX + AXIS_INDICATOR_SIZE
                && mouseY >= indicatorY && mouseY <= indicatorY + AXIS_INDICATOR_SIZE;
    }

    private boolean viewChanged() {
        for (int index = 0; index < viewArray.length; index++) {
            if (Math.abs(viewArray[index] - manipulatedViewArray[index]) > 1.0e-6f) {
                return true;
            }
        }
        return false;
    }

    private void renderBillboards(float imageX, float imageY, int width, int height) {
        Matrix4f viewProjection = new Matrix4f(editorCamera.camera().viewProjection());
        for (GameObject gameObject : activeDocument.get().scene().gameObjects()) {
            billboardIconFor(gameObject).ifPresent(icon ->
                    drawBillboard(gameObject, icon, viewProjection, imageX, imageY, width, height));
        }
    }

    private Optional<EditorIcon> billboardIconFor(GameObject gameObject) {
        if (gameObject.getComponent(MeshRenderer.class).isPresent()) {
            return Optional.empty();
        }
        if (gameObject.getComponent(Camera3D.class).isPresent()) {
            return Optional.of(EditorIcon.CAMERA_3D);
        }
        if (gameObject.getComponent(DirectionalLight.class).isPresent()) {
            return Optional.of(EditorIcon.DIRECTIONAL_LIGHT_3D);
        }
        if (gameObject.getComponent(PointLight.class).isPresent()) {
            return Optional.of(EditorIcon.OMNI_LIGHT_3D);
        }
        return gameObject.getComponent(SpotLight.class).map(light -> EditorIcon.SPOT_LIGHT_3D);
    }

    private void drawBillboard(GameObject gameObject, EditorIcon icon, Matrix4f viewProjection,
                               float imageX, float imageY, int width, int height) {
        Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
        if (transform.isEmpty()) {
            return;
        }
        Vector3f world = transform.get().worldMatrix().getTranslation(new Vector3f());
        Vector4f clip = viewProjection.transform(new Vector4f(world, 1.0f));
        if (clip.w <= 0.0f) {
            return;
        }
        float screenX = imageX + (clip.x / clip.w * 0.5f + 0.5f) * width;
        float screenY = imageY + (0.5f - clip.y / clip.w * 0.5f) * height;
        if (screenX < imageX || screenX > imageX + width || screenY < imageY || screenY > imageY + height) {
            return;
        }
        ImGui.getWindowDrawList().addCircleFilled(screenX, screenY,
                BILLBOARD_SHADOW_RADIUS, BILLBOARD_SHADOW_COLOR);
        ImGui.getWindowDrawList().addImage(icons.atlasTextureId(icon),
                screenX - BILLBOARD_HALF_SIZE, screenY - BILLBOARD_HALF_SIZE,
                screenX + BILLBOARD_HALF_SIZE, screenY + BILLBOARD_HALF_SIZE);
        handleBillboardClick(gameObject, screenX, screenY);
    }

    private void handleBillboardClick(GameObject gameObject, float screenX, float screenY) {
        if (!viewportHoveredThisFrame || billboardClickConsumed
                || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        float deltaX = ImGui.getMousePosX() - screenX;
        float deltaY = ImGui.getMousePosY() - screenY;
        if (deltaX * deltaX + deltaY * deltaY <= BILLBOARD_CLICK_RADIUS * BILLBOARD_CLICK_RADIUS) {
            activeDocument.get().selection().select(gameObject);
            billboardClickConsumed = true;
        }
    }

    private void updateCamera(float deltaSeconds, float imageX, float imageY, int width, int height) {
        editorCamera.updateFraming(deltaSeconds);
        boolean rightHeld = viewportHoveredThisFrame
                && GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean orbitHeld = viewportHoveredThisFrame && !rightHeld && ImGui.getIO().getKeyAlt()
                && GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        editorCamera.updateLook(ImGui.getMousePosX(), ImGui.getMousePosY(), rightHeld);
        editorCamera.updateOrbit(ImGui.getMousePosX(), ImGui.getMousePosY(), orbitHeld);
        applyScrollNavigation(rightHeld);
        if (!rightHeld) {
            return;
        }
        ImGui.setWindowFocus();
        editorCamera.updateMovement(keyDown(GLFW.GLFW_KEY_W), keyDown(GLFW.GLFW_KEY_S),
                keyDown(GLFW.GLFW_KEY_A), keyDown(GLFW.GLFW_KEY_D),
                keyDown(GLFW.GLFW_KEY_SPACE), keyDown(GLFW.GLFW_KEY_LEFT_SHIFT),
                keyDown(GLFW.GLFW_KEY_LEFT_CONTROL), deltaSeconds);
    }

    private void applyScrollNavigation(boolean rightHeld) {
        if (!viewportHoveredThisFrame) {
            return;
        }
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel == 0.0f) {
            return;
        }
        if (rightHeld) {
            editorCamera.applyZoom(wheel);
        } else {
            editorCamera.applyDolly(wheel);
        }
    }

    private void handleFrameShortcut() {
        if (!viewportHoveredThisFrame || ImGui.getIO().getWantTextInput()
                || !ImGui.isKeyPressed(ImGuiKey.F)) {
            return;
        }
        frameSelection();
    }

    public void frameSelection() {
        List<GameObject> selected = activeDocument.get().selection().all();
        if (selected.isEmpty()) {
            return;
        }
        Vector3f center = new Vector3f();
        int counted = 0;
        for (GameObject gameObject : selected) {
            Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
            if (transform.isPresent()) {
                center.add(transform.get().worldMatrix().getTranslation(new Vector3f()));
                counted++;
            }
        }
        if (counted == 0) {
            return;
        }
        center.div(counted);
        editorCamera.frame(center, boundsRadius(selected, center));
    }

    public void frameObject(GameObject gameObject) {
        Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
        if (transform.isEmpty()) {
            return;
        }
        Vector3f center = transform.get().worldMatrix().getTranslation(new Vector3f());
        editorCamera.frame(center, boundsRadius(List.of(gameObject), center));
    }

    private static float boundsRadius(List<GameObject> gameObjects, Vector3f center) {
        float radius = FRAME_MINIMUM_RADIUS;
        for (GameObject gameObject : gameObjects) {
            Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
            if (transform.isEmpty()) {
                continue;
            }
            Vector3f position = transform.get().worldMatrix().getTranslation(new Vector3f());
            Vector3f scale = transform.get().worldMatrix().getScale(new Vector3f());
            float objectRadius = Math.max(scale.x, Math.max(scale.y, scale.z)) * 0.87f;
            radius = Math.max(radius, center.distance(position) + objectRadius);
        }
        return radius;
    }

    private void handlePicking(boolean gizmoBusy, float imageX, float imageY, int width, int height) {
        if (!viewportHoveredThisFrame || gizmoBusy || billboardClickConsumed || ImGui.getIO().getKeyAlt()) {
            return;
        }
        boolean rightHeld = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (rightHeld || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        int localX = (int) (ImGui.getMousePosX() - imageX);
        int localY = (int) (ImGui.getMousePosY() - imageY);
        Optional<GameObject> hit = sceneHost.pickAt(editorCamera, localX, localY, width, height);
        hit.ifPresentOrElse(activeDocument.get().selection()::select, activeDocument.get().selection()::clear);
    }

    private void renderPlayDecorations(float imageX, float imageY, int width, int height) {
        if (!playSession.isActive()) {
            return;
        }
        ImGui.getWindowDrawList().addRect(imageX + 1.0f, imageY + 1.0f,
                imageX + width - 1.0f, imageY + height - 1.0f,
                EditorStyle.COLOR_ACCENT, 0.0f, 0, PLAY_BORDER_THICKNESS);
        renderViewModeButtons(imageX, imageY);
    }

    private void renderViewModeButtons(float imageX, float imageY) {
        ImGui.setCursorScreenPos(imageX + AXIS_INDICATOR_MARGIN, imageY + AXIS_INDICATOR_MARGIN);
        if (renderModeButton(TextKey.EDITOR_VIEWPORT_VIEW_SCENE, viewMode == ViewMode.SCENE)) {
            viewMode = ViewMode.SCENE;
        }
        ImGui.sameLine();
        if (renderModeButton(TextKey.EDITOR_VIEWPORT_VIEW_GAME, viewMode == ViewMode.GAME)) {
            viewMode = ViewMode.GAME;
        }
    }

    private boolean renderModeButton(TextKey key, boolean active) {
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        }
        String label = I18n.translate(key);
        boolean clicked = ImGui.button(I18n.label(key, "viewport-mode-" + key.name()));
        if (active) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_VIEWPORT_VIEW_VIEW_TOOLTIP, label));
        }
        return clicked;
    }

    private void renderCameraPreview(float imageX, float imageY, int width, int height) {
        if (playSession.isActive()) {
            return;
        }
        Optional<Camera3D> selectedCamera = selectedCamera();
        if (selectedCamera.isEmpty()) {
            return;
        }
        int previewWidth = Math.max(64, (int) (width * PREVIEW_WIDTH_FRACTION));
        int previewHeight = Math.max(36, (int) (previewWidth / PREVIEW_ASPECT));
        int textureId = sceneHost.renderPreviewFrom(selectedCamera.get(),
                previewWidth * supersampleFactor, previewHeight * supersampleFactor);
        float x0 = imageX + width - previewWidth - PREVIEW_MARGIN;
        float y0 = imageY + height - previewHeight - PREVIEW_MARGIN;
        drawPreviewFrame(textureId, x0, y0, previewWidth, previewHeight);
    }

    private void drawPreviewFrame(int textureId, float x0, float y0, int previewWidth, int previewHeight) {
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(x0 - 1.0f, y0 - 1.0f, x0 + previewWidth + 1.0f, y0 + previewHeight + 1.0f,
                EditorStyle.COLOR_OUTLINE, PREVIEW_ROUNDING);
        drawList.addImageRounded(textureId, x0, y0, x0 + previewWidth, y0 + previewHeight,
                0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF, PREVIEW_ROUNDING);
        drawList.addRect(x0, y0, x0 + previewWidth, y0 + previewHeight,
                EditorStyle.COLOR_ACCENT, PREVIEW_ROUNDING, 0, 1.5f);
        drawList.addText(x0 + 8.0f, y0 + 6.0f, EditorStyle.COLOR_TEXT,
                I18n.translate(TextKey.EDITOR_VIEWPORT_VIEW_CAMERA_PREVIEW));
    }

    private Optional<Camera3D> selectedCamera() {
        return activeDocument.get().selection().get()
                .flatMap(gameObject -> gameObject.getComponent(Camera3D.class));
    }

    private void renderContextMenu(float imageX, float imageY, int width, int height) {
        if (playSession.isActive()) {
            return;
        }
        if (shouldOpenContextMenu()) {
            ImGui.openPopup(CONTEXT_POPUP);
        }
        if (!ImGui.beginPopup(CONTEXT_POPUP)) {
            return;
        }
        Vector3f spawn = dropPointAtMouse(imageX, imageY, width, height);
        if (ImGui.beginMenu(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_CREATE, "viewport-context-create"))) {
            renderCreateMenuItems(spawn);
            ImGui.endMenu();
        }
        ImGui.endPopup();
    }

    private boolean shouldOpenContextMenu() {
        if (!ImGui.isWindowHovered() || !ImGui.isMouseReleased(ImGuiMouseButton.Right)) {
            return false;
        }
        float draggedX = Math.abs(ImGui.getMouseDragDeltaX(ImGuiMouseButton.Right));
        float draggedY = Math.abs(ImGui.getMouseDragDeltaY(ImGuiMouseButton.Right));
        return Math.max(draggedX, draggedY) <= CONTEXT_MENU_DRAG_TOLERANCE;
    }

    private void renderCreateMenuItems(Vector3f spawn) {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_CUBE, "viewport-context-cube"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CUBE, spawn);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_PLANE, "viewport-context-plane"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.PLANE, spawn);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_CAPSULE, "viewport-context-capsule"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CAPSULE, spawn);
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_POINT_LIGHT, "viewport-context-point-light"))) {
            objectFactory.createPointLight(spawn);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_SPOT_LIGHT, "viewport-context-spot-light"))) {
            objectFactory.createSpotLight(spawn);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_DIRECTIONAL_LIGHT,
                "viewport-context-directional-light"))) {
            objectFactory.createDirectionalLight(spawn);
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_CAMERA, "viewport-context-camera"))) {
            objectFactory.createCamera(spawn);
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_EMPTY, "viewport-context-empty"))) {
            objectFactory.createEmpty(spawn);
        }
    }

    private void samplePlayInput(float imageX, float imageY, boolean active) {
        boolean inputActive = active && playSession.isActive()
                && (ImGui.isWindowFocused() || viewportHoveredThisFrame);
        playSession.inputSampler().sample(windowHandle, inputActive,
                ImGui.getMousePosX() - imageX, ImGui.getMousePosY() - imageY,
                inputActive ? ImGui.getIO().getMouseWheel() : 0.0f);
    }

    private boolean keyDown(int glfwKey) {
        return GLFW.glfwGetKey(windowHandle, glfwKey) == GLFW.GLFW_PRESS;
    }

    public void dispose() {
        if (gridOverlay != null) {
            gridOverlay.close();
            gridOverlay = null;
        }
        if (colliderOverlay != null) {
            colliderOverlay.close();
            colliderOverlay = null;
        }
        if (selectionOverlay != null) {
            selectionOverlay.close();
            selectionOverlay = null;
        }
    }
}
