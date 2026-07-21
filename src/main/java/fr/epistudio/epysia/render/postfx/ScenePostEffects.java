package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.scene.Scene;

import java.util.function.Supplier;

public final class ScenePostEffects implements PostEffects {

    private final Supplier<Scene> activeScene;
    private final PostEffectBuiltins builtins;

    public ScenePostEffects(Supplier<Scene> activeScene, Supplier<PostProcessSettings> settings) {
        this.activeScene = activeScene;
        this.builtins = new PostEffectBuiltins(settings);
    }

    @Override
    public PostEffectStack globalStack() {
        return activeScene.get().postEffects();
    }

    @Override
    public PostEffectStack stackFor(Camera3D camera) {
        return camera.enablePostEffectStack();
    }

    @Override
    public PostEffectBuiltins builtins() {
        return builtins;
    }
}
