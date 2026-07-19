package fr.epistudio.epysia.editor.export;

import java.nio.file.Path;

public record TemplateLayout(Path launcher, Path config, Path applicationDirectory) {
}
