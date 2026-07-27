package fr.epistudio.epysia.editor.scripteditor;

public final class SourceIndent {

    private static final char INDENT_CHARACTER = '\t';

    private SourceIndent() {
    }

    public static String forNewLineAfter(String textBeforeCursor) {
        return indentOf(blockDepth(textBeforeCursor));
    }

    public static String indentOf(int depth) {
        return String.valueOf(INDENT_CHARACTER).repeat(Math.max(0, depth));
    }

    public static int blockDepth(String textBeforeCursor) {
        Scanner scanner = new Scanner();
        int depth = 0;
        for (int index = 0; index < textBeforeCursor.length(); index++) {
            char character = textBeforeCursor.charAt(index);
            if (scanner.consume(character, textBeforeCursor, index)) {
                continue;
            }
            depth += braceDelta(character);
        }
        return depth;
    }

    private static int braceDelta(char character) {
        if (character == '{' || character == '(' || character == '[') {
            return 1;
        }
        if (character == '}' || character == ')' || character == ']') {
            return -1;
        }
        return 0;
    }

    private static final class Scanner {

        private boolean inLineComment;
        private boolean inBlockComment;
        private boolean inString;
        private boolean inCharacter;
        private boolean escaped;

        private boolean consume(char character, String text, int index) {
            if (inLineComment) {
                inLineComment = character != '\n';
                return true;
            }
            if (inBlockComment) {
                inBlockComment = !(character == '/' && index > 0 && text.charAt(index - 1) == '*');
                return true;
            }
            if (inString || inCharacter) {
                consumeQuoted(character);
                return true;
            }
            return openLiteralOrComment(character, text, index);
        }

        private void consumeQuoted(char character) {
            if (escaped) {
                escaped = false;
                return;
            }
            if (character == '\\') {
                escaped = true;
                return;
            }
            if (character == '"') {
                inString = false;
            }
            if (character == '\'') {
                inCharacter = false;
            }
        }

        private boolean openLiteralOrComment(char character, String text, int index) {
            if (character == '/' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                inLineComment = true;
                return true;
            }
            if (character == '/' && index + 1 < text.length() && text.charAt(index + 1) == '*') {
                inBlockComment = true;
                return true;
            }
            if (character == '"') {
                inString = true;
                return true;
            }
            if (character == '\'') {
                inCharacter = true;
                return true;
            }
            return false;
        }
    }
}
