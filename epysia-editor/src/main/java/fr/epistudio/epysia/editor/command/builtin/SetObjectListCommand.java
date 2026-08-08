package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.reflection.ExportedProperty;

import java.util.ArrayList;
import java.util.List;

public final class SetObjectListCommand implements EditorCommand {

    private final IComponent owner;
    private final ExportedProperty property;
    private final List<Object> before;
    private final List<Object> after;

    public SetObjectListCommand(IComponent owner, ExportedProperty property,
                                List<Object> before, List<Object> after) {
        this.owner = owner;
        this.property = property;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(CommandContext context) {
        List<Object> target = (List<Object>) property.read();
        target.clear();
        target.addAll(after);
        owner.onLoad(context.services());
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetObjectListCommand(owner, property, new ArrayList<>(after), new ArrayList<>(before));
    }

}
