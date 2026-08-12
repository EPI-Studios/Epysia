package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.components.IComponent;
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
    public ScheduledAction after(float seconds, Runnable action) {
        return schedule(new Entry(Math.max(0.0f, seconds), 0.0f, action, null));
    }

    @Override
    public ScheduledAction every(float seconds, Runnable action) {
        float interval = Math.max(0.0001f, seconds);
        return schedule(new Entry(interval, interval, action, null));
    }

    @Override
    public ScheduledAction nextFrame(Runnable action) {
        return schedule(new Entry(0.0f, 0.0f, action, null));
    }

    @Override
    public ScheduledAction after(IComponent owner, float seconds, Runnable action) {
        return schedule(new Entry(Math.max(0.0f, seconds), 0.0f, action, owner));
    }

    @Override
    public ScheduledAction every(IComponent owner, float seconds, Runnable action) {
        float interval = Math.max(0.0001f, seconds);
        return schedule(new Entry(interval, interval, action, owner));
    }

    private ScheduledAction schedule(Entry entry) {
        pending.add(entry);
        return entry;
    }

    public void tick(float deltaTimeSeconds) {
        entries.addAll(pending);
        pending.clear();
        List<Entry> repeats = new ArrayList<>();
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            advance(iterator, iterator.next(), deltaTimeSeconds, repeats);
        }
        entries.addAll(repeats);
    }

    private void advance(Iterator<Entry> iterator, Entry entry, float deltaTimeSeconds,
                         List<Entry> repeats) {
        if (entry.isAbandoned()) {
            entry.cancel();
            iterator.remove();
            return;
        }
        entry.remaining -= deltaTimeSeconds;
        if (entry.remaining > 0.0f) {
            return;
        }
        run(entry.action);
        iterator.remove();
        if (entry.interval > 0.0f && !entry.cancelled) {
            entry.remaining += entry.interval;
            repeats.add(entry);
        } else {
            entry.cancelled = true;
        }
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

    public void clear() {
        entries.clear();
        pending.clear();
    }

    private static final class Entry implements ScheduledAction {
        float remaining;
        final float interval;
        final Runnable action;
        final IComponent owner;
        boolean cancelled;

        Entry(float remaining, float interval, Runnable action, IComponent owner) {
            this.remaining = remaining;
            this.interval = interval;
            this.action = action;
            this.owner = owner;
        }

        boolean isAbandoned() {
            return cancelled || (owner != null && !owner.isAlive());
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isPending() {
            return !cancelled;
        }
    }
}
