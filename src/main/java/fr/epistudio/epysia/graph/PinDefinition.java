package fr.epistudio.epysia.graph;

public record PinDefinition(String name, PinType type) {

    public static PinDefinition exec(String name) {
        return new PinDefinition(name, PinType.EXEC);
    }
}
