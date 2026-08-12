package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.ui.MeshColliderFitSection;
import fr.epistudio.epysia.editor.ui.SpriteColliderFitSection;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.function.Consumer;

public final class ColliderFitSection implements ComponentSection {

    private final SpriteColliderFitSection spriteFit;
    private final MeshColliderFitSection meshFit;
    private final Runnable markDirty;
    private final Consumer<EditorCommand> execute;

    public ColliderFitSection(SpriteColliderFitSection spriteFit, MeshColliderFitSection meshFit,
                              Runnable markDirty, Consumer<EditorCommand> execute) {
        this.spriteFit = spriteFit;
        this.meshFit = meshFit;
        this.markDirty = markDirty;
        this.execute = execute;
    }

    @Override
    public boolean handles(IComponent component) {
        return true;
    }

    @Override
    public void render(GameObject gameObject, IComponent component) {
        if (spriteFit.render(gameObject, component)) {
            markDirty.run();
        }
        meshFit.render(gameObject, component).ifPresent(execute);
    }
}
