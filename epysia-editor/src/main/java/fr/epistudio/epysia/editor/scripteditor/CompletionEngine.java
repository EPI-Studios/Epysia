package fr.epistudio.epysia.editor.scripteditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompletionEngine {

    public record Context(String prefix, Optional<String> receiver) {

        public boolean isMember() {
            return receiver.isPresent();
        }
    }

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]{2,}\\b");
    private static final int MAX_RESULTS = 50;
    private static final int MINIMUM_PREFIX_LENGTH = 2;

    private final JavaSymbols symbols;

    public CompletionEngine(JavaSymbols symbols) {
        this.symbols = symbols;
    }

    public Context contextAt(String lineText, int cursorIndex) {
        int end = Math.min(Math.max(cursorIndex, 0), lineText.length());
        int start = end;
        while (start > 0 && isWordCharacter(lineText.charAt(start - 1))) {
            start--;
        }
        String prefix = lineText.substring(start, end);
        if (start > 0 && lineText.charAt(start - 1) == '.') {
            return new Context(prefix, Optional.of(receiverBefore(lineText, start - 1)));
        }
        return new Context(prefix, Optional.empty());
    }

    private static String receiverBefore(String lineText, int dotIndex) {
        int start = dotIndex;
        while (start > 0 && isWordCharacter(lineText.charAt(start - 1))) {
            start--;
        }
        return lineText.substring(start, dotIndex);
    }

    public boolean shouldTrigger(Context context) {
        return context.isMember() || context.prefix().length() >= MINIMUM_PREFIX_LENGTH;
    }

    public List<CompletionSymbol> candidates(Context context, String fullText) {
        List<CompletionSymbol> pool = context.isMember()
                ? memberPool(context.receiver().orElse(""), fullText)
                : generalPool(fullText, context.prefix());
        return rank(pool, context.prefix());
    }

    private List<CompletionSymbol> memberPool(String receiver, String fullText) {
        if (symbols.knowsType(receiver)) {
            return symbols.staticMembersOf(receiver);
        }
        Optional<String> declaredType = declaredTypeOf(receiver, fullText);
        if (declaredType.isPresent() && symbols.knowsType(declaredType.get())) {
            return symbols.instanceMembersOf(declaredType.get());
        }
        return symbols.globalPool().stream()
                .filter(symbol -> symbol.kind() != CompletionKind.KEYWORD)
                .toList();
    }

    private static Optional<String> declaredTypeOf(String receiver, String fullText) {
        if (receiver.isEmpty()) {
            return Optional.empty();
        }
        Pattern declaration = Pattern.compile(
                "\\b([A-Z][A-Za-z0-9_]*)(?:<[^<>]*>)?\\s+" + Pattern.quote(receiver) + "\\b");
        Matcher matcher = declaration.matcher(fullText);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private List<CompletionSymbol> generalPool(String fullText, String prefix) {
        Map<String, CompletionSymbol> merged = new LinkedHashMap<>();
        symbols.globalPool().forEach(symbol -> merged.putIfAbsent(baseName(symbol), symbol));
        Matcher matcher = IDENTIFIER_PATTERN.matcher(fullText);
        while (matcher.find()) {
            String word = matcher.group();
            if (!word.equals(prefix)) {
                merged.putIfAbsent(word, new CompletionSymbol(word, word, CompletionKind.LOCAL));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String baseName(CompletionSymbol symbol) {
        int parenthesisIndex = symbol.insertText().indexOf('(');
        return parenthesisIndex < 0 ? symbol.insertText() : symbol.insertText().substring(0, parenthesisIndex);
    }

    private static List<CompletionSymbol> rank(List<CompletionSymbol> pool, String prefix) {
        record Ranked(CompletionSymbol symbol, int rank) {
        }
        List<Ranked> ranked = new ArrayList<>();
        for (CompletionSymbol symbol : pool) {
            int rank = matchRank(symbol.insertText(), prefix);
            if (rank >= 0 && !symbol.insertText().equals(prefix)) {
                ranked.add(new Ranked(symbol, rank));
            }
        }
        ranked.sort(Comparator.comparingInt(Ranked::rank).thenComparing(entry -> entry.symbol().label()));
        return ranked.stream().limit(MAX_RESULTS).map(Ranked::symbol).toList();
    }

    private static int matchRank(String candidate, String prefix) {
        if (prefix.isEmpty()) {
            return 0;
        }
        if (candidate.startsWith(prefix)) {
            return 0;
        }
        if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return 1;
        }
        return matchesCamelCase(candidate, prefix) ? 2 : -1;
    }

    private static boolean matchesCamelCase(String candidate, String prefix) {
        int matched = 0;
        for (int position = 0; position < candidate.length() && matched < prefix.length(); position++) {
            char candidateCharacter = candidate.charAt(position);
            boolean humpStart = position == 0 || Character.isUpperCase(candidateCharacter);
            if (humpStart && Character.toLowerCase(candidateCharacter)
                    == Character.toLowerCase(prefix.charAt(matched))) {
                matched++;
            }
        }
        return matched == prefix.length();
    }

    static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
