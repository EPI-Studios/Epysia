package fr.epistudio.epysia.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class AudioSourcePool {

    private final List<AudioSource> allSources = new ArrayList<>();
    private final Deque<AudioSource> available = new ArrayDeque<>();

    public AudioSourcePool(int capacity) {
        for (int i = 0; i < capacity; i++) {
            AudioSource source = new AudioSource();
            allSources.add(source);
            available.add(source);
        }
    }

    public AudioSource acquire() {
        return available.pollFirst();
    }

    public void release(AudioSource source) {
        if (source == null) {
            return;
        }
        source.resetForReuse();
        available.addLast(source);
    }

    public int capacity() {
        return allSources.size();
    }

    public int availableCount() {
        return available.size();
    }

    public void destroy() {
        for (AudioSource source : allSources) {
            source.destroy();
        }
        allSources.clear();
        available.clear();
    }
}
