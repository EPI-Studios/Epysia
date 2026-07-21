package fr.epistudio.epysia.render.postfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PostEffectStack {

    private final List<PostEffect> effects = new ArrayList<>();
    private final List<PostEffect> effectsView = Collections.unmodifiableList(effects);
    private long structureRevision;

    public List<PostEffect> effects() {
        return effectsView;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public Optional<PostEffect> effect(String name) {
        for (PostEffect effect : effects) {
            if (effect.name().equals(name)) {
                return Optional.of(effect);
            }
        }
        return Optional.empty();
    }

    public PostEffect add(String name, String shaderPath, PostEffectInsertionPoint insertionPoint) {
        remove(name);
        PostEffect effect = new PostEffect(name, shaderPath, insertionPoint);
        effects.add(effect);
        structureRevision++;
        return effect;
    }

    public void addEffect(PostEffect effect) {
        remove(effect.name());
        effects.add(effect);
        structureRevision++;
    }

    public void remove(String name) {
        if (effects.removeIf(effect -> effect.name().equals(name))) {
            structureRevision++;
        }
    }

    public void reorder(String name, int targetIndex) {
        Optional<PostEffect> found = effect(name);
        if (found.isEmpty()) {
            return;
        }
        effects.remove(found.get());
        int clamped = Math.clamp(targetIndex, 0, effects.size());
        effects.add(clamped, found.get());
        structureRevision++;
    }

    public void clear() {
        if (!effects.isEmpty()) {
            effects.clear();
            structureRevision++;
        }
    }

    public void enable(String name) {
        effect(name).ifPresent(found -> found.setEnabled(true));
    }

    public void disable(String name) {
        effect(name).ifPresent(found -> found.setEnabled(false));
    }

    public boolean isEnabled(String name) {
        return effect(name).map(PostEffect::enabled).orElse(false);
    }

    public long combinedStructureRevision() {
        long combined = structureRevision;
        for (PostEffect effect : effects) {
            combined += effect.structureRevision();
        }
        return combined;
    }
}
