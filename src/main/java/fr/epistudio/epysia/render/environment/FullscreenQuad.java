package fr.epistudio.epysia.render.environment;

import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

public final class FullscreenQuad {

    public static final VertexLayout LAYOUT = new VertexLayout(
            List.of(new VertexAttribute(0, VertexFormat.FLOAT2, 0)), 8);

    private static final float[] VERTICES = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
             1.0f,  1.0f,
            -1.0f,  1.0f
    };
    private static final int[] INDICES = {0, 1, 2, 0, 2, 3};

    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private MeshHandle mesh;
    private RenderBackend backend;

    public void initialize(RenderBackend backend) {
        this.backend = backend;
        ByteBuffer vertexBytes = BufferUtils.createByteBuffer(VERTICES.length * Float.BYTES);
        vertexBytes.asFloatBuffer().put(VERTICES);
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexBytes));
        ByteBuffer indexBytes = BufferUtils.createByteBuffer(INDICES.length * Integer.BYTES);
        indexBytes.asIntBuffer().put(INDICES);
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indexBytes));
        mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0, INDICES.length, IndexFormat.UINT32));
    }

    public MeshHandle mesh() {
        return mesh;
    }

    public void shutdown() {
        if (backend == null) {
            return;
        }
        backend.destroy(mesh);
        backend.destroy(indexBuffer);
        backend.destroy(vertexBuffer);
        backend = null;
    }
}
