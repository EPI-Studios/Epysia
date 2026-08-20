package fr.epistudio.epysia.lang.python;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class PythonStubs {

    private static final String HEADER = """
            from typing import Any

            class Vec3:
                x: float
                y: float
                z: float
                def __init__(self, x: float = 0.0, y: float = 0.0, z: float = 0.0) -> None: ...
                def length(self) -> float: ...
                def normalized(self) -> "Vec3": ...
                def dot(self, other: "Vec3") -> float: ...
                def cross(self, other: "Vec3") -> "Vec3": ...
                def to_java(self) -> Any: ...

            ZERO: Vec3
            UP: Vec3
            RIGHT: Vec3
            FORWARD: Vec3

            def component(name: str = ..., category: str = "Scripts", description: str = "") -> Any: ...

            def export(default: Any, label: str = "", minimum: float = 0.0, maximum: float = 0.0,
                       step: float = 0.0, color: bool = False) -> Any: ...
            """;

    private static final String TRANSFORM_SUGAR = """
                position: Vec3
                scale: Vec3
                world_position: Vec3
                def move(self, delta: Vec3) -> None: ...
                def rotate(self, pitch: float = 0.0, yaw: float = 0.0, roll: float = 0.0) -> None: ...
                def look_at(self, target: Vec3, up: Vec3 = ...) -> None: ...
            """;

    private static final String INPUT_SUGAR = """
                cursor: tuple[float, float]
                mouse_delta: tuple[float, float]
                scroll: float
                def key(self, name: str) -> bool: ...
                def key_pressed(self, name: str) -> bool: ...
                def key_released(self, name: str) -> bool: ...
                def mouse(self, button: str = "left") -> bool: ...
                def mouse_pressed(self, button: str = "left") -> bool: ...
            """;

    private static final String BEHAVIOUR = """
            class Behaviour:
                game_object: Any
                services: Any
                object: Object3D
                transform: Transform
                position: Vec3
                def find(self, name: str) -> Any: ...
                def on_start(self) -> None: ...
                def on_update(self, input: Input, delta_seconds: float) -> None: ...
                def on_fixed_update(self, fixed_step_seconds: float) -> None: ...
                def on_destroy(self) -> None: ...
            """;

    private PythonStubs() {
    }

    static String generate() {
        StringBuilder stub = new StringBuilder(HEADER).append('\n');
        appendClass(stub, "Transform", Transform3D.class, TRANSFORM_SUGAR);
        appendClass(stub, "Object3D", GameObject.class, "");
        appendClass(stub, "Input", InputState.class, INPUT_SUGAR);
        return stub.append(BEHAVIOUR).toString();
    }

    private static void appendClass(StringBuilder stub, String name, Class<?> source, String sugar) {
        stub.append("class ").append(name).append(":\n").append(sugar);
        for (String signature : signaturesOf(source, declaredNamesOf(sugar))) {
            stub.append("    ").append(signature).append('\n');
        }
        stub.append('\n');
    }

    private static Set<String> declaredNamesOf(String sugar) {
        Set<String> declared = new HashSet<>();
        for (String line : sugar.lines().map(String::strip).toList()) {
            if (line.startsWith("def ")) {
                declared.add(line.substring(4, line.indexOf('(')));
            } else if (line.contains(":")) {
                declared.add(line.substring(0, line.indexOf(':')).strip());
            }
        }
        return declared;
    }

    private static List<String> signaturesOf(Class<?> source, Set<String> declared) {
        TreeSet<String> signatures = new TreeSet<>(Comparator.naturalOrder());
        for (Method method : source.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class
                    || declared.contains(snakeCase(method.getName()))) {
                continue;
            }
            signatures.add(signatureOf(method));
        }
        return new ArrayList<>(signatures);
    }

    private static String signatureOf(Method method) {
        List<String> parameters = new ArrayList<>();
        parameters.add("self");
        Parameter[] declared = method.getParameters();
        for (int index = 0; index < declared.length; index++) {
            parameters.add(nameOf(declared[index], index) + ": " + pythonType(declared[index].getType()));
        }
        return "def " + snakeCase(method.getName()) + "(" + String.join(", ", parameters) + ") -> "
                + pythonType(method.getReturnType()) + ": ...";
    }

    private static String nameOf(Parameter parameter, int index) {
        return parameter.isNamePresent() ? snakeCase(parameter.getName()) : "argument" + index;
    }

    private static String pythonType(Class<?> type) {
        if (type == void.class) {
            return "None";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "bool";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "int";
        }
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return "float";
        }
        if (type == String.class) {
            return "str";
        }
        return "Any";
    }

    private static String snakeCase(String name) {
        StringBuilder converted = new StringBuilder(name.length() + 4);
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                converted.append('_');
            }
            converted.append(Character.toLowerCase(character));
        }
        return converted.toString().toLowerCase(Locale.ROOT);
    }
}
