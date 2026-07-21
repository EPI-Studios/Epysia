package fr.epistudio.epysia.editor.scripteditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ImportPlanner {

    public record ImportPlan(int lineIndex, String insertionText, int addedLines) {
    }

    private static final String IMPORT_PREFIX = "import ";
    private static final String PACKAGE_PREFIX = "package ";
    private static final String JAVA_LANG_PACKAGE = "java.lang";
    private static final Pattern TYPE_DECLARATION_PATTERN =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern IMPORTED_NAME_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_.]+)\\s*;");

    private ImportPlanner() {
    }

    public static Optional<ImportPlan> plan(String buffer, String qualifiedName) {
        String simpleName = simpleNameOf(qualifiedName);
        String packageName = packageNameOf(qualifiedName);
        List<String> lines = List.of(buffer.split("\n", -1));
        if (packageName.equals(JAVA_LANG_PACKAGE)
                || declaresType(buffer, simpleName)
                || alreadyImportedOrConflicting(lines, qualifiedName, simpleName)) {
            return Optional.empty();
        }
        return Optional.of(insertionPlan(lines, qualifiedName));
    }

    private static boolean declaresType(String buffer, String simpleName) {
        var matcher = TYPE_DECLARATION_PATTERN.matcher(buffer);
        while (matcher.find()) {
            if (matcher.group(1).equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyImportedOrConflicting(List<String> lines, String qualifiedName,
                                                        String simpleName) {
        for (String importedName : importedNames(lines)) {
            if (importedName.equals(qualifiedName)) {
                return true;
            }
            if (simpleNameOf(importedName).equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> importedNames(List<String> lines) {
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            if (isTypeDeclarationLine(line)) {
                return names;
            }
            var matcher = IMPORTED_NAME_PATTERN.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static boolean isTypeDeclarationLine(String line) {
        return TYPE_DECLARATION_PATTERN.matcher(line).find();
    }

    private static ImportPlan insertionPlan(List<String> lines, String qualifiedName) {
        String statement = IMPORT_PREFIX + qualifiedName + ";";
        Optional<ImportPlan> withinBlock = planWithinImportBlock(lines, statement);
        return withinBlock.orElseGet(() -> planWithoutImportBlock(lines, statement));
    }

    private static Optional<ImportPlan> planWithinImportBlock(List<String> lines, String statement) {
        int lastImportLine = -1;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String trimmed = lines.get(lineIndex).strip();
            if (isTypeDeclarationLine(lines.get(lineIndex))) {
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
