package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.components.IComponent;

import java.util.ArrayList;
import java.util.List;

public final class ScheduledSequence implements ScheduledAction {

    private record Step(float delaySeconds, Runnable action) {
    }

    private final Scheduler scheduler;
    private final IComponent owner;
    private final List<Step> steps = new ArrayList<>();
    private ScheduledAction current;
    private int nextStep;
    private boolean cancelled;
    private boolean started;

    ScheduledSequence(Scheduler scheduler, IComponent owner) {
        this.scheduler = scheduler;
        this.owner = owner;
    }

    public ScheduledSequence then(float delaySeconds, Runnable action) {
        steps.add(new Step(Math.max(0.0f, delaySeconds), action));
        return this;
    }

    public ScheduledSequence thenNow(Runnable action) {
        return then(0.0f, action);
    }

    public ScheduledSequence start() {
        if (started) {
            return this;
        }
        started = true;
        scheduleNext();
        return this;
    }

    private void scheduleNext() {
        if (cancelled || nextStep >= steps.size()) {
            return;
        }
        Step step = steps.get(nextStep);
        nextStep++;
        current = owner == null
                ? scheduler.after(step.delaySeconds(), () -> runStep(step))
                : scheduler.after(owner, step.delaySeconds(), () -> runStep(step));
    }

    private void runStep(Step step) {
        if (cancelled) {
            return;
        }
        step.action().run();
        scheduleNext();
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (current != null) {
            current.cancel();
        }
    }

    @Override
    public boolean isPending() {
        return !cancelled && (!started || nextStep < steps.size() || pendingCurrent());
    }

    private boolean pendingCurrent() {
        return current != null && current.isPending();
    }
}
