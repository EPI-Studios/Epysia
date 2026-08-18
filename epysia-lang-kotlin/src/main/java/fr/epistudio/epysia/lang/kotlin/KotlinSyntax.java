package fr.epistudio.epysia.lang.kotlin;

import java.util.List;
import java.util.Set;

final class KotlinSyntax {

    static final List<String> KEYWORDS = List.of(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get",
            "import", "init", "param", "property", "receiver", "set", "setparam", "value", "where",
            "abstract", "actual", "annotation", "companion", "const", "crossinline", "data", "enum",
            "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
            "noinline", "open", "operator", "out", "override", "private", "protected", "public",
            "reified", "sealed", "suspend", "tailrec", "vararg");

    static final Set<String> DECLARATIONS = Set.of(
            "abstract", "actual", "annotation", "class", "companion", "const", "crossinline",
            "data", "enum", "expect", "external", "final", "fun", "infix", "init", "inline",
            "inner", "interface", "internal", "lateinit", "noinline", "object", "open", "operator",
            "out", "override", "private", "protected", "public", "reified", "sealed", "suspend",
            "tailrec", "typealias", "val", "var", "vararg");

    static final Set<String> IMPLICIT_PACKAGES = Set.of(
            "kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons",
            "kotlin.io", "kotlin.jvm", "kotlin.ranges", "kotlin.sequences", "kotlin.text",
            "java.lang");

    private KotlinSyntax() {
    }
}
