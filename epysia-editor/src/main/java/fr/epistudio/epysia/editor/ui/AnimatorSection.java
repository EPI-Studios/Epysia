package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.assets.epyclip.EpyClipReader;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.editor.assets.ClipCatalog;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import imgui.ImGui;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class AnimatorSection {

    private static final String NONE_LABEL = "(none)";
    private static final long CACHE_TTL_NANOS = 500_000_000L;
    private static final long NO_SKELETON_KEY = Long.MIN_VALUE;

    private final Supplier<SceneDocument> activeDocument;
    private final ClipCatalog catalog;
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
        if (!ImGui.beginCombo("Animation", previewLabel(animator, entries))) {
            return;
        }
        renderNoneOption(animator);
        for (ClipCatalog.ClipEntry entry : entries) {
            renderEntryOption(animator, entry);
        }
        ImGui.endCombo();
    }

    private void renderNoneOption(Animator animator) {
        if (ImGui.selectable(NONE_LABEL, animator.clipPath().isEmpty()) && !animator.clipPath().isEmpty()) {
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

    private static String previewLabel(Animator animator, List<ClipCatalog.ClipEntry> entries) {
        if (animator.clipPath().isEmpty()) {
            return NONE_LABEL;
        }
        for (ClipCatalog.ClipEntry entry : entries) {
            if (entry.path().toString().equals(animator.clipPath())) {
                return entry.name();
            }
        }
        return Path.of(animator.clipPath()).getFileName().toString();
    }

    private static OptionalLong skeletonChecksum(GameObject gameObject) {
        Optional<Skeleton> skeleton = gameObject.getComponent(MeshRenderer.class)
                .flatMap(MeshRenderer::mesh)
                .flatMap(UploadedMesh::skeleton);
        return skeleton.map(present -> OptionalLong.of(present.nameChecksum()))
                .orElseGet(OptionalLong::empty);
    }
}
