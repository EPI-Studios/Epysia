package fr.epistudio.epysia.render.shader;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderInsertionAnnotation {

    private static final Pattern ANNOTATION_PATTERN =
            Pattern.compile("@insertion\\s+(\\w+)");

    private ShaderInsertionAnnotation() {
    }

    public static Optional<String> parse(String source) {
        Matcher matcher = ANNOTATION_PATTERN.matcher(source);
        return matcher.find() ? Optional.of(matcher.group(1).toLowerCase()) : Optional.empty();
    }
}
