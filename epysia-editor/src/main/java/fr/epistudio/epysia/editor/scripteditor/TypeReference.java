package fr.epistudio.epysia.editor.scripteditor;

public record TypeReference(TypeOrigin origin, String name) {

    private static final TypeReference CALL_CLASS_ARGUMENT =
            new TypeReference(TypeOrigin.CALL_CLASS_ARGUMENT, "");
    private static final TypeReference RECEIVER_ELEMENT =
            new TypeReference(TypeOrigin.RECEIVER_ELEMENT, "");
    private static final TypeReference UNKNOWN = new TypeReference(TypeOrigin.UNKNOWN, "");

    public static TypeReference concrete(String name) {
        return new TypeReference(TypeOrigin.CONCRETE, name);
    }

    public static TypeReference callClassArgument() {
        return CALL_CLASS_ARGUMENT;
    }

    public static TypeReference receiverElement() {
        return RECEIVER_ELEMENT;
    }

    public static TypeReference unknown() {
        return UNKNOWN;
    }
}
