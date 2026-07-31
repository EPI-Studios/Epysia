package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.ProjectDependencies;
import fr.epistudio.epysia.project.ProjectLibraries;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenResolveSmokeTest {

    private static final String CENTRAL_HOST = "repo.maven.apache.org";
    private static final int HTTPS_PORT = 443;
    private static final int REACHABILITY_TIMEOUT_MILLIS = 2000;

    @Test
    void resolvesATransitiveTreeIntoTheCache(@TempDir Path root) {
        Assumptions.assumeTrue(centralIsReachable(), "Maven Central is not reachable");
        MavenLibraryResolver resolver = new MavenLibraryResolver(root.resolve("repo"));
        ProjectDependencies dependencies =
                new ProjectDependencies(List.of("com.squareup.okhttp3:okhttp:4.12.0"));

        MavenLibraryResolver.Outcome outcome = resolver.resolveInto(dependencies, root.resolve("cache"));

        assertTrue(outcome.ok(), String.join("\n", outcome.messages()));
        assertTrue(outcome.resolvedCount() > 1, "okhttp pulls okio and kotlin-stdlib transitively");
        assertEquals(outcome.resolvedCount(), ProjectLibraries.in(root.resolve("cache")).archives().size());
    }

    private static boolean centralIsReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(CENTRAL_HOST, HTTPS_PORT), REACHABILITY_TIMEOUT_MILLIS);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }
}
