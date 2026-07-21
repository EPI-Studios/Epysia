package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

public final class JavaSymbols {

    private static final List<String> KEYWORDS = List.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "permits", "private", "protected", "public", "record",
            "return", "sealed", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
            "yield", "true", "false", "null");

    private static final List<Class<?>> CORE_CLASSES = List.of(
            Behaviour.class, EngineServices.class, GameObject.class, Transform3D.class,
            Scene.class, InputState.class, KeyCode.class,
            Vector2f.class, Vector3f.class, Vector4f.class,
            Matrix3f.class, Matrix4f.class, Quaternionf.class,
            String.class, Math.class, Optional.class, List.class, Map.class);

    private final Map<String, List<CompletionSymbol>> instanceMembers = new HashMap<>();
    private final Map<String, List<CompletionSymbol>> staticMembers = new HashMap<>();
    private final List<CompletionSymbol> globalPool = new ArrayList<>();
    private final Map<String, String> qualifiedBySimpleName = new TreeMap<>();

    public JavaSymbols(ComponentRegistry registry) {
        List<Class<?>> classes = new ArrayList<>(CORE_CLASSES);
        registry.entries().forEach(entry -> classes.add(entry.componentClass()));
        classes.forEach(this::indexClass);
        buildGlobalPool(classes);
    }

    private void buildGlobalPool(List<Class<?>> classes) {
        for (String keyword : KEYWORDS) {
            globalPool.add(new CompletionSymbol(keyword, keyword, CompletionKind.KEYWORD));
        }
        for (Class<?> type : classes) {
            globalPool.add(new CompletionSymbol(type.getSimpleName(), type.getSimpleName(),
                    CompletionKind.TYPE, Optional.of(type.getName())));
        }
        globalPool.addAll(instanceMembersOf(Behaviour.class.getSimpleName()));
    }

    private void indexClass(Class<?> type) {
        String name = type.getSimpleName();
        instanceMembers.putIfAbsent(name, collectMembers(type, false));
        staticMembers.putIfAbsent(name, collectMembers(type, true));
        qualifiedBySimpleName.putIfAbsent(name, type.getName());
    }

    private static List<CompletionSymbol> collectMembers(Class<?> type, boolean wantStatic) {
        Map<String, CompletionSymbol> byLabel = new TreeMap<>();
        for (Method method : type.getMethods()) {
            boolean matchesStaticness = Modifier.isStatic(method.getModifiers()) == wantStatic;
            if (method.getDeclaringClass() != Object.class && matchesStaticness) {
                CompletionSymbol symbol = methodSymbol(method);
                byLabel.putIfAbsent(symbol.label(), symbol);
            }
        }
        for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) == wantStatic) {
                byLabel.putIfAbsent(field.getName(),
                        new CompletionSymbol(field.getName(), field.getName(), CompletionKind.FIELD));
            }
        }
        return List.copyOf(byLabel.values());
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
        return new CompletionSymbol(label, insertText, CompletionKind.METHOD);
    }

    public List<CompletionSymbol> instanceMembersOf(String simpleTypeName) {
        return instanceMembers.getOrDefault(simpleTypeName, List.of());
    }

    public List<CompletionSymbol> staticMembersOf(String simpleTypeName) {
        return staticMembers.getOrDefault(simpleTypeName, List.of());
    }

    public boolean knowsType(String simpleTypeName) {
        return instanceMembers.containsKey(simpleTypeName);
    }

    public Set<String> typeNames() {
        return Set.copyOf(instanceMembers.keySet());
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
