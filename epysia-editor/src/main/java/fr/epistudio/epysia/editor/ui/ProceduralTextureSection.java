package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.procedural.CurveTextureLoader;
import fr.epistudio.epysia.assets.procedural.GradientTextureLoader;
import fr.epistudio.epysia.assets.procedural.NoiseTextureLoader;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.ui.kit.NumberFields;
import fr.epistudio.epysia.editor.ui.kit.SegmentedControl;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.editor.ui.widgets.CurveEditorWidget;
import fr.epistudio.epysia.editor.ui.widgets.GradientEditorWidget;
import fr.epistudio.epysia.vfx.lut.VfxCurve;
import imgui.ImGui;
import imgui.type.ImBoolean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class ProceduralTextureSection {

    private static final float PREVIEW_HEIGHT = 96.0f;
    private static final List<String> NOISE_KINDS = List.of("Value", "Fractal", "Cellular");

    private final GradientEditorWidget gradientEditor = new GradientEditorWidget();
    private final CurveEditorWidget curveEditor = new CurveEditorWidget();
    private final ProceduralPreview preview;
    private final Notifier notifier;
    private final Consumer<Path> onSaved;
    private ProceduralDocumentModel model = ProceduralDocumentModel.empty();

    public ProceduralTextureSection(ProceduralPreview preview, Notifier notifier, Consumer<Path> onSaved) {
        this.preview = preview;
        this.notifier = notifier;
        this.onSaved = onSaved;
    }

    public boolean render(Optional<Path> selectedAsset) {
        Optional<Path> file = selectedAsset.filter(ProceduralTextureSection::isProcedural);
        if (file.isEmpty()) {
            return false;
        }
        Path path = file.get();
        if (!model.matches(path)) {
            model = ProceduralDocumentModel.read(path);
        }
        Sections.title(path.getFileName().toString());
        renderPreview(path);
        boolean changed = renderBody();
        if (changed) {
            save(path);
        }
        return true;
    }

    private void renderPreview(Path path) {
        preview.textureFor(path, model).ifPresent(texture ->
                ImGui.image(texture, ImGui.getContentRegionAvailX(), EditorScale.of(PREVIEW_HEIGHT)));
    }

    private boolean renderBody() {
        return switch (model.kind()) {
            case NOISE -> renderNoise();
            case GRADIENT -> renderGradient();
            case CURVE -> renderCurve();
        };
    }

    private boolean renderNoise() {
        boolean changed = false;
        Texts.muted("Type");
        int selected = SegmentedControl.render("##noise-kind", NOISE_KINDS, model.noiseKindIndex());
        if (selected != model.noiseKindIndex()) {
            model.setNoiseKindIndex(selected);
            changed = true;
        }
        changed |= intRow("Taille", "##noise-size", model.size(), model::setSize);
        changed |= intRow("Graine", "##noise-seed", model.seed(), model::setSeed);
        changed |= intRow("Répétition", "##noise-period", model.period(), model::setPeriod);
        if (model.noiseKindName().equals("FRACTAL")) {
            changed |= intRow("Octaves", "##noise-octaves", model.octaves(), model::setOctaves);
            changed |= floatRow("Lacunarité", "##noise-lacunarity", model.lacunarity(), model::setLacunarity);
            changed |= floatRow("Gain", "##noise-gain", model.gain(), model::setGain);
        }
        changed |= boolRow("Inversé", "##noise-inverted", model.inverted(), model::setInverted);
        return changed;
    }

    private boolean renderGradient() {
        boolean changed = intRow("Largeur", "##gradient-width", model.size(), model::setSize);
        changed |= boolRow("Vertical", "##gradient-vertical", model.inverted(), model::setInverted);
        changed |= gradientEditor.render("##gradient-editor", model.gradient());
        return changed;
    }

    private boolean renderCurve() {
        boolean changed = intRow("Largeur", "##curve-width", model.size(), model::setSize);
        changed |= curveEditor.render("##curve-editor", model.curve());
        return changed;
    }

    private static boolean intRow(String label, String id, int current, IntConsumer apply) {
        Texts.muted(label);
        ImGui.setNextItemWidth(-1.0f);
        float edited = NumberFields.scalar(id, current, 1.0f, ImGui.calcItemWidth());
        if (Math.round(edited) == current) {
            return false;
        }
        apply.accept(Math.round(edited));
        return true;
    }

    private static boolean floatRow(String label, String id, float current, FloatConsumer apply) {
        Texts.muted(label);
        ImGui.setNextItemWidth(-1.0f);
        float edited = NumberFields.scalar(id, current, 0.05f, ImGui.calcItemWidth());
        if (Math.abs(edited - current) < 1.0e-5f) {
            return false;
        }
        apply.accept(edited);
        return true;
    }

    private static boolean boolRow(String label, String id, boolean current, BooleanConsumer apply) {
        ImBoolean flag = new ImBoolean(current);
        if (!ImGui.checkbox(id, flag)) {
            ImGui.sameLine();
            Texts.muted(label);
            return false;
        }
        ImGui.sameLine();
        Texts.muted(label);
        apply.accept(flag.get());
        return true;
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float value);
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private void save(Path path) {
        try {
            Files.writeString(path, model.toJson());
            preview.invalidate(path);
            onSaved.accept(path);
        } catch (IOException unwritable) {
            notifier.show("Écriture impossible: " + unwritable.getMessage());
        }
    }

    public static boolean isProcedural(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(NoiseTextureLoader.EXTENSION)
                || name.endsWith(GradientTextureLoader.EXTENSION)
                || name.endsWith(CurveTextureLoader.EXTENSION));
    }

    public interface ProceduralPreview {

        Optional<Integer> textureFor(Path path, ProceduralDocumentModel model);

        void invalidate(Path path);
    }
}
