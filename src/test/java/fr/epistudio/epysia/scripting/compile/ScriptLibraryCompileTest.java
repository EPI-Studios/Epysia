package fr.epistudio.epysia.scripting.compile;

import fr.epistudio.epysia.project.ProjectLibraries;
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

final class ScriptLibraryCompileTest {

    private static final String SCRIPT_SOURCE = """
            public final class UsesLibrary {
                public String call() {
                    return new sample.Greeter().greet();
                }
            }
            """;

    @Test
    void scriptCompilesAgainstAClassFromAProjectLibrary(@TempDir Path root) throws IOException {
        Path libraries = Files.createDirectories(root.resolve("libs"));
        writeGreeterJar(libraries.resolve("greeter.jar"), root.resolve("jar-build"));
        Path scripts = writeScript(root);

        ScriptCompileResult result = ScriptLanguages.discover()
                .compileAll(scripts, root.resolve("out"), ProjectLibraries.in(libraries));

        assertTrue(result.ok(), String.join("\n", result.messages()));
    }

    @Test
    void scriptFailsWithoutTheLibrary(@TempDir Path root) throws IOException {
        Path scripts = writeScript(root);

        ScriptCompileResult result = ScriptLanguages.discover()
                .compileAll(scripts, root.resolve("out"), ProjectLibraries.none());

        assertFalse(result.ok());
    }

    private static Path writeScript(Path root) throws IOException {
        Path scripts = Files.createDirectories(root.resolve("scripts"));
        Files.writeString(scripts.resolve("UsesLibrary.java"), SCRIPT_SOURCE);
        return scripts;
    }

    private static void writeGreeterJar(Path jarPath, Path workDirectory) throws IOException {
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
        packJar(jarPath, classes);
    }

    private static void packJar(Path jarPath, Path classes) throws IOException {
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
