package fr.epistudio.epysia.scene.serialization;

public final class JsonWriter {

    private final StringBuilder buffer = new StringBuilder();
    private int indentLevel;
    private boolean needsComma;

    public JsonWriter beginObject() {
        prepareValue();
        buffer.append("{");
        indentLevel++;
        needsComma = false;
        return this;
    }

    public JsonWriter endObject() {
        indentLevel--;
        appendNewline();
        buffer.append("}");
        needsComma = true;
        return this;
    }

    public JsonWriter beginArray() {
        prepareValue();
        buffer.append("[");
        indentLevel++;
        needsComma = false;
        return this;
    }

    public JsonWriter endArray() {
        indentLevel--;
        appendNewline();
        buffer.append("]");
        needsComma = true;
        return this;
    }

    public JsonWriter key(String name) {
        if (needsComma) {
            buffer.append(",");
        }
        appendNewline();
        buffer.append('"').append(escape(name)).append("\": ");
        needsComma = false;
        return this;
    }

    public JsonWriter valueString(String value) {
        prepareValue();
        buffer.append('"').append(escape(value)).append('"');
        needsComma = true;
        return this;
    }

    public JsonWriter valueNumber(float value) {
        prepareValue();
        buffer.append(formatFloat(value));
        needsComma = true;
        return this;
    }

    public JsonWriter valueNumber(int value) {
        prepareValue();
        buffer.append(value);
        needsComma = true;
        return this;
    }

    public JsonWriter valueNumber(long value) {
        prepareValue();
        buffer.append(value);
        needsComma = true;
        return this;
    }

    public JsonWriter valueNull() {
        prepareValue();
        buffer.append("null");
        needsComma = true;
        return this;
    }

    public JsonWriter valueBoolean(boolean value) {
        prepareValue();
        buffer.append(value ? "true" : "false");
        needsComma = true;
        return this;
    }

    private void prepareValue() {
        if (needsComma) {
            buffer.append(",");
            appendNewline();
        }
    }

    private void appendNewline() {
        buffer.append('\n');
        for (int i = 0; i < indentLevel; i++) {
            buffer.append("  ");
        }
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    private static String formatFloat(float value) {
        if (value == Math.floor(value) && !Float.isInfinite(value)) {
            return Integer.toString((int) value) + ".0";
        }
        return Float.toString(value);
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(character);
            }
        }
        return result.toString();
    }
}
