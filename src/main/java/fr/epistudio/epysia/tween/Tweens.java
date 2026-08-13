package fr.epistudio.epysia.tween;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Tweens {

    private final List<Tween> active = new ArrayList<>();
    private final List<Tween> pending = new ArrayList<>();

    public TweenBuilder over(float durationSeconds) {
        return new TweenBuilder(this, durationSeconds);
    }

    public int activeCount() {
        return active.size() + pending.size();
    }

    public void cancelAll() {
        active.forEach(Tween::cancel);
        pending.forEach(Tween::cancel);
        active.clear();
        pending.clear();
    }

    public void advance(float deltaSeconds) {
        active.addAll(pending);
        pending.clear();
        Iterator<Tween> iterator = active.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().advance(deltaSeconds)) {
                iterator.remove();
            }
        }
    }

    Tween add(Tween tween) {
        pending.add(tween);
        return tween;
    }
}
