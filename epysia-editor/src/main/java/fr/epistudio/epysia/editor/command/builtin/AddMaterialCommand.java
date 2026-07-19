package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.render.material.LitMaterial;

public final class AddMaterialCommand implements EditorCommand {

    private final MeshRenderer renderer;
    private final int slot;

    public AddMaterialCommand(MeshRenderer renderer, int slot) {
        this.renderer = renderer;
        this.slot = slot;
    }

    @Override
    public void apply(CommandContext context) {
        while (renderer.materials().size() <= slot) {
            renderer.addMaterial(new LitMaterial());
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetMaterialsCommand(renderer, renderer.materials());
    }

    @Override
    public String label() {
        return "Add Material";
    }
}
