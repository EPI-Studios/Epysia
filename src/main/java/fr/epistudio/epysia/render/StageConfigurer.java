package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.TextureHandle;

import java.util.Optional;

public interface StageConfigurer {

    void bindStageTarget(RenderPass pass, RenderTargetHandle target, PassClear clear);

    void bindStageTargetFollowing(RenderPass pass, RenderPass followed, PassClear clear);

    default void bindStagePreparation(RenderPass pass, Runnable preparation) {
    }

    default void publishSceneTexture(SceneTexture slot, TextureHandle texture) {
    }

    default Optional<AssetRegistry> assetRegistry() {
        return Optional.empty();
    }

    default Optional<TextureHandle> sceneTexture(SceneTexture slot) {
        return Optional.empty();
    }
}
