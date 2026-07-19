package fr.epistudio.epysia.editor.command;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;

public record CommandContext(Scene scene,
                             EditorSelection selection,
                             EngineServices services,
                             ComponentRegistry componentRegistry) {
}
