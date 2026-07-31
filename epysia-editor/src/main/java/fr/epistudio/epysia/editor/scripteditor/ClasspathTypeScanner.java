package fr.epistudio.epysia.editor.scripteditor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class ClasspathTypeScanner {

    private static final String CLASS_SUFFIX = ".class";

    private ClasspathTypeScanner() {
    }

    public static List<Class<?>> typesUnder(Class<?> anchor, String packagePrefix) {
        Optional<Path> root = codeSourceOf(anchor);
        if (root.isEmpty()) {
            return List.of();
        }
        List<String> names = Files.isDirectory(root.get())
                ? namesInDirectory(root.get(), packagePrefix)
                : namesInArchive(root.get(), packagePrefix);
        return resolve(names, anchor.getClassLoader());
    }

    public static List<Class<?>> typesIn(List<Path> roots, ClassLoader parent) {
        List<Path> present = roots.stream().filter(Files::exists).toList();
        if (present.isEmpty()) {
            return List.of();
        }
        ClassLoader loader = loaderFor(present, parent);
        List<Class<?>> types = new ArrayList<>();
        for (Path root : present) {
            List<String> names = Files.isDirectory(root)
                    ? namesInDirectory(root, "")
                    : namesInArchive(root, "");
            types.addAll(resolve(names, loader));
        }
        return types;
    }

    private static ClassLoader loaderFor(List<Path> roots, ClassLoader parent) {
        List<URL> urls = new ArrayList<>(roots.size());
        for (Path root : roots) {
            try {
                urls.add(root.toUri().toURL());
            } catch (MalformedURLException ignored) {
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), parent);
    }

    private static Optional<Path> codeSourceOf(Class<?> anchor) {
        CodeSource source = anchor.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException error) {
            return Optional.empty();
        }
    }

    private static List<String> namesInDirectory(Path root, String packagePrefix) {
        Path packageRoot = root.resolve(packagePrefix.replace('.', '/'));
        if (!Files.isDirectory(packageRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(packageRoot)) {
            return files.filter(path -> path.toString().endsWith(CLASS_SUFFIX))
                    .map(path -> binaryName(root.relativize(path).toString()))
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static List<String> namesInArchive(Path archive, String packagePrefix) {
        String prefix = packagePrefix.replace('.', '/');
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(archive.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(prefix) && name.endsWith(CLASS_SUFFIX)) {
                    names.add(binaryName(name));
                }
            }
        } catch (IOException error) {
            return List.of();
        }
        return names;
    }

    private static String binaryName(String relativePath) {
        String withoutSuffix = relativePath.substring(0, relativePath.length() - CLASS_SUFFIX.length());
        return withoutSuffix.replace('/', '.').replace('\\', '.');
    }

    private static List<Class<?>> resolve(List<String> names, ClassLoader loader) {
        List<Class<?>> types = new ArrayList<>(names.size());
        for (String name : names) {
            load(name, loader).ifPresent(types::add);
        }
        return types;
    }

    private static Optional<Class<?>> load(String name, ClassLoader loader) {
        if (name.contains("$")) {
            return Optional.empty();
        }
        try {
            Class<?> type = Class.forName(name, false, loader);
            return type.isAnonymousClass() || !isPublic(type) ? Optional.empty() : Optional.of(type);
        } catch (ClassNotFoundException | LinkageError error) {
            return Optional.empty();
        }
    }

    private static boolean isPublic(Class<?> type) {
        return java.lang.reflect.Modifier.isPublic(type.getModifiers());
    }
}
