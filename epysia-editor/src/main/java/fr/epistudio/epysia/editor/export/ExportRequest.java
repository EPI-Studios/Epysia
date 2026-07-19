package fr.epistudio.epysia.editor.export;

import java.nio.file.Path;

public record ExportRequest(Path outputDirectory, String title, String sceneFileName, TargetPlatform platform) {
}
