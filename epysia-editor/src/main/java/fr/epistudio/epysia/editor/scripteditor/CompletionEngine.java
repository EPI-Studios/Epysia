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

    private record ResolvedType(String name, Optional<String> element) {
    }

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]{2,}\\b");
    private static final Pattern IMPORT_LINE_PATTERN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([A-Za-z0-9_.]*)$");
    private static final Pattern CLASS_LITERAL_PATTERN =
            Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\s*\\.\\s*class\\b");
    private static final Pattern ENCLOSING_TYPE_PATTERN =
            Pattern.compile("\\b(?:class|record|enum|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final String BEHAVIOUR_TYPE_NAME = "Behaviour";
    private static final String SELF_RECEIVER = "this";
    private static final int MAX_RESULTS = 50;
    private static final int MINIMUM_PREFIX_LENGTH = 2;
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
            return new Context(prefix, Optional.of(ReceiverChain.expressionBefore(lineText, start - 1)));
        }
        return new Context(prefix, Optional.empty());
    }

    public boolean shouldTrigger(Context context) {
        return context.isImport()
                || context.isMember()
                || context.prefix().length() >= MINIMUM_PREFIX_LENGTH;
    }

    public List<CompletionSymbol> candidates(Context context, String fullText, ImportStyle style) {
        if (context.isImport()) {
            return importPool(context.importPath().orElse(""), style);
        }
        List<CompletionSymbol> pool = context.isMember()
                ? memberPool(context.receiver().orElse(""), fullText)
                : generalPool(fullText, context.prefix());
        return rank(pool, context.prefix());
    }

    private List<CompletionSymbol> importPool(String typedPath, ImportStyle style) {
        List<String> matches = symbols.qualifiedTypeNames().stream()
                .filter(qualifiedName -> qualifiedName.startsWith(typedPath)
                        && !qualifiedName.equals(typedPath))
                .sorted()
                .toList();
        Map<String, CompletionSymbol> merged = new LinkedHashMap<>();
        for (String qualifiedName : matches) {
            CompletionSymbol candidate = importCandidate(qualifiedName, typedPath, matches, style);
            merged.putIfAbsent(candidate.insertText(), candidate);
        }
        return merged.values().stream().limit(MAX_RESULTS).toList();
    }

    private static CompletionSymbol importCandidate(String qualifiedName, String typedPath,
                                                    List<String> matches, ImportStyle style) {
        int nextSeparator = qualifiedName.indexOf(PACKAGE_SEPARATOR, typedPath.length());
        if (nextSeparator < 0) {
            String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf(PACKAGE_SEPARATOR) + 1);
            return new CompletionSymbol(simpleName, style.completionInsertTextFor(qualifiedName),
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
        List<String> segments = ReceiverChain.segmentsOf(receiver);
        if (segments.size() == 1 && symbols.knowsType(segments.getFirst())) {
            return typeMembersOf(segments.getFirst());
        }
        return resolveChain(segments, fullText)
                .map(resolved -> symbols.instanceMembersOf(resolved.name()))
                .orElseGet(List::of);
    }

    private List<CompletionSymbol> typeMembersOf(String typeName) {
        List<CompletionSymbol> members = new ArrayList<>(symbols.staticMembersOf(typeName));
        members.addAll(symbols.instanceMembersOf(typeName));
        return members;
    }

    private Optional<ResolvedType> resolveChain(List<String> segments, String fullText) {
        Optional<ResolvedType> current = rootTypeOf(segments.getFirst(), fullText);
        for (int index = 1; index < segments.size() && current.isPresent(); index++) {
            current = memberOf(current.get(), segments.get(index));
        }
        return current.filter(resolved -> symbols.knowsType(resolved.name()));
    }

    private Optional<ResolvedType> memberOf(ResolvedType receiver, String segment) {
        return symbols.memberTypeOf(receiver.name(), ReceiverChain.memberNameOf(segment))
                .flatMap(memberType -> substituted(memberType, receiver, segment));
    }

    private static Optional<ResolvedType> substituted(MemberType memberType, ResolvedType receiver,
                                                      String segment) {
        Optional<String> element = referenceValue(memberType.element(), receiver, segment);
        return referenceValue(memberType.outer(), receiver, segment)
                .map(name -> new ResolvedType(name, element));
    }

    private static Optional<String> referenceValue(TypeReference reference, ResolvedType receiver,
                                                   String segment) {
        return switch (reference.origin()) {
            case CONCRETE -> Optional.of(reference.name());
            case CALL_CLASS_ARGUMENT -> firstMatchOf(CLASS_LITERAL_PATTERN, segment);
            case RECEIVER_ELEMENT -> receiver.element();
            case UNKNOWN -> Optional.empty();
        };
    }

    private Optional<ResolvedType> rootTypeOf(String segment, String fullText) {
        String name = ReceiverChain.memberNameOf(segment);
        if (symbols.knowsType(name)) {
            return Optional.of(new ResolvedType(name, Optional.empty()));
        }
        if (name.equals(SELF_RECEIVER)) {
            return firstMatchOf(ENCLOSING_TYPE_PATTERN, fullText)
                    .map(enclosing -> new ResolvedType(enclosing, Optional.empty()));
        }
        if (segment.contains("(")) {
            return memberOf(new ResolvedType(BEHAVIOUR_TYPE_NAME, Optional.empty()), segment);
        }
        return declaredTypeOf(name, fullText);
    }

    private static Optional<ResolvedType> declaredTypeOf(String receiver, String fullText) {
        if (receiver.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)(?:<\\s*([A-Za-z0-9_]+)\\s*>)?\\s+"
                + Pattern.quote(receiver) + "\\b").matcher(fullText);
        return matcher.find()
                ? Optional.of(new ResolvedType(matcher.group(1), Optional.ofNullable(matcher.group(2))))
                : Optional.empty();
    }

    private static Optional<String> firstMatchOf(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
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
            if (rank >= 0 && !isRedundantLocal(symbol, prefix)) {
                ranked.add(new Ranked(symbol, rank));
            }
        }
        ranked.sort(Comparator.comparingInt(Ranked::rank).thenComparing(entry -> entry.symbol().label()));
        return ranked.stream().limit(MAX_RESULTS).map(Ranked::symbol).toList();
    }

    private static boolean isRedundantLocal(CompletionSymbol symbol, String prefix) {
        return symbol.kind() == CompletionKind.LOCAL && symbol.insertText().equals(prefix);
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

    public static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
