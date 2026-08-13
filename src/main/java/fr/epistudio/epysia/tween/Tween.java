package fr.epistudio.epysia.tween;

import fr.epistudio.epysia.components.IComponent;

import java.util.function.Consumer;

public final class Tween {

    private final float durationSeconds;
    private final Easing easing;
    private final Consumer<Float> sink;
    private final IComponent owner;
    private final TweenLoop loop;

    private float delaySeconds;
    private float elapsedSeconds;
    private boolean cancelled;
    private boolean finished;
    private boolean forward = true;
    private Runnable onComplete = () -> {
    };

    Tween(float durationSeconds, Easing easing, Consumer<Float> sink, IComponent owner,
          TweenLoop loop, float delaySeconds) {
        this.durationSeconds = Math.max(0.0001f, durationSeconds);
        this.easing = easing;
        this.sink = sink;
        this.owner = owner;
        this.loop = loop;
        this.delaySeconds = Math.max(0.0f, delaySeconds);
    }

    public Tween onComplete(Runnable action) {
        onComplete = action == null ? () -> {
        } : action;
        return this;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isPending() {
        return !cancelled && !finished;
    }

    public boolean isFinished() {
        return finished;
    }

    boolean isAbandoned() {
        return cancelled || (owner != null && !owner.isAlive());
    }

    boolean advance(float deltaSeconds) {
        if (isAbandoned()) {
            return false;
        }
        if (delaySeconds > 0.0f) {
            delaySeconds -= deltaSeconds;
            return true;
        }
        elapsedSeconds += deltaSeconds;
        float progress = Math.clamp(elapsedSeconds / durationSeconds, 0.0f, 1.0f);
        sink.accept(easing.at(forward ? progress : 1.0f - progress));
        if (progress < 1.0f) {
            return true;
        }
        return completeOrRepeat();
    }

    private boolean completeOrRepeat() {
        switch (loop) {
            case ONCE -> {
                finished = true;
                onComplete.run();
                return false;
            }
            case REPEAT -> {
                elapsedSeconds = 0.0f;
                return true;
            }
            case PING_PONG -> {
                elapsedSeconds = 0.0f;
                forward = !forward;
                return true;
            }
            default -> {
                finished = true;
                return false;
            }
        }
    }
}
