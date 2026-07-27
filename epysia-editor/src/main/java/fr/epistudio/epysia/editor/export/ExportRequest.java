package fr.epistudio.epysia.editor.export;

import fr.epistudio.epysia.gpu.GpuPreference;
import java.nio.file.Path;

public record ExportRequest(Path outputDirectory, String title, String sceneFileName, TargetPlatform platform,
                            GpuPreference gpuPreference) {
}
