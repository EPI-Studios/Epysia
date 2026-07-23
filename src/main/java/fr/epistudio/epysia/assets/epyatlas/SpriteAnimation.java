package fr.epistudio.epysia.assets.epyatlas;

import java.util.List;

public record SpriteAnimation(String name, float framesPerSecond, boolean loop, List<String> frames) {

    public SpriteAnimation {
        frames = List.copyOf(frames);
    }
}
