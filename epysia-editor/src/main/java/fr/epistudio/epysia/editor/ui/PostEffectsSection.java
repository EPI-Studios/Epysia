package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.render.postfx.PostEffect;
import fr.epistudio.epysia.render.postfx.PostEffectInsertionPoint;
import fr.epistudio.epysia.render.shader.ShaderUniformParser;
import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import fr.epistudio.epysia.render.postfx.PostEffectStack;
import fr.epistudio.epysia.render.shader.ShaderUniformDeclaration;
import fr.epistudio.epysia.render.shader.ShaderUniformKind;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class PostEffectsSection {

    private static final Set<String> POST_EFFECT_EXTENSIONS = Set.of(".post.glsl");
    private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".bmp");
    private static final String POST_EFFECT_SUFFIX = ".post.glsl";
    private static final float FLOAT_DRAG_STEP = 0.01f;
    private static final float TEXTURE_WELL_SIZE = 32.0f;
    private static final int EFFECT_HEADER_FLAGS = ImGuiTreeNodeFlags.DefaultOpen
            | ImGuiTreeNodeFlags.SpanAvailWidth;

    private final AssetFilePicker filePicker;
    private final ThumbnailCache thumbnails;
    private final Map<String, CachedParse> parseCache = new HashMap<>();

    public PostEffectsSection(Project project, ThumbnailCache thumbnails) {
        this.filePicker = new AssetFilePicker(project, thumbnails);
        this.thumbnails = thumbnails;
    }

    public void render(PostEffectStack stack, Runnable onChanged) {
        List<PostEffect> snapshot = new ArrayList<>(stack.effects());
        if (snapshot.isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_POST_EFFECTS_SECTION_EMPTY));
        }
        for (int index = 0; index < snapshot.size(); index++) {
            renderEffect(stack, snapshot.get(index), index, snapshot.size(), onChanged);
        }
        if (ImGui.button(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_ADD,
                "post-effects-add"))) {
            filePicker.open(POST_EFFECT_EXTENSIONS, false, path -> addEffect(stack, path, onChanged));
        }
        filePicker.render();
    }

    private void addEffect(PostEffectStack stack, String path, Runnable onChanged) {
        String fileName = Path.of(path).getFileName().toString();
        String baseName = fileName.endsWith(POST_EFFECT_SUFFIX)
                ? fileName.substring(0, fileName.length() - POST_EFFECT_SUFFIX.length()) : fileName;
        stack.add(uniqueName(stack, baseName), path, PostEffectInsertionPoint.AFTER_TONEMAP);
        onChanged.run();
    }

    private static String uniqueName(PostEffectStack stack, String baseName) {
        if (stack.effect(baseName).isEmpty()) {
            return baseName;
        }
        int counter = 2;
        while (stack.effect(baseName + counter).isPresent()) {
            counter++;
        }
        return baseName + counter;
    }

    private void renderEffect(PostEffectStack stack, PostEffect effect, int index, int count, Runnable onChanged) {
        ImGui.pushID("post-effect-" + effect.name());
        if (ImGui.checkbox("##enabled", effect.enabled())) {
            effect.setEnabled(!effect.enabled());
            onChanged.run();
        }
        ImGui.sameLine();
        boolean expanded = ImGui.treeNodeEx(effect.name(), EFFECT_HEADER_FLAGS);
        if (expanded) {
            renderEffectBody(stack, effect, index, count, onChanged);
            ImGui.treePop();
        }
        ImGui.popID();
    }

    private void renderEffectBody(PostEffectStack stack, PostEffect effect, int index, int count,
                                  Runnable onChanged) {
        renderOrderButtons(stack, effect, index, count, onChanged);
        renderInsertionCombo(effect, onChanged);
        ImGui.textDisabled(Path.of(effect.shaderPath()).getFileName().toString());
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(effect.shaderPath());
        }
        renderUniformRows(effect, onChanged);
    }

    private void renderOrderButtons(PostEffectStack stack, PostEffect effect, int index, int count,
                                    Runnable onChanged) {
        if (ImGui.button(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_UP,
                "post-effects-up")) && index > 0) {
            stack.reorder(effect.name(), index - 1);
            onChanged.run();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_DOWN,
                "post-effects-down")) && index < count - 1) {
            stack.reorder(effect.name(), index + 1);
            onChanged.run();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_REMOVE,
                "post-effects-remove"))) {
            stack.remove(effect.name());
            onChanged.run();
        }
    }

    private void renderInsertionCombo(PostEffect effect, Runnable onChanged) {
        Optional<PostEffectInsertionPoint> declared = declaredInsertionFor(effect.shaderPath());
        if (declared.isPresent()) {
            renderDeclaredInsertion(declared.get());
            return;
        }
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_INSERTION_POINT,
                "post-effects-insertion-point"), I18n.translate(insertionPointKey(effect.insertionPoint())))) {
            return;
        }
        for (PostEffectInsertionPoint point : PostEffectInsertionPoint.values()) {
            if (ImGui.selectable(I18n.label(insertionPointKey(point),
                    "post-effects-insertion-" + point.name()), point == effect.insertionPoint())
                    && point != effect.insertionPoint()) {
                effect.setInsertionPoint(point);
                onChanged.run();
            }
        }
        ImGui.endCombo();
    }

    private static void renderDeclaredInsertion(PostEffectInsertionPoint declared) {
        ImGui.beginDisabled(true);
        ImGui.labelText(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_INSERTION_POINT,
                "post-effects-declared-insertion-point"), I18n.translate(insertionPointKey(declared)));
        ImGui.endDisabled();
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_POST_EFFECTS_SECTION_SHADER_INSERTION,
                declared.annotationToken()));
    }

    private static TextKey insertionPointKey(PostEffectInsertionPoint point) {
        return switch (point) {
            case BEFORE_TONEMAP -> TextKey.EDITOR_POST_EFFECTS_SECTION_INSERTION_BEFORE_TONEMAP;
            case AFTER_TONEMAP -> TextKey.EDITOR_POST_EFFECTS_SECTION_INSERTION_AFTER_TONEMAP;
        };
    }

    private void renderUniformRows(PostEffect effect, Runnable onChanged) {
        Optional<ParsedSource> parsed = parsedSourceFor(effect.shaderPath());
        if (parsed.isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_POST_EFFECTS_SECTION_PARSE_ERROR));
            return;
        }
        for (ShaderUniformDeclaration declaration : parsed.get().declarations()) {
            ImGui.pushID("uniform-" + declaration.name());
            renderUniformRow(effect, declaration, onChanged);
            ImGui.popID();
        }
    }

    private void renderUniformRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        if (declaration.isSampler()) {
            renderTextureRow(effect, declaration, onChanged);
            return;
        }
        if (declaration.isArray()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_POST_EFFECTS_SECTION_ARRAY_SCRIPT_CONTROLLED,
                    declaration.name(), declaration.arraySize()));
            return;
        }
        renderScalarRow(effect, declaration, onChanged);
    }

    private void renderScalarRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        switch (declaration.kind()) {
            case FLOAT -> renderFloatRow(effect, declaration, onChanged);
            case INT -> renderIntRow(effect, declaration, onChanged);
            case BOOL -> renderBoolRow(effect, declaration, onChanged);
            case VECTOR2, VECTOR3, VECTOR4 -> renderVectorRow(effect, declaration, onChanged);
            case MATRIX4 -> ImGui.textDisabled(I18n.translate(
                    TextKey.EDITOR_POST_EFFECTS_SECTION_SCRIPT_CONTROLLED, declaration.name()));
            case SAMPLER2D -> {
            }
        }
    }

    private void renderFloatRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        float current = effect.uniformValue(declaration.name())
                .map(value -> value instanceof ShaderUniformValue.FloatValue number ? number.value() : 0.0f)
                .orElse(0.0f);
        float[] holder = {current};
        if (ImGui.dragFloat(declaration.name(), holder, FLOAT_DRAG_STEP)
                && Float.compare(holder[0], current) != 0) {
            effect.setFloat(declaration.name(), holder[0]);
            onChanged.run();
        }
    }

    private void renderIntRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        int current = effect.uniformValue(declaration.name())
                .map(value -> value instanceof ShaderUniformValue.IntValue number ? number.value() : 0)
                .orElse(0);
        int[] holder = {current};
        if (ImGui.dragInt(declaration.name(), holder) && holder[0] != current) {
            effect.setInt(declaration.name(), holder[0]);
            onChanged.run();
        }
    }

    private void renderBoolRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        boolean current = effect.uniformValue(declaration.name())
                .map(value -> value instanceof ShaderUniformValue.BoolValue flag && flag.value())
                .orElse(false);
        if (ImGui.checkbox(declaration.name(), current)) {
            effect.setBool(declaration.name(), !current);
            onChanged.run();
        }
    }

    private void renderVectorRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        float[] components = currentComponents(effect, declaration);
        boolean changed = declaration.color()
                ? renderColorWidget(declaration, components)
                : renderDragWidget(declaration, components);
        if (changed) {
            applyComponents(effect, declaration, components);
            onChanged.run();
        }
    }

    private boolean renderColorWidget(ShaderUniformDeclaration declaration, float[] components) {
        return declaration.kind() == ShaderUniformKind.VECTOR3
                ? ImGui.colorEdit3(declaration.name(), components)
                : ImGui.colorEdit4(declaration.name(), components);
    }

    private boolean renderDragWidget(ShaderUniformDeclaration declaration, float[] components) {
        return switch (declaration.kind()) {
            case VECTOR2 -> ImGui.dragFloat2(declaration.name(), components, FLOAT_DRAG_STEP);
            case VECTOR3 -> ImGui.dragFloat3(declaration.name(), components, FLOAT_DRAG_STEP);
            default -> ImGui.dragFloat4(declaration.name(), components, FLOAT_DRAG_STEP);
        };
    }

    private float[] currentComponents(PostEffect effect, ShaderUniformDeclaration declaration) {
        int componentCount = switch (declaration.kind()) {
            case VECTOR2 -> 2;
            case VECTOR3 -> 3;
            default -> 4;
        };
        float[] components = new float[componentCount];
        effect.uniformValue(declaration.name()).ifPresent(value -> fillComponents(components, value));
        return components;
    }

    private static void fillComponents(float[] components, ShaderUniformValue value) {
        float[] source = switch (value) {
            case ShaderUniformValue.Vector2Value vector -> new float[] {vector.x(), vector.y()};
            case ShaderUniformValue.Vector3Value vector -> new float[] {vector.x(), vector.y(), vector.z()};
            case ShaderUniformValue.Vector4Value vector -> new float[] {vector.x(), vector.y(), vector.z(), vector.w()};
            default -> new float[0];
        };
        System.arraycopy(source, 0, components, 0, Math.min(source.length, components.length));
    }

    private void applyComponents(PostEffect effect, ShaderUniformDeclaration declaration, float[] components) {
        ShaderUniformValue value = switch (declaration.kind()) {
            case VECTOR2 -> new ShaderUniformValue.Vector2Value(components[0], components[1]);
            case VECTOR3 -> new ShaderUniformValue.Vector3Value(components[0], components[1], components[2]);
            default -> new ShaderUniformValue.Vector4Value(components[0], components[1], components[2], components[3]);
        };
        effect.setUniformValue(declaration.name(), value);
    }

    private void renderTextureRow(PostEffect effect, ShaderUniformDeclaration declaration, Runnable onChanged) {
        String currentPath = effect.uniformValue(declaration.name())
                .map(value -> value instanceof ShaderUniformValue.TextureValue texture ? texture.path() : "")
                .orElse("");
        renderTextureWell(effect, declaration, currentPath, onChanged);
        acceptTextureDrop(effect, declaration, currentPath, onChanged);
        renderTextureLabel(effect, declaration, currentPath, onChanged);
    }

    private void renderTextureWell(PostEffect effect, ShaderUniformDeclaration declaration,
                                   String currentPath, Runnable onChanged) {
        OptionalInt thumbnail = currentPath.isEmpty() ? OptionalInt.empty() : thumbnails.get(currentPath);
        boolean clicked = thumbnail.isPresent()
                ? ImGui.imageButton(thumbnail.getAsInt(), TEXTURE_WELL_SIZE, TEXTURE_WELL_SIZE)
                : ImGui.button(currentPath.isEmpty()
                                ? I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_NONE,
                                        "post-effects-texture-none-" + declaration.name())
                                : "…",
                        TEXTURE_WELL_SIZE + 8.0f, TEXTURE_WELL_SIZE);
        if (ImGui.isItemHovered() && !currentPath.isEmpty()) {
            ImGui.setTooltip(currentPath);
        }
        if (clicked) {
            filePicker.open(TEXTURE_EXTENSIONS, true, pickedPath -> {
                effect.setTexture(declaration.name(), pickedPath);
                onChanged.run();
            });
        }
    }

    private void acceptTextureDrop(PostEffect effect, ShaderUniformDeclaration declaration,
                                   String currentPath, Runnable onChanged) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String droppedPath = ImGui.acceptDragDropPayload(AssetMimeTypes.TEXTURE, String.class);
        if (droppedPath != null && !droppedPath.equals(currentPath)) {
            effect.setTexture(declaration.name(), droppedPath);
            onChanged.run();
        }
        ImGui.endDragDropTarget();
    }

    private void renderTextureLabel(PostEffect effect, ShaderUniformDeclaration declaration,
                                    String currentPath, Runnable onChanged) {
        ImGui.sameLine();
        ImGui.textUnformatted(declaration.name());
        if (currentPath.isEmpty()) {
            return;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_POST_EFFECTS_SECTION_CLEAR,
                "post-effects-clear-texture-" + declaration.name()))) {
            effect.setTexture(declaration.name(), "");
            onChanged.run();
        }
    }

    private Optional<ParsedSource> parsedSourceFor(String shaderPath) {
        return cachedParse(shaderPath).parsed();
    }

    private Optional<PostEffectInsertionPoint> declaredInsertionFor(String shaderPath) {
        return cachedParse(shaderPath).declaredInsertion();
    }

    private CachedParse cachedParse(String shaderPath) {
        Path file = Path.of(shaderPath);
        long modified = modifiedMillis(file);
        CachedParse cached = parseCache.get(shaderPath);
        if (cached != null && cached.modifiedMillis() == modified) {
            return cached;
        }
        CachedParse fresh = parseFile(file, modified);
        parseCache.put(shaderPath, fresh);
        return fresh;
    }

    private static CachedParse parseFile(Path file, long modified) {
        try {
            String source = Files.readString(file);
            return new CachedParse(modified, Optional.of(ShaderUniformParser.parse(source)),
                    PostEffectInsertionPoint.declaredIn(source));
        } catch (IOException | RuntimeException error) {
            return new CachedParse(modified, Optional.empty(), Optional.empty());
        }
    }

    private static long modifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    private record CachedParse(long modifiedMillis, Optional<ParsedSource> parsed,
                               Optional<PostEffectInsertionPoint> declaredInsertion) {
    }
}
