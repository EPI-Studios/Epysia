package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.logging.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DefaultScheduler implements Scheduler {

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> pending = new ArrayList<>();
    private Logger logger;

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void after(float seconds, Runnable action) {
        pending.add(new Entry(Math.max(0.0f, seconds), 0.0f, action));
    }

    @Override
    public void every(float seconds, Runnable action) {
        float interval = Math.max(0.0001f, seconds);
        pending.add(new Entry(interval, interval, action));
    }

    @Override
    public void nextFrame(Runnable action) {
        pending.add(new Entry(0.0f, 0.0f, action));
    }

    public void tick(float deltaTimeSeconds) {
        entries.addAll(pending);
        pending.clear();
        List<Entry> repeats = new ArrayList<>();
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            entry.remaining -= deltaTimeSeconds;
            if (entry.remaining > 0.0f) {
                continue;
            }
            run(entry.action);
            iterator.remove();
            if (entry.interval > 0.0f) {
                entry.remaining += entry.interval;
                repeats.add(entry);
            }
        }
        entries.addAll(repeats);
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (logger != null) {
                logger.error("[Scheduler] scheduled action threw", error);
            }
        }
    }

    private static final class Entry {
        float remaining;
        final float interval;
        final Runnable action;

        Entry(float remaining, float interval, Runnable action) {
            this.remaining = remaining;
            this.interval = interval;
            this.action = action;
        }
    }
}
