package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class AddComponentCommand implements EditorCommand {

    private final GameObject target;
    private final Class<? extends IComponent> componentClass;
    private final IComponent existingInstance;

    public AddComponentCommand(GameObject target, Class<? extends IComponent> componentClass) {
        this(target, componentClass, null);
    }

    public AddComponentCommand(GameObject target, Class<? extends IComponent> componentClass, IComponent existingInstance) {
        this.target = target;
        this.componentClass = componentClass;
        this.existingInstance = existingInstance;
    }

    @Override
    public void apply(CommandContext context) {
        IComponent fresh = existingInstance != null
                ? existingInstance
                : context.componentRegistry().factoryFor(componentClass)
                        .orElseThrow(() -> new IllegalStateException("No factory for " + componentClass.getName())).get();
        target.addComponent(fresh);
        fresh.onLoad(context.services());
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        IComponent current = target.getComponent(componentClass).map(c -> (IComponent) c).orElse(existingInstance);
        return new RemoveComponentCommand(target, componentClass, current);
    }

    @Override
    public String label() {
        return "Add " + componentClass.getSimpleName();
    }
}
