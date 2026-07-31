package fr.epistudio.epysia.concurrent;

import fr.epistudio.epysia.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundTasksTest {

    private static final long SETTLE_TIMEOUT_SECONDS = 5L;

    private final BackgroundTasks tasks = new BackgroundTasks(SilentLogger::new, 2);

    @AfterEach
    void stopWorkers() {
        tasks.shutdown();
    }

    @Test
    void neverDeliversAResultUntilTheMainThreadAsksForIt() throws InterruptedException {
        CountDownLatch ranOffThread = new CountDownLatch(1);
        List<String> delivered = new ArrayList<>();

        tasks.submit(() -> {
            ranOffThread.countDown();
            return "chunk";
        }, delivered::add);

        assertTrue(ranOffThread.await(SETTLE_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the work must run off thread");
        awaitSettled();
        assertEquals(List.of(), delivered, "a finished task must wait for the main thread");

        tasks.deliverCompleted();
        assertEquals(List.of("chunk"), delivered, "the result must arrive on the main thread");
    }

    @Test
    void runsTheWorkOnAWorkerThread() throws InterruptedException {
        AtomicReference<String> workThread = new AtomicReference<>();
        AtomicReference<String> deliveryThread = new AtomicReference<>();

        tasks.submit(() -> {
            workThread.set(Thread.currentThread().getName());
            return "done";
        }, ignored -> deliveryThread.set(Thread.currentThread().getName()));

        awaitSettled();
        tasks.deliverCompleted();

        assertTrue(workThread.get().startsWith("epysia-worker-"),
                "the work must not run on the caller thread, ran on " + workThread.get());
        assertEquals(Thread.currentThread().getName(), deliveryThread.get(),
                "the result must be delivered on the thread that drained it");
    }

    @Test
    void dropsTheResultOfACancelledTask() throws InterruptedException {
        List<String> delivered = new ArrayList<>();

        BackgroundTask<String> task = tasks.submit(() -> "stale", delivered::add);
        awaitSettled();
        task.cancel();
        tasks.deliverCompleted();

        assertEquals(List.of(), delivered, "a cancelled task must not deliver");
    }

    @Test
    void routesAFailureToItsHandlerAndKeepsTheWorkersAlive() throws InterruptedException {
        RuntimeException thrown = new RuntimeException("generation failed");
        AtomicReference<Throwable> captured = new AtomicReference<>();
        List<String> delivered = new ArrayList<>();

        tasks.submit(() -> {
            throw thrown;
        }, ignored -> delivered.add("should not happen"), captured::set);
        awaitSettled();
        tasks.deliverCompleted();

        assertSame(thrown, captured.get(), "the failure must reach its handler");
        assertEquals(List.of(), delivered, "a failed task must not deliver a result");

        tasks.submit(() -> "still working", delivered::add);
        awaitSettled();
        tasks.deliverCompleted();
        assertEquals(List.of("still working"), delivered, "a failure must not kill the pool");
    }

    @Test
    void reportsPendingWorkBeforeItSettles() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        tasks.submit(() -> {
            release.await();
            return "late";
        }, ignored -> {
        });

        assertEquals(1, tasks.pendingCount(), "submitted work must count as pending");
        assertFalse(tasks.settledCount() > 0, "blocked work must not be settled");
        release.countDown();
        awaitSettled();
        assertEquals(0, tasks.pendingCount(), "settled work must leave the pending count");
    }

    private void awaitSettled() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_TIMEOUT_SECONDS);
        while (tasks.settledCount() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertTrue(tasks.settledCount() > 0, "the task never settled");
    }

    private static final class SilentLogger implements Logger {

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable cause) {
        }
    }
}
