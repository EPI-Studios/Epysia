package fr.epistudio.epysia.project;

public final class ProjectQualityProperties {
    public static final String SHADOW_SIZE_PROPERTY = "epysia.shadow.size";
    public static final String SHADOW_CASCADES_PROPERTY = "epysia.shadow.cascades";
    public static final String TEXTURE_FILTER_PROPERTY = "epysia.texture.filter";
    public static final String DEPTH_PREPASS_PROPERTY = "epysia.depthPrepass";
    public static final String PCF_SAMPLES_PROPERTY = "epysia.shadow.pcfSamples";
    public static final String PCF_CASCADES_PROPERTY = "epysia.shadow.pcfCascades";
    public static final String SHADOW_DEPTH_STEPS_PROPERTY = "epysia.shadow.depthSteps";

    private ProjectQualityProperties() {
    }

    public static void apply(ProjectQuality quality) {
        ProjectQuality clamped = quality.clamped();
        System.setProperty(SHADOW_SIZE_PROPERTY, Integer.toString(clamped.shadowMapSize()));
        System.setProperty(SHADOW_CASCADES_PROPERTY, Integer.toString(clamped.cascadeCount()));
        System.setProperty(TEXTURE_FILTER_PROPERTY, clamped.nearestTextureFilter() ? "nearest" : "linear");
        System.setProperty(DEPTH_PREPASS_PROPERTY, Boolean.toString(clamped.depthPrepass()));
        System.setProperty(PCF_SAMPLES_PROPERTY, Integer.toString(clamped.shadowFilterSamples()));
        System.setProperty(PCF_CASCADES_PROPERTY, Integer.toString(clamped.filteredCascades()));
        System.setProperty(SHADOW_DEPTH_STEPS_PROPERTY, Integer.toString(clamped.shadowDepthSteps()));
    }
}
