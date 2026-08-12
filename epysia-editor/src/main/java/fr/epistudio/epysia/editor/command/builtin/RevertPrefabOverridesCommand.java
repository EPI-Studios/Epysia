package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabFieldApplier;
import fr.epistudio.epysia.prefab.PrefabInstanceSnapshot;
import fr.epistudio.epysia.prefab.PrefabRefresher;

public final class RevertPrefabOverridesCommand implements EditorCommand {

    private final GameObject instance;
    private final PrefabRefresher refresher;
    private final PrefabFieldApplier applier;
    private PrefabInstanceSnapshot discarded;

    public RevertPrefabOverridesCommand(GameObject instance, PrefabRefresher refresher,
                                        PrefabFieldApplier applier) {
        this.instance = instance;
        this.refresher = refresher;
        this.applier = applier;
    }

    @Override
    public void apply(CommandContext context) {
        discarded = PrefabInstanceSnapshot.capture(instance);
        refresher.revertEverything(instance);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RestorePrefabInstanceCommand(instance, discarded, applier);
    }

    @Override
    public String label() {
        return "Revert " + instance.name() + " to its prefab";
    }
}
