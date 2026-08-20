package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddMaterialCommand;
import fr.epistudio.epysia.editor.command.builtin.SetMaterialPropertyCommand;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.command.builtin.SetMaterialsCommand;
import fr.epistudio.epysia.editor.command.builtin.SetMultiMeshMaterialCommand;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.AssetPaths;
import fr.epistudio.epysia.editor.assets.EditorAssetPaths;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.scene.serialization.MaterialJsonCodec;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialClassMetadata;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.render.material.ShaderMaterial;
import fr.epistudio.epysia.render.material.Uniform;
import fr.epistudio.epysia.render.material.UniformDisplay;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.mesh.UploadedSubmesh;
import fr.epistudio.epysia.editor.ui.kit.Disabled;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MaterialsSection {

    private static final float TEXTURE_WELL_SIZE = 32.0f;
    private static final float FLOAT_DRAG_STEP = 0.05f;
    private static final float SHADER_PATH_BUTTON_WIDTH = 180.0f;
    private static final Set<String> UNIT_RANGE_FIELDS = Set.of("metallic", "roughness", "alphaCutoff");
    private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".bmp", ".epynoise", ".epygradient", ".epycurve");
    private static final Set<String> SHADER_EXTENSIONS = Set.of(".glsl", ".vert", ".frag");
    private static final Set<String> SURFACE_SHADER_EXTENSIONS = Set.of(".surf.glsl");
    private static final Set<String> MATERIAL_EXTENSIONS = Set.of(".epymaterial");
    private static final String SURFACE_SHADER_SUFFIX = ".surf.glsl";
    private static final String DEFAULT_CUSTOM_VERTEX = "custom_default.vert.glsl";
    private static final String DEFAULT_CUSTOM_FRAGMENT = "custom_default.frag.glsl";
    private static final int SLOT_HEADER_FLAGS = ImGuiTreeNodeFlags.DefaultOpen
            | ImGuiTreeNodeFlags.SpanAvailWidth;

    private final Supplier<SceneDocument> activeDocument;
    private final ThumbnailCache thumbnails;
    private final AssetFilePicker filePicker;
    private final SurfaceUniformRows surfaceUniformRows;
    private String samplerCachePath = "";
    private Map<String, Integer> samplerCache = Map.of();
    private final MaterialJsonCodec materialCodec = new MaterialJsonCodec();
    private final Map<String, Material> assetMaterials = new HashMap<>();
    private final Map<String, String> savedDocuments = new HashMap<>();
    private final AssetLocator locator;
    private final Notifier notifier;

    public MaterialsSection(Supplier<SceneDocument> activeDocument, ThumbnailCache thumbnails,
                            Project project, Notifier notifier) {
        this.activeDocument = activeDocument;
        this.notifier = notifier;
        this.thumbnails = thumbnails;
        this.locator = project.locator();
        this.filePicker = new AssetFilePicker(project, thumbnails);
        ShaderLoader uniformLoader = ShaderLoader.autoDetect();
        uniformLoader.useProject(project::locator);
        this.surfaceUniformRows = new SurfaceUniformRows(uniformLoader, this::history,
                filePicker, locator);
    }

    private EditorHistory history() {
        return activeDocument.get().history();
    }

    public void render(MeshRenderer renderer) {
        ImGui.spacing();
        Texts.muted(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_TITLE));
        ImGui.separator();
        int slotCount = slotCount(renderer);
        for (int slot = 0; slot < slotCount; slot++) {
            renderSlot(renderer, slot);
        }
        filePicker.render();
    }

    private static int slotCount(MeshRenderer renderer) {
        int fromMesh = renderer.mesh().map(MaterialsSection::highestSlot).orElse(0);
        return Math.max(1, Math.max(fromMesh, renderer.materials().size()));
    }

    private static int highestSlot(UploadedMesh mesh) {
        int highest = 0;
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            highest = Math.max(highest, submesh.materialSlot() + 1);
        }
        return highest;
    }

    private void renderSlot(MeshRenderer renderer, int slot) {
        ImGui.pushID("material-slot-" + slot);
        Optional<Material> material = renderer.materialForSlot(slot);
        String label = "Slot " + slot + (material.isPresent()
                ? "  (" + material.get().getClass().getSimpleName() + ")"
                : "  (" + I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_EMPTY) + ")");
        if (ImGui.treeNodeEx(label, SLOT_HEADER_FLAGS)) {
            renderSlotBody(renderer, slot, material);
            ImGui.treePop();
        }
        ImGui.popID();
    }

    private void renderSlotBody(MeshRenderer renderer, int slot, Optional<Material> material) {
        if (material.isEmpty()) {
            if (ImGui.button(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_ADD_MATERIAL,
                    "materials-add"))) {
                history().execute(new AddMaterialCommand(renderer, slot));
            }
            return;
        }
        Consumer<Material> replace = replacement -> replaceSlot(renderer, slot, replacement);
        renderAssetRow(material.get(), replace);
        renderMaterialBody(material.get(), replace);
    }

    private void renderMaterialBody(Material material, Consumer<Material> replace) {
        renderTypeCombo(material, replace);
        if (material instanceof ShaderMaterial shaderMaterial) {
            renderShaderMaterialEditor(shaderMaterial, replace);
        } else {
            renderMaterialEditor(material);
        }
        saveAssetMaterialIfChanged(material);
    }

    public void render(MultiMeshRenderer renderer) {
        ImGui.spacing();
        Texts.muted(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_MATERIAL));
        ImGui.separator();
        renderMultiMeshMaterial(renderer);
        filePicker.render();
    }

    private void renderMultiMeshMaterial(MultiMeshRenderer renderer) {
        Material material = renderer.materialOrNull();
        if (material == null) {
            Texts.muted(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_NONE_RESOLVED));
            return;
        }
        if (material.assetPath().isEmpty()) {
            ImGui.textWrapped(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_PICK_HINT)
                    + " A material that is not an asset is not saved with the scene.");
            return;
        }
        renderMaterialBody(material, replacement -> replaceMultiMeshMaterial(renderer, material, replacement));
    }

    private void replaceMultiMeshMaterial(MultiMeshRenderer renderer, Material current, Material replacement) {
        replacement.setAssetPath(current.assetPath());
        assetMaterials.put(current.assetPath(), replacement);
        history().execute(new SetMultiMeshMaterialCommand(renderer, replacement));
    }

    private void renderAssetRow(Material material, Consumer<Material> replace) {
        ImGui.pushID("material-asset");
        String label = material.assetPath().isEmpty()
                ? I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_INLINE)
                : AssetPaths.fileNameOf(material.assetPath());
        if (ImGui.button(label, EditorScale.of(SHADER_PATH_BUTTON_WIDTH), 0.0f)) {
            filePicker.open(MATERIAL_EXTENSIONS, false, path -> assignAssetMaterial(path, replace));
        }
        if (ImGui.isItemHovered() && !material.assetPath().isEmpty()) {
            ImGui.setTooltip(material.assetPath());
        }
        ImGui.sameLine();
        Texts.muted(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_MATERIAL_ASSET));
        if (!material.assetPath().isEmpty()) {
            ImGui.sameLine();
            if (ImGui.smallButton(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_DETACH,
                    "materials-detach"))) {
                detachAssetMaterial(material, replace);
            }
        }
        ImGui.popID();
    }

    private void assignAssetMaterial(String path, Consumer<Material> replace) {
        if (path.isEmpty()) {
            return;
        }
        replace.accept(assetMaterials.computeIfAbsent(path, this::readAssetMaterial));
    }

    private Material readAssetMaterial(String path) {
        try {
            Material material = materialCodec.readSingle(Files.readString(fileOf(path)))
                    .orElseThrow(() -> new EpysiaException("Not a material document: " + path));
            material.setAssetPath(path);
            savedDocuments.put(path, materialCodec.writeSingle(material));
            return material;
        } catch (IOException error) {
            throw new EpysiaException("Failed to read material asset " + path + ": " + error.getMessage());
        }
    }

    private void detachAssetMaterial(Material material, Consumer<Material> replace) {
        Material copy = materialCodec.readSingle(materialCodec.writeSingle(material))
                .orElseGet(LitMaterial::new);
        copy.setAssetPath("");
        replace.accept(copy);
    }

    private void saveAssetMaterialIfChanged(Material material) {
        if (material.assetPath().isEmpty() || ImGui.isAnyItemActive()) {
            return;
        }
        String document = materialCodec.writeSingle(material);
        String known = savedDocuments.putIfAbsent(material.assetPath(), document);
        if (known == null || known.equals(document)) {
            return;
        }
        writeAssetDocument(material.assetPath(), document);
    }

    private void writeAssetDocument(String path, String document) {
        Optional<Path> file = resolveAssetFile(path);
        if (file.isEmpty()) {
            reportSaveFailure(path, document, "the asset path does not resolve to a project file");
            return;
        }
        try {
            Path target = file.get();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, document);
            savedDocuments.put(path, document);
        } catch (IOException error) {
            reportSaveFailure(path, document, error.getMessage());
        }
    }

    private Optional<Path> resolveAssetFile(String path) {
        return AssetUri.parse(path)
                .filter(uri -> !uri.isEmpty())
                .flatMap(locator::file)
                .or(() -> locator.projectRoot().map(root -> root.resolve(path)));
    }

    private void reportSaveFailure(String path, String document, String reason) {
        savedDocuments.put(path, document);
        notifier.show("Could not save material " + path + ": " + reason);
    }

    private void renderTypeCombo(Material material, Consumer<Material> replace) {
        boolean custom = material instanceof ShaderMaterial;
        String preview = custom
                ? I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_TYPE_CUSTOM_SHADER)
                : I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_TYPE_LIT);
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_TYPE, "materials-type"),
                preview)) {
            return;
        }
        if (ImGui.selectable(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_TYPE_LIT,
                "materials-type-lit"), !custom) && custom) {
            replace.accept(new LitMaterial());
        }
        if (ImGui.selectable(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_TYPE_CUSTOM_SHADER,
                "materials-type-custom-shader"), custom) && !custom) {
            replace.accept(new ShaderMaterial(DEFAULT_CUSTOM_VERTEX, DEFAULT_CUSTOM_FRAGMENT));
        }
        ImGui.endCombo();
    }

    private void replaceSlot(MeshRenderer renderer, int slot, Material replacement) {
        List<Material> materials = new ArrayList<>(renderer.materials());
        while (materials.size() <= slot) {
            materials.add(new LitMaterial());
        }
        materials.set(slot, replacement);
        history().execute(new SetMaterialsCommand(renderer, materials));
    }

    private void renderShaderMaterialEditor(ShaderMaterial material, Consumer<Material> replace) {
        renderShaderPathRow(TextKey.EDITOR_MATERIALS_SECTION_VERTEX_SHADER, material.vertexShaderPath(),
                path -> replace.accept(copyWithPaths(material, path, material.fragmentShaderPath())));
        renderShaderPathRow(TextKey.EDITOR_MATERIALS_SECTION_FRAGMENT_SHADER, material.fragmentShaderPath(),
                path -> replace.accept(copyWithPaths(material, material.vertexShaderPath(), path)));
        renderSamplerRows(material);
        renderTransparentRow(material);
        renderDoubleSidedRow(material);
        surfaceUniformRows.render(material,
                List.of(material.vertexShaderPath(), material.fragmentShaderPath()));
    }

    private static ShaderMaterial copyWithPaths(ShaderMaterial source, String vertexPath, String fragmentPath) {
        ShaderMaterial copy = new ShaderMaterial(vertexPath, fragmentPath);
        copy.setTransparent(source.transparent());
        copy.setDoubleSided(source.doubleSided());
        copy.setAssetPath(source.assetPath());
        for (Map.Entry<String, String> entry : source.texturePaths().entrySet()) {
            copy.setTexturePath(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, ShaderUniformValue> entry : source.surfaceUniforms().all().entrySet()) {
            copy.surfaceUniforms().set(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private void renderShaderPathRow(TextKey labelKey, String currentPath, Consumer<String> onPathChosen) {
        ImGui.pushID(labelKey.key());
        if (ImGui.button(shaderPathButtonLabel(currentPath), EditorScale.of(SHADER_PATH_BUTTON_WIDTH), 0.0f)) {
            filePicker.open(SHADER_EXTENSIONS, false, onPathChosen);
        }
        if (ImGui.isItemHovered() && !currentPath.isEmpty()) {
            ImGui.setTooltip(currentPath);
        }
        acceptShaderDrop(currentPath, onPathChosen);
        ImGui.sameLine();
        ImGui.textUnformatted(I18n.translate(labelKey));
        ImGui.popID();
    }

    private static String shaderPathButtonLabel(String currentPath) {
        if (currentPath.isEmpty()) {
            return I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_NONE);
        }
        return AssetPaths.fileNameOf(currentPath);
    }

    private void acceptShaderDrop(String currentPath, Consumer<String> onPathChosen) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String droppedPath = ImGui.acceptDragDropPayload(AssetMimeTypes.SHADER, String.class);
        if (droppedPath != null && !droppedPath.equals(currentPath)) {
            onPathChosen.accept(droppedPath);
        }
        ImGui.endDragDropTarget();
    }

    private void renderSamplerRows(ShaderMaterial material) {
        for (Map.Entry<String, Integer> sampler : detectedSamplers(material).entrySet()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_SAMPLER_BOUND,
                    sampler.getKey(), sampler.getValue()));
        }
    }

    private Map<String, Integer> detectedSamplers(ShaderMaterial material) {
        if (material.fragmentShaderPath().equals(samplerCachePath)) {
            return samplerCache;
        }
        samplerCachePath = material.fragmentShaderPath();
        samplerCache = parseSamplers(fileOf(material.fragmentShaderPath()));
        return samplerCache;
    }

    private Path fileOf(String reference) {
        return locator.file(reference).orElseGet(() -> Path.of(AssetPaths.fileNameOf(reference)));
    }

    private static Map<String, Integer> parseSamplers(Path fragmentPath) {
        if (!fragmentPath.isAbsolute() || !Files.isRegularFile(fragmentPath)) {
            return Map.of();
        }
        try {
            return new TreeMap<>(MaterialClassMetadata.samplerBindings(Files.readString(fragmentPath)));
        } catch (IOException error) {
            return Map.of();
        }
    }

    private void renderMaterialEditor(Material material) {
        if (material instanceof LitMaterial lit) {
            renderSurfaceShaderRow(lit);
        }
        for (Field field : MaterialFields.uniformFields(material.getClass())) {
            renderUniformRow(material, field);
        }
        for (Field field : MaterialFields.textureFields(material.getClass())) {
            renderTextureRow(material, field);
        }
        renderTransparentRow(material);
        renderDoubleSidedRow(material);
        if (material instanceof LitMaterial lit) {
            renderReceiveShadowsRow(lit);
            renderAnimatedShadowRow(lit);
            surfaceUniformRows.render(lit, List.of(lit.surfaceShaderPath()));
        }
    }

    private void renderReceiveShadowsRow(LitMaterial material) {
        boolean current = material.receiveShadows();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_RECEIVE_SHADOWS), current)) {
            history().execute(new SetMaterialPropertyCommand(material,
                    SetMaterialPropertyCommand.Target.RECEIVE_SHADOWS, "", current, !current));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_RECEIVE_SHADOWS_TOOLTIP));
        }
    }

    private void renderAnimatedShadowRow(LitMaterial material) {
        boolean hasSurfaceShader = !material.surfaceShaderPath().isEmpty();
        Disabled.push(!hasSurfaceShader);
        boolean current = material.animatedShadow();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_ANIMATE_SHADOW,
                "materials-animate-shadow"), current)) {
            history().execute(new SetMaterialPropertyCommand(material,
                    SetMaterialPropertyCommand.Target.ANIMATED_SHADOW, "", current, !current));
        }
        Disabled.pop(!hasSurfaceShader);
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_ANIMATE_SHADOW_TOOLTIP));
        }
    }

    private void renderSurfaceShaderRow(LitMaterial material) {
        ImGui.pushID("surface-shader");
        String currentPath = material.surfaceShaderPath();
        if (ImGui.button(shaderPathButtonLabel(currentPath), EditorScale.of(SHADER_PATH_BUTTON_WIDTH), 0.0f)) {
            filePicker.open(SURFACE_SHADER_EXTENSIONS, true,
                    pickedPath -> executeSurfaceShaderChange(material, currentPath, pickedPath));
        }
        if (ImGui.isItemHovered() && !currentPath.isEmpty()) {
            ImGui.setTooltip(currentPath);
        }
        acceptSurfaceShaderDrop(material, currentPath);
        renderSurfaceShaderLabel(material, currentPath);
        ImGui.popID();
    }

    private void renderSurfaceShaderLabel(LitMaterial material, String currentPath) {
        ImGui.sameLine();
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_SURFACE_SHADER));
        if (currentPath.isEmpty()) {
            return;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_CLEAR,
                "materials-surface-shader-clear"))) {
            executeSurfaceShaderChange(material, currentPath, "");
        }
    }

    private void acceptSurfaceShaderDrop(LitMaterial material, String currentPath) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String droppedPath = ImGui.acceptDragDropPayload(AssetMimeTypes.SHADER, String.class);
        if (droppedPath != null && droppedPath.endsWith(SURFACE_SHADER_SUFFIX) && !droppedPath.equals(currentPath)) {
            executeSurfaceShaderChange(material, currentPath, droppedPath);
        }
        ImGui.endDragDropTarget();
    }

    private void executeSurfaceShaderChange(LitMaterial material, String before, String after) {
        if (before.equals(after)) {
            return;
        }
        history().execute(new SetMaterialPropertyCommand(material,
                SetMaterialPropertyCommand.Target.SURFACE_SHADER, "", before, after));
    }

    private void renderUniformRow(Material material, Field field) {
        Object value = MaterialFields.read(material, field);
        switch (value) {
            case Vector3f vector -> renderVectorRow(material, field, vector);
            case Float number -> renderFloatRow(material, field, number);
            case Integer number -> renderOptionRow(material, field, number);
            case null, default -> {
            }
        }
    }

    private void renderVectorRow(Material material, Field field, Vector3f vector) {
        if (field.getAnnotation(Uniform.class).display() == UniformDisplay.COLOR) {
            renderColorRow(material, field, vector);
            return;
        }
        float[] components = {vector.x, vector.y, vector.z};
        if (ImGui.dragFloat3(labelFor(field), components, FLOAT_DRAG_STEP)
                && (vector.x != components[0] || vector.y != components[1] || vector.z != components[2])) {
            executeUniformChange(material, field, new Vector3f(vector),
                    new Vector3f(components[0], components[1], components[2]));
        }
    }

    private void renderOptionRow(Material material, Field field, int current) {
        String[] options = field.getAnnotation(Uniform.class).options();
        if (options.length == 0) {
            return;
        }
        ImInt value = new ImInt(current);
        if (ImGui.combo(labelFor(field), value, options) && value.get() != current) {
            executeUniformChange(material, field, current, value.get());
        }
    }

    private void renderColorRow(Material material, Field field, Vector3f vector) {
        float[] components = {vector.x, vector.y, vector.z};
        if (ImGui.colorEdit3(labelFor(field), components)
                && (vector.x != components[0] || vector.y != components[1] || vector.z != components[2])) {
            executeUniformChange(material, field, new Vector3f(vector),
                    new Vector3f(components[0], components[1], components[2]));
        }
    }

    private void renderFloatRow(Material material, Field field, float current) {
        float[] value = {current};
        boolean changed = UNIT_RANGE_FIELDS.contains(field.getName())
                ? ImGui.sliderFloat(labelFor(field), value, 0.0f, 1.0f)
                : ImGui.dragFloat(labelFor(field), value, FLOAT_DRAG_STEP);
        if (changed && Float.compare(value[0], current) != 0) {
            executeUniformChange(material, field, current, value[0]);
        }
    }

    private void executeUniformChange(Material material, Field field, Object before, Object after) {
        history().execute(new SetMaterialPropertyCommand(material,
                SetMaterialPropertyCommand.Target.UNIFORM, field.getName(), before, after));
    }

    private void renderTextureRow(Material material, Field field) {
        ImGui.pushID("texture-" + field.getName());
        String currentPath = material.texturePath(field.getName()).orElse("");
        renderTextureWell(material, field, currentPath);
        acceptTextureDrop(material, field, currentPath);
        renderTextureLabel(material, field, currentPath);
        ImGui.popID();
    }

    private void renderTextureWell(Material material, Field field, String currentPath) {
        OptionalInt thumbnail = currentPath.isEmpty() ? OptionalInt.empty()
                : thumbnails.get(EditorAssetPaths.absolute(locator, currentPath));
        boolean clicked;
        if (thumbnail.isPresent()) {
            clicked = ImGui.imageButton("##texture-well", thumbnail.getAsInt(), EditorScale.of(TEXTURE_WELL_SIZE),
                    EditorScale.of(TEXTURE_WELL_SIZE));
        } else {
            clicked = ImGui.button(currentPath.isEmpty()
                            ? I18n.label(TextKey.EDITOR_MATERIALS_SECTION_NONE,
                                    "materials-texture-none-" + field.getName())
                            : "…",
                    EditorScale.of(TEXTURE_WELL_SIZE) + 8.0f, EditorScale.of(TEXTURE_WELL_SIZE));
        }
        if (ImGui.isItemHovered() && !currentPath.isEmpty()) {
            ImGui.setTooltip(currentPath);
        }
        if (clicked) {
            openTexturePicker(material, field, currentPath);
        }
    }

    private void openTexturePicker(Material material, Field field, String currentPath) {
        filePicker.open(TEXTURE_EXTENSIONS, true, pickedPath -> {
            if (!pickedPath.equals(currentPath)) {
                executeTextureChange(material, field, currentPath, pickedPath);
            }
        });
    }

    private void acceptTextureDrop(Material material, Field field, String currentPath) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String droppedPath = ImGui.acceptDragDropPayload(AssetMimeTypes.TEXTURE, String.class);
        String stored = droppedPath == null ? null : EditorAssetPaths.stored(locator, droppedPath);
        if (stored != null && !stored.equals(currentPath)) {
            executeTextureChange(material, field, currentPath, stored);
        }
        ImGui.endDragDropTarget();
    }

    private void renderTextureLabel(Material material, Field field, String currentPath) {
        ImGui.sameLine();
        ImGui.textUnformatted(labelFor(field));
        if (currentPath.isEmpty()) {
            return;
        }
        ImGui.sameLine();
        Texts.muted(AssetPaths.fileNameOf(currentPath));
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_CLEAR,
                "materials-texture-clear-" + field.getName()))) {
            executeTextureChange(material, field, currentPath, "");
        }
    }

    private void executeTextureChange(Material material, Field field, String before, String after) {
        history().execute(new SetMaterialPropertyCommand(material,
                SetMaterialPropertyCommand.Target.TEXTURE, field.getName(), before, after));
    }

    private void renderTransparentRow(Material material) {
        boolean current = material.transparent();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_TRANSPARENT,
                "materials-transparent"), current)) {
            history().execute(new SetMaterialPropertyCommand(material,
                    SetMaterialPropertyCommand.Target.TRANSPARENT, "", current, !current));
        }
        if (material.alphaScissor()) {
            ImGui.sameLine();
            ImGui.textDisabled("(alpha cutoff wins: drawn opaque)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(I18n.translate(TextKey.EDITOR_MATERIALS_SECTION_ALPHA_SCISSOR_TOOLTIP));
            }
        }
    }

    private void renderDoubleSidedRow(Material material) {
        boolean current = material.doubleSided();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_MATERIALS_SECTION_DOUBLE_SIDED,
                "materials-double-sided"), current)) {
            history().execute(new SetMaterialPropertyCommand(material,
                    SetMaterialPropertyCommand.Target.DOUBLE_SIDED, "", current, !current));
        }
    }

    private static String labelFor(Field field) {
        String name = field.getName();
        StringBuilder label = new StringBuilder();
        for (char character : name.toCharArray()) {
            if (Character.isUpperCase(character) && !label.isEmpty()) {
                label.append(' ');
            }
            label.append(character);
        }
        return capitalize(label.toString());
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text
                : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
