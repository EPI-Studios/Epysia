package fr.epistudio.epysia.graph;

public enum PinType {
    EXEC,
    FLOAT,
    INT,
    BOOLEAN,
    STRING,
    VECTOR2,
    VECTOR3,
    VECTOR4,
    NUMERIC,
    GAME_OBJECT,
    OBJECT;

    public boolean isData() {
        return this != EXEC;
    }

    public boolean isShaderValue() {
        return this == FLOAT || this == VECTOR2 || this == VECTOR3 || this == VECTOR4 || this == NUMERIC;
    }

    public boolean acceptsFrom(PinType source) {
        if (this == source) {
            return true;
        }
        if (this == EXEC || source == EXEC) {
            return false;
        }
        if (this == FLOAT && source == INT) {
            return true;
        }
        if (isShaderValue() && source.isShaderValue()) {
            return acceptsShaderValueFrom(source);
        }
        return this == OBJECT || this == STRING || source == OBJECT;
    }

    private boolean acceptsShaderValueFrom(PinType source) {
        if (this == NUMERIC || source == NUMERIC) {
            return true;
        }
        return source == FLOAT;
    }
}
