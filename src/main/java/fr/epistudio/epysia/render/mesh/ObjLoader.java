package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.assets.source.AssetResolver;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.texture.Texture2D;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ObjLoader {

    private static final String CLASSPATH_ROOT = "models/";
    private static final float DEFAULT_ALPHA_CUTOFF = 0.5f;

    private ObjLoader() {
    }

    public static LoadedObj load(RenderBackend backend, String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Model resource not found on filesystem or classpath: " + path));
        return loadFrom(backend, source, location.directory());
    }

    public static LoadedObj loadFromResource(RenderBackend backend, String relativeObjPath) {
        return load(backend, relativeObjPath);
    }

    private static LoadedObj loadFrom(RenderBackend backend, AssetSource objSource, AssetResolver siblings) {
        String objText = readText(objSource);
        ObjParseResult parsed = ObjMesh.parseDetailedFromSource(objText);
        Map<String, MtlDefinition> mtlByName = loadMaterialLibraries(siblings, parsed.mtllibPaths());
        UploadedMesh uploaded = MeshUploader.upload(backend, parsed.mesh());
        Map<String, TextureHandle> textureCache = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<Material> materials = buildMaterials(backend, siblings, parsed.materialSlotNames(),
                mtlByName, textureCache, warnings);
        return new LoadedObj(uploaded, materials, warnings);
    }

    private static Map<String, MtlDefinition> loadMaterialLibraries(AssetResolver siblings, List<String> mtllibPaths) {
        Map<String, MtlDefinition> byName = new HashMap<>();
        for (String mtlRelative : mtllibPaths) {
            Optional<AssetSource> mtlSource = siblings.resolve(mtlRelative);
            if (mtlSource.isEmpty()) {
                continue;
            }
            Optional<String> mtlText = tryReadText(mtlSource.get());
            if (mtlText.isEmpty()) {
                continue;
            }
            for (MtlDefinition definition : MtlParser.parseFromSource(mtlText.get())) {
                byName.put(definition.name(), definition);
            }
        }
        return byName;
    }

    private static List<Material> buildMaterials(
            RenderBackend backend,
            AssetResolver siblings,
            List<String> slotNames,
            Map<String, MtlDefinition> mtlByName,
            Map<String, TextureHandle> textureCache,
            List<String> warnings
    ) {
        if (slotNames.isEmpty()) {
            return List.of(new LitMaterial());
        }
        List<Material> result = new ArrayList<>(slotNames.size());
        for (String name : slotNames) {
            result.add(buildMaterial(backend, siblings, mtlByName.get(name), textureCache, warnings));
        }
        return result;
    }

    private static Material buildMaterial(
            RenderBackend backend,
            AssetResolver siblings,
            MtlDefinition definition,
            Map<String, TextureHandle> textureCache,
            List<String> warnings
    ) {
        LitMaterial material = new LitMaterial();
        if (definition == null) {
            return material;
        }
        material.setBaseColor(definition.diffuseColor().x, definition.diffuseColor().y, definition.diffuseColor().z);
        definition.diffuseTexturePath().ifPresent(relativeTexture -> {
            material.setAlbedo(loadTexture(backend, siblings, relativeTexture, textureCache,
                    TextureFormat.SRGB8_ALPHA8, warnings));
            recordTexturePath(material, "albedo", siblings, relativeTexture);
        });
        definition.normalTexturePath().ifPresent(relativeTexture -> {
            material.setNormalMap(loadTexture(backend, siblings, relativeTexture, textureCache,
                    TextureFormat.RGBA8, warnings));
            recordTexturePath(material, "normalMap", siblings, relativeTexture);
        });
        applyAlphaMask(material, definition);
        return material;
    }

    private static void applyAlphaMask(LitMaterial material, MtlDefinition definition) {
        if (definition.alphaMaskTexturePath().isEmpty()) {
            return;
        }
        material.setAlphaCutoff(DEFAULT_ALPHA_CUTOFF);
        material.setDoubleSided(true);
    }

    private static void recordTexturePath(LitMaterial material, String fieldName,
                                          AssetResolver siblings, String relativeTexture) {
        siblings.resolve(relativeTexture).ifPresent(source ->
                material.setTexturePath(fieldName, source.path()));
    }

    private static TextureHandle loadTexture(
            RenderBackend backend,
            AssetResolver siblings,
            String relativeTexture,
            Map<String, TextureHandle> textureCache,
            TextureFormat format,
            List<String> warnings
    ) {
        String cacheKey = format.name() + ":" + relativeTexture;
        return textureCache.computeIfAbsent(cacheKey, ignored ->
                siblings.resolve(relativeTexture)
                        .map(textureSource -> tryLoadTexture(backend, textureSource, format, warnings))
                        .orElseGet(() -> fallbackTexture(backend, relativeTexture, warnings)));
    }

    private static TextureHandle tryLoadTexture(RenderBackend backend, AssetSource source,
                                                TextureFormat format, List<String> warnings) {
        try {
            return Texture2D.loadFrom(backend, source, format);
        } catch (EpysiaException error) {
            return fallbackTexture(backend, source.path(), warnings);
        }
    }

    private static TextureHandle fallbackTexture(RenderBackend backend, String texturePath,
                                                 List<String> warnings) {
        warnings.add("Texture not found, using white fallback: " + texturePath);
        return Texture2D.whitePixel(backend);
    }

    private static String readText(AssetSource source) {
        return tryReadText(source).orElseThrow(() ->
                new EpysiaException("Model resource not found on filesystem or classpath: " + source.path()));
    }

    private static Optional<String> tryReadText(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = opened.get()) {
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }
}
