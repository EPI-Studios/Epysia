package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LibraryCompletionTest {

    @Test
    void libraryTypesAndMembersEnterTheSymbolPool(@TempDir Path root) throws IOException {
        Path libraries = Files.createDirectories(root.resolve("libs"));
        writeGreeterJar(libraries.resolve("greeter.jar"), root.resolve("jar-build"));

        JavaSymbols symbols = new JavaSymbols(new ComponentRegistry(),
                ProjectLibraries.in(libraries), root.resolve("missing-scripts-out"));

        assertTrue(symbols.knowsType("Greeter"));
        assertTrue(symbols.instanceMembersOf("Greeter").stream()
                .anyMatch(symbol -> symbol.name().startsWith("greet")));
        assertTrue(symbols.qualifiedTypeNames().contains("sample.Greeter"));
    }

    @Test
    void compiledProjectClassesEnterTheSymbolPool(@TempDir Path root) throws IOException {
        Path classes = compileGreeter(root.resolve("scripts-out-build"));

        JavaSymbols symbols = new JavaSymbols(new ComponentRegistry(), ProjectLibraries.none(), classes);

        assertTrue(symbols.knowsType("Greeter"));
    }

    @Test
    void noLibrariesLeavesThePoolUnchanged(@TempDir Path root) {
        JavaSymbols symbols = new JavaSymbols(new ComponentRegistry(),
                ProjectLibraries.none(), root.resolve("missing-scripts-out"));

        assertFalse(symbols.knowsType("Greeter"));
        assertTrue(symbols.knowsType("Behaviour"));
    }

    private static Path compileGreeter(Path workDirectory) throws IOException {
        Path source = workDirectory.resolve("sample/Greeter.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package sample;

                public final class Greeter {
                    public String greet() {
                        return "hello";
                    }
                }
                """);
        Path classes = Files.createDirectories(workDirectory.resolve("classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null, "-d", classes.toString(), source.toString());
        return classes;
    }

    private static void writeGreeterJar(Path jarPath, Path workDirectory) throws IOException {
        Path classes = compileGreeter(workDirectory);
        try (OutputStream stream = Files.newOutputStream(jarPath);
             JarOutputStream jar = new JarOutputStream(stream);
             Stream<Path> walk = Files.walk(classes)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                jar.write(Files.readAllBytes(path));
                jar.closeEntry();
            }
        }
    }
}
