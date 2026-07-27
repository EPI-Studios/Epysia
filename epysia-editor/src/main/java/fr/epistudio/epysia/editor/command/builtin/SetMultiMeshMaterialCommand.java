package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.render.material.Material;

public final class SetMultiMeshMaterialCommand implements EditorCommand {

    private final MultiMeshRenderer renderer;
    private final Material material;

    public SetMultiMeshMaterialCommand(MultiMeshRenderer renderer, Material material) {
        this.renderer = renderer;
        this.material = material;
    }

    @Override
    public void apply(CommandContext context) {
        renderer.materialRef().setPath(material.assetPath());
        renderer.setMaterial(material);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetMultiMeshMaterialCommand(renderer, renderer.materialOrNull());
    }

    @Override
    public String label() {
        return "Set Multi Mesh Material";
    }
}
