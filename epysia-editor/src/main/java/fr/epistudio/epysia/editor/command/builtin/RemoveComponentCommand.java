package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class RemoveComponentCommand implements EditorCommand {

    private final GameObject target;
    private final Class<? extends IComponent> componentClass;
    private final IComponent instanceSnapshot;

    public RemoveComponentCommand(GameObject target, Class<? extends IComponent> componentClass,
                                  IComponent instanceSnapshot) {
        this.target = target;
        this.componentClass = componentClass;
        this.instanceSnapshot = instanceSnapshot;
    }

    @Override
    public void apply(CommandContext context) {
        target.removeComponent(componentClass);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new AddComponentCommand(target, componentClass, instanceSnapshot);
    }

    @Override
    public String label() {
        return "Remove " + componentClass.getSimpleName();
    }
}
