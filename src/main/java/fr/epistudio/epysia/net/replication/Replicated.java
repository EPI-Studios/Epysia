package fr.epistudio.epysia.net.replication;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Replicated {
    int sendRate() default 0;

    ReplicationCondition condition() default ReplicationCondition.ALWAYS;

    boolean interpolate() default false;

    float precision() default 0.0f;
}
