package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabFieldApplier;
import fr.epistudio.epysia.prefab.PrefabInstanceSnapshot;

public final class RestorePrefabInstanceCommand implements EditorCommand {

    private final GameObject instance;
    private final PrefabInstanceSnapshot target;
    private final PrefabFieldApplier applier;
    private PrefabInstanceSnapshot replaced;

    public RestorePrefabInstanceCommand(GameObject instance, PrefabInstanceSnapshot target,
                                        PrefabFieldApplier applier) {
        this.instance = instance;
        this.target = target;
        this.applier = applier;
    }

    @Override
    public void apply(CommandContext context) {
        replaced = PrefabInstanceSnapshot.capture(instance);
        target.restoreInto(instance, applier);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RestorePrefabInstanceCommand(instance, replaced, applier);
    }

    @Override
    public String label() {
        return "Restore " + instance.name() + " from its prefab";
    }
}
