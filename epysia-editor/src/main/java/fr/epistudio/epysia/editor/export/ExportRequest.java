package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.project.ReleaseSettings;

import java.nio.file.Path;
import java.util.Optional;

public record ExportRequest(Path outputDirectory, String title, String version, String sceneFileName,
                            TargetPlatform platform, GpuPreference gpuPreference, Optional<Path> iconFile,
                            Optional<Path> templateArchive) {

    public ExportRequest {
        version = new ReleaseSettings(version).sanitized().version();
    }
}
