package fr.epistudio.epysia.editor.scripteditor;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ImportStyle {

    private static final String IMPORT_KEYWORD = "import ";

    private final String statementSuffix;
    private final Set<String> implicitPackages;
    private final Pattern typeDeclarationPattern;
    private final Pattern importedNamePattern;

    private ImportStyle(String statementSuffix, Set<String> implicitPackages,
                        List<String> declarationKeywords) {
        this.statementSuffix = statementSuffix;
        this.implicitPackages = Set.copyOf(implicitPackages);
        this.typeDeclarationPattern = declarationPattern(declarationKeywords);
        this.importedNamePattern = importedNamePattern(statementSuffix);
    }

    public static ImportStyle of(String statementSuffix, Set<String> implicitPackages,
                                 List<String> declarationKeywords) {
        return new ImportStyle(statementSuffix, implicitPackages, declarationKeywords);
    }

    private static Pattern declarationPattern(List<String> keywords) {
        String alternatives = String.join("|", keywords.stream().map(Pattern::quote).toList());
        return Pattern.compile("\\b(?:" + alternatives + ")\\s+([A-Za-z_][A-Za-z0-9_]*)");
    }

    private static Pattern importedNamePattern(String statementSuffix) {
        String suffix = statementSuffix.isEmpty() ? "\\s*$" : "\\s*" + Pattern.quote(statementSuffix);
        return Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_.]+)" + suffix);
    }

    public String statementFor(String qualifiedName) {
        return IMPORT_KEYWORD + qualifiedName + statementSuffix;
    }

    public String completionInsertTextFor(String qualifiedName) {
        return qualifiedName + statementSuffix;
    }

    public boolean isImplicitPackage(String packageName) {
        return implicitPackages.contains(packageName);
    }

    public Pattern typeDeclarationPattern() {
        return typeDeclarationPattern;
    }

    public Pattern importedNamePattern() {
        return importedNamePattern;
    }
}
