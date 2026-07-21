package fr.epistudio.epysia.graph;

public final class GraphVariable {

    private String name;
    private PinType type;
    private Object defaultValue;
    private boolean exposed;

    public GraphVariable(String name, PinType type, Object defaultValue, boolean exposed) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.exposed = exposed;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PinType type() {
        return type;
    }

    public void setType(PinType type) {
        this.type = type;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean exposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }
}
