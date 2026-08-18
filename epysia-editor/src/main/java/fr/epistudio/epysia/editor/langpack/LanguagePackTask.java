package fr.epistudio.epysia.editor.langpack;

import fr.epistudio.epysia.project.Project;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class LanguagePackTask {

    private static final String THREAD_NAME = "epysia-language-packs";

    public record Outcome(String message, boolean changed, LanguagePackCatalogue catalogue) {

        static Outcome of(String message, boolean changed) {
            return new Outcome(message, changed, LanguagePackCatalogue.empty());
        }
    }

    private final LanguagePackStore store = new LanguagePackStore();
    private final AtomicReference<Outcome> finished = new AtomicReference<>();
    private volatile boolean running;

    public boolean isRunning() {
        return running;
    }

    public void fetchCatalogue() {
        start(() -> {
            try {
                return new Outcome("", false, store.fetchCatalogue());
            } catch (Exception failure) {
                return Outcome.of("Could not read the language pack list: " + messageOf(failure), false);
            }
        });
    }

    public void install(Project project, LanguagePack pack) {
        start(() -> {
            try {
                store.install(project, pack);
                return Outcome.of("Installed " + pack.name() + " " + pack.version() + ".", true);
            } catch (Exception failure) {
                return Outcome.of("Could not install " + pack.name() + ": " + messageOf(failure), false);
            }
        });
    }

    public void remove(Project project, LanguagePack pack) {
        start(() -> {
            try {
                store.remove(project, pack.identifier());
                return Outcome.of("Removed " + pack.name() + ".", true);
            } catch (Exception failure) {
                return Outcome.of("Could not remove " + pack.name() + ": " + messageOf(failure), false);
            }
        });
    }

    public Optional<String> installedVersion(Project project, String identifier) {
        return store.installedVersion(project, identifier);
    }

    private void start(Supplier<Outcome> work) {
        if (running) {
            return;
        }
        running = true;
        finished.set(null);
        Thread worker = new Thread(() -> run(work), THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    private void run(Supplier<Outcome> work) {
        try {
            finished.set(work.get());
        } finally {
            running = false;
        }
    }

    private static String messageOf(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public Optional<Outcome> drainOutcome() {
        return Optional.ofNullable(finished.getAndSet(null));
    }
}
