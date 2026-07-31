package fr.epistudio.epysia.editor.scripteditor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImportPlannerTest {

    private static final ImportStyle JAVA = new JavaScriptSyntax().importStyle();
    private static final ImportStyle KOTLIN = new KotlinScriptSyntax().importStyle();

    @Test
    void javaImportsCarryASemicolon() {
        String buffer = """
                import fr.epistudio.epysia.scripting.Behaviour;

                public final class Player extends Behaviour {
                }
                """;

        Optional<ImportPlanner.ImportPlan> plan =
                ImportPlanner.plan(buffer, "org.joml.Vector3f", JAVA);

        assertTrue(plan.isPresent());
        assertEquals("import org.joml.Vector3f;\n", plan.get().insertionText());
    }

    @Test
    void kotlinImportsCarryNoSemicolon() {
        String buffer = """
                import fr.epistudio.epysia.scripting.Behaviour

                class Player : Behaviour() {
                }
                """;

        Optional<ImportPlanner.ImportPlan> plan =
                ImportPlanner.plan(buffer, "org.joml.Vector3f", KOTLIN);

        assertTrue(plan.isPresent());
        assertEquals("import org.joml.Vector3f\n", plan.get().insertionText());
    }

    @Test
    void kotlinSeesItsExistingImportsAndDoesNotDuplicateThem() {
        String buffer = """
                package game

                import org.joml.Vector3f

                class Player {
                }
                """;

        assertTrue(ImportPlanner.plan(buffer, "org.joml.Vector3f", KOTLIN).isEmpty());
    }

    @Test
    void kotlinImportBlockEndsAtAnObjectOrFunctionDeclaration() {
        String buffer = """
                package game

                import org.joml.Vector3f

                object Registry {
                }
                """;

        Optional<ImportPlanner.ImportPlan> plan =
                ImportPlanner.plan(buffer, "org.joml.Quaternionf", KOTLIN);

        assertTrue(plan.isPresent());
        assertEquals(2, plan.get().lineIndex());
    }

    @Test
    void implicitPackagesAreSkippedPerLanguage() {
        String kotlinBuffer = "class Player {\n}\n";
        String javaBuffer = "public final class Player {\n}\n";

        assertTrue(ImportPlanner.plan(kotlinBuffer, "kotlin.collections.List", KOTLIN).isEmpty());
        assertTrue(ImportPlanner.plan(javaBuffer, "java.lang.String", JAVA).isEmpty());
        assertTrue(ImportPlanner.plan(javaBuffer, "kotlin.collections.List", JAVA).isPresent());
    }

    @Test
    void styleIsSelectedByFileExtension() {
        ScriptSyntaxes syntaxes = ScriptSyntaxes.discover();

        assertEquals("import a.B", syntaxes.importStyleFor(Path.of("Player.kt"))
                .orElseThrow().statementFor("a.B"));
        assertEquals("import a.B;", syntaxes.importStyleFor(Path.of("Player.java"))
                .orElseThrow().statementFor("a.B"));
        assertTrue(syntaxes.importStyleFor(Path.of("notes.txt")).isEmpty());
    }
}
