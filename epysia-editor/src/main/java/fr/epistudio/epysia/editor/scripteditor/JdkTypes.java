package fr.epistudio.epysia.editor.scripteditor;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class JdkTypes {

    private static final String CLASS_SUFFIX = ".class";
    private static final String NESTED_SEPARATOR = "$";
    private static final char PACKAGE_SEPARATOR = '.';

    private static final Set<String> MODULES = Set.of("java.base");

    private static final Set<String> PACKAGES = Set.of(
            "java.lang", "java.lang.reflect",
            "java.util", "java.util.function", "java.util.stream", "java.util.regex",
            "java.util.concurrent", "java.util.concurrent.atomic", "java.util.random",
            "java.io", "java.nio.charset", "java.nio.file",
            "java.math", "java.text",
            "java.time", "java.time.format", "java.time.temporal");

    private JdkTypes() {
    }

    static List<Class<?>> publicApi() {
        List<Class<?>> types = new ArrayList<>();
        ModuleFinder finder = ModuleFinder.ofSystem();
        for (String moduleName : MODULES) {
            finder.find(moduleName).ifPresent(reference -> types.addAll(typesIn(reference)));
        }
        return types;
    }

    private static List<Class<?>> typesIn(ModuleReference reference) {
        try (ModuleReader reader = reference.open(); Stream<String> entries = reader.list()) {
            return entries.map(JdkTypes::exportedNameOf)
                    .flatMap(Optional::stream)
                    .map(JdkTypes::loadPublic)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static Optional<String> exportedNameOf(String entry) {
        if (!entry.endsWith(CLASS_SUFFIX) || entry.contains(NESTED_SEPARATOR)) {
            return Optional.empty();
        }
        String binaryName = entry.substring(0, entry.length() - CLASS_SUFFIX.length()).replace('/', PACKAGE_SEPARATOR);
        return PACKAGES.contains(packageNameOf(binaryName)) ? Optional.of(binaryName) : Optional.empty();
    }

    private static String packageNameOf(String binaryName) {
        int lastSeparator = binaryName.lastIndexOf(PACKAGE_SEPARATOR);
        return lastSeparator < 0 ? "" : binaryName.substring(0, lastSeparator);
    }

    private static Optional<Class<?>> loadPublic(String binaryName) {
        try {
            Class<?> type = Class.forName(binaryName, false, JdkTypes.class.getClassLoader());
            return Modifier.isPublic(type.getModifiers()) ? Optional.of(type) : Optional.empty();
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
