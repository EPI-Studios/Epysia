package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.gpu.GpuPreference;

import java.nio.file.Path;
import java.util.Optional;

public record ExportRequest(Path outputDirectory, String title, String sceneFileName, TargetPlatform platform,
                            GpuPreference gpuPreference, Optional<Path> iconFile) {

    public ExportRequest(Path outputDirectory, String title, String sceneFileName, TargetPlatform platform,
                         GpuPreference gpuPreference) {
        this(outputDirectory, title, sceneFileName, platform, gpuPreference, Optional.empty());
    }
}
