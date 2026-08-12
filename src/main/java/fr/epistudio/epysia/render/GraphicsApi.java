package fr.epistudio.epysia.render;

import java.util.Locale;

public enum GraphicsApi {
    OPENGL,
    VULKAN;

    public static final String PROPERTY = "epysia.render.api";

    private static GraphicsApi selected;

    public static synchronized void select(GraphicsApi api) {
        if (selected != null && selected != api) {
            throw new IllegalStateException("Graphics API already resolved as " + selected
                    + ", cannot switch to " + api + " after the renderer has been touched");
        }
        selected = api;
    }

    public static synchronized GraphicsApi selected() {
        if (selected == null) {
            selected = fromProperty();
        }
        return selected;
    }

    public static boolean isVulkan() {
        return selected() == VULKAN;
    }

    public static GraphicsApi parse(String value, GraphicsApi fallback) {
        if (value == null) {
            return fallback;
        }
        String requested = value.toLowerCase(Locale.ROOT).trim();
        if (requested.equals("vulkan") || requested.equals("vk")) {
            return VULKAN;
        }
        return requested.equals("opengl") || requested.equals("gl") ? OPENGL : fallback;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static GraphicsApi fromProperty() {
        return parse(System.getProperty(PROPERTY), OPENGL);
    }
}
