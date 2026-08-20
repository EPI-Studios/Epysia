package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.ui.kit.Notices;
import fr.epistudio.epysia.editor.ui.settings.CollisionMatrixSection;
import fr.epistudio.epysia.editor.ui.settings.InputActionsSection;
import fr.epistudio.epysia.editor.ui.settings.NetworkSection;
import fr.epistudio.epysia.editor.ui.settings.ProjectIconSection;
import fr.epistudio.epysia.editor.ui.settings.ScriptingSection;
import fr.epistudio.epysia.editor.ui.settings.SteamSection;
import fr.epistudio.epysia.editor.ui.settings.ViewportSection;
import fr.epistudio.epysia.editor.ui.settings.WindowSection;
import fr.epistudio.epysia.editor.ui.settings.SettingsChrome;
import fr.epistudio.epysia.editor.ui.kit.Rows;
import fr.epistudio.epysia.editor.ui.kit.SearchField;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.editor.ui.kit.Toolbars;
import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.input.action.InputBinding;
import fr.epistudio.epysia.project.EditorSettings;
import fr.epistudio.epysia.project.NetworkSettings;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectQuality;
import fr.epistudio.epysia.project.RenderSettings;
import fr.epistudio.epysia.project.SteamSettings;
import fr.epistudio.epysia.project.RenderTuning;
import fr.epistudio.epysia.render.environment.SkySettings;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.postfx.PostEffectStack;
import fr.epistudio.epysia.render.postfx.FogShaderComposer;
import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import fr.epistudio.epysia.render.postfx.StretchAspect;
import fr.epistudio.epysia.editor.ui.kit.Sliders;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
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

public final class SettingsDialog implements SettingsChrome {

    private static final String POPUP_TITLE = "Settings";
    private static final float DIALOG_WIDTH = 860.0f;
    private static final float DIALOG_HEIGHT = 620.0f;
    private static final float DIALOG_PADDING = 14.0f;
    private static final float BODY_PADDING = 14.0f;
    private static final float SEARCH_WIDTH = 240.0f;
    private static final float HEADER_GAP = 8.0f;
    private static final float GROUP_GAP = 10.0f;
    private static final float MARKER_WIDTH = 2.0f;
    private static final float MARKER_INSET = 5.0f;
    private static final float ITEM_TEXT_INSET = 10.0f;
    private static final float ITEM_PADDING_Y = 5.0f;
    private static final float SELECTED_ALPHA = 0.16f;
    private static final float HOVER_ALPHA = 0.5f;
    private static final int WINDOW_FLAGS = imgui.flag.ImGuiWindowFlags.NoSavedSettings
            | imgui.flag.ImGuiWindowFlags.NoTitleBar;
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
    private static final float CATEGORY_PANE_WIDTH = 190.0f;
    private static final float CATEGORY_INDENT = 8.0f;
    private static final float LABEL_COLUMN_DESIGN_WIDTH = 190.0f;
    private static final float MINIMUM_SLIDER_WIDTH = 80.0f;
    private static final float SLIDER_VALUE_COLUMN_WIDTH = 56.0f;
    private static final float FOOTER_BUTTON_DESIGN_WIDTH = 96.0f;
    private static final float DEPTH_RATIO_WARNING = 20000.0f;
    private static final int SEARCH_CAPACITY = 64;

    private record Category(String group, String name, Runnable body) {
    }

    public interface ViewportTuningListener {
        void onViewportTuningChanged(float overlayThickness, float gridFadeDistance);
    }

