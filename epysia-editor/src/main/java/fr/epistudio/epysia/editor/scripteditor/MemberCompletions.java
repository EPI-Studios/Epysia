package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.scripting.editor.SyntaxDescriptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemberCompletions implements Completions {

    private static final int MINIMUM_PREFIX = 2;
    private static final int MAXIMUM_CANDIDATES = 60;
    private static final Pattern RECEIVER_PATTERN =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\.$");
    private static final Pattern DEFINITION_PATTERN =
            Pattern.compile("^\\s*(?:def|class)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.MULTILINE);
    private static final Pattern ASSIGNMENT_PATTERN =
            Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=", Pattern.MULTILINE);

    private final SyntaxDescriptor descriptor;

    public MemberCompletions(SyntaxDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public CompletionContext contextAt(String lineText, int cursorIndex) {
        int end = Math.min(cursorIndex, lineText.length());
        int start = end;
        while (start > 0 && CompletionEngine.isWordCharacter(lineText.charAt(start - 1))) {
            start--;
        }
        String prefix = lineText.substring(start, end);
        Matcher matcher = RECEIVER_PATTERN.matcher(lineText.substring(0, start));
        return new CompletionContext(prefix,
                matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty());
    }

    @Override
    public boolean shouldTrigger(CompletionContext context) {
        return context.isMember() || context.prefix().length() >= MINIMUM_PREFIX;
    }

    @Override
    public List<CompletionSymbol> candidates(CompletionContext context, String fullText, ImportStyle style) {
        Set<String> names = context.isMember()
                ? membersOf(context.receiver().orElseThrow())
                : globalsOf(fullText);
        String prefix = context.prefix().toLowerCase(Locale.ROOT);
        List<CompletionSymbol> matched = new ArrayList<>();
        for (String name : names) {
            if (matched.size() >= MAXIMUM_CANDIDATES) {
                break;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matched.add(new CompletionSymbol(name, name, CompletionKind.METHOD));
            }
        }
        return matched;
    }

    private Set<String> membersOf(String receiver) {
        Set<String> names = new LinkedHashSet<>(descriptor.receiverExtras()
                .getOrDefault(receiver, List.of()));
        String typeName = descriptor.receiverTypes().get(receiver);
        if (typeName != null) {
            names.addAll(reflectedMembersOf(typeName));
        }
        return names;
    }

    private static Set<String> reflectedMembersOf(String typeName) {
        Set<String> names = new LinkedHashSet<>();
        try {
            for (Method method : Class.forName(typeName).getMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getDeclaringClass() == Object.class) {
                    continue;
                }
                names.add(snakeCase(method.getName()));
            }
        } catch (ClassNotFoundException unknown) {
            return names;
        }
        return names;
    }

    static String snakeCase(String name) {
        StringBuilder converted = new StringBuilder(name.length() + 4);
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                converted.append('_');
            }
            converted.append(Character.toLowerCase(character));
        }
        return converted.toString();
    }

    private Set<String> globalsOf(String fullText) {
        Set<String> names = new LinkedHashSet<>(descriptor.globalNames());
        names.addAll(descriptor.keywords());
        collectMatches(DEFINITION_PATTERN, fullText, names);
        collectMatches(ASSIGNMENT_PATTERN, fullText, names);
        return names;
    }

    private static void collectMatches(Pattern pattern, String text, Set<String> names) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
    }
}
