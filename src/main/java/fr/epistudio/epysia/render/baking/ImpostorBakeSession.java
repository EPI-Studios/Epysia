package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.assets.epyimpostor.EpyImpostorFormat;
import fr.epistudio.epysia.assets.epyimpostor.ImpostorAtlas;
import fr.epistudio.epysia.assets.epyimpostor.ImpostorAtlasJsonCodec;
import fr.epistudio.epysia.assets.loaders.TextureImportSettings;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.mesh.UploadedSubmesh;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.texture.Texture2D;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImageWrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ImpostorBakeSession {

    private static final String VERTEX_PATH = "impostor_bake.vert.glsl";
    private static final String ALBEDO_FRAGMENT_PATH = "impostor_bake_albedo.frag.glsl";
    private static final String NORMAL_FRAGMENT_PATH = "impostor_bake_normal.frag.glsl";
    private static final int VIEW_UBO_BINDING = 0;
    private static final int ALBEDO_TEXTURE_BINDING = 1;
    private static final int VIEW_UBO_SIZE = 160;
    private static final int NORMAL_MATRIX_OFFSET = 64;
    private static final int BASE_COLOR_OFFSET = 128;
    private static final int SURFACE_PARAMETERS_OFFSET = 144;
    private static final int BYTES_PER_PIXEL = 4;
    private static final float MINIMUM_RADIUS = 1.0e-4f;

    private final RenderBackend backend;
    private final ShaderLoader shaderLoader;
    private final ImpostorBakeRequest request;
    private final int gridSize;
    private final int tileSize;
    private final int atlasSize;
    private final Vector3f center;
    private final float radius;
    private final ByteBuffer viewUboScratch = BufferUtils.createByteBuffer(VIEW_UBO_SIZE);
    private final ByteBuffer tileScratch;
    private final ByteBuffer albedoAtlas;
    private final ByteBuffer normalAtlas;
    private final List<UploadedPart> uploadedParts = new ArrayList<>();
    private final List<TextureHandle> ownedTextures = new ArrayList<>();
    private final Map<Integer, PipelineHandle> albedoPipelines = new HashMap<>();
    private final Map<Integer, PipelineHandle> normalPipelines = new HashMap<>();

    private BindingSetLayout bindingLayout;
    private BufferHandle viewUbo;
    private TextureHandle albedoTexture;
    private TextureHandle normalTexture;
    private TextureHandle depthTexture;
    private RenderTargetHandle albedoTarget;
    private RenderTargetHandle normalTarget;

    ImpostorBakeSession(RenderBackend backend, ShaderLoader shaderLoader, ImpostorBakeRequest request) {
        this.backend = backend;
        this.shaderLoader = shaderLoader;
        this.request = request;
        this.gridSize = request.settings().gridSize();
        this.tileSize = request.settings().tileSize();
        this.atlasSize = gridSize * tileSize;
        this.center = ImpostorBounds.centerOf(request.parts());
        this.radius = Math.max(ImpostorBounds.radiusOf(request.parts(), center), MINIMUM_RADIUS);
        this.tileScratch = BufferUtils.createByteBuffer(tileSize * tileSize * BYTES_PER_PIXEL);
        this.albedoAtlas = BufferUtils.createByteBuffer(atlasSize * atlasSize * BYTES_PER_PIXEL);
        this.normalAtlas = BufferUtils.createByteBuffer(atlasSize * atlasSize * BYTES_PER_PIXEL);
    }

    List<Path> run() {
        createResources();
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                bakeView(column, row);
            }
        }
        return publish();
    }

    private void bakeView(int column, int row) {
        Matrix4f viewProjection = viewProjectionFor(column, row);
        renderView(albedoTarget, albedoPipelines, viewProjection);
        copyTile(albedoTarget, albedoAtlas, column, row);
        renderView(normalTarget, normalPipelines, viewProjection);
        copyTile(normalTarget, normalAtlas, column, row);
    }

    private Matrix4f viewProjectionFor(int column, int row) {
        Vector3f direction = HemiOctahedralGrid.directionAt(column, row, gridSize);
        Vector3f eye = new Vector3f(direction).mul(radius).add(center);
        return new Matrix4f()
                .ortho(-radius, radius, -radius, radius, 0.0f, radius * 2.0f)
                .lookAt(eye, center, HemiOctahedralGrid.referenceUp(direction));
    }

    private void renderView(RenderTargetHandle target, Map<Integer, PipelineHandle> pipelines, Matrix4f viewProjection) {
        backend.beginPass(target, new PassClear(true, true, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f));
        for (UploadedPart uploaded : uploadedParts) {
            drawPart(uploaded, pipelines.get(uploaded.vertexStride()), viewProjection);
        }
        backend.endPass();
    }

    private void drawPart(UploadedPart uploaded, PipelineHandle pipeline, Matrix4f viewProjection) {
        Matrix4f viewProjectionModel = new Matrix4f(viewProjection).mul(uploaded.part().transform());
        Matrix3f normalMatrix = uploaded.part().transform().normal(new Matrix3f());
        for (UploadedSubmesh submesh : uploaded.mesh().submeshes()) {
            writeViewUbo(viewProjectionModel, normalMatrix, uploaded.part().surfaceForSlot(submesh.materialSlot()));
            backend.execute(new DrawCommand(pipeline, submesh.handle(),
                    uploaded.bindingsForSlot(submesh.materialSlot()), 0L, 1));
        }
    }

    private void writeViewUbo(Matrix4f viewProjectionModel, Matrix3f normalMatrix, ImpostorSurface surface) {
        viewUboScratch.clear();
        viewProjectionModel.get(0, viewUboScratch);
        new Matrix4f(normalMatrix).get(NORMAL_MATRIX_OFFSET, viewUboScratch);
        viewUboScratch.putFloat(BASE_COLOR_OFFSET, surface.baseColor().x);
        viewUboScratch.putFloat(BASE_COLOR_OFFSET + 4, surface.baseColor().y);
        viewUboScratch.putFloat(BASE_COLOR_OFFSET + 8, surface.baseColor().z);
        viewUboScratch.putFloat(BASE_COLOR_OFFSET + 12, surface.baseColor().w);
        viewUboScratch.putFloat(SURFACE_PARAMETERS_OFFSET, surface.alphaCutoff());
        viewUboScratch.putFloat(SURFACE_PARAMETERS_OFFSET + 4, surface.opaque() ? 1.0f : 0.0f);
        viewUboScratch.putFloat(SURFACE_PARAMETERS_OFFSET + 8, 0.0f);
        viewUboScratch.putFloat(SURFACE_PARAMETERS_OFFSET + 12, 0.0f);
        viewUboScratch.position(0).limit(VIEW_UBO_SIZE);
        backend.writeBuffer(viewUbo, viewUboScratch, 0L);
    }

    private void copyTile(RenderTargetHandle target, ByteBuffer atlas, int column, int row) {
        tileScratch.clear();
        backend.readPixelsRgba(target, 0, 0, tileSize, tileSize, tileScratch);
        int rowBytes = tileSize * BYTES_PER_PIXEL;
        for (int line = 0; line < tileSize; line++) {
            int destination = ((row * tileSize + line) * atlasSize + column * tileSize) * BYTES_PER_PIXEL;
            atlas.put(destination, tileScratch, line * rowBytes, rowBytes);
        }
    }

    private void createResources() {
        bindingLayout = new BindingSetLayout(List.of(
                new BindingSlot(VIEW_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(ALBEDO_TEXTURE_BINDING, BindingType.SAMPLED_TEXTURE_2D)));
        viewUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(VIEW_UBO_SIZE)));
        createTargets();
        for (ImpostorPart part : request.parts()) {
            uploadedParts.add(uploadPart(part));
        }
    }

    private UploadedPart uploadPart(ImpostorPart part) {
        int stride = MeshShaderBindings.vertexStride(part.mesh().hasSkin(), part.mesh().hasVertexColors());
        albedoPipelines.computeIfAbsent(stride, key -> createPipeline(ALBEDO_FRAGMENT_PATH, key));
        normalPipelines.computeIfAbsent(stride, key -> createPipeline(NORMAL_FRAGMENT_PATH, key));
        return new UploadedPart(part, MeshUploader.upload(backend, part.mesh()), surfaceBindings(part), stride);
    }

    private List<BindingSetHandle> surfaceBindings(ImpostorPart part) {
        List<ImpostorSurface> surfaces = part.surfaces().isEmpty()
                ? List.of(ImpostorSurface.untextured()) : part.surfaces();
        List<BindingSetHandle> bindings = new ArrayList<>();
        for (ImpostorSurface surface : surfaces) {
            bindings.add(createBindingSet(albedoTextureFor(surface)));
        }
        return bindings;
    }

    private TextureHandle albedoTextureFor(ImpostorSurface surface) {
        TextureHandle texture = surface.albedoImage()
                .filter(Files::isRegularFile)
                .map(image -> Texture2D.loadFromFile(backend, image, TextureFormat.RGBA8))
                .orElseGet(() -> Texture2D.whitePixel(backend));
        ownedTextures.add(texture);
        return texture;
    }

    private BindingSetHandle createBindingSet(TextureHandle albedoImage) {
        return backend.createBindingSet(new BindingSetDescriptor(bindingLayout, List.of(
                new Binding(VIEW_UBO_BINDING, UniformBufferBinding.whole(viewUbo, VIEW_UBO_SIZE)),
                new Binding(ALBEDO_TEXTURE_BINDING, new SampledTextureBinding(albedoImage)))));
    }

    private PipelineHandle createPipeline(String fragmentPath, int vertexStride) {
        VertexLayout layout = new VertexLayout(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT3, 0),
                new VertexAttribute(1, VertexFormat.FLOAT3, 12),
                new VertexAttribute(2, VertexFormat.FLOAT2, 24)), vertexStride);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.LESS_EQUAL, BlendMode.OPAQUE, CullMode.NONE);
        return backend.createPipeline(new PipelineDescriptor(loadShaderSource(fragmentPath), layout, state, bindingLayout));
    }

    private void createTargets() {
        albedoTexture = createColorTexture();
        normalTexture = createColorTexture();
        depthTexture = backend.createTexture(new TextureDescriptor(tileSize, tileSize,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.NEAREST));
        albedoTarget = backend.createRenderTarget(new RenderTargetDescriptor(tileSize, tileSize,
                List.of(albedoTexture), Optional.of(depthTexture)));
        normalTarget = backend.createRenderTarget(new RenderTargetDescriptor(tileSize, tileSize,
                List.of(normalTexture), Optional.of(depthTexture)));
    }

    private TextureHandle createColorTexture() {
        return backend.createTexture(new TextureDescriptor(tileSize, tileSize,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.NEAREST));
    }

    private ShaderSource loadShaderSource(String fragmentPath) {
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private List<Path> publish() {
        createOutputDirectory();
        String albedoName = request.name() + EpyImpostorFormat.ALBEDO_ATLAS_SUFFIX;
        String normalName = request.name() + EpyImpostorFormat.NORMAL_ATLAS_SUFFIX;
        Path albedoFile = writeAtlas(albedoAtlas, albedoName, TextureImportSettings.COLOR_SPACE_SRGB);
        Path normalFile = writeAtlas(normalAtlas, normalName, TextureImportSettings.COLOR_SPACE_LINEAR);
        Path descriptorFile = writeDescriptor(albedoName, normalName);
        return List.of(albedoFile, normalFile, descriptorFile);
    }

    private void createOutputDirectory() {
        try {
            Files.createDirectories(request.outputDirectory());
        } catch (IOException failure) {
            throw new EpysiaException("Cannot create impostor output directory: " + failure.getMessage(), failure);
        }
    }

    private Path writeAtlas(ByteBuffer pixels, String fileName, String colorSpace) {
        Path file = request.outputDirectory().resolve(fileName);
        pixels.position(0).limit(atlasSize * atlasSize * BYTES_PER_PIXEL);
        if (!writeBottomUpPng(file, pixels)) {
            throw new EpysiaException("Failed to write impostor atlas to " + file);
        }
        writeAtlasImportSettings(file, colorSpace);
        return file;
    }

    private boolean writeBottomUpPng(Path file, ByteBuffer pixels) {
        STBImageWrite.stbi_flip_vertically_on_write(true);
        try {
            return STBImageWrite.stbi_write_png(file.toString(), atlasSize, atlasSize, BYTES_PER_PIXEL, pixels,
                    atlasSize * BYTES_PER_PIXEL);
        } finally {
            STBImageWrite.stbi_flip_vertically_on_write(false);
        }
    }

    private static void writeAtlasImportSettings(Path atlasFile, String colorSpace) {
        Path metaFile = AssetMetaFile.pathFor(atlasFile);
        AssetMetaFile.writeString(metaFile, TextureImportSettings.COLOR_SPACE_KEY, colorSpace);
        AssetMetaFile.writeString(metaFile, TextureImportSettings.WRAP_KEY, TextureImportSettings.WRAP_CLAMP);
        AssetMetaFile.writeString(metaFile, TextureImportSettings.MIPMAPS_KEY, "false");
    }

    private Path writeDescriptor(String albedoName, String normalName) {
        ImpostorAtlas atlas = ImpostorAtlas.hemiOctahedral(gridSize, tileSize, radius, center, albedoName, normalName);
        Path file = request.outputDirectory().resolve(request.name() + EpyImpostorFormat.EXTENSION);
        try {
            Files.writeString(file, new ImpostorAtlasJsonCodec().write(atlas));
        } catch (IOException failure) {
            throw new EpysiaException("Failed to write " + file + ": " + failure.getMessage(), failure);
        }
        return file;
    }

    void destroy() {
        for (UploadedPart uploaded : uploadedParts) {
            uploaded.destroy(backend);
        }
        for (TextureHandle texture : ownedTextures) {
            backend.destroy(texture);
        }
        albedoPipelines.values().forEach(backend::destroy);
        normalPipelines.values().forEach(backend::destroy);
        destroyTargets();
        if (viewUbo != null) {
            backend.destroy(viewUbo);
        }
    }

    private void destroyTargets() {
        if (albedoTarget != null) {
            backend.destroy(albedoTarget);
        }
        if (normalTarget != null) {
            backend.destroy(normalTarget);
        }
        if (albedoTexture != null) {
            backend.destroy(albedoTexture);
        }
        if (normalTexture != null) {
            backend.destroy(normalTexture);
        }
        if (depthTexture != null) {
            backend.destroy(depthTexture);
        }
    }

    private record UploadedPart(ImpostorPart part, UploadedMesh mesh, List<BindingSetHandle> bindings,
                                int vertexStride) {

        BindingSetHandle bindingsForSlot(int materialSlot) {
            return bindings.get(Math.clamp(materialSlot, 0, bindings.size() - 1));
        }

        void destroy(RenderBackend backend) {
            bindings.forEach(backend::destroy);
            mesh.destroy(backend);
        }
    }
}
