package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.animation.AnimationBlendMode;
import fr.epistudio.epysia.animation.AnimationLayer;
import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.assets.epyclip.EpyClipReader;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.editor.assets.ClipCatalog;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import imgui.ImGui;
import imgui.type.ImString;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class AnimatorSection {

    private static final long CACHE_TTL_NANOS = 500_000_000L;
    private static final long NO_SKELETON_KEY = Long.MIN_VALUE;
    private static final int JOINT_NAME_CAPACITY = 128;

    private final Supplier<SceneDocument> activeDocument;
    private final ClipCatalog catalog;
    private final ImString maskRootBuffer = new ImString(JOINT_NAME_CAPACITY);
    private List<ClipCatalog.ClipEntry> cachedEntries = List.of();
    private long cachedChecksumKey = NO_SKELETON_KEY;
    private long cacheExpiryNanos;

    public AnimatorSection(Supplier<SceneDocument> activeDocument, Project project) {
        this.activeDocument = activeDocument;
        this.catalog = new ClipCatalog(project.rootDirectory());
    }

    public void render(GameObject gameObject, Animator animator) {
        OptionalLong skeletonChecksum = skeletonChecksum(gameObject);
        List<ClipCatalog.ClipEntry> entries = entriesFor(skeletonChecksum);
        renderBaseClipCombo(animator, entries);
        renderLayers(animator, entries);
    }

    private void renderBaseClipCombo(Animator animator, List<ClipCatalog.ClipEntry> entries) {
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_ANIMATION, "animator-animation"),
                previewLabel(animator.clipPath(), entries))) {
            return;
        }
        renderNoneOption(animator);
        for (ClipCatalog.ClipEntry entry : entries) {
            renderEntryOption(animator, entry);
        }
        ImGui.endCombo();
    }

    private void renderLayers(Animator animator, List<ClipCatalog.ClipEntry> entries) {
        ImGui.separator();
        ImGui.text(I18n.translate(TextKey.EDITOR_ANIMATOR_SECTION_LAYERS));
        for (int layerIndex = 0; layerIndex < animator.layers().size(); layerIndex++) {
            if (renderLayer(animator, animator.layers().get(layerIndex), layerIndex, entries)) {
                return;
            }
        }
        if (ImGui.button(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_ADD_LAYER, "animator-add-layer"))) {
            animator.addLayer();
            activeDocument.get().markDirty();
        }
    }

    private boolean renderLayer(Animator animator, AnimationLayer layer, int layerIndex,
                                List<ClipCatalog.ClipEntry> entries) {
        ImGui.pushID(layerIndex);
        boolean removed = renderLayerBody(animator, layer, layerIndex, entries);
        ImGui.popID();
        return removed;
    }

    private boolean renderLayerBody(Animator animator, AnimationLayer layer, int layerIndex,
                                    List<ClipCatalog.ClipEntry> entries) {
        if (!ImGui.treeNodeEx(I18n.translate(TextKey.EDITOR_ANIMATOR_SECTION_LAYER_NAME, layerIndex + 1))) {
            return false;
        }
        renderLayerClipCombo(layer, entries);
        renderLayerBlendMode(layer);
        renderLayerWeight(layer);
        renderLayerMaskRoot(layer);
        boolean removed = ImGui.button(
                I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_REMOVE_LAYER, "animator-remove-layer"));
        if (removed) {
            animator.removeLayer(layerIndex);
            activeDocument.get().markDirty();
        }
        ImGui.treePop();
        return removed;
    }

    private void renderLayerClipCombo(AnimationLayer layer, List<ClipCatalog.ClipEntry> entries) {
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_LAYER_CLIP, "animator-layer-clip"),
                previewLabel(layer.clipPath(), entries))) {
            return;
        }
        if (ImGui.selectable(I18n.translate(TextKey.EDITOR_ANIMATOR_SECTION_NONE), layer.clipPath().isEmpty())
                && !layer.clipPath().isEmpty()) {
            layer.setClipPath("");
            activeDocument.get().markDirty();
        }
        for (ClipCatalog.ClipEntry entry : entries) {
            renderLayerEntryOption(layer, entry);
        }
        ImGui.endCombo();
    }

    private void renderLayerEntryOption(AnimationLayer layer, ClipCatalog.ClipEntry entry) {
        boolean selected = entry.path().toString().equals(layer.clipPath());
        if (!ImGui.selectable(entry.name(), selected) || selected) {
            return;
        }
        layer.assignClip(entry.path().toString(), EpyClipReader.readFile(entry.path()));
        activeDocument.get().markDirty();
    }

    private void renderLayerBlendMode(AnimationLayer layer) {
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_LAYER_MODE, "animator-layer-mode"),
                layer.blendMode().name())) {
            return;
        }
        for (AnimationBlendMode mode : AnimationBlendMode.values()) {
            if (ImGui.selectable(mode.name(), mode == layer.blendMode()) && mode != layer.blendMode()) {
                layer.setBlendMode(mode);
                activeDocument.get().markDirty();
            }
        }
        ImGui.endCombo();
    }

    private void renderLayerWeight(AnimationLayer layer) {
        float[] weight = {layer.weight()};
        if (ImGui.sliderFloat(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_LAYER_WEIGHT, "animator-layer-weight"),
                weight, 0.0f, 1.0f)) {
            layer.setWeight(weight[0]);
            activeDocument.get().markDirty();
        }
    }

    private void renderLayerMaskRoot(AnimationLayer layer) {
        maskRootBuffer.set(layer.maskRootJoint());
        if (ImGui.inputText(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_LAYER_MASK_ROOT, "animator-layer-mask"),
                maskRootBuffer)) {
            layer.setMaskRootJoint(maskRootBuffer.get());
            activeDocument.get().markDirty();
        }
    }

    private void renderNoneOption(Animator animator) {
        if (ImGui.selectable(I18n.label(TextKey.EDITOR_ANIMATOR_SECTION_NONE, "animator-animation-none"),
                animator.clipPath().isEmpty()) && !animator.clipPath().isEmpty()) {
            animator.setClipPath("");
            activeDocument.get().markDirty();
        }
    }

    private void renderEntryOption(Animator animator, ClipCatalog.ClipEntry entry) {
        boolean selected = entry.path().toString().equals(animator.clipPath());
        if (ImGui.selectable(entry.name(), selected) && !selected) {
            assignClip(animator, entry);
        }
    }

    private void assignClip(Animator animator, ClipCatalog.ClipEntry entry) {
        Clip clip = EpyClipReader.readFile(entry.path());
        animator.assignClip(entry.path().toString(), clip);
        activeDocument.get().markDirty();
    }

    private List<ClipCatalog.ClipEntry> entriesFor(OptionalLong skeletonChecksum) {
        long now = System.nanoTime();
        long key = skeletonChecksum.orElse(NO_SKELETON_KEY);
        if (now < cacheExpiryNanos && key == cachedChecksumKey) {
            return cachedEntries;
        }
        cachedChecksumKey = key;
        cachedEntries = skeletonChecksum.isPresent()
                ? catalog.matching(skeletonChecksum.getAsLong())
                : catalog.all();
        cacheExpiryNanos = now + CACHE_TTL_NANOS;
        return cachedEntries;
    }

    private static String previewLabel(String clipPath, List<ClipCatalog.ClipEntry> entries) {
        if (clipPath.isEmpty()) {
            return I18n.translate(TextKey.EDITOR_ANIMATOR_SECTION_NONE);
        }
        for (ClipCatalog.ClipEntry entry : entries) {
            if (entry.path().toString().equals(clipPath)) {
                return entry.name();
            }
        }
        return Path.of(clipPath).getFileName().toString();
    }

    private static OptionalLong skeletonChecksum(GameObject gameObject) {
        Optional<Skeleton> skeleton = gameObject.getComponent(MeshRenderer.class)
                .flatMap(MeshRenderer::mesh)
                .flatMap(UploadedMesh::skeleton);
        return skeleton.map(present -> OptionalLong.of(present.nameChecksum()))
                .orElseGet(OptionalLong::empty);
    }
}
