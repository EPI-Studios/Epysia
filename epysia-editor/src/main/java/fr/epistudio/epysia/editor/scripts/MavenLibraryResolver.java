package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.ProjectDependencies;
import fr.epistudio.epysia.project.ProjectLibraries;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class MavenLibraryResolver {

    private static final String CENTRAL_ID = "central";
    private static final String CENTRAL_LAYOUT = "default";
    private static final String CENTRAL_URL = "https://repo.maven.apache.org/maven2/";
    private static final String LOCAL_REPOSITORY_NAME = "maven-repository";

    public record Outcome(boolean ok, int resolvedCount, List<String> messages) {
    }

    private final Path localRepository;

    public MavenLibraryResolver(Path localRepository) {
        this.localRepository = localRepository;
    }

    public Outcome resolveInto(ProjectDependencies dependencies, Path cacheDirectory) {
        List<String> messages = new ArrayList<>();
        try {
            List<Path> artifacts = resolveArtifacts(dependencies, messages);
            replaceCache(cacheDirectory, artifacts);
            return new Outcome(true, artifacts.size(), messages);
        } catch (DependencyResolutionException error) {
            messages.add("Resolution failed: " + error.getMessage());
            return new Outcome(false, 0, messages);
        } catch (IOException error) {
            messages.add("Could not write the library cache: " + error.getMessage());
            return new Outcome(false, 0, messages);
        }
    }

    private List<Path> resolveArtifacts(ProjectDependencies dependencies, List<String> messages)
            throws DependencyResolutionException {
        if (dependencies.isEmpty()) {
            return List.of();
        }
        RepositorySystem system = new RepositorySystemSupplier().get();
        RepositorySystemSession session = newSession(system);
        List<Path> files = new ArrayList<>();
        for (ArtifactResult result : system.resolveDependencies(session, request(dependencies))
                .getArtifactResults()) {
            files.add(result.getArtifact().getFile().toPath());
        }
        messages.add("Resolved " + dependencies.coordinates().size() + " declared coordinate(s) to "
                + files.size() + " jar(s).");
        return files;
    }

    private static DependencyRequest request(ProjectDependencies dependencies) {
        CollectRequest collect = new CollectRequest();
        collect.addRepository(new RemoteRepository.Builder(CENTRAL_ID, CENTRAL_LAYOUT, CENTRAL_URL).build());
        for (String coordinate : dependencies.coordinates()) {
            collect.addDependency(new Dependency(new DefaultArtifact(coordinate), JavaScopes.COMPILE));
        }
        return new DependencyRequest(collect, DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE));
    }

    private RepositorySystemSession newSession(RepositorySystem system) {
        org.eclipse.aether.DefaultRepositorySystemSession session =
                org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session,
                new LocalRepository(localRepository.resolve(LOCAL_REPOSITORY_NAME).toFile())));
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, false);
        return session;
    }

    private static void replaceCache(Path cacheDirectory, List<Path> artifacts) throws IOException {
        clearCache(cacheDirectory);
        Files.createDirectories(cacheDirectory);
        for (Path artifact : artifacts) {
            Files.copy(artifact, cacheDirectory.resolve(artifact.getFileName().toString()),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void clearCache(Path cacheDirectory) throws IOException {
        if (!Files.isDirectory(cacheDirectory)) {
            return;
        }
        try (var entries = Files.list(cacheDirectory)) {
            for (Path entry : entries.filter(ProjectLibraries::isArchive).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
