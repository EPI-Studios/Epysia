package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.AssetPaths;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.command.builtin.SetSurfaceUniformCommand;
import fr.epistudio.epysia.render.shader.SurfaceUniformHost;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderUniformDeclaration;
import fr.epistudio.epysia.render.shader.ShaderUniformDefaults;
import fr.epistudio.epysia.render.shader.ShaderUniformParser;
import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.editor.assets.EditorAssetPaths;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class SurfaceUniformRows {

    private static final float DRAG_STEP = 0.01f;
    private static final float TEXTURE_BUTTON_WIDTH = 180.0f;
    private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".bmp");
    private static final int COLOR_EDIT_FLAGS = ImGuiColorEditFlags.NoInputs
            | ImGuiColorEditFlags.NoLabel | ImGuiColorEditFlags.HDR | ImGuiColorEditFlags.Float;

    private final ShaderLoader shaderLoader;
    private final Supplier<EditorHistory> history;
    private final AssetFilePicker filePicker;
    private final AssetLocator locator;
    private final Map<String, List<ShaderUniformDeclaration>> declarationCache = new HashMap<>();

    public SurfaceUniformRows(ShaderLoader shaderLoader, Supplier<EditorHistory> history,
                              AssetFilePicker filePicker, AssetLocator locator) {
        this.shaderLoader = shaderLoader;
        this.history = history;
        this.filePicker = filePicker;
        this.locator = locator;
    }

    public void invalidate() {
        declarationCache.clear();
    }

    public void render(SurfaceUniformHost material, List<String> shaderPaths) {
        List<ShaderUniformDeclaration> declarations = declarationsFor(shaderPaths);
        if (declarations.isEmpty()) {
            return;
        }
        ImGui.separator();
        ImGui.textDisabled("Shader Parameters");
        for (ShaderUniformDeclaration declaration : declarations) {
            renderRow(material, declaration);
        }
    }

    private List<ShaderUniformDeclaration> declarationsFor(List<String> shaderPaths) {
        List<String> present = shaderPaths.stream().filter(path -> !path.isEmpty()).toList();
        if (present.isEmpty()) {
            return List.of();
        }
        return declarationCache.computeIfAbsent(String.join("|", present), ignored -> parse(present));
    }

    private List<ShaderUniformDeclaration> parse(List<String> shaderPaths) {
        try {
            List<ParsedSource> parsed = shaderPaths.stream()
                    .map(path -> ShaderUniformParser.parse(shaderLoader.load(path).source()))
                    .toList();
            return ShaderUniformParser.merge(parsed).declarations();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private void renderRow(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        if (declaration.isArray()) {
            return;
        }
        if (declaration.isSampler()) {
            renderTexture(material, declaration);
            return;
        }
        ImGui.pushID(declaration.name());
        switch (declaration.kind()) {
            case FLOAT -> renderFloat(material, declaration);
            case VECTOR3 -> renderVector3(material, declaration);
            case VECTOR2 -> renderVector(material, declaration, 2);
            case VECTOR4 -> renderVector(material, declaration, 4);
            case INT -> renderInt(material, declaration);
            case BOOL -> renderBool(material, declaration);
            default -> ImGui.labelText(declaration.name(), "(unsupported)");
        }
        ImGui.popID();
    }

    private void renderTexture(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        ImGui.pushID(declaration.name());
        String currentPath = currentTexturePath(material, declaration);
        if (ImGui.button(textureButtonLabel(currentPath), EditorScale.of(TEXTURE_BUTTON_WIDTH), 0.0f)) {
            filePicker.open(TEXTURE_EXTENSIONS, true,
                    picked -> commitTexture(material, declaration, currentPath, picked));
        }
        if (ImGui.isItemHovered() && !currentPath.isEmpty()) {
            ImGui.setTooltip(currentPath);
        }
        acceptTextureDrop(material, declaration, currentPath);
        ImGui.sameLine();
        ImGui.textUnformatted(declaration.name());
        ImGui.popID();
    }

    private void acceptTextureDrop(SurfaceUniformHost material, ShaderUniformDeclaration declaration, String currentPath) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String droppedPath = ImGui.acceptDragDropPayload(AssetMimeTypes.TEXTURE, String.class);
        if (droppedPath != null) {
            commitTexture(material, declaration, currentPath, EditorAssetPaths.stored(locator, droppedPath));
        }
        ImGui.endDragDropTarget();
    }

    private void commitTexture(SurfaceUniformHost material, ShaderUniformDeclaration declaration,
                               String before, String after) {
        if (before.equals(after)) {
            return;
        }
        history.get().execute(new SetSurfaceUniformCommand(material, declaration.name(),
                Optional.of(new ShaderUniformValue.TextureValue(before)),
                new ShaderUniformValue.TextureValue(after)));
    }

    private static String currentTexturePath(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        if (material.surfaceUniforms().value(declaration.name())
                .orElse(null) instanceof ShaderUniformValue.TextureValue texture) {
            return texture.path();
        }
        return declaration.hasDefault() ? declaration.defaultText() : "";
    }

    private static String textureButtonLabel(String currentPath) {
        return currentPath.isEmpty() ? "None" : AssetPaths.fileNameOf(currentPath);
    }

    private void renderFloat(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        float current = currentFloat(material, declaration);
        float[] value = {current};
        if (ImGui.dragFloat(declaration.name(), value, DRAG_STEP) && value[0] != current) {
            commit(material, declaration, new ShaderUniformValue.FloatValue(value[0]));
        }
    }

    private void renderInt(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        int current = (int) currentFloat(material, declaration);
        int[] value = {current};
        if (ImGui.dragInt(declaration.name(), value) && value[0] != current) {
            commit(material, declaration, new ShaderUniformValue.IntValue(value[0]));
        }
    }

    private void renderBool(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        boolean current = currentFloat(material, declaration) != 0.0f;
        if (ImGui.checkbox(declaration.name(), current)) {
            commit(material, declaration, new ShaderUniformValue.BoolValue(!current));
        }
    }

    private void renderVector3(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        float[] components = currentComponents(material, declaration, 3);
        float[] edited = components.clone();
        boolean picked = ImGui.colorEdit3("##swatch", edited, COLOR_EDIT_FLAGS);
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean dragged = ImGui.dragFloat3(declaration.name(), edited, DRAG_STEP);
        if ((picked || dragged) && changed(components, edited)) {
            commit(material, declaration,
                    new ShaderUniformValue.Vector3Value(edited[0], edited[1], edited[2]));
        }
    }

    private void renderVector(SurfaceUniformHost material, ShaderUniformDeclaration declaration, int size) {
        float[] components = currentComponents(material, declaration, size);
        float[] edited = components.clone();
        boolean dragged = size == 2
                ? ImGui.dragFloat2(declaration.name(), edited, DRAG_STEP)
                : ImGui.dragFloat4(declaration.name(), edited, DRAG_STEP);
        if (dragged && changed(components, edited)) {
            commit(material, declaration, size == 2
                    ? new ShaderUniformValue.Vector2Value(edited[0], edited[1])
                    : new ShaderUniformValue.Vector4Value(edited[0], edited[1], edited[2], edited[3]));
        }
    }

    private static boolean changed(float[] before, float[] after) {
        for (int index = 0; index < before.length; index++) {
            if (before[index] != after[index]) {
                return true;
            }
        }
        return false;
    }

    private void commit(SurfaceUniformHost material, ShaderUniformDeclaration declaration,
                        ShaderUniformValue after) {
        ShaderUniformValue before = resolved(material, declaration)
                .orElse(new ShaderUniformValue.FloatValue(0.0f));
        history.get().execute(new SetSurfaceUniformCommand(material, declaration.name(),
                Optional.of(before), after));
    }

    private Optional<ShaderUniformValue> resolved(SurfaceUniformHost material,
                                                  ShaderUniformDeclaration declaration) {
        Optional<ShaderUniformValue> assigned = material.surfaceUniforms().value(declaration.name());
        return assigned.isPresent() ? assigned : ShaderUniformDefaults.of(declaration);
    }

    private float currentFloat(SurfaceUniformHost material, ShaderUniformDeclaration declaration) {
        return switch (resolved(material, declaration).orElse(null)) {
            case ShaderUniformValue.FloatValue value -> value.value();
            case ShaderUniformValue.IntValue value -> value.value();
            case ShaderUniformValue.BoolValue value -> value.value() ? 1.0f : 0.0f;
            case null, default -> 0.0f;
        };
    }

    private float[] currentComponents(SurfaceUniformHost material, ShaderUniformDeclaration declaration,
                                      int size) {
        float[] components = new float[size];
        switch (resolved(material, declaration).orElse(null)) {
            case ShaderUniformValue.Vector2Value value -> fill(components, value.x(), value.y(), 0.0f, 0.0f);
            case ShaderUniformValue.Vector3Value value -> fill(components, value.x(), value.y(), value.z(), 0.0f);
            case ShaderUniformValue.Vector4Value value -> fill(components, value.x(), value.y(), value.z(), value.w());
            case null, default -> fill(components, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        return components;
    }

    private static void fill(float[] destination, float x, float y, float z, float w) {
        float[] source = {x, y, z, w};
        System.arraycopy(source, 0, destination, 0, destination.length);
    }
}
