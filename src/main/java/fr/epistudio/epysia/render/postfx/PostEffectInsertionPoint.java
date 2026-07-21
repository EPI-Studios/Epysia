package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.render.shader.ShaderInsertionAnnotation;

import java.util.Optional;

public enum PostEffectInsertionPoint {
    BEFORE_TONEMAP("Before Tonemap (HDR)", "before_tonemap"),
    AFTER_TONEMAP("After Tonemap (LDR)", "after_tonemap");

    private final String displayName;
    private final String annotationToken;

    PostEffectInsertionPoint(String displayName, String annotationToken) {
        this.displayName = displayName;
        this.annotationToken = annotationToken;
    }

    public String displayName() {
        return displayName;
    }

    public String annotationToken() {
        return annotationToken;
    }

    public static Optional<PostEffectInsertionPoint> declaredIn(String shaderSource) {
        return ShaderInsertionAnnotation.parse(shaderSource).flatMap(PostEffectInsertionPoint::fromToken);
    }

    private static Optional<PostEffectInsertionPoint> fromToken(String token) {
        for (PostEffectInsertionPoint point : values()) {
            if (point.annotationToken.equals(token)) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }
}
