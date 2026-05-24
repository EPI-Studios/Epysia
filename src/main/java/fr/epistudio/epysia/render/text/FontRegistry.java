package fr.epistudio.epysia.render.text;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;

import java.util.HashMap;
import java.util.Map;

public final class FontRegistry {

    public static final String DEFAULT_NAME = "default";

    private final RenderBackend backend;
    private final Map<String, Font> fontsByName = new HashMap<>();

    public FontRegistry(RenderBackend backend) {
        this.backend = backend;
    }

    public Font load(String name, String resourcePath, float pixelHeight) {
        return load(name, resourcePath, pixelHeight, SamplerFilter.LINEAR);
    }

    public Font load(String name, String resourcePath, float pixelHeight, SamplerFilter samplerFilter) {
        Font existing = fontsByName.get(name);
        if (existing != null) {
            return existing;
        }
        Font font = Font.loadFromResource(backend, resourcePath, pixelHeight, samplerFilter);
        fontsByName.put(name, font);
        return font;
    }

    public void register(String name, Font font) {
        fontsByName.put(name, font);
    }

    public Font get(String name) {
        Font font = fontsByName.get(name);
        if (font == null) {
            throw new EpysiaException("Font not registered: " + name);
        }
        return font;
    }

    public Font getOrDefault(String name) {
        Font font = fontsByName.get(name);
        return font != null ? font : fontsByName.get(DEFAULT_NAME);
    }

    public boolean contains(String name) {
        return fontsByName.containsKey(name);
    }

    public void destroyAll() {
        for (Font font : fontsByName.values()) {
            font.destroy(backend);
        }
        fontsByName.clear();
    }
}