    private final Consumer<EditorSettings> onSettingsSaved;
    private final Consumer<EditorPreferences> onPreferencesSaved;
    private final Consumer<NetworkSettings> onNetworkSaved;
    private final Consumer<SteamSettings> onSteamSaved;
    private final Consumer<RenderSettings> onRenderSaved;
    private final ViewportTuningListener viewportTuningListener;
    private final float[] gravity = new float[3];
    private final ImInt fixedTimestepHertz = new ImInt();
    private final ImInt shadowMapSize = new ImInt();
    private final ImInt cascadeCount = new ImInt();
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
    private final float[] uiScale = new float[1];
    private boolean uiScaleAutomatic;
    private final int[] autosaveInterval = new int[1];
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
    private Optional<ScriptingSection> scriptingSection = Optional.empty();
    private Optional<Supplier<PostEffectStack>> globalPostEffectStack = Optional.empty();
    private Runnable onPostEffectsChanged = () -> {
    };
    private final InputActionsSection inputActionsSection = new InputActionsSection(this);
    private final CollisionMatrixSection collisionMatrixSection = new CollisionMatrixSection(this);
    private final NetworkSection networkSection = new NetworkSection(this);
    private final WindowSection windowSection = new WindowSection(this);
    private final ProjectIconSection projectIconSection;
    private final ViewportSection viewportSection;
    private final SteamSection steamSection = new SteamSection(this);
    private final ImString searchFilter = new ImString(SEARCH_CAPACITY);
    private final List<Category> categories = buildCategories();
    private int selectedCategory;
    private int matchedRows;
    private String captionPending = "";
    private String currentCaption = "";
    private Project project;
    private boolean openRequested;

    public SettingsDialog(Consumer<EditorSettings> onSettingsSaved,
                          Consumer<EditorPreferences> onPreferencesSaved,
                          Consumer<NetworkSettings> onNetworkSaved,
                          Consumer<SteamSettings> onSteamSaved,
                          Consumer<RenderSettings> onRenderSaved,
                          ViewportTuningListener viewportTuningListener,
                          IconWidgets icons) {
        this.projectIconSection = new ProjectIconSection(icons);
        this.onSettingsSaved = onSettingsSaved;
        this.onPreferencesSaved = onPreferencesSaved;
        this.onNetworkSaved = onNetworkSaved;
        this.onSteamSaved = onSteamSaved;
        this.onRenderSaved = onRenderSaved;
        this.viewportTuningListener = viewportTuningListener;
        this.viewportSection = new ViewportSection(this, viewportTuningListener::onViewportTuningChanged);
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

    public void attachScripting(ScriptingSection section) {
        scriptingSection = Optional.of(section);
    }

    public void attachPostEffects(PostEffectsSection section, Supplier<PostEffectStack> stack, Runnable onChanged) {
        postEffectsSection = Optional.of(section);
        globalPostEffectStack = Optional.of(stack);
        onPostEffectsChanged = onChanged;
    }

    public void openFor(EditorSettings settings, EditorPreferences preferences, Project openedProject,
                        ProjectQuality quality, List<InputAction> actions, NetworkSettings network,
                        SteamSettings steam, RenderSettings render) {
        collisionMatrixSection.load(settings);
        loadPreferences(preferences);
        loadQuality(quality);
        networkSection.load(network);
        steamSection.load(steam);
        windowSection.loadRender(render);
        inputActionsSection.load(actions);
        project = openedProject;
        loadRenderTuning();
        openRequested = true;
    }

    private void loadPreferences(EditorPreferences preferences) {
        basePreferences = preferences;
        viewportSection.load(preferences);
        uiScaleAutomatic = preferences.uiScale() <= EditorPreferences.AUTOMATIC_UI_SCALE;
        uiScale[0] = uiScaleAutomatic ? EditorScale.factor() : preferences.uiScale();
        autosaveInterval[0] = preferences.autosaveIntervalSeconds();
        autosaveEnabled = preferences.autosaveEnabled();
        detachableWindows = preferences.detachableWindows();
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
        windowSection.load(quality);
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
                shadowMapSize.get(), cascadeCount.get(), windowSection.title(),
                windowSection.width(), windowSection.height(), windowSection.verticalSync(),
                windowSection.maximumFrameRate(),
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
        centerNextWindow();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(DIALOG_PADDING),
                EditorScale.of(DIALOG_PADDING));
        boolean open = ImGui.beginPopupModal(POPUP_TITLE, WINDOW_FLAGS);
        ImGui.popStyleVar();
        if (!open) {
            return;
        }
        renderHeader();
        renderPanes();
        renderFooter();
        ImGui.endPopup();
    }

