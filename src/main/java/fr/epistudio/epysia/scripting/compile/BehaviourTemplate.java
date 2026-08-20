package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.scripting.LanguageResource;

public record BehaviourTemplate(String body) {

    private static final String CLASS_NAME_PLACEHOLDER = "{{ClassName}}";

    public static BehaviourTemplate loadedFrom(Class<?> owner, String resourceName) {
        return new BehaviourTemplate(LanguageResource.read(owner, resourceName));
    }

    public String rendered(String className) {
        return body.replace(CLASS_NAME_PLACEHOLDER, className);
    }
}
