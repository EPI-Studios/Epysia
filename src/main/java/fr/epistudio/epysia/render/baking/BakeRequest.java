package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.EpysiaEngine;

import java.nio.file.Path;

public record BakeRequest(EpysiaEngine engine, Path outputDirectory, Runnable stageBindingRestore) {

    public static BakeRequest of(EpysiaEngine engine, Path outputDirectory) {
        return new BakeRequest(engine, outputDirectory, () -> {
        });
    }
}
