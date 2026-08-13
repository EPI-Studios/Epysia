package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.pool.ObjectPools;
import fr.epistudio.epysia.tween.Tweens;
import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.PreRenderPass;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Hud;
import fr.epistudio.epysia.scripting.Scheduler;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetOwnershipTest {

    private static final String ALBEDO_PATH = "textures/albedo.png";

    @Test
    void freesATextureOnceItsHolderReleasesIt(@TempDir Path root) throws IOException {
        Fixture fixture = Fixture.of(root);
        LitMaterial material = new LitMaterial();
        material.setTexturePath("albedo", ALBEDO_PATH);

        AcquiredAssets owned = new AcquiredAssets();
        MaterialFields.acquireTextures(material, fixture.registry, owned);
        assertTrue(material.albedo != null, "acquiring must still populate the material field");

        fixture.registry.unloadUnused();
        assertEquals(0, fixture.disposed.size(), "a held texture must not be freed");

        owned.releaseAll(fixture.registry);
        fixture.registry.unloadUnused();
        assertEquals(1, fixture.disposed.size(), "a released texture must be freed");
    }

    @Test
    void freesAssetReferencesHeldInsideACollection(@TempDir Path root) throws IOException {
        Fixture fixture = Fixture.of(root);
        LevelHolder holder = new LevelHolder();
        holder.levels.add(new Level(referenceTo(fixture)));
        holder.levels.get(0).reference().resolve(fixture.registry);

        fixture.registry.unloadUnused();
        assertEquals(0, fixture.disposed.size(), "a held reference must not be freed");

        AssetRefFields.releaseAll(holder);
        fixture.registry.unloadUnused();
        assertEquals(1, fixture.disposed.size(),
                "a reference nested in a collection must be released with its holder");
    }

    @Test
    void freesAssetReferencesHeldDirectly(@TempDir Path root) throws IOException {
        Fixture fixture = Fixture.of(root);
        DirectHolder holder = new DirectHolder();
        holder.reference.setPath(ALBEDO_PATH);
        holder.reference.resolve(fixture.registry);

        AssetRefFields.releaseAll(holder);
        fixture.registry.unloadUnused();

        assertEquals(1, fixture.disposed.size(), "a directly held reference must be released");
    }

    private static AssetRef<TextureHandle> referenceTo(Fixture fixture) {
        AssetRef<TextureHandle> reference = new AssetRef<>(TextureHandle.class);
        reference.setPath(ALBEDO_PATH);
        return reference;
    }

    private static final class DirectHolder {
        private final AssetRef<TextureHandle> reference = new AssetRef<>(TextureHandle.class);
    }

    private record Level(AssetRef<TextureHandle> reference) {
    }

    private static final class LevelHolder {
        private final List<Level> levels = new ArrayList<>();
    }

    private record Fixture(AssetRegistry registry, List<TextureHandle> disposed) {

        private static Fixture of(Path root) throws IOException {
            Path albedo = root.resolve(ALBEDO_PATH);
            Files.createDirectories(albedo.getParent());
            Files.writeString(albedo, "pixels");
            HeadlessServices services = new HeadlessServices();
            AssetRegistry registry = new AssetRegistry(services);
            services.attach(registry);
            List<TextureHandle> disposed = new ArrayList<>();
            registry.register(new CountingTextureLoader(disposed));
            registry.attachProject(root);
            return new Fixture(registry, disposed);
        }
    }

    private record CountingTextureLoader(List<TextureHandle> disposed) implements AssetLoader<TextureHandle> {

        @Override
        public Class<TextureHandle> assetType() {
            return TextureHandle.class;
        }

        @Override
        public String[] supportedExtensions() {
            return new String[]{".png"};
        }

        @Override
        public TextureHandle load(EngineServices services, AssetLoadRequest request) {
            return new TextureHandle(request.uri().toString().hashCode() & 0xFFFFL);
        }

        @Override
        public void dispose(EngineServices services, TextureHandle value) {
            disposed.add(value);
        }
    }

    private static final class HeadlessServices implements EngineServices {

        private final Tweens tweens = new Tweens();

    @Override
    public Tweens tweens() {
        return tweens;
    }

private final ObjectPools pools = new ObjectPools(this);

    @Override
    public ObjectPools pools() {
        return pools;
    }

        private AssetRegistry registry;

        private void attach(AssetRegistry attached) {
            this.registry = attached;
        }

        @Override
        public AssetRegistry assets() {
            return registry;
        }

        @Override
        public Logger logger() {
            return new SilentLogger();
        }

        @Override
        public Window window() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderBackend renderBackend() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FontRegistry fonts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scene scene() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SystemRegistry systems() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scheduler scheduler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BackgroundTasks backgroundTasks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputActions inputActions() {
            return InputActions.defaults();
        }

        @Override
        public Hud hud() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PostEffects postEffects() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addPreRenderPass(PreRenderPass pass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removePreRenderPass(PreRenderPass pass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addRenderSystem(RenderSystem renderSystem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeRenderSystem(RenderSystem renderSystem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends RenderSystem> T renderSystem(Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SilentLogger implements Logger {

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable cause) {
        }
    }
}
