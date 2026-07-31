package fr.epistudio.epysia.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BackgroundTask<T> {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean settled = new AtomicBoolean();

    BackgroundTask() {
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isSettled() {
        return settled.get();
    }

    boolean claimSettlement() {
        return settled.compareAndSet(false, true);
    }
}
