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

    public record Context(String prefix, Optional<String> receiver, Optional<String> importPath) {

        public Context(String prefix, Optional<String> receiver) {
            this(prefix, receiver, Optional.empty());
        }

        public boolean isMember() {
            return receiver.isPresent();
        }

        public boolean isImport() {
            return importPath.isPresent();
        }
    }

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]{2,}\\b");
    private static final Pattern IMPORT_LINE_PATTERN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([A-Za-z0-9_.]*)$");
    private static final int MAX_RESULTS = 50;
    private static final int MINIMUM_PREFIX_LENGTH = 2;
    private static final String IMPORT_STATEMENT_SUFFIX = ";";
    private static final char PACKAGE_SEPARATOR = '.';

    private final JavaSymbols symbols;

    public CompletionEngine(JavaSymbols symbols) {
        this.symbols = symbols;
    }

    public Context contextAt(String lineText, int cursorIndex) {
        int end = Math.min(Math.max(cursorIndex, 0), lineText.length());
        Matcher importMatcher = IMPORT_LINE_PATTERN.matcher(lineText.substring(0, end));
        if (importMatcher.matches()) {
            String importPath = importMatcher.group(2);
            return new Context(wordSuffixOf(importPath), Optional.empty(), Optional.of(importPath));
        }
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
        return context.isImport()
                || context.isMember()
                || context.prefix().length() >= MINIMUM_PREFIX_LENGTH;
    }

    public List<CompletionSymbol> candidates(Context context, String fullText) {
        if (context.isImport()) {
            return importPool(context.importPath().orElse(""));
        }
        List<CompletionSymbol> pool = context.isMember()
                ? memberPool(context.receiver().orElse(""), fullText)
                : generalPool(fullText, context.prefix());
        return rank(pool, context.prefix());
    }

    private List<CompletionSymbol> importPool(String typedPath) {
        List<String> matches = symbols.qualifiedTypeNames().stream()
                .filter(qualifiedName -> qualifiedName.startsWith(typedPath)
                        && !qualifiedName.equals(typedPath))
                .sorted()
                .toList();
        Map<String, CompletionSymbol> merged = new LinkedHashMap<>();
        for (String qualifiedName : matches) {
            CompletionSymbol candidate = importCandidate(qualifiedName, typedPath, matches);
            merged.putIfAbsent(candidate.insertText(), candidate);
        }
        return merged.values().stream().limit(MAX_RESULTS).toList();
    }

    private static CompletionSymbol importCandidate(String qualifiedName, String typedPath,
                                                    List<String> matches) {
        int nextSeparator = qualifiedName.indexOf(PACKAGE_SEPARATOR, typedPath.length());
        if (nextSeparator < 0) {
            String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf(PACKAGE_SEPARATOR) + 1);
            return new CompletionSymbol(simpleName, qualifiedName + IMPORT_STATEMENT_SUFFIX,
                    CompletionKind.TYPE, Optional.of(qualifiedName));
        }
        String collapsed = collapsedPackage(qualifiedName.substring(0, nextSeparator + 1), matches);
        return new CompletionSymbol(collapsed, collapsed, CompletionKind.PACKAGE, Optional.empty());
    }

    private static String collapsedPackage(String packagePrefix, List<String> matches) {
        String current = packagePrefix;
        while (true) {
            List<String> continuations = distinctContinuations(current, matches);
            if (continuations.size() != 1 || !continuations.getFirst().endsWith(String.valueOf(PACKAGE_SEPARATOR))) {
                return current;
            }
            current = current + continuations.getFirst();
        }
    }

    private static List<String> distinctContinuations(String packagePrefix, List<String> matches) {
        return matches.stream()
                .filter(qualifiedName -> qualifiedName.startsWith(packagePrefix))
                .map(qualifiedName -> continuationOf(qualifiedName, packagePrefix))
                .distinct()
                .toList();
    }

    private static String continuationOf(String qualifiedName, String packagePrefix) {
        int nextSeparator = qualifiedName.indexOf(PACKAGE_SEPARATOR, packagePrefix.length());
        return nextSeparator < 0
                ? qualifiedName.substring(packagePrefix.length())
                : qualifiedName.substring(packagePrefix.length(), nextSeparator + 1);
    }

    private static String wordSuffixOf(String importPath) {
        int lastSeparator = importPath.lastIndexOf(PACKAGE_SEPARATOR);
        return importPath.substring(lastSeparator + 1);
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
