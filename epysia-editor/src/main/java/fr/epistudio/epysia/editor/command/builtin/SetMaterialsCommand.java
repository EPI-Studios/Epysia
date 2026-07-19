package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.render.material.Material;

import java.util.List;

public final class SetMaterialsCommand implements EditorCommand {

    private final MeshRenderer renderer;
    private final List<Material> materials;

    public SetMaterialsCommand(MeshRenderer renderer, List<Material> materials) {
        this.renderer = renderer;
        this.materials = List.copyOf(materials);
    }

    @Override
    public void apply(CommandContext context) {
        renderer.setMaterials(materials);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetMaterialsCommand(renderer, renderer.materials());
    }

    @Override
    public String label() {
        return "Set Materials";
    }
}
