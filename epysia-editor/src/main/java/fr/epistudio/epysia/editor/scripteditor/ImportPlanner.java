package fr.epistudio.epysia.editor.scripteditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ImportPlanner {

    public record ImportPlan(int lineIndex, String insertionText, int addedLines) {
    }

    private static final String IMPORT_PREFIX = "import ";
    private static final String PACKAGE_PREFIX = "package ";

    private ImportPlanner() {
    }

    public static Optional<ImportPlan> plan(String buffer, String qualifiedName, ImportStyle style) {
        String simpleName = simpleNameOf(qualifiedName);
        String packageName = packageNameOf(qualifiedName);
        List<String> lines = List.of(buffer.split("\n", -1));
        if (style.isImplicitPackage(packageName)
                || declaresType(buffer, simpleName, style)
                || alreadyImportedOrConflicting(lines, qualifiedName, simpleName, style)) {
            return Optional.empty();
        }
        return Optional.of(insertionPlan(lines, style.statementFor(qualifiedName), style));
    }

    private static boolean declaresType(String buffer, String simpleName, ImportStyle style) {
        var matcher = style.typeDeclarationPattern().matcher(buffer);
        while (matcher.find()) {
            if (matcher.group(1).equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyImportedOrConflicting(List<String> lines, String qualifiedName,
                                                        String simpleName, ImportStyle style) {
        for (String importedName : importedNames(lines, style)) {
            if (importedName.equals(qualifiedName) || simpleNameOf(importedName).equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> importedNames(List<String> lines, ImportStyle style) {
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            if (isTypeDeclarationLine(line, style)) {
                return names;
            }
            var matcher = style.importedNamePattern().matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static boolean isTypeDeclarationLine(String line, ImportStyle style) {
        return style.typeDeclarationPattern().matcher(line).find();
    }

    private static ImportPlan insertionPlan(List<String> lines, String statement, ImportStyle style) {
        Optional<ImportPlan> withinBlock = planWithinImportBlock(lines, statement, style);
        return withinBlock.orElseGet(() -> planWithoutImportBlock(lines, statement));
    }

    private static Optional<ImportPlan> planWithinImportBlock(List<String> lines, String statement,
                                                              ImportStyle style) {
        int lastImportLine = -1;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String trimmed = lines.get(lineIndex).strip();
            if (isTypeDeclarationLine(lines.get(lineIndex), style)) {
                break;
            }
            if (trimmed.startsWith(IMPORT_PREFIX)) {
                lastImportLine = lineIndex;
                if (trimmed.compareTo(statement) > 0) {
                    return Optional.of(new ImportPlan(lineIndex, statement + "\n", 1));
                }
            }
        }
        return lastImportLine < 0
                ? Optional.empty()
                : Optional.of(new ImportPlan(lastImportLine + 1, statement + "\n", 1));
    }

    private static ImportPlan planWithoutImportBlock(List<String> lines, String statement) {
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (lines.get(lineIndex).strip().startsWith(PACKAGE_PREFIX)) {
                return new ImportPlan(lineIndex + 1, "\n" + statement + "\n", 2);
            }
        }
        return new ImportPlan(0, statement + "\n\n", 2);
    }

    private static String simpleNameOf(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static String packageNameOf(String qualifiedName) {
        int lastDotIndex = qualifiedName.lastIndexOf('.');
        return lastDotIndex < 0 ? "" : qualifiedName.substring(0, lastDotIndex);
    }
}
