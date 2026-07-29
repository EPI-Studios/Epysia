package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.physics.components.PhysicsMaterial;
import fr.epistudio.epysia.physics.components.PhysicsMaterial.CombineMode;
import fr.epistudio.epysia.scene.serialization.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public final class PhysicsMaterialLoader implements AssetLoader<PhysicsMaterial> {

    private static final String CLASSPATH_ROOT = "materials/";

    @Override
    public Class<PhysicsMaterial> assetType() {
        return PhysicsMaterial.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{".epymat"};
    }

    @Override
    public PhysicsMaterial load(EngineServices services, AssetLoadRequest request) {
        String path = services.assets().locator().resolvedPath(request.uri());
        Map<String, Object> fields = readFields(path);
        return new PhysicsMaterial(
                floatField(fields, "dynamicFriction", PhysicsMaterial.DEFAULT.dynamicFriction()),
                floatField(fields, "staticFriction", PhysicsMaterial.DEFAULT.staticFriction()),
                floatField(fields, "restitution", PhysicsMaterial.DEFAULT.restitution()),
                combineField(fields, "frictionCombine", PhysicsMaterial.DEFAULT.frictionCombine()),
                combineField(fields, "bounceCombine", PhysicsMaterial.DEFAULT.bounceCombine()));
    }

    private Map<String, Object> readFields(String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Physics material not found on filesystem or classpath: " + path));
        return new JsonReader(readText(source)).readRootObject();
    }

    private static String readText(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Physics material not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }

    private static float floatField(Map<String, Object> fields, String key, float fallback) {
        Object value = fields.get(key);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static CombineMode combineField(Map<String, Object> fields, String key, CombineMode fallback) {
        Object value = fields.get(key);
        if (!(value instanceof String name) || name.isEmpty()) {
            return fallback;
        }
        try {
            return CombineMode.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
