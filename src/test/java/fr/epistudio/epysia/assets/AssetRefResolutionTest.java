package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.SystemRegistry;
import fr.epistudio.epysia.assets.loaders.TextureImportSettings;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.PreRenderPass;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.backend.RenderBackend;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetRefResolutionTest {

    private record Payload(String contents) {
    }

    @Test
    void resolvingNeverRewritesTheSerialisedPath(@TempDir Path root) throws IOException {
        Fixture fixture = Fixture.of(root);
        AssetRef<Payload> reference = fixture.referenceToHero();
        String storedBefore = reference.path();

        assertTrue(reference.resolve(fixture.registry).isPresent());

        assertEquals(storedBefore, reference.path());
        assertEquals("res://sprites/hero.png", reference.resolvedUri().toString());
    }

    @Test
    void aSecondResolveReturnsTheSameInstance(@TempDir Path root) throws IOException {
        Fixture fixture = Fixture.of(root);
        AssetRef<Payload> reference = fixture.referenceToHero();
        Payload first = reference.resolve(fixture.registry).orElseThrow();
        Payload second = reference.resolve(fixture.registry).orElseThrow();
        assertSame(first, second);
    }

    @Test
    void aLegacyPointPrefixMovesIntoTheImportSettingsEvenWhenTheGuidResolves(@TempDir Path root)
            throws IOException {
        Fixture fixture = Fixture.of(root);
        AssetRef<Payload> reference = fixture.referenceToHero();
        reference.resolve(fixture.registry);

        Path metaFile = AssetMetaFile.pathFor(root.resolve("sprites").resolve("hero.png"));
        assertEquals(Optional.of(TextureImportSettings.FILTER_POINT),
                AssetMetaFile.readString(metaFile, TextureImportSettings.FILTER_KEY));
    }

    private record Fixture(AssetRegistry registry, Path heroFile, String heroGuid) {

        private static Fixture of(Path root) throws IOException {
            Path hero = root.resolve("sprites").resolve("hero.png");
            Files.createDirectories(hero.getParent());
            Files.writeString(hero, "hero pixels");
            HeadlessServices services = new HeadlessServices();
            AssetRegistry registry = new AssetRegistry(services);
            services.attach(registry);
            registry.register(new PayloadLoader());
            registry.attachProject(root);
            String guid = registry.database().orElseThrow().guidForPath("sprites/hero.png").orElseThrow();
            return new Fixture(registry, hero, guid);
        }

        private AssetRef<Payload> referenceToHero() {
            AssetRef<Payload> reference = new AssetRef<>(Payload.class);
            reference.setPath("point:" + heroFile.toAbsolutePath());
            reference.setGuid(heroGuid);
            return reference;
        }
    }

    private static final class PayloadLoader implements AssetLoader<Payload> {

        @Override
        public Class<Payload> assetType() {
            return Payload.class;
        }

        @Override
        public String[] supportedExtensions() {
            return new String[]{".png"};
        }

        @Override
        public Payload load(EngineServices services, AssetLoadRequest request) {
            return services.assets().locator().open(request.uri())
                    .flatMap(source -> source.open().map(stream -> new Payload(source.path())))
                    .orElse(null);
        }
    }

    private static final class HeadlessServices implements EngineServices {

        private AssetRegistry registry;

        private void attach(AssetRegistry registry) {
            this.registry = registry;
        }

        @Override
        public AssetRegistry assets() {
            return registry;
        }

        @Override
        public Logger logger() {
            return new CollectingLogger();
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

    private static final class CollectingLogger implements Logger {

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
