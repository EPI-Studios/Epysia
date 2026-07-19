package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.gl.OverlayShader;
import fr.epistudio.epysia.editor.gl.OverlayTarget;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public final class SelectionOutlineOverlay implements AutoCloseable {

    private static final Vector3f ACCENT_COLOR = new Vector3f(0.10f, 0.58f, 0.95f);
    private static final float OUTLINE_HALF_WIDTH_PIXELS = 1.25f;
    private static final float FRUSTUM_HALF_WIDTH_PIXELS = 1.0f;
    private static final float OUTLINE_ALPHA = 1.0f;
    private static final float MESH_HALF_EXTENT = 0.5f;
    private static final float ICON_HALF_EXTENT = 0.25f;
    private static final float FRUSTUM_DISPLAY_FAR = 12.0f;

    private final OverlayTarget framebuffer = new OverlayTarget();
    private final OverlayShader shader = new OverlayShader();
    private final int vao;
    private final int vbo;
    private int capacityFloats;
    private int vertexCount;
    private FloatBuffer scratch = BufferUtils.createFloatBuffer(4096);

    public SelectionOutlineOverlay() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 1L, GL_DYNAMIC_DRAW);
        int stride = 6 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public int textureId() {
        return framebuffer.colorTextureId();
    }

    public void render(List<GameObject> selectedObjects, Matrix4f viewProjection, Vector3f cameraPosition,
                       int pixelWidth, int pixelHeight, float lineThicknessScale) {
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            vertexCount = 0;
            return;
        }
        buildGeometry(selectedObjects, viewProjection, cameraPosition, pixelWidth, lineThicknessScale);
        framebuffer.ensureSize(pixelWidth, pixelHeight);
        framebuffer.bind();
        glViewport(0, 0, pixelWidth, pixelHeight);
        configureState();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        uploadBuffer();
        draw(viewProjection);
    }

    private void buildGeometry(List<GameObject> selectedObjects, Matrix4f viewProjection,
                               Vector3f cameraPosition, int pixelWidth, float lineThicknessScale) {
        BoxEdgeWriter writer = new BoxEdgeWriter(viewProjection, cameraPosition,
                pixelWidth, lineThicknessScale);
        for (GameObject gameObject : selectedObjects) {
            appendOutline(gameObject, writer);
        }
        scratch = writer.finish();
        vertexCount = scratch.remaining() / 6;
    }

    private void appendOutline(GameObject gameObject, BoxEdgeWriter writer) {
        Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
        if (transform.isEmpty()) {
            return;
        }
        Optional<Camera3D> camera = gameObject.getComponent(Camera3D.class);
        if (camera.isPresent()) {
            writer.writeCorners(frustumCorners(camera.get()), FRUSTUM_HALF_WIDTH_PIXELS);
            return;
        }
        float halfExtent = gameObject.getComponent(MeshRenderer.class).isPresent()
                ? MESH_HALF_EXTENT
                : ICON_HALF_EXTENT;
        writer.writeBox(new Matrix4f(transform.get().worldMatrix()), halfExtent, OUTLINE_HALF_WIDTH_PIXELS);
    }

    private static Vector3f[] frustumCorners(Camera3D camera) {
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(camera.fieldOfViewDegrees()), camera.aspectRatio(),
                camera.nearPlane(), Math.min(camera.farPlane(), FRUSTUM_DISPLAY_FAR));
        Matrix4f inverse = projection.mul(camera.view()).invert();
        Vector3f[] corners = new Vector3f[8];
        for (int index = 0; index < corners.length; index++) {
            float x = (index & 1) == 0 ? -1.0f : 1.0f;
            float y = (index & 2) == 0 ? -1.0f : 1.0f;
            float z = (index & 4) == 0 ? -1.0f : 1.0f;
            corners[index] = inverse.transformProject(new Vector3f(x, y, z));
        }
        return corners;
    }

    private void configureState() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void uploadBuffer() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (scratch.capacity() > capacityFloats) {
            capacityFloats = scratch.capacity();
            glBufferData(GL_ARRAY_BUFFER, (long) capacityFloats * Float.BYTES, GL_DYNAMIC_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, scratch);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void draw(Matrix4f viewProjection) {
        shader.bind(viewProjection, OUTLINE_ALPHA, 1.0f);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
        shader.unbind();
    }

    @Override
    public void close() {
        shader.close();
        framebuffer.close();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }

    private static final class BoxEdgeWriter {

        private static final int[][] EDGES = {
                {0, 1}, {1, 3}, {3, 2}, {2, 0},
                {4, 5}, {5, 7}, {7, 6}, {6, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        private final Vector3f cameraPosition;
        private final ScreenSpaceWidth screenSpaceWidth;
        private FloatBuffer buffer = BufferUtils.createFloatBuffer(4096);

        BoxEdgeWriter(Matrix4f viewProjection, Vector3f cameraPosition, int pixelWidth, float thicknessScale) {
            this.cameraPosition = cameraPosition;
            this.screenSpaceWidth = new ScreenSpaceWidth(viewProjection, cameraPosition, pixelWidth, thicknessScale);
        }

        void writeBox(Matrix4f world, float halfExtent, float halfWidthPixels) {
            Vector3f[] corners = new Vector3f[8];
            for (int index = 0; index < corners.length; index++) {
                float x = (index & 1) == 0 ? -halfExtent : halfExtent;
                float y = (index & 2) == 0 ? -halfExtent : halfExtent;
                float z = (index & 4) == 0 ? -halfExtent : halfExtent;
                corners[index] = world.transformPosition(new Vector3f(x, y, z));
            }
            writeCorners(corners, halfWidthPixels);
        }

        void writeCorners(Vector3f[] corners, float halfWidthPixels) {
            for (int[] edge : EDGES) {
                writeEdge(corners[edge[0]], corners[edge[1]], halfWidthPixels);
            }
        }

        private void writeEdge(Vector3f start, Vector3f end, float halfWidthPixels) {
            Vector3f midpoint = new Vector3f(start).add(end).mul(0.5f);
            Vector3f viewDirection = new Vector3f(cameraPosition).sub(midpoint);
            if (viewDirection.lengthSquared() < 1.0e-8f) {
                viewDirection.set(0.0f, 0.0f, 1.0f);
            }
            Vector3f side = new Vector3f(end).sub(start).cross(viewDirection);
            if (side.lengthSquared() < 1.0e-10f) {
                return;
            }
            side.normalize().mul(screenSpaceWidth.worldHalfWidthAt(midpoint, halfWidthPixels));
            writeQuad(new Vector3f(start).add(side), new Vector3f(start).sub(side),
                    new Vector3f(end).sub(side), new Vector3f(end).add(side));
        }

        private void writeQuad(Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
            writeVertex(a);
            writeVertex(b);
            writeVertex(c);
            writeVertex(a);
            writeVertex(c);
            writeVertex(d);
        }

        private void writeVertex(Vector3f point) {
            ensureCapacity(6);
            buffer.put(point.x).put(point.y).put(point.z)
                    .put(ACCENT_COLOR.x).put(ACCENT_COLOR.y).put(ACCENT_COLOR.z);
        }

        private void ensureCapacity(int floats) {
            if (buffer.remaining() >= floats) {
                return;
            }
            FloatBuffer grown = BufferUtils.createFloatBuffer(
                    Math.max(buffer.capacity() * 2, buffer.capacity() + floats));
            buffer.flip();
            grown.put(buffer);
            buffer = grown;
        }

        FloatBuffer finish() {
            buffer.flip();
            return buffer;
        }
    }
}
