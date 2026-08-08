package fr.epistudio.epysia.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderTuningPersistenceTest {
    @Test
    void everyRenderToggleSurvivesASaveAndReload(@TempDir Path root) throws IOException {
        ProjectStore store = new ProjectStore();
        Project project = new Project("tuning", root, "test", 0L);
        RenderTuning inverted = new RenderTuning(true, 7, false, false, false, false, false,
                false, false, false, 42.5f, false, true, true, true, false);

        store.writeQuality(project, withTuning(inverted));
        RenderTuning reloaded = store.readQuality(project).renderTuning();

        assertEquals(inverted, reloaded,
                "a render toggle was dropped on the way to disk or back");
    }

    private static ProjectQuality withTuning(RenderTuning tuning) {
        ProjectQuality defaults = ProjectQuality.defaults();
        return new ProjectQuality(defaults.gravityX(), defaults.gravityY(), defaults.gravityZ(),
                defaults.fixedTimestepHertz(), defaults.shadowMapSize(), defaults.cascadeCount(),
                defaults.windowTitle(), defaults.windowWidth(), defaults.windowHeight(),
                defaults.verticalSync(), defaults.maximumFrameRate(), defaults.nearestTextureFilter(),
                defaults.depthPrepass(), defaults.shadowFilterSamples(), defaults.filteredCascades(),
                defaults.shadowDepthSteps(),
                tuning);
    }
}
