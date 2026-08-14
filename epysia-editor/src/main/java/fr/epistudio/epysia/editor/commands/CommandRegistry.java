package fr.epistudio.epysia.editor.commands;

import fr.epistudio.epysia.editor.ui.kit.FuzzyScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CommandRegistry {

    private static final int MAXIMUM_RESULTS = 40;

    private final List<EditorCommand> commands = new ArrayList<>();

    public void add(EditorCommand command) {
        commands.add(command);
    }

    public List<EditorCommand> all() {
        return List.copyOf(commands);
    }

    public List<EditorCommand> matching(String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return commands.stream().limit(MAXIMUM_RESULTS).toList();
        }
        record Scored(EditorCommand command, int score) {
        }
        return commands.stream()
                .map(command -> new Scored(command, FuzzyScore.of(command.searchLabel(), trimmed)))
                .filter(scored -> scored.score() > FuzzyScore.NO_MATCH)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(MAXIMUM_RESULTS)
                .map(Scored::command)
                .toList();
    }

}
