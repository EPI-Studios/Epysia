package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.render.lighting.CubeCaptureFace;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "Light Probe Volume", category = "Rendering",
        description = "Bakes indirect light into a grid so moving objects pick it up.")
@RequiresComponent(Transform3D.class)
public final class LightProbeVolume extends Component {

    @Export(label = "Extents", min = 0.1f, max = 500.0f, step = 0.1f)
    private final Vector3f extents = new Vector3f(5.0f, 5.0f, 5.0f);

    @Export(label = "Resolution X", min = 1.0f, max = 32.0f, step = 1.0f)
    private int resolutionX = 4;

    @Export(label = "Resolution Y", min = 1.0f, max = 32.0f, step = 1.0f)
    private int resolutionY = 3;

    @Export(label = "Resolution Z", min = 1.0f, max = 32.0f, step = 1.0f)
    private int resolutionZ = 4;

    @Export(label = "Baked Probes")
    private final AssetRef<BakedProbes> bakedProbes = new AssetRef<>(BakedProbes.class);

    @Export(label = "Realtime Refresh")
    private boolean realtimeRefresh;

    @Export(label = "Refresh Faces Per Frame", min = 1.0f, max = 6.0f, step = 1.0f)
    private int refreshFacesPerFrame = 1;

    @Export(label = "Refresh Interval (ms)", min = 0.0f, max = 5000.0f, step = 10.0f)
    private int refreshIntervalMillis = 250;

    @Export(label = "Refresh Budget (ms)", min = 0.5f, max = 33.0f, step = 0.5f)
    private float refreshBudgetMillis = 3.0f;

    public long refreshIntervalNanos() {
        return Math.max(0, refreshIntervalMillis) * 1_000_000L;
    }

    public long refreshBudgetNanos() {
        return (long) (Math.clamp(refreshBudgetMillis, 0.5f, 33.0f) * 1_000_000.0f);
    }

    public boolean realtimeRefresh() {
        return realtimeRefresh;
    }

    public LightProbeVolume setRealtimeRefresh(boolean realtimeRefresh) {
        this.realtimeRefresh = realtimeRefresh;
        return this;
    }

    public int refreshFacesPerFrame() {
        return Math.clamp(refreshFacesPerFrame, 1, CubeCaptureFace.COUNT);
    }

    public Vector3f extents(Vector3f destination) {
        return destination.set(extents);
    }

    public LightProbeVolume setExtents(float x, float y, float z) {
        extents.set(x, y, z);
        return this;
    }

    public int resolutionX() {
        return resolutionX;
    }

    public int resolutionY() {
        return resolutionY;
    }

    public int resolutionZ() {
        return resolutionZ;
    }

    public LightProbeVolume setResolution(int x, int y, int z) {
        resolutionX = Math.max(1, x);
        resolutionY = Math.max(1, y);
        resolutionZ = Math.max(1, z);
        return this;
    }

    public AssetRef<BakedProbes> bakedProbesRef() {
        return bakedProbes;
    }

    public Optional<BakedProbes> bakedProbes() {
        return bakedProbes.direct();
    }

    @Override
    public void onLoad(EngineServices services) {
        if (bakedProbes.direct().isEmpty() && !bakedProbes.isEmpty()) {
            bakedProbes.resolve(services.assets());
        }
    }
}
