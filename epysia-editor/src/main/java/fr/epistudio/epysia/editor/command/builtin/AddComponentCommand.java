package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.function.Supplier;

public final class AddComponentCommand implements EditorCommand {

    private final GameObject target;
    private final Class<? extends IComponent> componentClass;
    private final Supplier<? extends IComponent> factory;

    public AddComponentCommand(GameObject target, Class<? extends IComponent> componentClass,
                               Supplier<? extends IComponent> factory) {
        this.target = target;
        this.componentClass = componentClass;
        this.factory = factory;
    }

    @Override
    public void apply(CommandContext context) {
        if (target.getComponent(componentClass).isPresent()) {
            return;
        }
        target.addComponent(factory.get());
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RemoveComponentCommand(target, componentClass, factory);
    }

    @Override
    public String label() {
        return "Add " + componentClass.getSimpleName();
    }
}