    private static void centerNextWindow() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing,
                0.5f, 0.5f);
        ImGui.setNextWindowSize(EditorScale.of(DIALOG_WIDTH), EditorScale.of(DIALOG_HEIGHT),
                ImGuiCond.Appearing);
    }

    private void renderHeader() {
        Sections.title(POPUP_TITLE);
        ImGui.sameLine();
        float searchWidth = EditorScale.of(SEARCH_WIDTH);
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(),
                ImGui.getContentRegionMaxX() - searchWidth));
        SearchField.render("##settings-search", "Search settings", searchFilter, searchWidth);
        ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
    }

    private void renderPanes() {
        float bodyHeight = -ImGui.getFrameHeightWithSpacing()
                - ImGui.getStyle().getItemSpacingY() * 2.0f;
        ImGui.pushStyleColor(ImGuiCol.ChildBg, EditorStyle.COLOR_SUNKEN_BACKGROUND);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(CATEGORY_INDENT),
                EditorScale.of(CATEGORY_INDENT));
        ImGui.beginChild("##settings-categories", EditorScale.of(CATEGORY_PANE_WIDTH),
                bodyHeight, ImGuiChildFlags.AlwaysUseWindowPadding,
                imgui.flag.ImGuiWindowFlags.NoScrollbar);
        renderCategoryList();
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(BODY_PADDING),
                EditorScale.of(BODY_PADDING));
        ImGui.beginChild("##settings-body", 0.0f, bodyHeight,
                ImGuiChildFlags.AlwaysUseWindowPadding);
        renderBody();
        ImGui.endChild();
        ImGui.popStyleVar();
    }

    private void renderCategoryList() {
        String lastGroup = "";
        for (int index = 0; index < categories.size(); index++) {
            Category category = categories.get(index);
            if (!category.group().equals(lastGroup)) {
                lastGroup = category.group();
                renderGroupCaption(lastGroup, index > 0);
            }
            renderCategoryItem(index, category);
        }
    }

    private static void renderGroupCaption(String group, boolean spaced) {
        if (spaced) {
            ImGui.dummy(0.0f, EditorScale.of(GROUP_GAP));
        }
        Sections.caption(group.toUpperCase());
    }

    private void renderCategoryItem(int index, Category category) {
        boolean active = index == selectedCategory && !filtering();
        float width = ImGui.getContentRegionAvailX();
        float height = ImGui.getTextLineHeight() + EditorScale.of(ITEM_PADDING_Y) * 2.0f;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##category-" + category.group() + "-" + category.name(), width, height);
        if (ImGui.isItemClicked()) {
            selectedCategory = index;
            searchFilter.set("");
        }
        paintCategoryItem(category.name(), active, left, top, width, height);
    }

    private static void paintCategoryItem(String label, boolean active, float left, float top,
                                          float width, float height) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        int fill = active
                ? EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTED_ALPHA)
                : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER,
                        ImGui.isItemHovered() ? HOVER_ALPHA : 0.0f);
        drawList.addRectFilled(left, top, left + width, top + height, fill,
                EditorStyle.frameRounding());
        if (active) {
            drawList.addRectFilled(left, top + EditorScale.of(MARKER_INSET),
                    left + EditorScale.ofAtLeastOne(MARKER_WIDTH),
                    top + height - EditorScale.of(MARKER_INSET), EditorStyle.COLOR_ACCENT);
        }
        drawList.addText(left + EditorScale.of(ITEM_TEXT_INSET),
                top + (height - ImGui.getTextLineHeight()) * 0.5f,
                active ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED, label);
    }

    private void renderBody() {
        if (filtering()) {
            renderFilteredBody();
            return;
        }
        Category category = categories.get(Math.clamp(selectedCategory, 0, categories.size() - 1));
        Sections.caption(category.group().toUpperCase());
        Sections.title(category.name());
        ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
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
            Texts.muted(I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_NO_MATCH, searchFilter.get()));
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
            Texts.muted(captionPending);
            captionPending = "";
        }
    }

    @Override
    public boolean skipWhileFiltering() {
        if (!filtering()) {
            return false;
        }
        if (!categoryMatchesSearch()) {
            return true;
        }
        noteMatch();
        return false;
    }

    @Override
    public boolean filtering() {
        return !searchFilter.get().trim().isEmpty();
    }

    @Override
    public boolean accepts(String label) {
        if (!filtering()) {
            return true;
        }
        if (!categoryMatchesSearch() && !contains(label)) {
            return false;
        }
        noteMatch();
        return true;
    }

    @Override
    public float labelColumnWidth() {
        return EditorScale.of(LABEL_COLUMN_DESIGN_WIDTH);
    }

    @Override
    public float sliderWidth() {
        return Math.max(EditorScale.of(MINIMUM_SLIDER_WIDTH),
                ImGui.getContentRegionAvailX() - EditorScale.of(SLIDER_VALUE_COLUMN_WIDTH));
    }

    @Override
    public void row(String label, Runnable control) {
        if (!accepts(label)) {
            return;
        }
        Rows.of(label, labelColumnWidth(), control);
    }

    @Override
    public boolean toggleRow(String label, boolean value) {
        if (!accepts(label)) {
            return value;
        }
        return Rows.toggle(label, labelColumnWidth(), value);
    }

    @Override
    public void hint(TextKey key) {
        if (filtering()) {
            return;
        }
        Texts.muted(I18n.translate(key));
    }

    private void hint(TextKey key, Object... arguments) {
        if (filtering()) {
            return;
        }
        Texts.muted(I18n.translate(key, arguments));
    }

    private List<Category> buildCategories() {
        List<Category> built = new ArrayList<>();
        built.add(new Category("Application", "General", this::renderApplicationCategory));
        built.add(new Category("Application", "Project", this::renderProjectIdentity));
        built.add(new Category("Application", "Libraries", this::renderLibrariesCategory));
        built.add(new Category("Application", "Scripting", this::renderScriptingCategory));
        built.add(new Category("Display", "Window", this::renderWindowCategory));
        built.add(new Category("Display", "Stretch", this::renderStretchCategory));
        built.add(new Category("Input", "Actions", this::renderInputActionsCategory));
        built.add(new Category("Editor", "Interface", this::renderInterfaceCategory));
        built.add(new Category("Editor", "Camera", this::renderEditorCameraCategory));
        built.add(new Category("Editor", "Scene view", this::renderSceneViewCategory));
        built.add(new Category("Editor", "Viewport", this::renderViewportCategory));
        built.add(new Category("Editor", "Workflow", this::renderWorkflowCategory));
        built.add(new Category("Network", "Session", this::renderNetworkCategory));
        built.add(new Category("Network", "Steam", this::renderSteamCategory));
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
        row("Window title", () -> ImGui.inputText("##value", windowSection.titleBuffer()));
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

    private void renderScriptingCategory() {
        if (filtering() || scriptingSection.isEmpty()) {
            return;
        }
        if (project == null) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_NO_PROJECT);
            return;
        }
        scriptingSection.get().render(project);
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
        row("Icon", () -> projectIconSection.render(project));
    }

    public void dispose() {
        projectIconSection.dispose();
    }


    private void renderWindowCategory() {
        windowSection.render();
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
        inputActionsSection.render();
    }

    public List<InputAction> buildInputActions() {
        return inputActionsSection.build();
    }

    private void renderEditorCameraCategory() {
        viewportSection.renderCamera();
    }

    private void renderSceneViewCategory() {
        viewportSection.renderSceneView();
    }

    private void renderViewportCategory() {
        viewportSection.renderViewport();
    }

    private void renderInterfaceCategory() {
        uiScaleAutomatic = toggleRow(I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_UI_SCALE_AUTOMATIC),
                uiScaleAutomatic);
        if (uiScaleAutomatic) {
            hint(TextKey.EDITOR_SETTINGS_DIALOG_UI_SCALE_DETECTED);
        } else {
            row(I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_UI_SCALE), this::renderUiScaleSlider);
        }
        hint(TextKey.EDITOR_SETTINGS_DIALOG_RESTART_HELP);
    }

    private void renderUiScaleSlider() {
        ImGui.sliderFloat("##value", uiScale,
                EditorPreferences.MIN_UI_SCALE, EditorPreferences.MAX_UI_SCALE, "%.2fx");
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


    private void renderSteamCategory() {
        steamSection.render();
    }

    private void renderNetworkCategory() {
        networkSection.render();
    }

    private void renderPhysicsCategory() {
        row("Gravity", () -> ImGui.dragFloat3("##value", gravity, 0.05f, -200.0f, 200.0f));
        row("Fixed timestep (Hz)", () -> ImGui.dragInt("##value", fixedTimestepHertz.getData(), 1.0f,
                ProjectQuality.MIN_FIXED_TIMESTEP_HERTZ, ProjectQuality.MAX_FIXED_TIMESTEP_HERTZ));
        hint(TextKey.EDITOR_SETTINGS_DIALOG_PHYSICS_HELP);
    }

    private void renderLayersCategory() {
        collisionMatrixSection.render();
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
        ImGui.dummy(0.0f, EditorScale.of(HEADER_GAP));
        String cancel = I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_CANCEL);
        String save = I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_SAVE);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + footerIndent());
        if (ImGui.button(cancel, footerButtonWidth(), Toolbars.buttonHeight())) {
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_ON_ACCENT);
        boolean confirmed = ImGui.button(save, footerButtonWidth(), Toolbars.buttonHeight());
        ImGui.popStyleColor(3);
        if (confirmed) {
            save();
            ImGui.closeCurrentPopup();
        }
    }

    private static float footerButtonWidth() {
        return EditorScale.of(FOOTER_BUTTON_DESIGN_WIDTH);
    }

    private static float footerIndent() {
        float buttons = footerButtonWidth() * 2.0f + ImGui.getStyle().getItemSpacingX();
        return Math.max(0.0f, ImGui.getContentRegionAvailX() - buttons);
    }

    private void save() {
        applyRenderTuning();
        onSettingsSaved.accept(buildSettings());
        onNetworkSaved.accept(networkSection.build());
        onSteamSaved.accept(steamSection.build());
        onRenderSaved.accept(windowSection.buildRender());
        onPreferencesSaved.accept(new EditorPreferences(viewportSection.cameraSpeed(), viewportSection.cameraBoost(),
                autosaveEnabled, autosaveInterval[0],
                basePreferences.gridVisible(), basePreferences.snapEnabled(),
                viewportSection.overlayThickness(), viewportSection.gridFadeDistance(),
                gpuPreferences[selectedGpuIndex], detachableWindows,
                basePreferences.shaderNodePreviewsEnabled(), basePreferences.viewport2DMode(),
                viewportSection.sceneNear(), viewportSection.sceneFar(),
                viewportSection.sceneFieldOfView(), viewportSection.lookSensitivity(),
                viewportSection.invertLookY(),
                uiScaleAutomatic ? EditorPreferences.AUTOMATIC_UI_SCALE : uiScale[0]));
    }

    private EditorSettings buildSettings() {
        return collisionMatrixSection.build();
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
