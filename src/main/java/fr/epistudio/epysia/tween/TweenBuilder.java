package fr.epistudio.epysia.tween;

import fr.epistudio.epysia.components.IComponent;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Consumer;

public final class TweenBuilder {

    private final Tweens tweens;
    private final float durationSeconds;

    private Easing easing = Easing.QUAD_OUT;
    private IComponent owner;
    private TweenLoop loop = TweenLoop.ONCE;
    private float delaySeconds;

    TweenBuilder(Tweens tweens, float durationSeconds) {
        this.tweens = tweens;
        this.durationSeconds = durationSeconds;
    }

    public TweenBuilder easing(Easing value) {
        easing = value == null ? Easing.LINEAR : value;
        return this;
    }

    public TweenBuilder ownedBy(IComponent value) {
        owner = value;
        return this;
    }

    public TweenBuilder loop(TweenLoop value) {
        loop = value == null ? TweenLoop.ONCE : value;
        return this;
    }

    public TweenBuilder after(float seconds) {
        delaySeconds = Math.max(0.0f, seconds);
        return this;
    }

    public Tween value(float from, float to, Consumer<Float> sink) {
        return start(progress -> sink.accept(from + (to - from) * progress));
    }

    public Tween vector2(Vector2f from, Vector2f to, Consumer<Vector2f> sink) {
        Vector2f origin = new Vector2f(from);
        Vector2f target = new Vector2f(to);
        Vector2f scratch = new Vector2f();
        return start(progress -> sink.accept(origin.lerp(target, progress, scratch)));
    }

    public Tween vector3(Vector3f from, Vector3f to, Consumer<Vector3f> sink) {
        Vector3f origin = new Vector3f(from);
        Vector3f target = new Vector3f(to);
        Vector3f scratch = new Vector3f();
        return start(progress -> sink.accept(origin.lerp(target, progress, scratch)));
    }

    public Tween vector4(Vector4f from, Vector4f to, Consumer<Vector4f> sink) {
        Vector4f origin = new Vector4f(from);
        Vector4f target = new Vector4f(to);
        Vector4f scratch = new Vector4f();
        return start(progress -> sink.accept(origin.lerp(target, progress, scratch)));
    }

    private Tween start(Consumer<Float> sink) {
        return tweens.add(new Tween(durationSeconds, easing, sink, owner, loop, delaySeconds));
    }
}
