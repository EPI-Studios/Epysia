package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.DrawCommand;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class Frame implements FrameBuilder {

    private static final java.util.Comparator<DrawCommand> SORT_KEY_ORDER =
            java.util.Comparator.comparingLong(DrawCommand::sortKey);

    private final EnumMap<Stage, List<DrawCommand>> commandsByStage = new EnumMap<>(Stage.class);

    public Frame() {
        for (Stage stage : Stage.values()) {
            commandsByStage.put(stage, new ArrayList<>());
        }
    }

    @Override
    public void submit(Stage stage, DrawCommand command) {
        commandsByStage.get(stage).add(command);
    }

    public List<DrawCommand> commandsFor(Stage stage) {
        return commandsByStage.get(stage);
    }

    public void sortByKey(Stage stage) {
        commandsByStage.get(stage).sort(SORT_KEY_ORDER);
    }

    public void reset() {
        for (List<DrawCommand> bucket : commandsByStage.values()) {
            bucket.clear();
        }
    }
}
