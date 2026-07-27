package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import fr.epistudio.epysia.render.postfx.StretchAspect;

import java.util.List;
import java.util.Map;

public final class PostProcessSettingsJsonCodec {

    public void write(JsonWriter writer, PostProcessSettings settings) {
        writer.beginObject()
                .key("vignetteStrength").valueNumber(settings.vignetteStrength())
                .key("gradeGamma").valueNumber(settings.gradeGamma())
                .key("gradeExposure").valueNumber(settings.gradeExposure())
                .key("fogEnabled").valueBoolean(settings.fogEnabled())
                .key("fogColor").beginArray()
                .valueNumber(settings.fogColor().x)
                .valueNumber(settings.fogColor().y)
                .valueNumber(settings.fogColor().z)
                .endArray()
                .key("fogDistanceStart").valueNumber(settings.fogDistanceStart())
                .key("fogDistanceDensity").valueNumber(settings.fogDistanceDensity())
                .key("fogHeightOrigin").valueNumber(settings.fogHeightOrigin())
                .key("fogHeightFalloff").valueNumber(settings.fogHeightFalloff())
                .key("fogHeightDensity").valueNumber(settings.fogHeightDensity())
                .key("bloomEnabled").valueBoolean(settings.bloomEnabled())
                .key("bloomThreshold").valueNumber(settings.bloomThreshold())
                .key("bloomKnee").valueNumber(settings.bloomKnee())
                .key("bloomIntensity").valueNumber(settings.bloomIntensity())
                .key("ambientOcclusionEnabled").valueBoolean(settings.ambientOcclusionEnabled())
                .key("ambientOcclusionRadius").valueNumber(settings.ambientOcclusionRadius())
                .key("ambientOcclusionIntensity").valueNumber(settings.ambientOcclusionIntensity())
                .key("ambientOcclusionPower").valueNumber(settings.ambientOcclusionPower())
                .key("ambientOcclusionFullResolution").valueBoolean(settings.ambientOcclusionFullResolution())
                .key("pixelPerfectEnabled").valueBoolean(settings.pixelPerfectEnabled())
                .key("pixelPerfectBaseHeight").valueNumber(settings.pixelPerfectBaseHeight())
                .key("pixelPerfectBaseWidth").valueNumber(settings.pixelPerfectBaseWidth())
                .key("pixelPerfectIntegerScale").valueBoolean(settings.pixelPerfectIntegerScale())
                .key("pixelPerfectAspect").valueString(settings.pixelPerfectAspect().name())
                .key("antiAliasingEnabled").valueBoolean(settings.antiAliasingEnabled())
                .key("fogShaderPath").valueString(settings.fogShaderPath())
                .endObject();
    }

    public void read(Map<String, Object> root, PostProcessSettings settings) {
        PostProcessSettings defaults = new PostProcessSettings();
        settings.setVignetteStrength(number(root, "vignetteStrength", defaults.vignetteStrength()));
        settings.setGradeGamma(number(root, "gradeGamma", defaults.gradeGamma()));
        settings.setGradeExposure(number(root, "gradeExposure", defaults.gradeExposure()));
        readFog(root, settings, defaults);
        readBloom(root, settings, defaults);
        readAmbientOcclusion(root, settings, defaults);
        settings.setPixelPerfectEnabled(flag(root, "pixelPerfectEnabled", defaults.pixelPerfectEnabled()));
        settings.setPixelPerfectBaseHeight((int) number(root, "pixelPerfectBaseHeight",
                defaults.pixelPerfectBaseHeight()));
        settings.setPixelPerfectBaseWidth((int) number(root, "pixelPerfectBaseWidth",
                defaults.pixelPerfectBaseWidth()));
        settings.setPixelPerfectIntegerScale(flag(root, "pixelPerfectIntegerScale",
                defaults.pixelPerfectIntegerScale()));
        settings.setPixelPerfectAspect(root.get("pixelPerfectAspect") instanceof String aspect
                ? StretchAspect.fromId(aspect) : defaults.pixelPerfectAspect());
        settings.setAntiAliasingEnabled(flag(root, "antiAliasingEnabled", defaults.antiAliasingEnabled()));
        settings.setFogShaderPath(root.get("fogShaderPath") instanceof String path ? path
                : defaults.fogShaderPath());
    }

    private void readFog(Map<String, Object> root, PostProcessSettings settings, PostProcessSettings defaults) {
        settings.setFogEnabled(flag(root, "fogEnabled", defaults.fogEnabled()));
        if (root.get("fogColor") instanceof List<?> color && color.size() >= 3) {
            settings.setFogColor(component(color, 0), component(color, 1), component(color, 2));
        }
        settings.setFogDistance(number(root, "fogDistanceStart", defaults.fogDistanceStart()),
                number(root, "fogDistanceDensity", defaults.fogDistanceDensity()));
        settings.setFogHeight(number(root, "fogHeightOrigin", defaults.fogHeightOrigin()),
                number(root, "fogHeightFalloff", defaults.fogHeightFalloff()),
                number(root, "fogHeightDensity", defaults.fogHeightDensity()));
    }

    private void readBloom(Map<String, Object> root, PostProcessSettings settings, PostProcessSettings defaults) {
        settings.setBloomEnabled(flag(root, "bloomEnabled", defaults.bloomEnabled()));
        settings.setBloom(number(root, "bloomThreshold", defaults.bloomThreshold()),
                number(root, "bloomKnee", defaults.bloomKnee()),
                number(root, "bloomIntensity", defaults.bloomIntensity()));
    }

    private void readAmbientOcclusion(Map<String, Object> root, PostProcessSettings settings,
                                      PostProcessSettings defaults) {
        settings.setAmbientOcclusionEnabled(flag(root, "ambientOcclusionEnabled",
                defaults.ambientOcclusionEnabled()));
        settings.setAmbientOcclusion(number(root, "ambientOcclusionRadius", defaults.ambientOcclusionRadius()),
                number(root, "ambientOcclusionIntensity", defaults.ambientOcclusionIntensity()));
        settings.setAmbientOcclusionPower(number(root, "ambientOcclusionPower", defaults.ambientOcclusionPower()));
        settings.setAmbientOcclusionFullResolution(flag(root, "ambientOcclusionFullResolution",
                defaults.ambientOcclusionFullResolution()));
    }

    private static float component(List<?> values, int index) {
        return values.get(index) instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static float number(Map<String, Object> root, String key, float fallback) {
        return root.get(key) instanceof Number number ? number.floatValue() : fallback;
    }

    private static boolean flag(Map<String, Object> root, String key, boolean fallback) {
        return root.get(key) instanceof Boolean value ? value : fallback;
    }
}
