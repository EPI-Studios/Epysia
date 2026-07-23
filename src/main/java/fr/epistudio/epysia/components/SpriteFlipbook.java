package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epyatlas.SpriteAnimation;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.TextureHandle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EpysiaComponent(name = "Sprite Flipbook", category = "Rendering")
@RequiresComponent(SpriteRenderer.class)
public final class SpriteFlipbook extends Component {

    @Export(label = "Atlas")
    private final AssetRef<SpriteAtlas> atlas = new AssetRef<>(SpriteAtlas.class);
    @Export(label = "Animation")
    private String animationName = "";
    @Export(label = "Frames")
    private String frames = "";
    @Export(label = "First Frame", min = 0.0f, step = 1.0f)
    private int firstFrame;
    @Export(label = "Frame Count", min = 0.0f, step = 1.0f)
    private int frameCount;
    @Export(label = "Frames Per Second", min = 0.01f, max = 1000.0f, step = 1.0f)
    private float framesPerSecond = 10.0f;
    @Export(label = "Loop")
    private boolean loop = true;
    @Export(label = "Playing")
    private boolean playing = true;

    private final transient List<String> frameNames = new ArrayList<>();
    private final transient AssetRef<TextureHandle> atlasTexture = new AssetRef<>(TextureHandle.class);
    private final transient Set<String> warnedAnimationNames = new HashSet<>();
    private transient Optional<SpriteAnimation> activeAnimation = Optional.empty();
    private transient Optional<Logger> logger = Optional.empty();
    private transient float localTimeSeconds;

    public AssetRef<SpriteAtlas> atlasRef() {
        return atlas;
    }

    public SpriteFlipbook setAtlasPath(String path) {
        atlas.setPath(path);
        return this;
    }

    public SpriteFlipbook setFrames(String commaSeparatedNames) {
        frames = commaSeparatedNames;
        return this;
    }

    public SpriteFlipbook setFrameRange(int first, int count) {
        firstFrame = first;
        frameCount = count;
        return this;
    }

    public float framesPerSecond() {
        return framesPerSecond;
    }

    public SpriteFlipbook setFramesPerSecond(float value) {
        framesPerSecond = value;
        return this;
    }

    public boolean loop() {
        return loop;
    }

    public SpriteFlipbook setLoop(boolean value) {
        loop = value;
        return this;
    }

    public boolean playing() {
        return playing;
    }

    public SpriteFlipbook setPlaying(boolean value) {
        if (value && !playing) {
            localTimeSeconds = 0.0f;
        }
        playing = value;
        return this;
    }

    public float localTimeSeconds() {
        return localTimeSeconds;
    }

    public SpriteFlipbook play(String name) {
        return play(name, true);
    }

    public SpriteFlipbook play(String name, boolean restartIfSame) {
        if (!restartIfSame && playing && name.equals(animationName)) {
            return this;
        }
        animationName = name;
        rebuildFrameNames(atlas.direct());
        localTimeSeconds = 0.0f;
        playing = true;
        applyCurrentFrame();
        return this;
    }

    public SpriteFlipbook stop() {
        playing = false;
        localTimeSeconds = 0.0f;
        applyCurrentFrame();
        return this;
    }

    public Optional<String> currentAnimation() {
        return activeAnimation.map(SpriteAnimation::name);
    }

    @Override
    public void onLoad(EngineServices services) {
        logger = Optional.of(services.logger());
        Optional<SpriteAtlas> resolved = atlas.resolve(services.assets());
        rebuildFrameNames(resolved);
        resolved.ifPresent(loaded -> applyAtlasTexture(services, loaded));
        applyCurrentFrame();
    }

    private void rebuildFrameNames(Optional<SpriteAtlas> resolved) {
        frameNames.clear();
        activeAnimation = resolved.flatMap(loaded -> loaded.animation(animationName));
        if (activeAnimation.isPresent()) {
            addAnimationFrames(resolved.get(), activeAnimation.get());
            return;
        }
        rebuildManualFrames(resolved);
    }

    private void addAnimationFrames(SpriteAtlas loaded, SpriteAnimation animation) {
        int missing = 0;
        for (String name : animation.frames()) {
            if (loaded.region(name).isPresent()) {
                frameNames.add(name);
            } else {
                missing++;
            }
        }
        if (missing > 0 && warnedAnimationNames.add(animation.name())) {
            int skipped = missing;
            logger.ifPresent(log -> log.warn("[SpriteFlipbook] animation \"" + animation.name()
                    + "\" skipped " + skipped + " frame(s) referencing missing regions"));
        }
    }

    private void rebuildManualFrames(Optional<SpriteAtlas> resolved) {
        if (!frames.isBlank()) {
            for (String name : frames.split(",")) {
                frameNames.add(name.strip());
            }
            return;
        }
        if (frameCount > 0) {
            for (int index = 0; index < frameCount; index++) {
                frameNames.add(Integer.toString(firstFrame + index));
            }
            return;
        }
        resolved.ifPresent(loaded -> frameNames.addAll(loaded.regionNames()));
    }

    private void applyAtlasTexture(EngineServices services, SpriteAtlas loaded) {
        if (loaded.texturePath().isEmpty()) {
            return;
        }
        atlasTexture.setPath(loaded.texturePath());
        atlasTexture.resolve(services.assets())
                .ifPresent(handle -> sibling().ifPresent(sprite -> sprite.setTexture(handle)));
    }

    public void advance(float deltaTimeSeconds) {
        if (!playing) {
            return;
        }
        localTimeSeconds += deltaTimeSeconds;
        applyCurrentFrame();
    }

    public SpriteFlipbook seek(float seconds) {
        localTimeSeconds = Math.max(0.0f, seconds);
        applyCurrentFrame();
        return this;
    }

    private void applyCurrentFrame() {
        if (frameNames.isEmpty()) {
            return;
        }
        atlas.direct().flatMap(loaded -> loaded.region(frameNames.get(currentFrameIndex())))
                .ifPresent(this::applyRegion);
    }

    private int currentFrameIndex() {
        float effectiveFramesPerSecond = activeAnimation.map(SpriteAnimation::framesPerSecond)
                .orElse(framesPerSecond);
        boolean effectiveLoop = activeAnimation.map(SpriteAnimation::loop).orElse(loop);
        int frame = (int) Math.floor(localTimeSeconds * effectiveFramesPerSecond);
        if (effectiveLoop) {
            return Math.floorMod(frame, frameNames.size());
        }
        return Math.min(frame, frameNames.size() - 1);
    }

    private void applyRegion(SpriteAtlasRegion region) {
        sibling().ifPresent(sprite ->
                sprite.setRegion(region.minU(), region.minV(), region.maxU(), region.maxV()));
    }

    private Optional<SpriteRenderer> sibling() {
        return owner().flatMap(gameObject -> gameObject.getComponent(SpriteRenderer.class));
    }
}
