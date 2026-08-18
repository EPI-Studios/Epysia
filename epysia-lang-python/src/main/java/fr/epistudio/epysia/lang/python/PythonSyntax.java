package fr.epistudio.epysia.lang.python;

import java.util.List;
import java.util.Set;

final class PythonSyntax {

    static final List<String> KEYWORDS = List.of(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return",
            "True", "try", "while", "with", "yield", "match", "case", "self");

    static final Set<String> DECLARATIONS = Set.of(
            "async", "class", "def", "global", "import", "from", "lambda", "nonlocal");

    static final Set<String> IMPLICIT_PACKAGES = Set.of("builtins", "java.lang");

    private PythonSyntax() {
    }
}
