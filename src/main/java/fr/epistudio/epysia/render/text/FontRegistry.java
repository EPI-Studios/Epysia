package fr.epistudio.epysia.render.text;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;

import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import fr.epistudio.epysia.assets.source.AssetSource;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class FontRegistry {
    public static final String DEFAULT_NAME = "default";
    public static final String DEFAULT_FONT_RESOURCE = "fonts/AdwaitaMono-Regular.ttf";

    private final RenderBackend backend;
    private final Map<String, Font> fontsByName = new HashMap<>();
    private final Map<String, Font> fontsByStyle = new HashMap<>();
    private final Map<String, ByteBuffer> fileBytes = new HashMap<>();

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

    public Font resolve(AssetLocator locator, String path, float pixelHeight) {
        return resolve(locator, path, pixelHeight, SamplerFilter.LINEAR);
    }

    public Font resolve(AssetLocator locator, String path, float pixelHeight, SamplerFilter samplerFilter) {
        return resolve(locator, path, pixelHeight, samplerFilter, 0.0f);
    }

    public Font resolve(AssetLocator locator, String path, float pixelHeight, SamplerFilter samplerFilter,
                        float edgeCutoff) {
        String key = path + "@" + pixelHeight + "/" + samplerFilter + "/" + edgeCutoff;
        Font existing = fontsByStyle.get(key);
        if (existing != null) {
            return existing;
        }
        Font created = bakeStyle(locator, path, pixelHeight, samplerFilter, edgeCutoff);
        fontsByStyle.put(key, created);
        return created;
    }

    private Font bakeStyle(AssetLocator locator, String path, float pixelHeight, SamplerFilter samplerFilter,
                           float edgeCutoff) {
        Optional<ByteBuffer> bytes = path.isEmpty() ? Optional.empty() : readFont(locator, path);
        ByteBuffer source = bytes.orElseGet(this::defaultFontBytes);
        return Font.bake(backend, source, pixelHeight, samplerFilter, edgeCutoff);
    }

    private ByteBuffer defaultFontBytes() {
        return fileBytes.computeIfAbsent(DEFAULT_FONT_RESOURCE,
                resource -> Font.readClasspathFont(resource));
    }

    private Optional<ByteBuffer> readFont(AssetLocator locator, String path) {
        ByteBuffer cached = fileBytes.get(path);
        if (cached != null) {
            return Optional.of(cached);
        }
        AssetUri uri = LegacyAssetReferences.interpretWithoutMigration(path, locator);
        Optional<AssetSource> source = locator.open(uri);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Optional<InputStream> stream = source.get().open();
        if (stream.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream input = stream.get()) {
            byte[] bytes = input.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            fileBytes.put(path, buffer);
            return Optional.of(buffer);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
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
        for (Font font : fontsByStyle.values()) {
            font.destroy(backend);
        }
        fontsByStyle.clear();
        fileBytes.clear();
        fontsByName.clear();
    }
}
