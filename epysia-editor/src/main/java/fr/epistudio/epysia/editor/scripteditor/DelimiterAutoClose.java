package fr.epistudio.epysia.editor.scripteditor;

public final class DelimiterAutoClose {

    public enum Action { NONE, INSERT_CLOSER, SKIP_CLOSER }

    public record Decision(Action action, String closer) {

        public static final Decision NOTHING = new Decision(Action.NONE, "");
    }

    private static final String OPENERS = "([{\"'";
    private static final String CLOSERS = ")]}\"'";
    private static final String CLOSE_ALLOWED_NEIGHBOURS = " \t)]},;";

    private int previousLine = -1;
    private int previousIndex = -1;
    private int previousLength = -1;

    public Decision observe(int line, int index, String lineText) {
        Decision decision = classify(line, index, lineText);
        previousLine = line;
        previousIndex = index;
        previousLength = lineText.length();
        return decision;
    }

    private Decision classify(int line, int index, String lineText) {
        if (!isSingleInsertion(line, index, lineText)) {
            return Decision.NOTHING;
        }
        char typed = lineText.charAt(index - 1);
        if (isCloser(typed) && index < lineText.length() && lineText.charAt(index) == typed) {
            return new Decision(Action.SKIP_CLOSER, String.valueOf(typed));
        }
        if (isOpener(typed) && acceptsCloser(lineText, index)) {
            return new Decision(Action.INSERT_CLOSER, String.valueOf(closerFor(typed)));
        }
        return Decision.NOTHING;
    }

    private boolean isSingleInsertion(int line, int index, String lineText) {
        return line == previousLine && index == previousIndex + 1
                && lineText.length() == previousLength + 1 && index > 0 && index <= lineText.length();
    }

    private static boolean acceptsCloser(String lineText, int index) {
        return index >= lineText.length() || CLOSE_ALLOWED_NEIGHBOURS.indexOf(lineText.charAt(index)) >= 0;
    }

    private static boolean isOpener(char character) {
        return OPENERS.indexOf(character) >= 0;
    }

    private static boolean isCloser(char character) {
        return CLOSERS.indexOf(character) >= 0;
    }

    private static char closerFor(char opener) {
        return CLOSERS.charAt(OPENERS.indexOf(opener));
    }

    public void forget() {
        previousLine = -1;
        previousIndex = -1;
        previousLength = -1;
    }
}
