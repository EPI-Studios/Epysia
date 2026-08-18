package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.texture.RenderTexture;

import java.util.Optional;

@EpysiaComponent(name = "Camera Texture", category = "Rendering",
        description = "Renders this camera into a texture that materials, sprites and the ui can read.")
@RequiresComponent(Camera3D.class)
public final class CameraTexture extends Component {

    public enum Refresh {
        EVERY_FRAME,
        ONCE,
        MANUAL
    }

    private static final int MINIMUM_SIZE = 16;
    private static final int MAXIMUM_SIZE = 4096;

    @Export(label = "Width", min = MINIMUM_SIZE, max = MAXIMUM_SIZE, step = 1.0f)
    private int width = 512;
    @Export(label = "Height", min = MINIMUM_SIZE, max = MAXIMUM_SIZE, step = 1.0f)
    private int height = 512;
    @Export(label = "Refresh")
    private Refresh refresh = Refresh.EVERY_FRAME;

    private transient RenderTexture renderTexture;
    private transient boolean captureRequested = true;

    public Optional<TextureHandle> texture() {
        return renderTexture == null ? Optional.empty() : Optional.of(renderTexture.texture());
    }

    public Optional<RenderTexture> renderTexture() {
        return Optional.ofNullable(renderTexture);
    }

    public Refresh refresh() {
        return refresh;
    }

    public CameraTexture setRefresh(Refresh value) {
        refresh = value;
        return this;
    }

    public void requestCapture() {
        captureRequested = true;
    }

    public boolean consumeCaptureRequest() {
        if (refresh == Refresh.EVERY_FRAME) {
            return true;
        }
        boolean requested = captureRequested;
        captureRequested = false;
        return requested;
    }

    public RenderTexture ensureTexture(EngineServices services) {
        int wantedWidth = Math.clamp(width, MINIMUM_SIZE, MAXIMUM_SIZE);
        int wantedHeight = Math.clamp(height, MINIMUM_SIZE, MAXIMUM_SIZE);
        if (renderTexture != null && renderTexture.width() == wantedWidth
                && renderTexture.height() == wantedHeight) {
            return renderTexture;
        }
        releaseTexture(services);
        renderTexture = RenderTexture.create(services, wantedWidth, wantedHeight);
        return renderTexture;
    }

    public void releaseTexture(EngineServices services) {
        if (renderTexture == null) {
            return;
        }
        renderTexture.destroy(services);
        renderTexture = null;
    }
}
