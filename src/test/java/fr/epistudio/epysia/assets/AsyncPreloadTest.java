package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPreloadTest {

    private record Payload(String text) {
    }

    private static final class RecordingLoader implements AssetLoader<Payload> {

        final AtomicInteger readsOffThread = new AtomicInteger();
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicInteger synchronousLoads = new AtomicInteger();

        @Override
        public Class<Payload> assetType() {
            return Payload.class;
        }

        @Override
        public String[] supportedExtensions() {
            return new String[]{".probe"};
        }

        @Override
        public Payload load(EngineServices services, AssetLoadRequest request) {
            synchronousLoads.incrementAndGet();
            return new Payload("synchronous");
        }

        @Override
        public Optional<Object> readOffThread(AssetLocator locator, AssetLoadRequest request) {
            readsOffThread.incrementAndGet();
            return Optional.of("bytes");
        }

        @Override
        public Payload loadFromRead(EngineServices services, AssetLoadRequest request, Object read) {
            uploads.incrementAndGet();
            return new Payload((String) read);
        }
    }

    @Test
    void preloadingReadsOffThreadAndUploadsOnlyWhenDrained() {
        EpysiaEngine engine = newEngine();
        RecordingLoader loader = new RecordingLoader();
        engine.assets().register(loader);
        AssetUri uri = AssetUri.project("probes/one.probe");

        engine.assets().preload(Payload.class, uri, AssetVariant.none());
        settle(engine);

        assertEquals(1, loader.readsOffThread.get(), "the read must happen once, off the main thread");
        assertEquals(0, loader.uploads.get(), "nothing may be uploaded before the drain");
        assertEquals(1, engine.assets().pendingUploadCount(), "the read result must be queued");

        engine.assets().drainReadyUploads(4);

        assertEquals(1, loader.uploads.get(), "the drain must upload what was read");
        assertEquals(0, loader.synchronousLoads.get(),
                "a preloaded asset must never take the synchronous path");
    }

    @Test
    void aDrainedAssetIsServedFromTheCacheWithoutLoadingAgain() {
        EpysiaEngine engine = newEngine();
        RecordingLoader loader = new RecordingLoader();
        engine.assets().register(loader);
        AssetUri uri = AssetUri.project("probes/two.probe");
        engine.assets().preload(Payload.class, uri, AssetVariant.none());
        settle(engine);
        engine.assets().drainReadyUploads(4);

        Optional<Payload> resolved = engine.assets().resolve(Payload.class, uri, AssetVariant.none());

        assertTrue(resolved.isPresent(), "the preloaded asset must resolve");
        assertEquals("bytes", resolved.get().text(), "it must be the asset built from the off-thread read");
        assertEquals(0, loader.synchronousLoads.get(),
                "resolving a preloaded asset must not fall back to loading it again");
    }

    @Test
    void theUploadBudgetIsRespected() {
        EpysiaEngine engine = newEngine();
        RecordingLoader loader = new RecordingLoader();
        engine.assets().register(loader);
        for (int index = 0; index < 5; index++) {
            engine.assets().preload(Payload.class,
                    AssetUri.project("probes/batch" + index + ".probe"), AssetVariant.none());
        }
        settle(engine);

        int applied = engine.assets().drainReadyUploads(2);

        assertEquals(2, applied, "the drain must stop at its budget");
        assertEquals(3, engine.assets().pendingUploadCount(), "the rest must wait for a later frame");
        assertTrue(engine.assets().isPreloading(), "work still queued counts as preloading");
    }

    @Test
    void preloadingTheSameAssetTwiceReadsItOnce() {
        EpysiaEngine engine = newEngine();
        RecordingLoader loader = new RecordingLoader();
        engine.assets().register(loader);
        AssetUri uri = AssetUri.project("probes/three.probe");

        engine.assets().preload(Payload.class, uri, AssetVariant.none());
        engine.assets().preload(Payload.class, uri, AssetVariant.none());
        settle(engine);
        engine.assets().drainReadyUploads(4);

        assertEquals(1, loader.readsOffThread.get(), "a second preload of the same asset must be ignored");
        assertFalse(engine.assets().isPreloading(), "the queue must be empty once everything drained");
    }

    private static void settle(EpysiaEngine engine) {
        long deadlineNanos = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadlineNanos) {
            engine.backgroundTasks().deliverAll();
            if (engine.backgroundTasks().pendingCount() == 0) {
                engine.backgroundTasks().deliverAll();
                return;
            }
            Thread.onSpinWait();
        }
    }

    private static EpysiaEngine newEngine() {
        return new EpysiaEngine(new Window("test", 1, 1), new NullRenderBackend());
    }
}
