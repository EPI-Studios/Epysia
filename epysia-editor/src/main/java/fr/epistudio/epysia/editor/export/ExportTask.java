package fr.epistudio.epysia.editor.export;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ExportTask implements ExportProgress {

    private static final String THREAD_NAME = "epysia-game-export";

    private final AtomicReference<ExportOutcome> finished = new AtomicReference<>();
    private volatile ExportStage stage = ExportStage.DOWNLOADING_TEMPLATE;
    private volatile float stageCompletion;
    private volatile boolean running;

    public boolean isRunning() {
        return running;
    }

    public ExportStage stage() {
        return stage;
    }

    public float completion() {
        return stage.overallCompletion(stageCompletion);
    }

    @Override
    public void report(ExportStage reachedStage, float reachedCompletion) {
        stage = reachedStage;
        stageCompletion = reachedCompletion;
    }

    public void start(GameExporter exporter, ExportRequest request) {
        start(() -> exporter.export(request, this));
    }

    public void start(Work work) {
        if (running) {
            return;
        }
        running = true;
        finished.set(null);
        report(ExportStage.DOWNLOADING_TEMPLATE, 0.0f);
        Thread worker = new Thread(() -> run(work), THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    private void run(Work work) {
        try {
            finished.set(ExportOutcome.exported(work.call()));
        } catch (Exception error) {
            finished.set(ExportOutcome.failed(describe(error)));
        } finally {
            running = false;
        }
    }

    @FunctionalInterface
    public interface Work {

        Path call() throws Exception;
    }

    private static String describe(Exception error) {
        return Optional.ofNullable(error.getMessage()).orElseGet(error::toString);
    }

    public Optional<ExportOutcome> drainOutcome() {
        return Optional.ofNullable(finished.getAndSet(null));
    }
}
