package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@EpysiaComponent(name = "Volumetric Renderer", category = "Rendering")
@RequiresComponent(DensityVolume.class)
public final class VolumetricRenderer extends Component {
    @Export(label = "Albedo", color = true)
    private final Vector3f albedo = new Vector3f(0.05f, 0.0465f, 0.043f);

    @Export(label = "Light Color", color = true)
    private final Vector3f lightColor = new Vector3f(3.5816119f, 2.7608259f, 1.4363756f);

    @Export(label = "Extinction Color", color = true)
    private final Vector3f extinctionColor = new Vector3f(1.0f, 1.0f, 1.0f);

    @Export(label = "Absorption", min = 0.0f, max = 3.0f, step = 0.01f)
    private float absorption = 0.5f;

    @Export(label = "Scattering", min = 0.0f, max = 3.0f, step = 0.01f)
    private float scattering = 2.6f;

    @Export(label = "Volume Density", min = 0.0f, max = 10.0f, step = 0.01f)
    private float volumeDensity = 4.0f;

    @Export(label = "Shadow Density", min = 0.0f, max = 10.0f, step = 0.01f)
    private float shadowDensity = 2.5f;

    @Export(label = "Phase Function")
    private PhaseFunction phaseFunction = PhaseFunction.RAYLEIGH;

    @Export(label = "Anisotropy", min = -1.0f, max = 1.0f, step = 0.01f)
    private float anisotropy;

    @Export(label = "Density Falloff", min = 0.0f, max = 1.0f, step = 0.01f)
    private float densityFalloff = 0.25f;

    @Export(label = "Alpha Threshold", min = 0.0f, max = 1.0f, step = 0.01f)
    private float alphaThreshold = 0.1f;

    @Export(label = "Step Count", min = 1.0f, max = 256.0f, step = 1.0f)
    private int stepCount = 150;

    @Export(label = "Step Size", min = 0.01f, max = 0.5f, step = 0.001f)
    private float stepSize = 0.05f;

    @Export(label = "Light Step Count", min = 1.0f, max = 32.0f, step = 1.0f)
    private int lightStepCount = 16;

    @Export(label = "Light Step Size", min = 0.01f, max = 1.0f, step = 0.01f)
    private float lightStepSize = 0.25f;

    @Export(label = "Detail Scale", min = 0.01f, max = 64.0f, step = 0.1f)
    private float detailScale = 4.0f;

    @Export(label = "Animation Direction", min = -2.0f, max = 2.0f, step = 0.01f)
    private final Vector3f animationDirection = new Vector3f(0.0f, -0.1f, 0.0f);

    @Export(label = "Resolution")
    private VolumetricResolution resolution = VolumetricResolution.QUARTER;

    @Export(label = "Bicubic Upscale")
    private boolean bicubicUpscale = false;

    @Export(label = "Sharpness", min = -1.0f, max = 1.0f, step = 0.01f)
    private float sharpness = -1.0f;

    @Export(label = "Debug View")
    private VolumetricDebugView debugView = VolumetricDebugView.COMPOSITE;

    @Export(label = "Noise Seed", min = 0.0f, max = 100000.0f, step = 1.0f)
    private int noiseSeed;

    @Export(label = "Noise Octaves", min = 1.0f, max = 16.0f, step = 1.0f)
    private int noiseOctaves = 6;

    @Export(label = "Noise Cell Size", min = 1.0f, max = 128.0f, step = 1.0f)
    private int noiseCellSize = 32;

    @Export(label = "Noise Axis Cells", min = 1.0f, max = 64.0f, step = 1.0f)
    private int noiseAxisCellCount = 4;

    @Export(label = "Noise Amplitude", min = 0.1f, max = 16.0f, step = 0.1f)
    private float noiseAmplitude = 0.62f;

    @Export(label = "Noise Warp", min = 0.0f, max = 5.0f, step = 0.01f)
    private float noiseWarp = 0.76f;

    @Export(label = "Noise Bias", min = -5.0f, max = 5.0f, step = 0.01f)
    private float noiseBias;

    @Export(label = "Invert Noise")
    private boolean noiseInverted = true;

    public VolumetricNoiseSettings noiseSettings() {
        return new VolumetricNoiseSettings(noiseSeed, noiseOctaves, noiseCellSize, noiseAxisCellCount,
                noiseAmplitude, noiseWarp, noiseBias, noiseInverted);
    }

    public Vector3fc albedo() {
        return albedo;
    }

    public Vector3fc lightColor() {
        return lightColor;
    }

    public Vector3fc extinctionColor() {
        return extinctionColor;
    }

    public Vector3fc animationDirection() {
        return animationDirection;
    }

    public float absorption() {
        return absorption;
    }

    public float scattering() {
        return scattering;
    }

    public float volumeDensity() {
        return volumeDensity;
    }

    public float shadowDensity() {
        return shadowDensity;
    }

    public PhaseFunction phaseFunction() {
        return phaseFunction;
    }

    public float anisotropy() {
        return anisotropy;
    }

    public float densityFalloff() {
        return densityFalloff;
    }

    public float alphaThreshold() {
        return alphaThreshold;
    }

    public int stepCount() {
        return stepCount;
    }

    public float stepSize() {
        return stepSize;
    }

    public int lightStepCount() {
        return lightStepCount;
    }

    public float lightStepSize() {
        return lightStepSize;
    }

    public float detailScale() {
        return detailScale;
    }

    public VolumetricResolution resolution() {
        return resolution;
    }

    public boolean bicubicUpscale() {
        return bicubicUpscale;
    }

    public float sharpness() {
        return sharpness;
    }

    public VolumetricDebugView debugView() {
        return debugView;
    }

    public VolumetricRenderer setAlbedo(float red, float green, float blue) {
        albedo.set(red, green, blue);
        return this;
    }

    public VolumetricRenderer setLightColor(float red, float green, float blue) {
        lightColor.set(red, green, blue);
        return this;
    }

    public VolumetricRenderer setPhaseFunction(PhaseFunction function) {
        phaseFunction = function;
        return this;
    }

    public VolumetricRenderer setAnisotropy(float value) {
        anisotropy = Math.clamp(value, -1.0f, 1.0f);
        return this;
    }

    public VolumetricRenderer setResolution(VolumetricResolution value) {
        resolution = value;
        return this;
    }

    public VolumetricRenderer setDebugView(VolumetricDebugView view) {
        debugView = view;
        return this;
    }

    public VolumetricRenderer setDetailScale(float value) {
        detailScale = Math.max(0.01f, value);
        return this;
    }

    public VolumetricRenderer setDensityFalloff(float value) {
        densityFalloff = Math.clamp(value, 0.0f, 1.0f);
        return this;
    }

    public VolumetricRenderer setNoise(int seed, int octaves, int cellSize, int axisCellCount) {
        noiseSeed = seed;
        noiseOctaves = Math.max(1, octaves);
        noiseCellSize = Math.max(1, cellSize);
        noiseAxisCellCount = Math.max(1, axisCellCount);
        return this;
    }
}
