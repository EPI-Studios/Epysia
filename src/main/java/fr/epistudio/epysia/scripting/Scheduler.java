package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.components.IComponent;

public interface Scheduler {
    ScheduledAction after(float seconds, Runnable action);

    ScheduledAction every(float seconds, Runnable action);

    ScheduledAction nextFrame(Runnable action);

    default ScheduledAction after(IComponent owner, float seconds, Runnable action) {
        return after(seconds, action);
    }

    default ScheduledAction every(IComponent owner, float seconds, Runnable action) {
        return every(seconds, action);
    }
}
