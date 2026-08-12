package fr.epistudio.epysia.editor.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

public final class CommandRegistry {

    private static final int WORD_START_BONUS = 8;
    private static final int ADJACENT_BONUS = 5;
    private static final int LEADING_PENALTY_LIMIT = 12;
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
                .map(command -> new Scored(command, score(command.searchLabel(), trimmed)))
                .filter(scored -> scored.score() > Integer.MIN_VALUE)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(MAXIMUM_RESULTS)
                .map(Scored::command)
                .toList();
    }

    static int score(String candidate, String query) {
        String haystack = candidate.toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int score = 0;
        int cursor = 0;
        int previousMatch = -2;
        for (int index = 0; index < needle.length(); index++) {
            OptionalInt found = indexOf(haystack, needle.charAt(index), cursor);
            if (found.isEmpty()) {
                return Integer.MIN_VALUE;
            }
            int position = found.getAsInt();
            score += positionScore(haystack, position, previousMatch);
            previousMatch = position;
            cursor = position + 1;
        }
        return score - Math.min(previousMatch, LEADING_PENALTY_LIMIT);
    }

    private static int positionScore(String haystack, int position, int previousMatch) {
        if (position == previousMatch + 1) {
            return ADJACENT_BONUS;
        }
        if (position == 0 || !Character.isLetterOrDigit(haystack.charAt(position - 1))) {
            return WORD_START_BONUS;
        }
        return 1;
    }

    private static OptionalInt indexOf(String haystack, char character, int from) {
        int found = haystack.indexOf(character, from);
        return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
    }
}
