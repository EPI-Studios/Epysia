package fr.epistudio.epysia.concurrent;

import fr.epistudio.epysia.logging.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BackgroundTasks {
    private static final int MAXIMUM_WORKERS = 8;
    private static final long SHUTDOWN_GRACE_SECONDS = 2L;
    private static final float DEFAULT_DELIVERY_BUDGET_SECONDS = 0.002f;
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final ExecutorService workers;
    private final ConcurrentLinkedQueue<Settled<?>> settled = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final Supplier<Logger> loggerSource;
    private long deliveryBudgetNanos = budgetNanosOf(DEFAULT_DELIVERY_BUDGET_SECONDS);

    private record Settled<T>(BackgroundTask<T> task, T result, Throwable failure,
                              Consumer<T> onCompleted, Consumer<Throwable> onFailed) {
    }

    public BackgroundTasks(Supplier<Logger> loggerSource) {
        this(loggerSource, defaultWorkerCount());
    }

    public BackgroundTasks(Supplier<Logger> loggerSource, int workerCount) {
        this.loggerSource = loggerSource;
        this.workers = Executors.newFixedThreadPool(Math.max(1, workerCount), namedThreads());
    }

    public static int defaultWorkerCount() {
        return Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, MAXIMUM_WORKERS);
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "epysia-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public <T> BackgroundTask<T> submit(Callable<T> work, Consumer<T> onCompleted) {
        return submit(work, onCompleted, failure ->
                loggerSource.get().error("[BackgroundTasks] task failed", failure));
    }

    public <T> BackgroundTask<T> submit(Callable<T> work, Consumer<T> onCompleted,
                                        Consumer<Throwable> onFailed) {
        BackgroundTask<T> task = new BackgroundTask<>();
        pending.incrementAndGet();
        workers.execute(() -> run(task, work, onCompleted, onFailed));
        return task;
    }

    private <T> void run(BackgroundTask<T> task, Callable<T> work,
                         Consumer<T> onCompleted, Consumer<Throwable> onFailed) {
        if (task.isCancelled()) {
            discard(task);
            return;
        }
        try {
            T result = work.call();
            publish(new Settled<>(task, result, null, onCompleted, onFailed));
        } catch (Throwable failure) {
            publish(new Settled<>(task, null, failure, onCompleted, onFailed));
        }
    }

    private <T> void publish(Settled<T> outcome) {
        settled.add(outcome);
        pending.decrementAndGet();
    }

    private void discard(BackgroundTask<?> task) {
        task.claimSettlement();
        pending.decrementAndGet();
    }

    public int pendingCount() {
        return pending.get();
    }

    public int settledCount() {
        return settled.size();
    }

    public void setDeliveryBudgetSeconds(float seconds) {
        this.deliveryBudgetNanos = budgetNanosOf(seconds);
    }

    public float deliveryBudgetSeconds() {
        return deliveryBudgetNanos / (float) NANOSECONDS_PER_SECOND;
    }

    private static long budgetNanosOf(float seconds) {
        return Math.max(0L, (long) (seconds * NANOSECONDS_PER_SECOND));
    }

    public void deliverCompleted() {
        MainThread.require("BackgroundTasks.deliverCompleted");
        long deadline = System.nanoTime() + deliveryBudgetNanos;
        Settled<?> outcome = settled.poll();
        while (outcome != null) {
            deliver(outcome);
            if (System.nanoTime() >= deadline) {
                return;
            }
            outcome = settled.poll();
        }
    }

    public void deliverAll() {
        MainThread.require("BackgroundTasks.deliverAll");
        Settled<?> outcome = settled.poll();
        while (outcome != null) {
            deliver(outcome);
            outcome = settled.poll();
        }
    }

    private <T> void deliver(Settled<T> outcome) {
        if (!outcome.task().claimSettlement() || outcome.task().isCancelled()) {
            return;
        }
        try {
            if (outcome.failure() != null) {
                outcome.onFailed().accept(outcome.failure());
                return;
            }
            outcome.onCompleted().accept(outcome.result());
        } catch (RuntimeException error) {
            loggerSource.get().error("[BackgroundTasks] delivering a task result failed", error);
        }
    }

    public void shutdown() {
        workers.shutdownNow();
        settled.clear();
        awaitTermination();
    }

    private void awaitTermination() {
        try {
            workers.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
