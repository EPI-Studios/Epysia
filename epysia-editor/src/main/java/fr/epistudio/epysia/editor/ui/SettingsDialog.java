package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.input.action.InputBinding;
import fr.epistudio.epysia.project.EditorSettings;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectQuality;
import fr.epistudio.epysia.project.RenderTuning;
import fr.epistudio.epysia.render.environment.SkySettings;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.postfx.PostEffectStack;
import fr.epistudio.epysia.render.postfx.FogShaderComposer;
import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import fr.epistudio.epysia.render.postfx.StretchAspect;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SettingsDialog {

    private static final String POPUP_TITLE = "Settings";
    private static final float DIALOG_WIDTH = 720.0f;
    private static final float DIALOG_HEIGHT = 560.0f;
    private static final int LAYER_NAME_CAPACITY = 64;
    private static final float MIN_CAMERA_SPEED = 0.5f;
    private static final float MAX_CAMERA_SPEED = 100.0f;
    private static final float MIN_CAMERA_BOOST = 1.0f;
    private static final float MAX_CAMERA_BOOST = 20.0f;
    private static final int MIN_AUTOSAVE_SECONDS = 10;
    private static final int MAX_AUTOSAVE_SECONDS = 3600;
    private static final float MIN_INTENSITY = 0.0f;
    private static final float MAX_INTENSITY = 5.0f;
    private static final float MIN_EXPOSURE = 0.1f;
    private static final float MAX_EXPOSURE = 4.0f;
    private static final float MIN_OCCLUSION_RADIUS = 0.1f;
    private static final float MAX_OCCLUSION_RADIUS = 3.0f;
    private static final float MAX_ANIMATION_DISTANCE = 500.0f;
    private static final float MIN_SHADOW_DISTANCE = 10.0f;
    private static final float MAX_SHADOW_DISTANCE = 500.0f;
    private static final float DEFAULT_SHADOW_DISTANCE = 60.0f;
    private static final int FOG_SHADER_PATH_CAPACITY = 256;
    private static final int WINDOW_TITLE_CAPACITY = 128;
    private static final float CATEGORY_PANE_WIDTH = 190.0f;
    private static final float CATEGORY_INDENT = 8.0f;
    private static final float LABEL_COLUMN_WIDTH = 190.0f;
    private static final float DEPTH_RATIO_WARNING = 20000.0f;
    private static final int SEARCH_CAPACITY = 64;

    private record Category(String group, String name, Runnable body) {
    }

    public interface ViewportTuningListener {
        void onViewportTuningChanged(float overlayThickness, float gridFadeDistance);
    }

    private final Consumer<EditorSettings> onSettingsSaved;
    private final Consumer<EditorPreferences> onPreferencesSaved;
    private final ViewportTuningListener viewportTuningListener;
    private final ImString[] layerNames = new ImString[EditorSettings.LAYER_COUNT];
    private final float[] cameraSpeed = new float[1];
    private final float[] gravity = new float[3];
    private final ImInt fixedTimestepHertz = new ImInt();
    private final ImInt shadowMapSize = new ImInt();
    private final ImInt cascadeCount = new ImInt();
    private final ImString windowTitle = new ImString(WINDOW_TITLE_CAPACITY);
    private final ImInt windowWidth = new ImInt();
    private final ImInt windowHeight = new ImInt();
    private final ImInt maximumFrameRate = new ImInt();
    private boolean verticalSync;
    private boolean nearestTextureFilter;
    private boolean depthPrepass;
    private RenderTuning renderTuning = RenderTuning.defaults();
    private final ImInt shadowFilterSamples = new ImInt();
    private final ImInt shadowDepthSteps = new ImInt();
    private final float[] animationFullRateDistance =
            {RenderTuning.DEFAULT_ANIMATION_FULL_RATE_DISTANCE};
    private final ImInt gpuCullMinimumInstances =
            new ImInt(RenderTuning.DEFAULT_GPU_CULL_MINIMUM_INSTANCES);
    private final ImInt filteredCascades = new ImInt();
    private final float[] fogColor = new float[3];
    private final float[] fogDistanceStart = new float[1];
    private final float[] fogDistanceDensity = new float[1];
    private final float[] fogHeightOrigin = new float[1];
    private final float[] fogHeightFalloff = new float[1];
    private final float[] fogHeightDensity = new float[1];
    private final ImString fogShaderPath = new ImString(FOG_SHADER_PATH_CAPACITY);
    private boolean fogEnabled;
    private final float[] sceneNear = new float[1];
    private final float[] sceneFar = new float[1];
    private final float[] sceneFieldOfView = new float[1];
    private final float[] lookSensitivity = new float[1];
    private boolean invertLookY;
    private final float[] cameraBoost = new float[1];
    private final int[] autosaveInterval = new int[1];
    private final float[] overlayThickness = new float[1];
    private final float[] gridFadeDistance = new float[1];
    private final GpuPreference[] gpuPreferences = GpuPreference.values();
    private int selectedGpuIndex;
    private final float[] skyIntensity = new float[1];
    private final float[] ambientIntensity = new float[1];
    private final float[] exposure = new float[1];
    private final float[] vignette = new float[1];
    private final float[] bloomIntensity = new float[1];
    private final float[] ambientOcclusionIntensity = new float[1];
    private final float[] ambientOcclusionRadius = new float[1];
    private final float[] shadowDistance = new float[1];
    private int[] collisionMatrix = new int[EditorSettings.LAYER_COUNT];
    private boolean autosaveEnabled;
    private boolean detachableWindows;
    private boolean lightCullingEnabled = true;
    private boolean bloomEnabled;
    private boolean pixelPerfectEnabled;
    private final imgui.type.ImInt pixelPerfectBaseHeight = new imgui.type.ImInt();
    private final imgui.type.ImInt pixelPerfectBaseWidth = new imgui.type.ImInt();
    private final StretchAspect[] stretchAspects = StretchAspect.values();
    private boolean pixelPerfectIntegerScale;
    private int selectedAspectIndex;
    private boolean ambientOcclusionEnabled;
    private boolean antiAliasEnabled;
    private EditorPreferences basePreferences = EditorPreferences.defaults();
    private Optional<PostProcessSettings> postProcessSettings = Optional.empty();
    private Optional<SkySettings> skySettings = Optional.empty();
    private Optional<MeshRenderSystem> meshRenderSystem = Optional.empty();
    private Optional<PostEffectsSection> postEffectsSection = Optional.empty();
    private Optional<LibrariesSection> librariesSection = Optional.empty();
    private Optional<Supplier<PostEffectStack>> globalPostEffectStack = Optional.empty();
    private Runnable onPostEffectsChanged = () -> {
    };
    private static final int ACTION_NAME_CAPACITY = 64;
    private static final float ACTION_NAME_FIELD_WIDTH = 200.0f;
    private final ImString searchFilter = new ImString(SEARCH_CAPACITY);
    private final List<Category> categories = buildCategories();
    private List<InputAction> inputActions = new ArrayList<>(InputActions.defaultActions());
    private final ImString newActionName = new ImString(ACTION_NAME_CAPACITY);
    private final Map<Integer, ImString> actionNameEditors = new HashMap<>();
    private int listeningAction = -1;
    private boolean listeningNegative;
    private int selectedCategory;
    private int matchedRows;
    private String captionPending = "";
    private String currentCaption = "";
    private Project project;
    private boolean openRequested;

    public SettingsDialog(Consumer<EditorSettings> onSettingsSaved,
                          Consumer<EditorPreferences> onPreferencesSaved,
                          ViewportTuningListener viewportTuningListener) {
        this.onSettingsSaved = onSettingsSaved;
        this.onPreferencesSaved = onPreferencesSaved;
        this.viewportTuningListener = viewportTuningListener;
        for (int i = 0; i < layerNames.length; i++) {
            layerNames[i] = new ImString(LAYER_NAME_CAPACITY);
        }
        shadowDistance[0] = DEFAULT_SHADOW_DISTANCE;
    }

    public void attachRenderTuning(PostProcessSettings postProcess, SkySettings sky, MeshRenderSystem meshSystem) {
        postProcessSettings = Optional.of(postProcess);
        skySettings = Optional.of(sky);
        meshRenderSystem = Optional.of(meshSystem);
        shadowDistance[0] = meshSystem.shadowDistance();
    }

    public void attachLibraries(LibrariesSection section) {
        librariesSection = Optional.of(section);
    }

    public void attachPostEffects(PostEffectsSection section, Supplier<PostEffectStack> stack, Runnable onChanged) {
        postEffectsSection = Optional.of(section);
        globalPostEffectStack = Optional.of(stack);
        onPostEffectsChanged = onChanged;
    }

    public void openFor(EditorSettings settings, EditorPreferences preferences, Project openedProject,
                        ProjectQuality quality, List<InputAction> actions) {
        collisionMatrix = settings.collisionMatrix();
        for (int i = 0; i < layerNames.length; i++) {
            layerNames[i].set(settings.layerNames().get(i));
        }
        loadPreferences(preferences);
        loadQuality(quality);
        inputActions = new ArrayList<>(actions);
        listeningAction = -1;
        project = openedProject;
        loadRenderTuning();
        openRequested = true;
    }

    private void loadPreferences(EditorPreferences preferences) {
        basePreferences = preferences;
        cameraSpeed[0] = preferences.cameraSpeed();
        sceneNear[0] = preferences.sceneNear();
        sceneFar[0] = preferences.sceneFar();
        sceneFieldOfView[0] = preferences.sceneFieldOfView();
        lookSensitivity[0] = preferences.lookSensitivity();
        invertLookY = preferences.invertLookY();
        cameraBoost[0] = preferences.cameraBoost();
        autosaveInterval[0] = preferences.autosaveIntervalSeconds();
        autosaveEnabled = preferences.autosaveEnabled();
        detachableWindows = preferences.detachableWindows();
        overlayThickness[0] = preferences.overlayThickness();
        gridFadeDistance[0] = preferences.gridFadeDistance();
        selectedGpuIndex = indexOf(preferences.gpuPreference());
    }

    private int indexOf(GpuPreference preference) {
        for (int index = 0; index < gpuPreferences.length; index++) {
            if (gpuPreferences[index] == preference) {
                return index;
            }
        }
        return 0;
    }

    private void loadRenderTuning() {
        if (postProcessSettings.isEmpty() || skySettings.isEmpty()) {
            return;
        }
        PostProcessSettings postProcess = postProcessSettings.get();
        skyIntensity[0] = skySettings.get().skyIntensity();
        ambientIntensity[0] = skySettings.get().ambientIntensity();
        exposure[0] = postProcess.gradeExposure();
        vignette[0] = postProcess.vignetteStrength();
        bloomIntensity[0] = postProcess.bloomIntensity();
        ambientOcclusionIntensity[0] = postProcess.ambientOcclusionIntensity();
        ambientOcclusionRadius[0] = postProcess.ambientOcclusionRadius();
        bloomEnabled = postProcess.bloomEnabled();
        pixelPerfectEnabled = postProcess.pixelPerfectEnabled();
        pixelPerfectBaseHeight.set(postProcess.pixelPerfectBaseHeight());
        pixelPerfectBaseWidth.set(postProcess.pixelPerfectBaseWidth());
        pixelPerfectIntegerScale = postProcess.pixelPerfectIntegerScale();
        selectedAspectIndex = postProcess.pixelPerfectAspect().ordinal();
        ambientOcclusionEnabled = postProcess.ambientOcclusionEnabled();
        antiAliasEnabled = postProcess.antiAliasingEnabled();
        loadFog(postProcess);
        meshRenderSystem.ifPresent(system -> lightCullingEnabled = system.clusteringEnabled());
    }

    private void loadQuality(ProjectQuality quality) {
        gravity[0] = quality.gravityX();
        gravity[1] = quality.gravityY();
        gravity[2] = quality.gravityZ();
        fixedTimestepHertz.set(quality.fixedTimestepHertz());
        shadowMapSize.set(quality.shadowMapSize());
        cascadeCount.set(quality.cascadeCount());
        windowTitle.set(quality.windowTitle());
        windowWidth.set(quality.windowWidth());
        windowHeight.set(quality.windowHeight());
        verticalSync = quality.verticalSync();
        maximumFrameRate.set(quality.maximumFrameRate());
        nearestTextureFilter = quality.nearestTextureFilter();
        depthPrepass = quality.depthPrepass();
        shadowFilterSamples.set(quality.shadowFilterSamples());
        filteredCascades.set(quality.filteredCascades());
        shadowDepthSteps.set(quality.shadowDepthSteps());
        renderTuning = quality.renderTuning();
        animationFullRateDistance[0] = renderTuning.animationFullRateDistance();
        gpuCullMinimumInstances.set(renderTuning.gpuCullMinimumInstances());
    }

    public ProjectQuality buildQuality() {
        return new ProjectQuality(gravity[0], gravity[1], gravity[2], fixedTimestepHertz.get(),
                shadowMapSize.get(), cascadeCount.get(), windowTitle.get().trim(),
                windowWidth.get(), windowHeight.get(), verticalSync, maximumFrameRate.get(),
                nearestTextureFilter, depthPrepass, shadowFilterSamples.get(),
                filteredCascades.get(), shadowDepthSteps.get(), renderTuning).clamped();
    }

    private void loadFog(PostProcessSettings postProcess) {
        fogEnabled = postProcess.fogEnabled();
        fogColor[0] = postProcess.fogColor().x;
        fogColor[1] = postProcess.fogColor().y;
        fogColor[2] = postProcess.fogColor().z;
        fogDistanceStart[0] = postProcess.fogDistanceStart();
        fogDistanceDensity[0] = postProcess.fogDistanceDensity();
        fogHeightOrigin[0] = postProcess.fogHeightOrigin();
        fogHeightFalloff[0] = postProcess.fogHeightFalloff();
        fogHeightDensity[0] = postProcess.fogHeightDensity();
        fogShaderPath.set(postProcess.fogShaderPath());
    }


    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_TITLE);
            openRequested = false;
        }
        ImGui.setNextWindowSize(DIALOG_WIDTH, DIALOG_HEIGHT, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(POPUP_TITLE)) {
            return;
        }
        renderSearchBar();
        renderPanes();
        renderFooter();
        ImGui.endPopup();
    }

    private void renderSearchBar() {
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##settings-search", "Search settings", searchFilter);
        ImGui.separator();
    }

    private void renderPanes() {
        float bodyHeight = -ImGui.getFrameHeightWithSpacing() - ImGui.getStyle().getItemSpacingY();
        ImGui.beginChild("##settings-categories", CATEGORY_PANE_WIDTH, bodyHeight, true);
        renderCategoryList();
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("##settings-body", 0.0f, bodyHeight, false);
        renderBody();
        ImGui.endChild();
    }

    private void renderCategoryList() {
        String lastGroup = "";
        for (int index = 0; index < categories.size(); index++) {
            Category category = categories.get(index);
            if (!category.group().equals(lastGroup)) {
                lastGroup = category.group();
                ImGui.textDisabled(lastGroup);
            }
            ImGui.indent(CATEGORY_INDENT);
            String label = category.name() + "###category-" + category.group() + "-" + category.name();
            if (ImGui.selectable(label, index == selectedCategory && !filtering())) {
                selectedCategory = index;
                searchFilter.set("");
            }
            ImGui.unindent(CATEGORY_INDENT);
        }
    }

    private void renderBody() {
        if (filtering()) {
            renderFilteredBody();
            return;
        }
        Category category = categories.get(Math.clamp(selectedCategory, 0, categories.size() - 1));
        ImGui.textDisabled(category.group() + "  /  " + category.name());
        ImGui.separator();
        category.body().run();
    }

    private void renderFilteredBody() {
        matchedRows = 0;
        for (Category category : categories) {
            int before = matchedRows;
            currentCaption = category.group() + "  /  " + category.name();
            captionPending = currentCaption;
            category.body().run();
            if (matchedRows > before) {
                ImGui.separator();
            }
        }
        captionPending = "";
        currentCaption = "";
        if (matchedRows == 0) {
            ImGui.textDisabled("No setting matches \"" + searchFilter.get() + "\".");
        }
    }

    private boolean contains(String text) {
        return text.toLowerCase(Locale.ROOT).contains(searchFilter.get().trim().toLowerCase(Locale.ROOT));
    }

    private boolean categoryMatchesSearch() {
        return filtering() && contains(currentCaption);
    }

    private void noteMatch() {
        matchedRows++;
        if (!captionPending.isEmpty()) {
            ImGui.textDisabled(captionPending);
            captionPending = "";
        }
    }

    private boolean skipWhileFiltering() {
        if (!filtering()) {
            return false;
        }
        if (!categoryMatchesSearch()) {
            return true;
        }
        noteMatch();
        return false;
    }

    private boolean filtering() {
        return !searchFilter.get().trim().isEmpty();
    }

    private boolean accepts(String label) {
        if (!filtering()) {
            return true;
        }
        if (!categoryMatchesSearch() && !contains(label)) {
            return false;
        }
        noteMatch();
        return true;
    }

    private void row(String label, Runnable control) {
        if (!accepts(label)) {
            return;
        }
        ImGui.pushID(label);
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(label);
        ImGui.sameLine(LABEL_COLUMN_WIDTH);
        ImGui.setNextItemWidth(-1.0f);
        control.run();
        ImGui.popID();
    }

    private boolean toggleRow(String label, boolean value) {
        if (!accepts(label)) {
            return value;
        }
        ImGui.pushID(label);
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(label);
        ImGui.sameLine(LABEL_COLUMN_WIDTH);
        boolean clicked = ImGui.checkbox("##value", value);
        ImGui.popID();
        return clicked ? !value : value;
    }

    private void hint(TextKey key) {
        if (filtering()) {
            return;
        }
        ImGui.textDisabled(I18n.translate(key));
    }

    private void hint(TextKey key, Object... arguments) {
        if (filtering()) {
            return;
        }
        ImGui.textDisabled(I18n.translate(key, arguments));
    }

    private List<Category> buildCategories() {
        List<Category> built = new ArrayList<>();
        built.add(new Category("Application", "General", this::renderApplicationCategory));
        built.add(new Category("Application", "Project", this::renderProjectIdentity));
        built.add(new Category("Application", "Libraries", this::renderLibrariesCategory));
        built.add(new Category("Display", "Window", this::renderWindowCategory));
        built.add(new Category("Display", "Stretch", this::renderStretchCategory));
        built.add(new Category("Input", "Actions", this::renderInputActionsCategory));
        built.add(new Category("Editor", "Camera", this::renderEditorCameraCategory));
        built.add(new Category("Editor", "Scene view", this::renderSceneViewCategory));
        built.add(new Category("Editor", "Viewport", this::renderViewportCategory));
        built.add(new Category("Editor", "Workflow", this::renderWorkflowCategory));
        built.add(new Category("Physics", "General", this::renderPhysicsCategory));
        built.add(new Category("Physics", "Layers", this::renderLayersCategory));
        built.add(new Category("Rendering", "Environment", this::renderEnvironmentCategory));
        built.add(new Category("Rendering", "Fog", this::renderFogCategory));
        built.add(new Category("Rendering", "Shadows", this::renderShadowCategory));
        built.add(new Category("Rendering", "Post processing", this::renderPostCategory));
        built.add(new Category("Rendering", "Textures", this::renderTextureCategory));
        built.add(new Category("Rendering", "Post effects", this::renderPostEffectsCategory));
        built.add(new Category("Rendering", "Performance", this::renderPerformanceCategory));
        return built;
    }

    private void renderApplicationCategory() {
        row("Window title", () -> ImGui.inputText("##value", windowTitle));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_WINDOW_TITLE_HELP);
    }

    private void renderLibrariesCategory() {
        if (filtering() || librariesSection.isEmpty()) {
            return;
        }
        if (project == null) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_NO_PROJECT);
            return;
        }
        librariesSection.get().render(project);
    }

    private void renderProjectIdentity() {
        if (project == null) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_NO_PROJECT);
            return;
        }
        row("Name", () -> ImGui.textUnformatted(project.name()));
        row("Engine version", () -> ImGui.textUnformatted(project.engineVersion()));
        row("Root directory", () -> ImGui.textUnformatted(project.rootDirectory().toString()));
        row("Default scene", () -> ImGui.textUnformatted(project.defaultScenePath().getFileName().toString()));
    }

    private void renderWindowCategory() {
        row("Width", () -> ImGui.dragInt("##value", windowWidth.getData(), 8.0f,
                ProjectQuality.MIN_WINDOW_SIZE, ProjectQuality.MAX_WINDOW_SIZE));
        row("Height", () -> ImGui.dragInt("##value", windowHeight.getData(), 8.0f,
                ProjectQuality.MIN_WINDOW_SIZE, ProjectQuality.MAX_WINDOW_SIZE));
        verticalSync = toggleRow("Vertical sync", verticalSync);
        row("Max frame rate", () -> ImGui.dragInt("##value", maximumFrameRate.getData(), 1.0f, 0,
                ProjectQuality.MAX_FRAME_RATE_LIMIT));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_FRAME_RATE_HELP);
    }

    private void renderStretchCategory() {
        if (postProcessSettings.isEmpty()) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_STRETCH_UNAVAILABLE);
            return;
        }
        pixelPerfectEnabled = toggleRow("Pixel perfect", pixelPerfectEnabled);
        if (!pixelPerfectEnabled && !filtering()) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_PIXEL_PERFECT_HELP);
            return;
        }
        row("Reference width", () -> ImGui.dragInt("##value", pixelPerfectBaseWidth.getData(), 1.0f, 32, 7680));
        row("Reference height", () -> ImGui.dragInt("##value", pixelPerfectBaseHeight.getData(), 1.0f, 32, 4320));
        row("Aspect", this::renderAspectCombo);
        pixelPerfectIntegerScale = toggleRow("Integer scale", pixelPerfectIntegerScale);
        hint(TextKey.EDITOR_SETTINGS_DIALOG_INTEGER_SCALE_HELP);
    }

    private void renderAspectCombo() {
        if (!ImGui.beginCombo("##value", stretchAspects[selectedAspectIndex].name())) {
            return;
        }
        for (int index = 0; index < stretchAspects.length; index++) {
            if (ImGui.selectable(stretchAspects[index].name(), index == selectedAspectIndex)) {
                selectedAspectIndex = index;
            }
        }
        ImGui.endCombo();
    }

    private void renderInputActionsCategory() {
        if (skipWhileFiltering()) {
            return;
        }
        hint(TextKey.EDITOR_SETTINGS_DIALOG_INPUT_ACTIONS_HELP);
        for (int index = 0; index < inputActions.size(); index++) {
            renderActionRows(index);
        }
        ImGui.separator();
        renderAddActionRow();
        if (inputActions.isEmpty()) {
            ImGui.textDisabled("No action yet. Name one above and bind it.");
        }
    }

    private void renderAddActionRow() {
        ImGui.setNextItemWidth(ACTION_NAME_FIELD_WIDTH);
        boolean submitted = TextFields.inputSubmitted("##new-action", newActionName);
        ImGui.sameLine();
        if ((ImGui.button("Add action") || submitted) && !newActionName.get().isBlank()) {
            addAction(newActionName.get());
            newActionName.set("");
        }
    }

    private void addAction(String desiredName) {
        inputActions.add(InputAction.button(InputActions.uniqueNameAmong(inputActions, desiredName)));
        actionNameEditors.clear();
        listeningAction = -1;
    }

    private void renderActionRows(int index) {
        InputAction action = inputActions.get(index);
        ImGui.pushID(index);
        ImGui.separator();
        renderActionHeader(index, action);
        renderBindingRow(index, action, false);
        renderBindingRow(index, action, true);
        ImGui.popID();
    }

    private void renderActionHeader(int index, InputAction action) {
        ImString editor = actionNameEditors.computeIfAbsent(index, key -> {
            ImString value = new ImString(ACTION_NAME_CAPACITY);
            value.set(action.name());
            return value;
        });
        ImGui.setNextItemWidth(ACTION_NAME_FIELD_WIDTH);
        if (ImGui.inputText("##name", editor)) {
            renameAction(index, editor.get());
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Remove")) {
            removeAction(index);
        }
    }

    private void renameAction(int index, String requestedName) {
        InputAction action = inputActions.get(index);
        String trimmed = requestedName.trim();
        if (trimmed.isEmpty() || trimmed.equals(action.name())) {
            return;
        }
        List<InputAction> others = new ArrayList<>(inputActions);
        others.remove(index);
        inputActions.set(index, new InputAction(InputActions.uniqueNameAmong(others, trimmed),
                action.positive(), action.negative()));
    }

    private void removeAction(int index) {
        inputActions.remove(index);
        actionNameEditors.clear();
        listeningAction = -1;
    }

    private void renderBindingRow(int index, InputAction action, boolean negative) {
        List<InputBinding> bindings = negative ? action.negative() : action.positive();
        boolean listening = listeningAction == index && listeningNegative == negative;
        ImGui.pushID(negative ? "negative" : "positive");
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled(negative ? "  negative" : "  positive");
        ImGui.sameLine(LABEL_COLUMN_WIDTH);
        ImGui.textUnformatted(listening ? "press a key or mouse button..." : describe(bindings));
        ImGui.sameLine();
        if (ImGui.smallButton(listening ? "Cancel" : "Rebind")) {
            listeningAction = listening ? -1 : index;
            listeningNegative = negative;
        }
        ImGui.popID();
        if (listening) {
            captureBinding(index, negative);
        }
    }

    private static String describe(List<InputBinding> bindings) {
        if (bindings.isEmpty()) {
            return "unbound";
        }
        StringBuilder text = new StringBuilder();
        for (InputBinding binding : bindings) {
            text.append(text.isEmpty() ? "" : ", ").append(binding.serialized());
        }
        return text.toString();
    }

    private void captureBinding(int index, boolean negative) {
        capturedBinding().ifPresent(binding -> {
            InputAction action = inputActions.get(index);
            List<InputBinding> replaced = List.of(binding);
            inputActions.set(index, negative
                    ? new InputAction(action.name(), action.positive(), replaced)
                    : new InputAction(action.name(), replaced, action.negative()));
            listeningAction = -1;
        });
    }

    private Optional<InputBinding> capturedBinding() {
        for (MouseButton button : MouseButton.values()) {
            if (ImGui.isMouseClicked(button.ordinal())) {
                return Optional.of(InputBinding.mouse(button));
            }
        }
        for (KeyCode key : KeyCode.values()) {
            if (ImGui.isKeyPressed(key.glfwCode())) {
                return Optional.of(InputBinding.key(key));
            }
        }
        return Optional.empty();
    }

    public List<InputAction> buildInputActions() {
        return List.copyOf(inputActions);
    }

    private void renderEditorCameraCategory() {
        row("Move speed", () -> ImGui.dragFloat("##value", cameraSpeed, 0.1f,
                MIN_CAMERA_SPEED, MAX_CAMERA_SPEED));
        row("Boost multiplier", () -> ImGui.dragFloat("##value", cameraBoost, 0.1f,
                MIN_CAMERA_BOOST, MAX_CAMERA_BOOST));
        row("Look sensitivity", () -> ImGui.dragFloat("##value", lookSensitivity, 0.0001f,
                EditorPreferences.MIN_LOOK_SENSITIVITY, EditorPreferences.MAX_LOOK_SENSITIVITY, "%.4f"));
        invertLookY = toggleRow("Invert look Y", invertLookY);
    }

    private void renderSceneViewCategory() {
        row("Near plane", () -> ImGui.dragFloat("##value", sceneNear, 0.01f,
                EditorPreferences.MIN_SCENE_NEAR, EditorPreferences.MAX_SCENE_NEAR, "%.3f"));
        row("Far plane", () -> ImGui.dragFloat("##value", sceneFar, 5.0f,
                EditorPreferences.MIN_SCENE_FAR, EditorPreferences.MAX_SCENE_FAR, "%.0f"));
        row("Field of view", () -> ImGui.dragFloat("##value", sceneFieldOfView, 0.5f,
                EditorPreferences.MIN_SCENE_FIELD_OF_VIEW, EditorPreferences.MAX_SCENE_FIELD_OF_VIEW, "%.1f"));
        if (sceneFar[0] / Math.max(sceneNear[0], EditorPreferences.MIN_SCENE_NEAR) > DEPTH_RATIO_WARNING) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_NEAR_PLANE_HELP);
        }
    }

    private void renderViewportCategory() {
        boolean changed = false;
        if (accepts("Overlay line thickness")) {
            ImGui.pushID("Overlay line thickness");
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted("Overlay line thickness");
            ImGui.sameLine(LABEL_COLUMN_WIDTH);
            ImGui.setNextItemWidth(-1.0f);
            changed = ImGui.sliderFloat("##value", overlayThickness,
                    EditorPreferences.MIN_OVERLAY_THICKNESS, EditorPreferences.MAX_OVERLAY_THICKNESS);
            ImGui.popID();
        }
        if (accepts("Grid fade distance")) {
            ImGui.pushID("Grid fade distance");
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted("Grid fade distance");
            ImGui.sameLine(LABEL_COLUMN_WIDTH);
            ImGui.setNextItemWidth(-1.0f);
            changed |= ImGui.sliderFloat("##value", gridFadeDistance,
                    EditorPreferences.MIN_GRID_FADE_DISTANCE, EditorPreferences.MAX_GRID_FADE_DISTANCE);
            ImGui.popID();
        }
        if (changed) {
            viewportTuningListener.onViewportTuningChanged(overlayThickness[0], gridFadeDistance[0]);
        }
    }

    private void renderWorkflowCategory() {
        autosaveEnabled = toggleRow("Autosave scenes", autosaveEnabled);
        if (autosaveEnabled || filtering()) {
            row("Autosave interval (s)", () -> ImGui.dragInt("##value", autosaveInterval, 1.0f,
                    MIN_AUTOSAVE_SECONDS, MAX_AUTOSAVE_SECONDS));
        }
        detachableWindows = toggleRow("Detachable windows", detachableWindows);
        row("Preferred GPU", this::renderGpuCombo);
        hint(TextKey.EDITOR_SETTINGS_DIALOG_RESTART_HELP);
    }

    private void renderGpuCombo() {
        if (!ImGui.beginCombo("##value", gpuPreferences[selectedGpuIndex].displayName())) {
            return;
        }
        for (int index = 0; index < gpuPreferences.length; index++) {
            if (ImGui.selectable(gpuPreferences[index].displayName(), index == selectedGpuIndex)) {
                selectedGpuIndex = index;
            }
        }
        ImGui.endCombo();
    }

    private void renderPhysicsCategory() {
        row("Gravity", () -> ImGui.dragFloat3("##value", gravity, 0.05f, -200.0f, 200.0f));
        row("Fixed timestep (Hz)", () -> ImGui.dragInt("##value", fixedTimestepHertz.getData(), 1.0f,
                ProjectQuality.MIN_FIXED_TIMESTEP_HERTZ, ProjectQuality.MAX_FIXED_TIMESTEP_HERTZ));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_PHYSICS_HELP);
    }

    private void renderLayersCategory() {
        if (skipWhileFiltering()) {
            return;
        }
        hint(TextKey.EDITOR_SETTINGS_DIALOG_LAYERS_HELP);
        ImGui.beginChild("##collision-grid", 0.0f, 0.0f);
        for (int row = 0; row < EditorSettings.LAYER_COUNT; row++) {
            renderCollisionRow(row);
        }
        ImGui.endChild();
    }

    private void renderCollisionRow(int row) {
        ImGui.pushID(row);
        ImGui.setNextItemWidth(150.0f);
        ImGui.inputText("##layer-name", layerNames[row]);
        for (int column = 0; column < EditorSettings.LAYER_COUNT; column++) {
            ImGui.sameLine();
            renderCollisionCell(row, column);
        }
        ImGui.popID();
    }

    private void renderCollisionCell(int row, int column) {
        ImGui.pushID(column);
        boolean checked = (collisionMatrix[row] & (1 << column)) != 0;
        if (ImGui.checkbox("##cell", checked)) {
            toggleCollision(row, column, !checked);
        }
        ImGui.popID();
    }

    private void toggleCollision(int row, int column, boolean enabled) {
        if (enabled) {
            collisionMatrix[row] |= (1 << column);
            collisionMatrix[column] |= (1 << row);
        } else {
            collisionMatrix[row] &= ~(1 << column);
            collisionMatrix[column] &= ~(1 << row);
        }
    }

    private void renderEnvironmentCategory() {
        if (postProcessSettings.isEmpty()) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_RENDERING_UNAVAILABLE);
            return;
        }
        row("Sky intensity", () -> ImGui.dragFloat("##value", skyIntensity, 0.02f, MIN_INTENSITY, MAX_INTENSITY));
        row("Ambient intensity", () -> ImGui.dragFloat("##value", ambientIntensity, 0.02f,
                MIN_INTENSITY, MAX_INTENSITY));
        row("Exposure", () -> ImGui.dragFloat("##value", exposure, 0.02f, MIN_EXPOSURE, MAX_EXPOSURE));
        row("Vignette", () -> ImGui.dragFloat("##value", vignette, 0.01f, 0.0f, 1.0f));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_ENVIRONMENT_HELP);
    }

    private void renderFogCategory() {
        if (postProcessSettings.isEmpty()) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_FOG_UNAVAILABLE);
            return;
        }
        fogEnabled = toggleRow("Fog", fogEnabled);
        if (!fogEnabled && !filtering()) {
            return;
        }
        row("Color", () -> ImGui.colorEdit3("##value", fogColor));
        row("Start distance", () -> ImGui.dragFloat("##value", fogDistanceStart, 0.1f, 0.0f, 10000.0f));
        row("Density", () -> ImGui.dragFloat("##value", fogDistanceDensity, 0.001f, 0.0f, 2.0f, "%.4f"));
        row("Height origin", () -> ImGui.dragFloat("##value", fogHeightOrigin, 0.1f, -10000.0f, 10000.0f));
        row("Height falloff", () -> ImGui.dragFloat("##value", fogHeightFalloff, 0.01f, 0.0f, 10.0f, "%.3f"));
        row("Height density", () -> ImGui.dragFloat("##value", fogHeightDensity, 0.01f, 0.0f, 4.0f, "%.3f"));
        row("Fog shader", () -> ImGui.inputText("##value", fogShaderPath));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_FOG_SHADER_HELP, FogShaderComposer.EXTENSION);
    }

    private void renderShadowCategory() {
        if (postProcessSettings.isPresent()) {
            row("Shadow distance", () -> ImGui.dragFloat("##value", shadowDistance, 1.0f,
                    MIN_SHADOW_DISTANCE, MAX_SHADOW_DISTANCE));
        }
        row("Shadow map size", () -> ImGui.dragInt("##value", shadowMapSize.getData(), 64.0f,
                ProjectQuality.MIN_SHADOW_MAP_SIZE, ProjectQuality.MAX_SHADOW_MAP_SIZE));
        row("Cascades", () -> ImGui.dragInt("##value", cascadeCount.getData(), 1.0f,
                ProjectQuality.MIN_CASCADE_COUNT, ProjectQuality.MAX_CASCADE_COUNT));
        row("Soft filter samples", () -> ImGui.dragInt("##value", shadowFilterSamples.getData(), 1.0f, 1, 32));
        row("Filtered cascades", () -> ImGui.dragInt("##value", filteredCascades.getData(), 1.0f, 0,
                ProjectQuality.MAX_CASCADE_COUNT));
        row("Depth snap steps", () -> ImGui.dragInt("##value", shadowDepthSteps.getData(), 8.0f,
                ProjectQuality.MIN_SHADOW_DEPTH_STEPS, ProjectQuality.MAX_SHADOW_DEPTH_STEPS));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_SHADOW_HELP);
    }

    private void renderPostCategory() {
        if (postProcessSettings.isEmpty()) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_POST_UNAVAILABLE);
            return;
        }
        bloomEnabled = toggleRow("Bloom", bloomEnabled);
        if (bloomEnabled || filtering()) {
            row("Bloom intensity", () -> ImGui.dragFloat("##value", bloomIntensity, 0.02f,
                    MIN_INTENSITY, MAX_INTENSITY));
        }
        ambientOcclusionEnabled = toggleRow("Ambient occlusion", ambientOcclusionEnabled);
        if (ambientOcclusionEnabled || filtering()) {
            row("Occlusion intensity", () -> ImGui.dragFloat("##value", ambientOcclusionIntensity, 0.02f,
                    MIN_INTENSITY, MAX_INTENSITY));
            row("Occlusion radius", () -> ImGui.dragFloat("##value", ambientOcclusionRadius, 0.02f,
                    MIN_OCCLUSION_RADIUS, MAX_OCCLUSION_RADIUS));
        }
        antiAliasEnabled = toggleRow("Anti-aliasing (FXAA)", antiAliasEnabled);
        lightCullingEnabled = toggleRow("GPU light culling", lightCullingEnabled);
        depthPrepass = toggleRow("Depth prepass", depthPrepass);
    }

    private int gpuCullMinimumInstancesRow() {
        row("GPU cull minimum instances", () -> ImGui.dragInt("##value", gpuCullMinimumInstances.getData(),
                1.0f, RenderTuning.MINIMUM_GPU_CULL_INSTANCES, RenderTuning.MAXIMUM_GPU_CULL_INSTANCES));
        return gpuCullMinimumInstances.get();
    }

    private float animationDistanceRow() {
        row("Animation full rate distance", () -> ImGui.dragFloat("##value", animationFullRateDistance,
                0.5f, 0.0f, MAX_ANIMATION_DISTANCE));
        return animationFullRateDistance[0];
    }

    private void renderPerformanceCategory() {
        hint(TextKey.EDITOR_SETTINGS_DIALOG_PERFORMANCE_HELP);
        renderTuning = new RenderTuning(
                toggleRow("GPU occlusion culling", renderTuning.gpuCulling()),
                gpuCullMinimumInstancesRow(),
                toggleRow("Scene render index", renderTuning.sceneIndex()),
                toggleRow("Multi draw batching", renderTuning.multiDraw()),
                toggleRow("Instancing", renderTuning.instancing()),
                toggleRow("Pipeline memoisation", renderTuning.pipelineMemo()),
                toggleRow("Cached transform lookup", renderTuning.cachedTransformLookup()),
                toggleRow("Shared material digest", renderTuning.sharedMaterialDigest()),
                toggleRow("Skin once per frame", renderTuning.skinOnce()),
                toggleRow("Animation culling", renderTuning.animationCulling()),
                animationDistanceRow(),
                toggleRow("Front to back opaque sorting", renderTuning.frontToBackOpaque()),
                toggleRow("Shadow static layer reuse", renderTuning.shadowLayerReuse()),
                toggleRow("Ring buffered instance data", renderTuning.ringInstanceBuffers()),
                toggleRow("Ring buffered object transforms", renderTuning.ringObjectUniforms()),
                toggleRow("Parallel pose sampling", renderTuning.parallelAnimation()));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_GPU_CULLING_HELP);
        hint(TextKey.EDITOR_SETTINGS_DIALOG_ANIMATION_CULLING_HELP);
    }

    private void renderTextureCategory() {
        nearestTextureFilter = toggleRow("Nearest filter by default", nearestTextureFilter);
        hint(TextKey.EDITOR_SETTINGS_DIALOG_TEXTURE_FILTER_HELP);
    }

    private void renderPostEffectsCategory() {
        if (filtering() || postEffectsSection.isEmpty() || globalPostEffectStack.isEmpty()) {
            return;
        }
        hint(TextKey.EDITOR_SETTINGS_DIALOG_POST_EFFECTS_HELP);
        postEffectsSection.get().render(globalPostEffectStack.get().get(), onPostEffectsChanged);
    }

    private void renderFooter() {
        ImGui.separator();
        if (ImGui.button("Save")) {
            save();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
    }

    private void save() {
        applyRenderTuning();
        onSettingsSaved.accept(buildSettings());
        onPreferencesSaved.accept(new EditorPreferences(cameraSpeed[0], cameraBoost[0],
                autosaveEnabled, autosaveInterval[0],
                basePreferences.gridVisible(), basePreferences.snapEnabled(),
                overlayThickness[0], gridFadeDistance[0], gpuPreferences[selectedGpuIndex], detachableWindows,
                basePreferences.shaderNodePreviewsEnabled(), basePreferences.viewport2DMode(),
                sceneNear[0], sceneFar[0], sceneFieldOfView[0], lookSensitivity[0], invertLookY));
    }

    private EditorSettings buildSettings() {
        List<String> names = new ArrayList<>(layerNames.length);
        for (ImString layerName : layerNames) {
            names.add(layerName.get());
        }
        return new EditorSettings(names, collisionMatrix);
    }

    private void applyRenderTuning() {
        if (postProcessSettings.isEmpty() || skySettings.isEmpty() || meshRenderSystem.isEmpty()) {
            return;
        }
        skySettings.get().setSkyIntensity(skyIntensity[0]);
        skySettings.get().setAmbientIntensity(ambientIntensity[0]);
        applyPostProcess(postProcessSettings.get());
        meshRenderSystem.get().setShadowDistance(shadowDistance[0]);
        meshRenderSystem.get().setClusteringEnabled(lightCullingEnabled);
        meshRenderSystem.get().setDepthPrepassEnabled(depthPrepass);
        meshRenderSystem.get().applyTuning(renderTuning);
    }

    private void applyPostProcess(PostProcessSettings postProcess) {
        postProcess.setGradeExposure(exposure[0]);
        postProcess.setVignetteStrength(vignette[0]);
        postProcess.setPixelPerfectEnabled(pixelPerfectEnabled);
        postProcess.setPixelPerfectBaseHeight(pixelPerfectBaseHeight.get());
        postProcess.setPixelPerfectBaseWidth(pixelPerfectBaseWidth.get());
        postProcess.setPixelPerfectIntegerScale(pixelPerfectIntegerScale);
        postProcess.setPixelPerfectAspect(stretchAspects[selectedAspectIndex]);
        postProcess.setBloomEnabled(bloomEnabled);
        postProcess.setBloom(postProcess.bloomThreshold(), postProcess.bloomKnee(), bloomIntensity[0]);
        postProcess.setAmbientOcclusionEnabled(ambientOcclusionEnabled);
        postProcess.setAmbientOcclusion(ambientOcclusionRadius[0], ambientOcclusionIntensity[0]);
        postProcess.setAntiAliasingEnabled(antiAliasEnabled);
        applyFog(postProcess);
    }

    private void applyFog(PostProcessSettings postProcess) {
        postProcess.setFogEnabled(fogEnabled);
        postProcess.setFogColor(fogColor[0], fogColor[1], fogColor[2]);
        postProcess.setFogDistance(fogDistanceStart[0], fogDistanceDensity[0]);
        postProcess.setFogHeight(fogHeightOrigin[0], fogHeightFalloff[0], fogHeightDensity[0]);
        postProcess.setFogShaderPath(fogShaderPath.get().trim());
    }
}
