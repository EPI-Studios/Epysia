package fr.epistudio.epysia.scripting.foreign;

public record ForeignPropertyDefinition(
        String name,
        String label,
        Class<?> type,
        Object defaultValue,
        float minimum,
        float maximum,
        float step,
        boolean color
) {

    public static ForeignPropertyDefinition of(String name, Class<?> type, Object defaultValue) {
        return new ForeignPropertyDefinition(name, "", type, defaultValue, 0.0f, 0.0f, 0.0f, false);
    }
}
