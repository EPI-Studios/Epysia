package fr.epistudio.epysia.lang.kotlin;

import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.scripting.compile.ScriptCompileResult;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class KotlinScriptCompileTest {

    @Test
    void kotlinIsDiscoveredAheadOfJava() {
        ScriptLanguages languages = ScriptLanguages.discover();

        assertTrue(languages.sourceExtensions().contains(".kt"));
        assertTrue(languages.sourceExtensions().contains(".java"));
        assertTrue(languages.languages().get(0) instanceof KotlinScriptLanguage,
                "Kotlin must compile before Java so Java can see Kotlin classes");
    }

    @Test
    void kotlinCompilesAndJavaSeesItsClasses(@TempDir Path root) throws IOException {
        Path scripts = Files.createDirectories(root.resolve("scripts"));
        Files.writeString(scripts.resolve("Greeter.kt"), """
                package sample

                class Greeter {
                    fun greet(): String = "hello"
                }
                """);
        Files.writeString(scripts.resolve("UsesKotlin.java"), """
                public final class UsesKotlin {
                    public String call() {
                        return new sample.Greeter().greet();
                    }
                }
                """);
        Path output = root.resolve("out");

        ScriptCompileResult result = ScriptLanguages.discover()
                .compileAll(scripts, output, ProjectLibraries.none());

        assertTrue(result.ok(), String.join("\n", result.messages()));
        assertTrue(Files.isRegularFile(output.resolve("sample/Greeter.class")));
        assertTrue(Files.isRegularFile(output.resolve("UsesKotlin.class")));
    }
}
