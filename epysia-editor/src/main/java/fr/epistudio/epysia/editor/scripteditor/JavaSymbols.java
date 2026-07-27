package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scripting.Behaviour;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

public final class JavaSymbols {

    private static final String ENGINE_PACKAGE = "fr.epistudio.epysia";

    private static final List<String> KEYWORDS = List.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "permits", "private", "protected", "public", "record",
            "return", "sealed", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
            "yield", "true", "false", "null");

    private static final List<Class<?>> EXTRA_CLASSES = List.of(
            Vector2f.class, Vector3f.class, Vector4f.class,
            Matrix3f.class, Matrix4f.class, Quaternionf.class,
            String.class, Math.class, Optional.class, List.class, Map.class);

    private final Map<String, Class<?>> typesBySimpleName = new TreeMap<>();
    private final Map<String, String> qualifiedBySimpleName = new TreeMap<>();
    private final Map<String, List<CompletionSymbol>> instanceMembers = new HashMap<>();
    private final Map<String, List<CompletionSymbol>> staticMembers = new HashMap<>();
    private final List<CompletionSymbol> globalPool = new ArrayList<>();

    public JavaSymbols(ComponentRegistry registry) {
        indexAll(discoverTypes(registry));
        buildGlobalPool();
    }

    private static List<Class<?>> discoverTypes(ComponentRegistry registry) {
        List<Class<?>> classes = new ArrayList<>(EXTRA_CLASSES);
        classes.addAll(ClasspathTypeScanner.typesUnder(Behaviour.class, ENGINE_PACKAGE));
        classes.addAll(ClasspathTypeScanner.typesUnder(EngineServices.class, ENGINE_PACKAGE));
        registry.entries().forEach(entry -> classes.add(entry.componentClass()));
        return classes;
    }

    private void indexAll(List<Class<?>> classes) {
        for (Class<?> type : classes) {
            typesBySimpleName.putIfAbsent(type.getSimpleName(), type);
            qualifiedBySimpleName.putIfAbsent(type.getSimpleName(), type.getName());
        }
    }

    private void buildGlobalPool() {
        for (String keyword : KEYWORDS) {
            globalPool.add(new CompletionSymbol(keyword, keyword, CompletionKind.KEYWORD));
        }
        for (Map.Entry<String, Class<?>> entry : typesBySimpleName.entrySet()) {
            globalPool.add(new CompletionSymbol(entry.getKey(), entry.getKey(),
                    CompletionKind.TYPE, Optional.of(entry.getValue().getName())));
        }
        globalPool.addAll(instanceMembersOf(Behaviour.class.getSimpleName()));
    }

    public List<CompletionSymbol> instanceMembersOf(String simpleTypeName) {
        return membersOf(instanceMembers, simpleTypeName, false);
    }

    public List<CompletionSymbol> staticMembersOf(String simpleTypeName) {
        return membersOf(staticMembers, simpleTypeName, true);
    }

    private List<CompletionSymbol> membersOf(Map<String, List<CompletionSymbol>> cache,
                                             String simpleTypeName, boolean wantStatic) {
        Class<?> type = typesBySimpleName.get(simpleTypeName);
        if (type == null) {
            return List.of();
        }
        return cache.computeIfAbsent(simpleTypeName, ignored -> collectMembers(type, wantStatic));
    }

    public Optional<String> memberTypeOf(String simpleTypeName, String memberName) {
        for (CompletionSymbol symbol : instanceMembersOf(simpleTypeName)) {
            if (symbol.name().equals(memberName)) {
                return symbol.memberTypeName();
            }
        }
        for (CompletionSymbol symbol : staticMembersOf(simpleTypeName)) {
            if (symbol.name().equals(memberName)) {
                return symbol.memberTypeName();
            }
        }
        return Optional.empty();
    }

    private static List<CompletionSymbol> collectMembers(Class<?> type, boolean wantStatic) {
        Map<String, CompletionSymbol> byLabel = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            boolean matchesStaticness = Modifier.isStatic(method.getModifiers()) == wantStatic;
            if (method.getDeclaringClass() != Object.class && matchesStaticness) {
                CompletionSymbol symbol = methodSymbol(method);
                byLabel.putIfAbsent(symbol.label(), symbol);
            }
        }
        for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) == wantStatic) {
                byLabel.putIfAbsent(field.getName(), fieldSymbol(field));
            }
        }
        return List.copyOf(new TreeMap<>(byLabel).values());
    }

    private static CompletionSymbol fieldSymbol(Field field) {
        return new CompletionSymbol(field.getName(), field.getName(), CompletionKind.FIELD,
                Optional.empty(), Optional.of(field.getType().getSimpleName()));
    }

    private static CompletionSymbol methodSymbol(Method method) {
        StringJoiner parameters = new StringJoiner(", ", "(", ")");
        for (Class<?> parameter : method.getParameterTypes()) {
            parameters.add(parameter.getSimpleName());
        }
        String label = method.getName() + parameters + " : " + method.getReturnType().getSimpleName();
        String insertText = method.getParameterCount() == 0
                ? method.getName() + "()"
                : method.getName() + "(";
        return new CompletionSymbol(label, insertText, CompletionKind.METHOD,
                Optional.empty(), Optional.of(method.getReturnType().getSimpleName()));
    }

    public boolean knowsType(String simpleTypeName) {
        return typesBySimpleName.containsKey(simpleTypeName);
    }

    public Set<String> typeNames() {
        return Set.copyOf(typesBySimpleName.keySet());
    }

    public List<String> qualifiedTypeNames() {
        return List.copyOf(qualifiedBySimpleName.values());
    }

    public List<CompletionSymbol> globalPool() {
        return List.copyOf(globalPool);
    }

    public static List<String> keywords() {
        return KEYWORDS;
    }
}
