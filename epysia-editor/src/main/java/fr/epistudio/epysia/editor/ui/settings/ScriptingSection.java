package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.langpack.LanguagePack;
import fr.epistudio.epysia.editor.langpack.LanguagePackCatalogue;
import fr.epistudio.epysia.editor.langpack.LanguagePackTask;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;

import java.util.Optional;

public final class ScriptingSection {

    private final Notifier notifier;
    private final Runnable onPacksChanged;
    private final LanguagePackTask task = new LanguagePackTask();
    private LanguagePackCatalogue catalogue = LanguagePackCatalogue.empty();
    private boolean requested;

    public ScriptingSection(Notifier notifier, Runnable onPacksChanged) {
        this.notifier = notifier;
        this.onPacksChanged = onPacksChanged;
    }

    public void render(Project project) {
        drainOutcome();
        requestCatalogueOnce();
        renderHints();
        renderRefresh();
        renderCatalogue(project);
    }

    private void requestCatalogueOnce() {
        if (requested) {
            return;
        }
        requested = true;
        task.fetchCatalogue();
    }

    private void renderHints() {
        Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_HINT,
                Project.LANGUAGE_PACKS_FILENAME));
        Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_CACHE_HINT,
                Project.LANGUAGE_PACKS_DIRECTORY_NAME));
        Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_RESTART_HINT));
    }

    private void renderRefresh() {
        if (task.isRunning()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_WORKING));
            return;
        }
        if (ImGui.button(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_REFRESH))) {
            task.fetchCatalogue();
        }
    }

    private void renderCatalogue(Project project) {
        ImGui.separator();
        if (catalogue.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_NO_CATALOGUE));
            return;
        }
        catalogue.packs().forEach(pack -> renderPack(project, pack));
    }

    private void renderPack(Project project, LanguagePack pack) {
        ImGui.pushID(pack.identifier());
        Optional<String> installed = task.installedVersion(project, pack.identifier());
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(pack.name() + "  " + pack.version() + "  " + pack.kilobytes() + " KB");
        ImGui.sameLine();
        renderAction(project, pack, installed);
        Texts.muted(pack.description());
        installed.filter(version -> !version.equals(pack.version())).ifPresent(version ->
                Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_PINNED, version)));
        ImGui.popID();
    }

    private void renderAction(Project project, LanguagePack pack, Optional<String> installed) {
        if (task.isRunning()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_WORKING));
            return;
        }
        if (installed.isEmpty()) {
            if (ImGui.button(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_INSTALL))) {
                task.install(project, pack);
            }
            return;
        }
        if (ImGui.button(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_REMOVE))) {
            task.remove(project, pack);
        }
        if (installed.filter(version -> version.equals(pack.version())).isPresent()) {
            return;
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.translate(TextKey.EDITOR_SCRIPTING_SECTION_UPDATE))) {
            task.install(project, pack);
        }
    }

    private void drainOutcome() {
        task.drainOutcome().ifPresent(outcome -> {
            if (!outcome.catalogue().isEmpty()) {
                catalogue = outcome.catalogue();
            }
            if (!outcome.message().isEmpty()) {
                notifier.show(outcome.message());
            }
            if (outcome.changed()) {
                onPacksChanged.run();
            }
        });
    }
}
