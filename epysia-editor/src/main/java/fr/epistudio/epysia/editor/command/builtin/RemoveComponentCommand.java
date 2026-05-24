package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.function.Supplier;

public final class RemoveComponentCommand implements EditorCommand {

    private final GameObject target;
    private final Class<? extends IComponent> componentClass;
    private final Supplier<? extends IComponent> factory;

    public RemoveComponentCommand(GameObject target, Class<? extends IComponent> componentClass,
                                  Supplier<? extends IComponent> factory) {
        this.target = target;
        this.componentClass = componentClass;
        this.factory = factory;
    }

    @Override
    public void apply(CommandContext context) {
        target.removeComponent(componentClass);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new AddComponentCommand(target, componentClass, factory);
    }

    @Override
    public String label() {
        return "Remove " + componentClass.getSimpleName();
    }
}
