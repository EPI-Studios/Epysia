package fr.epistudio.epysia.editor.scripts;

import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectDependencies;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LibraryResolutionTask {

    private static final String THREAD_NAME = "epysia-library-resolution";
    private static final String USER_DIRECTORY_NAME = ".epysia";

    private final AtomicReference<MavenLibraryResolver.Outcome> finished = new AtomicReference<>();
    private volatile boolean running;

    public boolean isRunning() {
        return running;
    }

    public void start(Project project) {
        if (running) {
            return;
        }
        running = true;
        finished.set(null);
        ProjectDependencies dependencies = project.dependencies();
        MavenLibraryResolver resolver = new MavenLibraryResolver(sharedRepositoryRoot());
        Thread worker = new Thread(() -> run(resolver, dependencies, project), THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    private void run(MavenLibraryResolver resolver, ProjectDependencies dependencies, Project project) {
        try {
            finished.set(resolver.resolveInto(dependencies, project.librariesCacheDirectory()));
        } finally {
            running = false;
        }
    }

    private static java.nio.file.Path sharedRepositoryRoot() {
        return java.nio.file.Path.of(System.getProperty("user.home"), USER_DIRECTORY_NAME);
    }

    public Optional<MavenLibraryResolver.Outcome> drainOutcome() {
        return Optional.ofNullable(finished.getAndSet(null));
    }
}
