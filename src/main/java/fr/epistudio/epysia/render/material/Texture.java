package fr.epistudio.epysia.render.material;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Texture {

    ColorSpace colorSpace() default ColorSpace.INHERIT;
}
