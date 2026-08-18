package fr.epistudio.epysia.scripting.editor;

import java.util.List;
import java.util.Set;

public record SyntaxDescriptor(
        String displayName,
        Style style,
        List<String> keywords,
        Set<String> declarationKeywords,
        String lineComment,
        String importStatementSuffix,
        Set<String> implicitPackages
) {

    public enum Style {
        CURLY_BRACE,
        INDENTED
    }

    public SyntaxDescriptor {
        keywords = List.copyOf(keywords);
        declarationKeywords = Set.copyOf(declarationKeywords);
        implicitPackages = Set.copyOf(implicitPackages);
    }

    public static SyntaxDescriptor curlyBrace(String displayName, List<String> keywords,
                                             Set<String> declarationKeywords,
                                             String importStatementSuffix,
                                             Set<String> implicitPackages) {
        return new SyntaxDescriptor(displayName, Style.CURLY_BRACE, keywords, declarationKeywords,
                "//", importStatementSuffix, implicitPackages);
    }

    public static SyntaxDescriptor indented(String displayName, List<String> keywords,
                                            Set<String> declarationKeywords,
                                            Set<String> implicitPackages) {
        return new SyntaxDescriptor(displayName, Style.INDENTED, keywords, declarationKeywords,
                "#", "", implicitPackages);
    }
}
