package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompletionChainTest {

    private static final ImportStyle IMPORT_STYLE = ImportStyle.of(";", Set.of("java.lang"), List.of("class"));

    private static final String SCRIPT = """
            package sample;

            import fr.epistudio.epysia.components.Transform3D;
            import fr.epistudio.epysia.scripting.Behaviour;

            public final class Mover extends Behaviour {
                private Transform3D cached;

                public void step() {
                }
            }
            """;

    @Test
    void classLiteralArgumentResolvesTheComponentType(@TempDir Path root) {
        List<String> members = membersAfter("ownerOrNull().getComponentOrNull(Transform3D.class).", root);

        assertTrue(members.stream().anyMatch(member -> member.startsWith("position")),
                "expected Transform3D members, got " + members);
    }

    @Test
    void optionalElementSurvivesUnwrapping(@TempDir Path root) {
        List<String> members = membersAfter("owner().get().", root);

        assertTrue(members.stream().anyMatch(member -> member.startsWith("getComponent")),
                "expected GameObject members, got " + members);
    }

    @Test
    void aTypeWithoutStaticMembersStillOffersItsInstanceMembers(@TempDir Path root) {
        List<String> members = membersAfter("Transform3D.", root);

        assertTrue(members.stream().anyMatch(member -> member.startsWith("position")),
                "expected Transform3D members, got " + members);
    }

    @Test
    void unresolvedReceiverOffersNothing(@TempDir Path root) {
        assertEquals(List.of(), membersAfter("mystery.", root));
    }

    private static List<String> membersAfter(String expression, Path root) {
        JavaSymbols symbols = new JavaSymbols(new ComponentRegistry(), ProjectLibraries.none(),
                root.resolve("missing-scripts-out"));
        CompletionEngine engine = new CompletionEngine(symbols);
        String line = "        " + expression;
        CompletionContext context = engine.contextAt(line, line.length());
        return engine.candidates(context, SCRIPT, IMPORT_STYLE).stream()
                .map(CompletionSymbol::name)
                .toList();
    }
}
