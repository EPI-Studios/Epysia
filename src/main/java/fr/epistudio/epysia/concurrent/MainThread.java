package fr.epistudio.epysia.concurrent;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.Optional;

public final class MainThread {
    private static volatile Optional<Thread> owner = Optional.empty();

    private MainThread() {
    }

    public static void adopt() {
        owner = Optional.of(Thread.currentThread());
    }

    public static boolean isCurrent() {
        return owner.map(claimed -> claimed == Thread.currentThread()).orElse(true);
    }

    public static void require(String operation) {
        if (isCurrent()) {
            return;
        }
        throw new EpysiaException(operation + " ran on " + Thread.currentThread().getName()
                + " but is only safe on the main thread. Produce the data in the background task"
                + " and touch the engine from its completion callback.");
    }
}
