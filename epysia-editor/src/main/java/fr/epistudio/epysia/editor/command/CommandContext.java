package fr.epistudio.epysia.editor.command;

import fr.epistudio.epysia.editor.EditorWorld;
import fr.epistudio.epysia.editor.selection.EditorSelectionBus;

public record CommandContext(EditorWorld world, EditorSelectionBus selection) {
}
