package fr.epistudio.epysia.editor.scripteditor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImportPlannerTest {

    private static final ImportStyle JAVA = new JavaScriptSyntax().importStyle();
    private static final ImportStyle SUFFIX_FREE = ImportStyle.of("",
            Set.of("kotlin", "kotlin.collections", "java.lang"),
            List.of("class", "object", "fun", "val", "var"));

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
    void suffixFreeImportsCarryNoSemicolon() {
        String buffer = """
                import fr.epistudio.epysia.scripting.Behaviour

                class Player : Behaviour() {
                }
                """;

        Optional<ImportPlanner.ImportPlan> plan =
                ImportPlanner.plan(buffer, "org.joml.Vector3f", SUFFIX_FREE);

        assertTrue(plan.isPresent());
        assertEquals("import org.joml.Vector3f\n", plan.get().insertionText());
    }

    @Test
    void existingImportsAreNotDuplicated() {
        String buffer = """
                package game

                import org.joml.Vector3f

                class Player {
                }
                """;

        assertTrue(ImportPlanner.plan(buffer, "org.joml.Vector3f", SUFFIX_FREE).isEmpty());
    }

    @Test
    void theImportBlockEndsAtADeclaration() {
        String buffer = """
                package game

                import org.joml.Vector3f

                object Registry {
                }
                """;

        Optional<ImportPlanner.ImportPlan> plan =
                ImportPlanner.plan(buffer, "org.joml.Quaternionf", SUFFIX_FREE);

        assertTrue(plan.isPresent());
        assertEquals(2, plan.get().lineIndex());
    }

    @Test
    void implicitPackagesAreSkippedPerLanguage() {
        String kotlinBuffer = "class Player {\n}\n";
        String javaBuffer = "public final class Player {\n}\n";

        assertTrue(ImportPlanner.plan(kotlinBuffer, "kotlin.collections.List", SUFFIX_FREE).isEmpty());
        assertTrue(ImportPlanner.plan(javaBuffer, "java.lang.String", JAVA).isEmpty());
        assertTrue(ImportPlanner.plan(javaBuffer, "kotlin.collections.List", JAVA).isPresent());
    }

    @Test
    void styleIsSelectedByFileExtension() {
        ScriptSyntaxes syntaxes = ScriptSyntaxes.discover();

        assertEquals("import a.B;", syntaxes.importStyleFor(Path.of("Player.java"))
                .orElseThrow().statementFor("a.B"));
        assertTrue(syntaxes.importStyleFor(Path.of("notes.txt")).isEmpty());
    }
}
