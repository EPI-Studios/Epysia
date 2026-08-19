package fr.epistudio.epysia.scripting.editor;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SyntaxDescriptor(
        String displayName,
        Style style,
        List<String> keywords,
        Set<String> declarationKeywords,
        String lineComment,
        String importStatementSuffix,
        Set<String> implicitPackages,
        List<String> globalNames,
        Map<String, String> receiverTypes,
        Map<String, List<String>> receiverExtras
) {

    public enum Style {
        CURLY_BRACE,
        INDENTED
    }

    public SyntaxDescriptor {
        keywords = List.copyOf(keywords);
        declarationKeywords = Set.copyOf(declarationKeywords);
        implicitPackages = Set.copyOf(implicitPackages);
        globalNames = List.copyOf(globalNames);
        receiverTypes = Map.copyOf(receiverTypes);
        receiverExtras = Map.copyOf(receiverExtras);
    }
}
