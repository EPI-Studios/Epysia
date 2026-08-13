package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class SetGameObjectFlagCommand implements EditorCommand {

    public enum Flag {
        ACTIVE("Active"),
        KEEP_ON_SCENE_CHANGE("Keep on scene change");

        private final String label;

        Flag(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private final GameObject target;
    private final Flag flag;
    private final boolean value;

    public SetGameObjectFlagCommand(GameObject target, Flag flag, boolean value) {
        this.target = target;
        this.flag = flag;
        this.value = value;
    }

    @Override
    public void apply(CommandContext context) {
        switch (flag) {
            case ACTIVE -> target.setActive(value);
            case KEEP_ON_SCENE_CHANGE -> target.setKeepOnSceneChange(value);
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetGameObjectFlagCommand(target, flag, !value);
    }

    @Override
    public String label() {
        return (value ? "Enable " : "Disable ") + flag.label() + " on " + target.name();
    }
}
