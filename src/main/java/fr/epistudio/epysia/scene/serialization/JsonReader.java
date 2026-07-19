package fr.epistudio.epysia.scene.serialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonReader {

    private final String source;
    private int position;

    public JsonReader(String source) {
        this.source = source;
    }

    public Object readValue() {
        skipWhitespace();
        if (position >= source.length()) {
            throw new IllegalStateException("Unexpected end of JSON");
        }
        char character = source.charAt(position);
        if (character == '{') return readObject();
        if (character == '[') return readArray();
        if (character == '"') return readString();
        if (character == 't' || character == 'f') return readBoolean();
        if (character == 'n') {
            expectLiteral("null");
            return null;
        }
        return readNumber();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readRootObject() {
        Object root = readValue();
        if (!(root instanceof Map)) {
            throw new IllegalStateException("Expected JSON object at root");
        }
        return (Map<String, Object>) root;
    }

    private Map<String, Object> readObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        position++;
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            require(':');
            Object value = readValue();
            result.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                position++;
            } else if (next == '}') {
                position++;
                return result;
            } else {
                throw new IllegalStateException("Expected ',' or '}' at position " + position);
            }
        }
    }

    private List<Object> readArray() {
        List<Object> result = new ArrayList<>();
        position++;
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return result;
        }
        while (true) {
            result.add(readValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                position++;
            } else if (next == ']') {
                position++;
                return result;
            } else {
                throw new IllegalStateException("Expected ',' or ']' at position " + position);
            }
        }
    }

    private String readString() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char character = source.charAt(position++);
            if (character == '"') {
                return result.toString();
            }
            if (character == '\\' && position < source.length()) {
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    default -> result.append(escaped);
                }
            } else {
                result.append(character);
            }
        }
        throw new IllegalStateException("Unterminated string");
    }

    private Number readNumber() {
        int start = position;
        if (peek() == '-' || peek() == '+') {
            position++;
        }
        boolean hasDecimal = false;
        while (position < source.length()) {
            char character = source.charAt(position);
            if (Character.isDigit(character)) {
                position++;
            } else if (character == '.' || character == 'e' || character == 'E' || character == '-' || character == '+') {
                hasDecimal = true;
                position++;
            } else {
                break;
            }
        }
        String segment = source.substring(start, position);
        if (hasDecimal) {
            return Double.parseDouble(segment);
        }
        try {
            return Long.parseLong(segment);
        } catch (NumberFormatException exception) {
            return Double.parseDouble(segment);
        }
    }

    private boolean readBoolean() {
        if (peek() == 't') {
            expectLiteral("true");
            return true;
        }
        expectLiteral("false");
        return false;
    }

    private void expectLiteral(String literal) {
        for (int i = 0; i < literal.length(); i++) {
            if (position >= source.length() || source.charAt(position) != literal.charAt(i)) {
                throw new IllegalStateException("Expected literal: " + literal);
            }
            position++;
        }
    }

    private void require(char expected) {
        if (peek() != expected) {
            throw new IllegalStateException("Expected '" + expected + "' at position " + position);
        }
        position++;
    }

    private char peek() {
        if (position >= source.length()) {
            return '\0';
        }
        return source.charAt(position);
    }

    private void skipWhitespace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }
}
