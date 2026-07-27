package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.vfx.ParticleBurst;
import fr.epistudio.epysia.vfx.ParticleEffect;
import imgui.ImGui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class VfxSection {

    private static final String GRAPH_EXTENSION = ".epygraph";
    private static final String VFX_KIND_MARKER = "\"kind\": \"VFX\"";
    private static final long CACHE_TTL_NANOS = 2_000_000_000L;
    private static final int KIND_PEEK_BYTES = 256;

    private final Supplier<SceneDocument> activeDocument;
    private final Path projectRoot;
    private List<Path> cachedGraphs = List.of();
    private long cacheExpiryNanos;

    public VfxSection(Supplier<SceneDocument> activeDocument, Project project) {
        this.activeDocument = activeDocument;
        this.projectRoot = project.rootDirectory();
    }

    public void render(ParticleEffect effect) {
        renderBurstFit(effect);
        List<Path> graphs = projectGraphs();
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_VFX_SECTION_EFFECT_GRAPH, "vfx-effect-graph"),
                previewLabel(effect))) {
            return;
        }
        renderNoneOption(effect);
        for (Path graph : graphs) {
            renderGraphOption(effect, graph);
        }
        ImGui.endCombo();
    }

    private static void renderBurstFit(ParticleEffect effect) {
        float duration = effect.durationSeconds();
        for (ParticleBurst burst : effect.burstsExceedingDuration()) {
            ImGui.textColored(EditorStyle.COLOR_WARNING, String.format(Locale.ROOT,
                    I18n.translate(TextKey.EDITOR_VFX_SECTION_BURST_WARNING, "%.2f", "%d", "%d", "%.2f", "%.2f"),
                    burst.timeSeconds(), burst.repeatsWithin(duration), burst.repeatCount(),
                    burst.requiredDurationSeconds(), duration));
        }
    }

    private String previewLabel(ParticleEffect effect) {
        if (effect.graphPath().isEmpty()) {
            return I18n.translate(TextKey.EDITOR_VFX_SECTION_NONE);
        }
        return stemOf(Path.of(effect.graphPath()));
    }

    private void renderNoneOption(ParticleEffect effect) {
        if (ImGui.selectable(I18n.label(TextKey.EDITOR_VFX_SECTION_NONE, "vfx-effect-none"),
                effect.graphPath().isEmpty()) && !effect.graphPath().isEmpty()) {
            effect.setGraphPath("");
            activeDocument.get().markDirty();
        }
    }

    private void renderGraphOption(ParticleEffect effect, Path graph) {
        String absolute = graph.toAbsolutePath().toString();
        boolean selected = absolute.equals(effect.graphPath());
        if (ImGui.selectable(stemOf(graph), selected) && !selected) {
            effect.setGraphPath(absolute);
            activeDocument.get().markDirty();
        }
    }

    private List<Path> projectGraphs() {
        long now = System.nanoTime();
        if (now < cacheExpiryNanos) {
            return cachedGraphs;
        }
        cachedGraphs = scanProjectGraphs();
        cacheExpiryNanos = now + CACHE_TTL_NANOS;
        return cachedGraphs;
    }

    private List<Path> scanProjectGraphs() {
        List<Path> graphs = new ArrayList<>();
        try (Stream<Path> files = Files.walk(projectRoot)) {
            files.filter(path -> path.toString().endsWith(GRAPH_EXTENSION))
                    .filter(VfxSection::isVfxGraph)
                    .forEach(graphs::add);
        } catch (IOException unreadable) {
            return List.of();
        }
        return List.copyOf(graphs);
    }

    private static boolean isVfxGraph(Path graph) {
        try {
            byte[] bytes = Files.readAllBytes(graph);
            int peek = Math.min(bytes.length, KIND_PEEK_BYTES);
            return new String(bytes, 0, peek).contains(VFX_KIND_MARKER);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static String stemOf(Path graph) {
        String fileName = graph.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
