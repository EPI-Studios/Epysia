package fr.epistudio.epysia.render.mesh;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ObjLoader {

    private static final String CLASSPATH_ROOT = "models/";
    private static final Path FILESYSTEM_ROOT = Path.of("src/main/resources/models");
    private static final String TEXTURE_CLASSPATH_ROOT = "models/";

    private ObjLoader() {
    }

    public static LoadedObj loadFromResource(RenderBackend backend, String relativeObjPath) {
        String objSource = readResource(relativeObjPath);
        ObjParseResult parsed = ObjMesh.parseDetailedFromSource(objSource);
        String objDirectory = parentDirectory(relativeObjPath);
        Map<String, MtlDefinition> mtlByName = loadMaterialLibraries(objDirectory, parsed.mtllibPaths());
        UploadedMesh uploaded = MeshUploader.upload(backend, parsed.mesh());
        Map<String, TextureHandle> textureCache = new HashMap<>();
        List<Material> materials = buildMaterials(backend, objDirectory, parsed.materialSlotNames(), mtlByName, textureCache);
        return new LoadedObj(uploaded, materials);
    }

    private static Map<String, MtlDefinition> loadMaterialLibraries(String objDirectory, List<String> mtllibPaths) {
        Map<String, MtlDefinition> byName = new HashMap<>();
        for (String mtlRelative : mtllibPaths) {
            String resolved = resolveSibling(objDirectory, mtlRelative);
            Optional<String> mtlSource = tryReadResource(resolved);
            if (mtlSource.isEmpty()) {
                continue;
            }
            for (MtlDefinition definition : MtlParser.parseFromSource(mtlSource.get())) {
                byName.put(definition.name(), definition);
            }
        }
        return byName;
    }

    private static List<Material> buildMaterials(
            RenderBackend backend,
            String objDirectory,
            List<String> slotNames,
            Map<String, MtlDefinition> mtlByName,
            Map<String, TextureHandle> textureCache
    ) {
        if (slotNames.isEmpty()) {
            return List.of(new LitMaterial());
        }
        List<Material> result = new ArrayList<>(slotNames.size());
        for (String name : slotNames) {
            result.add(buildMaterial(backend, objDirectory, mtlByName.get(name), textureCache));
        }
        return result;
    }

    private static Material buildMaterial(
            RenderBackend backend,
            String objDirectory,
            MtlDefinition definition,
            Map<String, TextureHandle> textureCache
    ) {
        LitMaterial material = new LitMaterial();
        if (definition == null) {
            return material;
        }
        material.setBaseColor(definition.diffuseColor().x, definition.diffuseColor().y, definition.diffuseColor().z);
        definition.diffuseTexturePath().ifPresent(relativeTexture ->
                material.setAlbedo(loadTexture(backend, objDirectory, relativeTexture, textureCache, TextureFormat.SRGB8_ALPHA8)));
        definition.normalTexturePath().ifPresent(relativeTexture ->
                material.setNormalMap(loadTexture(backend, objDirectory, relativeTexture, textureCache, TextureFormat.RGBA8)));
        return material;
    }

    private static TextureHandle loadTexture(
            RenderBackend backend,
            String objDirectory,
            String relativeTexture,
            Map<String, TextureHandle> textureCache,
            TextureFormat format
    ) {
        String resolvedRelativeToModels = resolveSibling(objDirectory, relativeTexture);
        String cacheKey = format.name() + ":" + resolvedRelativeToModels;
        return textureCache.computeIfAbsent(
                cacheKey,
                ignored -> Texture2D.loadFromResource(backend, TEXTURE_CLASSPATH_ROOT + resolvedRelativeToModels, format)
        );
    }

    private static String resolveSibling(String directory, String relativeName) {
        return directory.isEmpty() ? relativeName : directory + "/" + relativeName;
    }

    private static String parentDirectory(String relativePath) {
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash < 0 ? "" : relativePath.substring(0, lastSlash);
    }

    private static String readResource(String relativePath) {
        return tryReadResource(relativePath).orElseThrow(() ->
                new EpysiaException("Model resource not found on filesystem or classpath: " + relativePath));
    }

    private static Optional<String> tryReadResource(String relativePath) {
        Optional<String> fromFile = readFromFilesystem(relativePath);
        if (fromFile.isPresent()) {
            return fromFile;
        }
        return readFromClasspath(relativePath);
    }

    private static Optional<String> readFromFilesystem(String relativePath) {
        Path absolute = FILESYSTEM_ROOT.resolve(relativePath);
        if (!Files.isRegularFile(absolute)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(absolute, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + absolute + ": " + exception.getMessage());
        }
    }

    private static Optional<String> readFromClasspath(String relativePath) {
        try (InputStream stream = ObjLoader.class.getClassLoader().getResourceAsStream(CLASSPATH_ROOT + relativePath)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + relativePath + ": " + exception.getMessage());
        }
    }
}
