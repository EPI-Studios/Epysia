package fr.epistudio.epysia.editor.scripteditor;

import java.util.ArrayList;
import java.util.List;

public final class ReceiverChain {

    private ReceiverChain() {
    }

    public static String expressionBefore(String lineText, int dotIndex) {
        int start = dotIndex;
        while (start > 0) {
            char character = lineText.charAt(start - 1);
            if (character == ')' || character == ']') {
                start = openingIndexOf(lineText, start - 1);
                continue;
            }
            if (!CompletionEngine.isWordCharacter(character) && character != '.') {
                break;
            }
            start--;
        }
        return start < 0 ? "" : lineText.substring(start, dotIndex);
    }

    private static int openingIndexOf(String lineText, int closingIndex) {
        char closing = lineText.charAt(closingIndex);
        char opening = closing == ')' ? '(' : '[';
        int depth = 0;
        for (int index = closingIndex; index >= 0; index--) {
            char character = lineText.charAt(index);
            depth += character == closing ? 1 : 0;
            depth -= character == opening ? 1 : 0;
            if (depth == 0) {
                return index;
            }
        }
        return -1;
    }

    public static List<String> segmentsOf(String expression) {
        List<String> segments = new ArrayList<>();
        int depth = 0;
        int segmentStart = 0;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            depth += character == '(' || character == '[' ? 1 : 0;
            depth -= character == ')' || character == ']' ? 1 : 0;
            if (character == '.' && depth == 0) {
                segments.add(expression.substring(segmentStart, index));
                segmentStart = index + 1;
            }
        }
        segments.add(expression.substring(segmentStart));
        return segments;
    }

    public static String memberNameOf(String segment) {
        int parenthesisIndex = segment.indexOf('(');
        int bracketIndex = segment.indexOf('[');
        int end = segment.length();
        end = parenthesisIndex >= 0 ? Math.min(end, parenthesisIndex) : end;
        end = bracketIndex >= 0 ? Math.min(end, bracketIndex) : end;
        return segment.substring(0, end).trim();
    }
}
