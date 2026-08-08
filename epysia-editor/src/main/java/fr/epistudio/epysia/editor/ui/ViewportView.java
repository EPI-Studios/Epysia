package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.builtin.InstantiatePrefabCommand;
import fr.epistudio.epysia.editor.tilemap.TilePaintController;
import fr.epistudio.epysia.editor.command.builtin.Transform2DDragCommand;
import fr.epistudio.epysia.editor.command.builtin.TransformDragCommand;
import fr.epistudio.epysia.editor.command.CompositeCommand;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.gizmo.ColliderWireframeOverlay;
import org.joml.Vector3fc;
import fr.epistudio.epysia.editor.gizmo.PhysicsDebugOverlay;
import fr.epistudio.epysia.editor.gizmo.GizmoFollowers;
import fr.epistudio.epysia.editor.gizmo.LightDirectionOverlay;
import fr.epistudio.epysia.editor.gizmo.GridOverlay;
import fr.epistudio.epysia.editor.gizmo.GridOverlay2D;
import fr.epistudio.epysia.editor.gizmo.SelectionOutlineOverlay;
import fr.epistudio.epysia.editor.gizmo.SelectionSilhouetteOverlay;
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
import fr.epistudio.epysia.render.backend.TextureHandle;
import imgui.ImDrawList;
import imgui.flag.ImGuiFocusedFlags;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Operation;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private static final float PREVIEW_LABEL_INSET_X = 8.0f;
    private static final float PREVIEW_LABEL_INSET_Y = 6.0f;
    private static final float PREVIEW_PIN_SIZE = 12.0f;
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
    private static final int COLOR_PAINT_CURSOR = 0xFFCC7A00;
    private static final int COLOR_PAINT_RECTANGLE_FILL = 0x330077CC;
    private static final int COLOR_PAINT_RECTANGLE_BORDER = 0xFF3399DD;


    private final EditorScene3DHost sceneHost;
    private final EditorCamera editorCamera;
    private final Supplier<SceneDocument> activeDocument;
    private final GizmoState gizmoState;
    private final long windowHandle;
    private final EmbeddedPlaySession playSession;
    private final IconWidgets icons;
    private final GameObjectFactory objectFactory;
    private final AssetImportPipeline importPipeline;
    private final TilePalettePanel tilePalette;
    private final TilePaintController paintController;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final float[] viewArray = new float[16];
    private final float[] manipulatedViewArray = new float[16];
    private final float[] projectionArray = new float[16];
    private final float[] modelArray = new float[16];
    private static final float SMART_SNAP_TOLERANCE_FRACTION = 0.02f;
    private static final float SMART_SNAP_GUIDE_OVERSHOOT = 2.0f;
    private static final float SMART_SNAP_GUIDE_THICKNESS = 1.0f;
    private static final int COLOR_SMART_SNAP_GUIDE = 0xFF33CCFF;

    private final float[] snapArray = new float[3];
    private final GizmoFollowers followers = new GizmoFollowers();
    private final PanelTimings timings = new PanelTimings();
    private final LightDirectionOverlay lightDirectionOverlay = new LightDirectionOverlay();
    private List<SmartSnap.Guide> smartSnapGuides = List.of();
    private final Vector3f dragStartPosition = new Vector3f();
    private final Quaternionf dragStartRotation = new Quaternionf();
    private final Vector3f dragStartScale = new Vector3f();
    private final Vector2f dragStartPlanarPosition = new Vector2f();
    private final Vector2f dragStartPlanarScale = new Vector2f();
    private float dragStartPlanarRotation;
    private GridOverlay gridOverlay;
    private ColliderWireframeOverlay colliderOverlay;
    private final UiViewportEditor uiEditor = new UiViewportEditor();
    private boolean uiEditingEnabled = true;
    private boolean uiDragBusy;
    private SelectionOutlineOverlay selectionOverlay;
    private SelectionSilhouetteOverlay selectionSilhouette;
    private Transform3D dragTransform;
    private final PivotHandle pivotHandle = new PivotHandle();
    private Transform2D dragPlanarTransform;
    private boolean showGrid = true;
    private boolean showColliderWireframes;
    private boolean showPhysicsDebug;
    private final PhysicsDebugOverlay.Options physicsDebugOptions = new PhysicsDebugOverlay.Options();
    private boolean showTileCollision;
    private float overlayThicknessMultiplier = 1.0f;
    private float gridFadeDistance = GridOverlay.DEFAULT_MINOR_FADE_DISTANCE;
    private int supersampleFactor = DEFAULT_SUPERSAMPLE_FACTOR;
    private int renderedWidth;
    private int renderedHeight;
    private Optional<UUID> pinnedCameraId = Optional.empty();
    private boolean previewClickConsumed;
    private boolean viewportHoveredThisFrame;
    private boolean viewportFocusedThisFrame;
    private boolean billboardClickConsumed;
    private boolean paintingActiveThisFrame;
    private boolean paintEnabled;
    private ViewMode viewMode = ViewMode.SCENE;

    public ViewportView(EditorScene3DHost sceneHost, EditorCamera editorCamera,
                        Supplier<SceneDocument> activeDocument, GizmoState gizmoState, long windowHandle,
                        EmbeddedPlaySession playSession, IconWidgets icons, GameObjectFactory objectFactory,
                        AssetImportPipeline importPipeline, TilePalettePanel tilePalette) {
        this.sceneHost = sceneHost;
        this.editorCamera = editorCamera;
        this.activeDocument = activeDocument;
        this.gizmoState = gizmoState;
        this.windowHandle = windowHandle;
        this.playSession = playSession;
        this.icons = icons;
        this.objectFactory = objectFactory;
        this.importPipeline = importPipeline;
        this.tilePalette = tilePalette;
        this.paintController = new TilePaintController(tilePalette.brush(),
                command -> activeDocument.get().history().execute(command));
    }

    public boolean paintEnabled() {
        return paintEnabled;
    }

    public void setPaintEnabled(boolean enabled) {
        paintEnabled = enabled;
    }

    public void enablePainting() {
        paintEnabled = true;
    }

    public boolean showTileCollision() {
        return showTileCollision;
    }

    public void setShowTileCollision(boolean show) {
        showTileCollision = show;
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

    public int renderedWidth() {
        return renderedWidth;
    }

    public int renderedHeight() {
        return renderedHeight;
    }

    public void setSupersampleFactor(int factor) {
        supersampleFactor = Math.clamp(factor, MINIMUM_SUPERSAMPLE_FACTOR, MAXIMUM_SUPERSAMPLE_FACTOR);
    }

    public boolean isHovered() {
        return viewportHoveredThisFrame;
    }

    public boolean acceptsToolHotkeys() {
        return viewportHoveredThisFrame || viewportFocusedThisFrame;
    }

    public ViewMode viewMode() {
        return viewMode;
    }

    public void render(float deltaSeconds) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        boolean visible = ImGui.begin(I18n.label(TextKey.EDITOR_VIEWPORT_VIEW_TITLE, WINDOW_TITLE), WINDOW_FLAGS);
        ImGui.popStyleVar();
        if (!visible) {
            viewportHoveredThisFrame = false;
            viewportFocusedThisFrame = false;
            samplePlayInput(0.0f, 0.0f, false);
            ImGui.end();
            return;
        }
        viewportFocusedThisFrame = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
        timings.beginFrame();
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
        timings.measure("scene image", () -> drawSceneImage(width, height, gameView));
        viewportHoveredThisFrame = ImGui.isItemHovered();
        previewClickConsumed = mouseOverCameraPreview(imageX, imageY, width, height);
        acceptAssetDrops(imageX, imageY, width, height);
        renderSceneModeContent(deltaSeconds, imageX, imageY, width, height, gameView);
        renderPlayDecorations(imageX, imageY, width, height);
        timings.measure("camera preview", () -> renderCameraPreview(imageX, imageY, width, height));
        renderContextMenu(imageX, imageY, width, height);
        samplePlayInput(imageX, imageY, gameView || viewportHoveredThisFrame);
    }

    private void renderSceneModeContent(float deltaSeconds, float imageX, float imageY,
                                        int width, int height, boolean gameView) {
        if (gameView) {
            return;
        }
        timings.measure("overlays", () -> drawOverlays(imageX, imageY, width, height));
        uiDragBusy = renderUiEditing(imageX, imageY);
        if (uiDragBusy) {
            sceneHost.requestViewportRedraw();
        }
        Optional<TilemapRenderer> paintTarget = activePaintTarget();
        paintingActiveThisFrame = paintTarget.isPresent();
        boolean gizmoBusy = renderGizmoUnlessPainting(paintTarget, imageX, imageY, width, height);
        boolean pivotBusy = renderPivotHandle(imageX, imageY, width, height);
        if (!editorCamera.twoDimensional()) {
            timings.measure("axis indicator", () -> renderAxisIndicator(imageX, imageY, width));
        }
        timings.measure("billboards", () -> renderBillboards(imageX, imageY, width, height));
        timings.measure("light gizmos", () -> renderLightDirections(imageX, imageY, width, height));
        drawSmartSnapGuides(imageX, imageY, width, height);
        paintTarget.ifPresentOrElse(
                renderer -> handleTilemapPaint(renderer, imageX, imageY, width, height),
                paintController::cancel);
        updateCamera(deltaSeconds, imageX, imageY, width, height);
        handleFrameShortcut();
        handlePicking(gizmoBusy || pivotBusy || uiDragBusy || paintTarget.isPresent(),
                imageX, imageY, width, height);
    }

    private boolean renderUiEditing(float imageX, float imageY) {
        if (!uiEditingEnabled || playSession.isActive()) {
            return false;
        }
        return uiEditor.render(activeDocument.get().scene(),
                activeDocument.get().selection().get().orElse(null),
                activeDocument.get().history(), imageX, imageY, viewportHoveredThisFrame);
    }

    public boolean uiEditingEnabled() {
        return uiEditingEnabled;
    }

    public void setUiEditingEnabled(boolean value) {
        this.uiEditingEnabled = value;
    }

    private boolean renderPivotHandle(float imageX, float imageY, int width, int height) {
        Optional<GameObject> selected = activeDocument.get().selection().get();
        Optional<Transform2D> planar = selected.flatMap(gameObject -> gameObject.getComponent(Transform2D.class));
        if (gizmoState.tool() != GizmoState.Tool.PIVOT || playSession.isActive() || planar.isEmpty()) {
            return false;
        }
        Vector3f mouse = viewportWorldOnPlane(imageX, imageY, width, height);
        PivotHandle.WorldToScreen projection = (worldX, worldY) ->
                worldToScreen(new Vector2f(worldX, worldY), imageX, imageY, width, height);
        Optional<EditorCommand> finished = pivotHandle.render(planar.get(),
                spriteHalfExtents(selected.get()), projection, new Vector2f(mouse.x, mouse.y),
                viewportHoveredThisFrame);
        finished.ifPresent(command -> activeDocument.get().history().execute(command));
        return pivotHandle.busy() || finished.isPresent();
    }

    private Vector2f spriteHalfExtents(GameObject gameObject) {
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        if (sprite == null) {
            return new Vector2f();
        }
        return sprite.texture().map(texture -> halfExtentsOf(sprite, texture)).orElseGet(Vector2f::new);
    }

    private Vector2f halfExtentsOf(SpriteRenderer sprite, TextureHandle texture) {
        float pixelWidth = sceneHost.backend().textureWidth(texture)
                * (sprite.regionMaxU() - sprite.regionMinU());
        float pixelHeight = sceneHost.backend().textureHeight(texture)
                * (sprite.regionMaxV() - sprite.regionMinV());
        float perUnit = Math.max(0.01f, sprite.pixelsPerUnit());
        return new Vector2f(Math.abs(pixelWidth) / perUnit * 0.5f, Math.abs(pixelHeight) / perUnit * 0.5f);
    }

    private boolean renderGizmoUnlessPainting(Optional<TilemapRenderer> paintTarget,
                                              float imageX, float imageY, int width, int height) {
        if (paintTarget.isPresent()) {
            return false;
        }
        return playSession.isActive() ? false : renderGizmo(imageX, imageY, width, height);
    }

    private void drawSceneImage(int width, int height, boolean gameView) {
        int[] textureId = new int[1];
        timings.measure("  render frame", () -> textureId[0] = sceneTexture(width, height, gameView));
        timings.measure("  imgui image",
                () -> ImGui.image(textureId[0], width, height, 0.0f, 1.0f, 1.0f, 0.0f));
    }

    private int sceneTexture(int width, int height, boolean gameView) {
        int factor = sceneHost.postProcessSettings().pixelPerfectEnabled() ? 1 : supersampleFactor;
        int renderWidth = width * factor;
        int renderHeight = height * factor;
        renderedWidth = renderWidth;
        renderedHeight = renderHeight;
        float alpha = renderAlpha();
        return gameView
                ? renderGameViewTexture(renderWidth, renderHeight, alpha)
                : sceneHost.renderFrame(editorCamera, renderWidth, renderHeight, alpha);
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

    public PanelTimings timings() {
        return timings;
    }

    private void drawOverlays(float imageX, float imageY, int width, int height) {
        if (showGrid && editorCamera.twoDimensional()) {
            drawTwoDimensionalGrid(imageX, imageY, width, height);
        } else if (showGrid) {
            drawGridOverlay(imageX, imageY, width, height);
        }
        if (showColliderWireframes) {
            drawColliderOverlay(imageX, imageY, width, height);
        }
        drawSelectionOutline(imageX, imageY, width, height);
        drawTileCollisionOverlay(imageX, imageY, width, height);
        drawPhysicsDebugOverlay(imageX, imageY, width, height);
    }

    public PhysicsDebugOverlay.Options physicsDebugOptions() {
        return physicsDebugOptions;
    }

    public boolean showPhysicsDebug() {
        return showPhysicsDebug;
    }

    public void setShowPhysicsDebug(boolean show) {
        showPhysicsDebug = show;
    }

    private void drawPhysicsDebugOverlay(float imageX, float imageY, int width, int height) {
        if (!showPhysicsDebug) {
            return;
        }
        Matrix4f viewProjection = new Matrix4f(editorCamera.camera().projection())
                .mul(editorCamera.camera().view());
        PhysicsDebugOverlay.draw(activeDocument.get().scene(), ImGui.getWindowDrawList(),
                worldPoint -> projectToScreen(worldPoint, viewProjection, imageX, imageY, width, height),
                physicsDebugOptions);
    }

    private static Optional<float[]> projectToScreen(Vector3fc worldPoint, Matrix4f viewProjection,
                                                     float imageX, float imageY, int width, int height) {
        Vector4f clip = viewProjection.transform(
                new Vector4f(worldPoint.x(), worldPoint.y(), worldPoint.z(), 1.0f));
        if (clip.w <= 1.0e-4f) {
            return Optional.empty();
        }
        float normalizedX = clip.x / clip.w;
        float normalizedY = clip.y / clip.w;
        return Optional.of(new float[]{
                imageX + (normalizedX * 0.5f + 0.5f) * width,
                imageY + (0.5f - normalizedY * 0.5f) * height});
    }

    private void drawTileCollisionOverlay(float imageX, float imageY, int width, int height) {
        if (!showTileCollision || !editorCamera.twoDimensional()) {
            return;
        }
        ImDrawList drawList = ImGui.getWindowDrawList();
        TilemapCollisionOverlay.LocalProjection projection = (target, localX, localY) ->
                localToScreen(target, localX, localY, imageX, imageY, width, height);
        for (GameObject gameObject : activeDocument.get().scene().gameObjects()) {
            gameObject.getComponent(Transform2D.class).ifPresent(transform ->
                    drawObjectCollision(gameObject, transform, drawList, projection));
        }
    }

    private void drawObjectCollision(GameObject gameObject, Transform2D transform, ImDrawList drawList,
                                     TilemapCollisionOverlay.LocalProjection projection) {
        tilemapOf(gameObject).ifPresent(tilemap ->
                TilemapCollisionOverlay.draw(transform, tilemap, drawList, projection));
        Collider2DOverlay.draw(gameObject, transform, drawList, projection);
    }

    private Optional<SpriteTilemap> tilemapOf(GameObject gameObject) {
        Optional<TilemapRenderer> renderer = gameObject.getComponent(TilemapRenderer.class);
        renderer.ifPresent(found -> found.refresh(sceneHost.engine()));
        return renderer.flatMap(TilemapRenderer::tilemapValue);
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

    private void drawTwoDimensionalGrid(float imageX, float imageY, int width, int height) {
        Vector3f cameraPosition = editorCamera.camera().position(new Vector3f());
        GridOverlay2D.draw(ImGui.getWindowDrawList(), imageX, imageY, width, height,
                cameraPosition.x, cameraPosition.y, editorCamera.orthographicSize());
    }

    private void drawColliderOverlay(float imageX, float imageY, int width, int height) {
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            if (colliderOverlay == null) {
                colliderOverlay = new ColliderWireframeOverlay();
            }
            colliderOverlay.useSimulatedWorld(playSession.isActive()
                    ? sceneHost.engine().gameSystem(PhysicsSystem.class)
                            .orElse(null)
                    : null);
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
        boolean silhouetteReady;
        try {
            silhouetteReady = renderSelectionLayers(width, height);
        } finally {
            snapshot.restore();
        }
        if (silhouetteReady) {
            addOverlayImage(selectionSilhouette.textureId(), imageX, imageY, width, height);
        }
        addOverlayImage(selectionOverlay.textureId(), imageX, imageY, width, height);
    }

    private boolean renderSelectionLayers(int width, int height) {
        if (selectionOverlay == null) {
            selectionOverlay = new SelectionOutlineOverlay();
        }
        if (selectionSilhouette == null) {
            selectionSilhouette = new SelectionSilhouetteOverlay();
        }
        int pixelWidth = width * supersampleFactor;
        int pixelHeight = height * supersampleFactor;
        boolean ready = selectionSilhouette.render(activeDocument.get().selection().all(),
                editorCamera.camera().viewProjection(), pixelWidth, pixelHeight,
                overlayThicknessScale(), sceneHost.jointPalettes(), sceneHost.backend());
        selectionOverlay.render(activeDocument.get().selection().all(),
                editorCamera.camera().viewProjection(),
                editorCamera.camera().position(new Vector3f()),
                pixelWidth, pixelHeight, overlayThicknessScale());
        return ready;
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
        if (operation.isEmpty()) {
            finishDragIfReleased();
            return false;
        }
        Optional<Transform3D> spatial = selectedTransform();
        if (spatial.isPresent()) {
            prepareGizmoFrame(imageX, imageY, width, height);
            manipulate(spatial.get(), operation.get());
            return ImGuizmo.isUsing() || ImGuizmo.isOver();
        }
        return renderPlanarGizmo(imageX, imageY, width, height, operation.get());
    }

    private boolean renderPlanarGizmo(float imageX, float imageY, int width, int height, int operation) {
        Optional<Transform2D> planar = selectedPlanarTransform();
        if (planar.isEmpty()) {
            finishDragIfReleased();
            return false;
        }
        prepareGizmoFrame(imageX, imageY, width, height);
        manipulatePlanar(planar.get(), operation);
        return ImGuizmo.isUsing() || ImGuizmo.isOver();
    }

    private Optional<Transform3D> selectedTransform() {
        return activeDocument.get().selection().get()
                .flatMap(gameObject -> gameObject.getComponent(Transform3D.class));
    }

    private Optional<Transform2D> selectedPlanarTransform() {
        return activeDocument.get().selection().get()
                .flatMap(gameObject -> gameObject.getComponent(Transform2D.class));
    }

    private void prepareGizmoFrame(float imageX, float imageY, int width, int height) {
        ImGuizmo.setOrthographic(editorCamera.twoDimensional());
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
            followers.capture(transform, followerTransforms(transform));
        }
        if (usingNow) {
            writeWorldMatrix(transform, new Matrix4f().set(modelArray));
            followers.follow(transform, this::writeWorldMatrix);
        }
        if (!usingNow && wasUsing) {
            commitDrag(transform);
        }
    }

    private List<Transform3D> followerTransforms(Transform3D leader) {
        List<Transform3D> selected = selectedTransforms();
        List<Transform3D> result = new ArrayList<>(selected.size());
        for (Transform3D transform : selected) {
            if (transform != leader && !hasSelectedAncestor(transform, selected)) {
                result.add(transform);
            }
        }
        return result;
    }

    private List<Transform3D> selectedTransforms() {
        List<Transform3D> transforms = new ArrayList<>();
        for (GameObject gameObject : activeDocument.get().selection().all()) {
            gameObject.getComponent(Transform3D.class).ifPresent(transforms::add);
        }
        return transforms;
    }

    private static boolean hasSelectedAncestor(Transform3D transform, List<Transform3D> selected) {
        Optional<Transform3D> walker = transform.parent();
        while (walker.isPresent()) {
            if (selected.contains(walker.get())) {
                return true;
            }
            walker = walker.get().parent();
        }
        return false;
    }

    private void manipulatePlanar(Transform2D transform, int operation) {
        planarWorldMatrix(transform).get(modelArray);
        boolean wasUsing = dragPlanarTransform != null;
        int lockedOperation = planarOperation(operation);
        if (snapActive()) {
            fillSnapArray();
            ImGuizmo.manipulate(viewArray, projectionArray, lockedOperation, gizmoState.mode(),
                    modelArray, null, snapArray);
        } else {
            ImGuizmo.manipulate(viewArray, projectionArray, lockedOperation, gizmoState.mode(), modelArray, null);
        }
        applyPlanarManipulation(transform, wasUsing);
    }

    private void applySmartSnap(Transform2D transform) {
        if (gizmoState.tool() != GizmoState.Tool.TRANSLATE || !ImGui.getIO().getKeyShift()) {
            smartSnapGuides = List.of();
            return;
        }
        Optional<GameObject> dragged = activeDocument.get().selection().get();
        if (dragged.isEmpty()) {
            return;
        }
        SmartSnap.Result result = SmartSnap.align(activeDocument.get().scene(), dragged.get(), transform,
                SMART_SNAP_TOLERANCE_FRACTION * editorCamera.orthographicSize());
        smartSnapGuides = result.guides();
        Vector2f snapped = transform.worldPosition(new Vector2f());
        transform.setWorldPosition(snapped.x + result.correction().x,
                snapped.y + result.correction().y);
    }

    private static Matrix4f planarWorldMatrix(Transform2D transform) {
        Vector2f world = transform.worldPosition(new Vector2f());
        Vector2f scale = transform.worldScale(new Vector2f());
        return new Matrix4f()
                .translation(world.x, world.y, 0.0f)
                .rotateZ(transform.worldRotationRadians())
                .scale(scale.x, scale.y, 1.0f);
    }

    private static int planarOperation(int operation) {
        if (operation == Operation.TRANSLATE) {
            return Operation.TRANSLATE_X | Operation.TRANSLATE_Y;
        }
        if (operation == Operation.ROTATE) {
            return Operation.ROTATE_Z;
        }
        return Operation.SCALE_X | Operation.SCALE_Y;
    }

    private void applyPlanarManipulation(Transform2D transform, boolean wasUsing) {
        boolean usingNow = ImGuizmo.isUsing();
        if (usingNow && !wasUsing) {
            capturePlanarDragStart(transform);
        }
        if (usingNow) {
            writePlanarMatrix(transform, new Matrix4f().set(modelArray));
            applySmartSnap(transform);
        }
        if (!usingNow && wasUsing) {
            commitPlanarDrag(transform);
        }
    }

    private void capturePlanarDragStart(Transform2D transform) {
        dragPlanarTransform = transform;
        dragStartPlanarPosition.set(transform.position());
        dragStartPlanarRotation = transform.rotationRadians();
        dragStartPlanarScale.set(transform.scale());
    }

    private static void writePlanarMatrix(Transform2D transform, Matrix4f world) {
        Vector3f position = world.getTranslation(new Vector3f());
        Matrix4f orthonormal = world.normalize3x3(new Matrix4f());
        float rotation = (float) Math.atan2(orthonormal.m01(), orthonormal.m00());
        Vector3f scale = world.getScale(new Vector3f());
        transform.setWorldPosition(position.x, position.y);
        transform.setWorldRotationRadians(rotation);
        transform.setWorldScale(scale.x, scale.y);
        transform.markDirty();
    }

    private void commitPlanarDrag(Transform2D transform) {
        if (dragPlanarTransform != transform) {
            dragPlanarTransform = null;
            return;
        }
        Vector2f afterPosition = new Vector2f(transform.position());
        float afterRotation = transform.rotationRadians();
        Vector2f afterScale = new Vector2f(transform.scale());
        dragPlanarTransform = null;
        if (afterPosition.equals(dragStartPlanarPosition) && afterRotation == dragStartPlanarRotation
                && afterScale.equals(dragStartPlanarScale)) {
            return;
        }
        rewindAndExecutePlanar(transform, afterPosition, afterRotation, afterScale);
    }

    private void rewindAndExecutePlanar(Transform2D transform, Vector2f afterPosition,
                                        float afterRotation, Vector2f afterScale) {
        Transform2DDragCommand command = new Transform2DDragCommand(transform,
                dragStartPlanarPosition, dragStartPlanarRotation, dragStartPlanarScale,
                afterPosition, afterRotation, afterScale);
        transform.setPosition(dragStartPlanarPosition.x, dragStartPlanarPosition.y);
        transform.setRotationRadians(dragStartPlanarRotation);
        transform.setScale(dragStartPlanarScale.x, dragStartPlanarScale.y);
        transform.markDirty();
        activeDocument.get().history().execute(command);
    }

    private void finishDragIfReleased() {
        if (ImGuizmo.isUsing()) {
            return;
        }
        if (dragTransform != null) {
            commitDrag(dragTransform);
        }
        if (dragPlanarTransform != null) {
            commitPlanarDrag(dragPlanarTransform);
        }
    }

    private boolean snapActive() {
        boolean inverted = ImGui.getIO().getKeyCtrl();
        if (editorCamera.twoDimensional() && gizmoState.tool() == GizmoState.Tool.TRANSLATE) {
            return !inverted;
        }
        return gizmoState.snapEnabled() != inverted;
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
        Quaternionf rotation = local.normalize3x3(new Matrix4f())
                .getUnnormalizedRotation(new Quaternionf()).normalize();
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
            followers.clear();
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
        activeDocument.get().history().execute(withFollowers(command));
    }

    private EditorCommand withFollowers(EditorCommand leaderCommand) {
        List<EditorCommand> commands = followers.rewindAll();
        if (commands.isEmpty()) {
            return leaderCommand;
        }
        commands.addFirst(leaderCommand);
        return new CompositeCommand("Transform gizmo (" + commands.size() + " objects)", commands);
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

    private void renderLightDirections(float imageX, float imageY, int width, int height) {
        lightDirectionOverlay.render(activeDocument.get().selection().all(),
                editorCamera.camera().viewProjection(), ImGui.getWindowDrawList(),
                new LightDirectionOverlay.ScreenRect(imageX, imageY, width, height));
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
        if (!viewportHoveredThisFrame || billboardClickConsumed || previewClickConsumed
                || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        float deltaX = ImGui.getMousePosX() - screenX;
        float deltaY = ImGui.getMousePosY() - screenY;
        if (deltaX * deltaX + deltaY * deltaY <= BILLBOARD_CLICK_RADIUS * BILLBOARD_CLICK_RADIUS) {
            applyPick(gameObject);
            billboardClickConsumed = true;
        }
    }

    private void updateCamera(float deltaSeconds, float imageX, float imageY, int width, int height) {
        editorCamera.updateFraming(deltaSeconds);
        if (editorCamera.twoDimensional()) {
            updateTwoDimensionalNavigation(height);
            return;
        }
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

    private void updateTwoDimensionalNavigation(int height) {
        boolean panHeld = viewportHoveredThisFrame
                && (mouseButtonHeld(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                        || (!paintingActiveThisFrame && mouseButtonHeld(GLFW.GLFW_MOUSE_BUTTON_RIGHT)));
        float unitsPerPixel = 2.0f * editorCamera.orthographicSize() / Math.max(1, height);
        editorCamera.updatePan(ImGui.getMousePosX(), ImGui.getMousePosY(), panHeld, unitsPerPixel);
        if (viewportHoveredThisFrame) {
            editorCamera.applyOrthographicZoom(ImGui.getIO().getMouseWheel());
        }
    }

    private boolean mouseButtonHeld(int glfwButton) {
        return GLFW.glfwGetMouseButton(windowHandle, glfwButton) == GLFW.GLFW_PRESS;
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
            Optional<Vector3f> position = worldCenter(gameObject);
            if (position.isPresent()) {
                center.add(position.get());
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
        worldCenter(gameObject).ifPresent(center ->
                editorCamera.frame(center, boundsRadius(List.of(gameObject), center)));
    }

    private static Optional<Vector3f> worldCenter(GameObject gameObject) {
        Optional<Transform3D> spatial = gameObject.getComponent(Transform3D.class);
        if (spatial.isPresent()) {
            return Optional.of(spatial.get().worldMatrix().getTranslation(new Vector3f()));
        }
        return gameObject.getComponent(Transform2D.class)
                .map(planar -> new Vector3f(planar.position().x, planar.position().y, 0.0f));
    }

    private static float boundsRadius(List<GameObject> gameObjects, Vector3f center) {
        float radius = FRAME_MINIMUM_RADIUS;
        for (GameObject gameObject : gameObjects) {
            Optional<Vector3f> position = worldCenter(gameObject);
            if (position.isEmpty()) {
                continue;
            }
            float objectRadius = largestScaleAxis(gameObject) * 0.87f;
            radius = Math.max(radius, center.distance(position.get()) + objectRadius);
        }
        return radius;
    }

    private static float largestScaleAxis(GameObject gameObject) {
        Optional<Transform3D> spatial = gameObject.getComponent(Transform3D.class);
        if (spatial.isPresent()) {
            Vector3f scale = spatial.get().worldMatrix().getScale(new Vector3f());
            return Math.max(scale.x, Math.max(scale.y, scale.z));
        }
        return gameObject.getComponent(Transform2D.class)
                .map(planar -> Math.max(planar.scale().x, planar.scale().y))
                .orElse(1.0f);
    }

    private void handlePicking(boolean gizmoBusy, float imageX, float imageY, int width, int height) {
        if (!viewportHoveredThisFrame || gizmoBusy || billboardClickConsumed || previewClickConsumed
                || ImGui.getIO().getKeyAlt()) {
            return;
        }
        boolean rightHeld = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (rightHeld || !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        if (uiEditingEnabled && !playSession.isActive()) {
            Optional<GameObject> overlay = uiEditor.pick(activeDocument.get().scene(), imageX, imageY,
                    ImGui.getMousePosX(), ImGui.getMousePosY());
            if (overlay.isPresent()) {
                applyPick(overlay.get());
                return;
            }
        }
        int localX = (int) (ImGui.getMousePosX() - imageX);
        int localY = (int) (ImGui.getMousePosY() - imageY);
        Optional<GameObject> hit = sceneHost.pickAt(editorCamera, localX, localY, width, height);
        hit.ifPresentOrElse(this::applyPick, this::clearUnlessAdditive);
    }

    private void applyPick(GameObject gameObject) {
        if (ImGui.getIO().getKeyCtrl()) {
            activeDocument.get().selection().toggle(gameObject);
            return;
        }
        activeDocument.get().selection().select(gameObject);
    }

    private void clearUnlessAdditive() {
        if (ImGui.getIO().getKeyCtrl()) {
            return;
        }
        activeDocument.get().selection().clear();
    }

    private Optional<TilemapRenderer> activePaintTarget() {
        if (!paintEnabled || !editorCamera.twoDimensional() || playSession.isActive()) {
            return Optional.empty();
        }
        Optional<TilemapRenderer> renderer = activeDocument.get().selection().get()
                .flatMap(gameObject -> gameObject.getComponent(TilemapRenderer.class));
        renderer.ifPresent(found -> found.refresh(sceneHost.engine()));
        return renderer.filter(found -> found.tilemapValue().isPresent());
    }

    private void handleTilemapPaint(TilemapRenderer renderer, float imageX, float imageY, int width, int height) {
        Optional<Transform2D> transform = selectedPlanarTransform();
        Optional<SpriteTilemap> tilemap = renderer.tilemapValue();
        if (transform.isEmpty() || tilemap.isEmpty()) {
            paintController.cancel();
            return;
        }
        int[] cell = cellAtMouse(transform.get(), tilemap.get(), imageX, imageY, width, height);
        drawPaintCursor(transform.get(), tilemap.get(), cell, imageX, imageY, width, height);
        paintController.update(tilemap.get(), cell[0], cell[1], viewportHoveredThisFrame);
    }

    private int[] cellAtMouse(Transform2D transform, SpriteTilemap tilemap,
                              float imageX, float imageY, int width, int height) {
        Vector3f world = viewportWorldOnPlane(imageX, imageY, width, height);
        Vector2f local = new Matrix3x2f(transform.worldMatrix()).invert()
                .transformPosition(new Vector2f(world.x, world.y));
        int cellX = (int) Math.floor(local.x / tilemap.cellWidth());
        int cellY = (int) Math.floor(local.y / tilemap.cellHeight());
        return new int[]{cellX, cellY};
    }

    private Vector3f viewportWorldOnPlane(float imageX, float imageY, int width, int height) {
        float ndcX = ((ImGui.getMousePosX() - imageX) / width) * 2.0f - 1.0f;
        float ndcY = 1.0f - ((ImGui.getMousePosY() - imageY) / height) * 2.0f;
        Matrix4f inverse = new Matrix4f(editorCamera.camera().viewProjection()).invert();
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        Vector3f nearPoint = new Vector3f(near.x / near.w, near.y / near.w, near.z / near.w);
        Vector3f farPoint = new Vector3f(far.x / far.w, far.y / far.w, far.z / far.w);
        Vector3f direction = farPoint.sub(nearPoint, new Vector3f());
        float t = Math.abs(direction.z) < 1.0e-6f ? 0.0f : -nearPoint.z / direction.z;
        return nearPoint.add(direction.mul(t));
    }

    private void drawPaintCursor(Transform2D transform, SpriteTilemap tilemap, int[] cell,
                                 float imageX, float imageY, int width, int height) {
        if (!viewportHoveredThisFrame && !paintController.active()) {
            return;
        }
        paintController.selectionRange().ifPresent(range -> drawCellQuad(transform, tilemap,
                range.minX(), range.minY(), range.maxX(), range.maxY(),
                imageX, imageY, width, height, COLOR_PAINT_RECTANGLE_FILL, COLOR_PAINT_RECTANGLE_BORDER));
        Optional<TilePaintController.CellRange> pending = paintController.pendingRange(cell[0], cell[1]);
        if (pending.isPresent()) {
            drawCellQuad(transform, tilemap, pending.get().minX(), pending.get().minY(),
                    pending.get().maxX(), pending.get().maxY(),
                    imageX, imageY, width, height, COLOR_PAINT_RECTANGLE_FILL, COLOR_PAINT_RECTANGLE_BORDER);
            return;
        }
        drawCellQuad(transform, tilemap, cell[0], cell[1], cell[0], cell[1],
                imageX, imageY, width, height, 0, COLOR_PAINT_CURSOR);
    }

    private void drawCellQuad(Transform2D transform, SpriteTilemap tilemap, int cellX0, int cellY0,
                              int cellX1, int cellY1, float imageX, float imageY, int width, int height,
                              int fillColor, int borderColor) {
        int minX = Math.min(cellX0, cellX1);
        int maxX = Math.max(cellX0, cellX1);
        int minY = Math.min(cellY0, cellY1);
        int maxY = Math.max(cellY0, cellY1);
        projectAndDrawQuad(transform, minX * tilemap.cellWidth(), (maxX + 1) * tilemap.cellWidth(),
                minY * tilemap.cellHeight(), (maxY + 1) * tilemap.cellHeight(),
                imageX, imageY, width, height, fillColor, borderColor);
    }

    private void projectAndDrawQuad(Transform2D transform, float left, float right, float bottom, float top,
                                    float imageX, float imageY, int width, int height,
                                    int fillColor, int borderColor) {
        Vector2f cornerA = localToScreen(transform, left, bottom, imageX, imageY, width, height);
        Vector2f cornerB = localToScreen(transform, right, bottom, imageX, imageY, width, height);
        Vector2f cornerC = localToScreen(transform, right, top, imageX, imageY, width, height);
        Vector2f cornerD = localToScreen(transform, left, top, imageX, imageY, width, height);
        ImDrawList drawList = ImGui.getWindowDrawList();
        if (fillColor != 0) {
            drawList.addQuadFilled(cornerA.x, cornerA.y, cornerB.x, cornerB.y,
                    cornerC.x, cornerC.y, cornerD.x, cornerD.y, fillColor);
        }
        drawList.addQuad(cornerA.x, cornerA.y, cornerB.x, cornerB.y,
                cornerC.x, cornerC.y, cornerD.x, cornerD.y, borderColor);
    }

    private void drawSmartSnapGuides(float imageX, float imageY, int width, int height) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        for (SmartSnap.Guide guide : smartSnapGuides) {
            Vector2f start = worldToScreen(guideStart(guide), imageX, imageY, width, height);
            Vector2f end = worldToScreen(guideEnd(guide), imageX, imageY, width, height);
            drawList.addLine(start.x, start.y, end.x, end.y, COLOR_SMART_SNAP_GUIDE, SMART_SNAP_GUIDE_THICKNESS);
        }
    }

    private static Vector2f guideStart(SmartSnap.Guide guide) {
        return guide.vertical()
                ? new Vector2f(guide.worldCoordinate(), guide.spanStart() - SMART_SNAP_GUIDE_OVERSHOOT)
                : new Vector2f(guide.spanStart() - SMART_SNAP_GUIDE_OVERSHOOT, guide.worldCoordinate());
    }

    private static Vector2f guideEnd(SmartSnap.Guide guide) {
        return guide.vertical()
                ? new Vector2f(guide.worldCoordinate(), guide.spanEnd() + SMART_SNAP_GUIDE_OVERSHOOT)
                : new Vector2f(guide.spanEnd() + SMART_SNAP_GUIDE_OVERSHOOT, guide.worldCoordinate());
    }

    private Vector2f worldToScreen(Vector2f world, float imageX, float imageY, int width, int height) {
        Vector4f clip = editorCamera.camera().viewProjection()
                .transform(new Vector4f(world.x, world.y, 0.0f, 1.0f));
        return new Vector2f(imageX + (clip.x / clip.w * 0.5f + 0.5f) * width,
                imageY + (0.5f - clip.y / clip.w * 0.5f) * height);
    }

    private Vector2f localToScreen(Transform2D transform, float localX, float localY,
                                   float imageX, float imageY, int width, int height) {
        Vector2f world = transform.worldMatrix().transformPosition(new Vector2f(localX, localY));
        Vector4f clip = editorCamera.camera().viewProjection()
                .transform(new Vector4f(world.x, world.y, 0.0f, 1.0f));
        float screenX = imageX + (clip.x / clip.w * 0.5f + 0.5f) * width;
        float screenY = imageY + (0.5f - clip.y / clip.w * 0.5f) * height;
        return new Vector2f(screenX, screenY);
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
        Optional<GameObject> previewObject = previewCameraObject();
        if (previewObject.isEmpty()) {
            return;
        }
        GameObject cameraObject = previewObject.get();
        Optional<Camera3D> camera = cameraObject.getComponent(Camera3D.class);
        if (camera.isEmpty()) {
            return;
        }
        PreviewBounds bounds = previewBounds(imageX, imageY, width, height);
        int textureId = sceneHost.renderPreviewFrom(camera.get(),
                bounds.width() * supersampleFactor, bounds.height() * supersampleFactor);
        drawPreviewFrame(textureId, bounds, cameraObject);
        renderPreviewPinButton(bounds, cameraObject);
    }

    private record PreviewBounds(float x, float y, int width, int height) {

        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
    }

    private static PreviewBounds previewBounds(float imageX, float imageY, int width, int height) {
        int previewWidth = Math.max(64, (int) (width * PREVIEW_WIDTH_FRACTION));
        int previewHeight = Math.max(36, (int) (previewWidth / PREVIEW_ASPECT));
        return new PreviewBounds(imageX + width - previewWidth - PREVIEW_MARGIN,
                imageY + height - previewHeight - PREVIEW_MARGIN, previewWidth, previewHeight);
    }

    private boolean mouseOverCameraPreview(float imageX, float imageY, int width, int height) {
        if (playSession.isActive() || previewCameraObject().isEmpty()) {
            return false;
        }
        return previewBounds(imageX, imageY, width, height)
                .contains(ImGui.getMousePosX(), ImGui.getMousePosY());
    }

    private void drawPreviewFrame(int textureId, PreviewBounds bounds, GameObject cameraObject) {
        float x0 = bounds.x();
        float y0 = bounds.y();
        float x1 = x0 + bounds.width();
        float y1 = y0 + bounds.height();
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(x0 - 1.0f, y0 - 1.0f, x1 + 1.0f, y1 + 1.0f,
                EditorStyle.COLOR_OUTLINE, PREVIEW_ROUNDING);
        drawList.addImageRounded(textureId, x0, y0, x1, y1,
                0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF, PREVIEW_ROUNDING);
        drawList.addRect(x0, y0, x1, y1,
                isPinned(cameraObject) ? EditorStyle.COLOR_HIGHLIGHT : EditorStyle.COLOR_ACCENT,
                PREVIEW_ROUNDING, 0, 1.5f);
        drawList.addText(x0 + PREVIEW_LABEL_INSET_X, y0 + PREVIEW_LABEL_INSET_Y, EditorStyle.COLOR_TEXT,
                cameraObject.name());
    }

    private void renderPreviewPinButton(PreviewBounds bounds, GameObject cameraObject) {
        boolean pinned = isPinned(cameraObject);
        float restoreX = ImGui.getCursorScreenPosX();
        float restoreY = ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(bounds.x() + bounds.width() - PREVIEW_PIN_SIZE - PREVIEW_LABEL_INSET_X * 2.0f,
                bounds.y() + PREVIEW_LABEL_INSET_Y * 0.5f);
        if (icons.toggleButton("camera-preview-pin", pinned ? EditorIcon.LOCK : EditorIcon.UNLOCK,
                PREVIEW_PIN_SIZE, pinned)) {
            togglePin(cameraObject);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(pinned
                    ? TextKey.EDITOR_VIEWPORT_VIEW_CAMERA_PREVIEW_UNPIN
                    : TextKey.EDITOR_VIEWPORT_VIEW_CAMERA_PREVIEW_PIN));
        }
        ImGui.setCursorScreenPos(restoreX, restoreY);
    }

    private void togglePin(GameObject cameraObject) {
        pinnedCameraId = isPinned(cameraObject) ? Optional.empty() : Optional.of(cameraObject.id());
    }

    private boolean isPinned(GameObject cameraObject) {
        return pinnedCameraId.filter(id -> id.equals(cameraObject.id())).isPresent();
    }

    private Optional<GameObject> previewCameraObject() {
        return pinnedCameraObject().or(this::selectedCameraObject);
    }

    private Optional<GameObject> pinnedCameraObject() {
        return pinnedCameraId
                .flatMap(id -> activeDocument.get().scene().findById(id))
                .filter(gameObject -> gameObject.getComponent(Camera3D.class).isPresent());
    }

    private Optional<GameObject> selectedCameraObject() {
        return activeDocument.get().selection().get()
                .filter(gameObject -> gameObject.getComponent(Camera3D.class).isPresent());
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
        if (selectionSilhouette != null) {
            selectionSilhouette.close();
            selectionSilhouette = null;
        }
        if (selectionOverlay != null) {
            selectionOverlay.close();
            selectionOverlay = null;
        }
    }
}
