package fr.epistudio.epysia.concurrent;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FrameWorkers {
    @FunctionalInterface
    public interface Chunk {
        void run(int from, int toExclusive, int workerIndex);
    }

    private static final int MAXIMUM_WORKERS = 8;

    private final ExecutorService workers;
    private final int workerCount;

    public FrameWorkers() {
        this(defaultWorkerCount());
    }

    public FrameWorkers(int workerCount) {
        this.workerCount = Math.max(1, Math.min(MAXIMUM_WORKERS, workerCount));
        this.workers = this.workerCount == 1
                ? null
                : Executors.newFixedThreadPool(this.workerCount - 1, namedThreads());
    }

    public int workerCount() {
        return workerCount;
    }

    public void split(int itemCount, int minimumPerWorker, Chunk chunk) {
        if (itemCount <= 0) {
            return;
        }
        int chunks = chunkCount(itemCount, minimumPerWorker);
        if (chunks <= 1) {
            chunk.run(0, itemCount, 0);
            return;
        }
        runChunks(itemCount, chunks, chunk);
    }

    private int chunkCount(int itemCount, int minimumPerWorker) {
        if (workers == null) {
            return 1;
        }
        return Math.max(1, Math.min(workerCount, itemCount / Math.max(1, minimumPerWorker)));
    }

    private void runChunks(int itemCount, int chunks, Chunk chunk) {
        CountDownLatch done = new CountDownLatch(chunks - 1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int span = (itemCount + chunks - 1) / chunks;
        for (int index = 1; index < chunks; index++) {
            int from = index * span;
            int to = Math.min(itemCount, from + span);
            int worker = index;
            workers.execute(() -> runChunk(chunk, from, to, worker, failure, done));
        }
        runGuarded(chunk, 0, Math.min(itemCount, span), 0, failure);
        awaitQuietly(done);
        rethrow(failure.get());
    }

    private static void runChunk(Chunk chunk, int from, int to, int worker,
                                 AtomicReference<Throwable> failure, CountDownLatch done) {
        try {
            runGuarded(chunk, from, to, worker, failure);
        } finally {
            done.countDown();
        }
    }

    private static void runGuarded(Chunk chunk, int from, int to, int worker,
                                   AtomicReference<Throwable> failure) {
        try {
            if (from < to) {
                chunk.run(from, to, worker);
            }
        } catch (RuntimeException | Error thrown) {
            failure.compareAndSet(null, thrown);
        }
    }

    private static void awaitQuietly(CountDownLatch done) {
        try {
            done.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EpysiaException("Interrupted while waiting for frame workers.");
        }
    }

    private static void rethrow(Throwable failure) {
        switch (failure) {
            case null -> {
            }
            case RuntimeException runtime -> throw runtime;
            case Error error -> throw error;
            default -> throw new EpysiaException("Frame worker failed: " + failure);
        }
    }

    public void shutdown() {
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    private static int defaultWorkerCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "epysia-frame-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
