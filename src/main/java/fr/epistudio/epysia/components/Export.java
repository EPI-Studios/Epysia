package fr.epistudio.epysia.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Export {

    String label() default "";

    float min() default Float.NEGATIVE_INFINITY;

    float max() default Float.POSITIVE_INFINITY;

    float step() default 0.05f;

    boolean color() default false;

    boolean layerMask() default false;

    String[] assetExtensions() default {};
}
