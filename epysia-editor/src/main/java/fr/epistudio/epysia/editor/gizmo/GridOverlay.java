package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.editor.gl.OverlayShader;
import fr.epistudio.epysia.editor.gl.OverlayTarget;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

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
import org.lwjgl.opengl.GL11;

public final class GridOverlay implements AutoCloseable {

    public static final float DEFAULT_MINOR_FADE_DISTANCE = 40.0f;
    public static final float MAJOR_FADE_MULTIPLIER = 3.0f;

    private static final Vector3f MINOR_COLOR = new Vector3f(0.46f, 0.47f, 0.50f);
    private static final Vector3f MAJOR_COLOR = new Vector3f(0.62f, 0.63f, 0.67f);
    private static final Vector3f AXIS_X_COLOR = new Vector3f(0.93f, 0.33f, 0.30f);
    private static final Vector3f AXIS_Z_COLOR = new Vector3f(0.32f, 0.56f, 0.96f);
    private static final float MINOR_SPACING = 1.0f;
    private static final int MAJOR_EVERY = 10;
    private static final int HALF_LINE_COUNT = 130;
    private static final float MINOR_HALF_WIDTH_PIXELS = 0.75f;
    private static final float MAJOR_HALF_WIDTH_PIXELS = 1.0f;
    private static final float GRID_ALPHA = 0.9f;
    private static final float MINIMUM_HALF_WIDTH_WORLD = 0.002f;
    private static final int FLOATS_PER_VERTEX = 6;

    private final OverlayTarget framebuffer = new OverlayTarget();
    private final OverlayShader shader = new OverlayShader();
    private final int vao;
    private final int vbo;
    private int capacityFloats;
    private int minorVertexCount;
    private int vertexCount;
    private float thicknessScale = 1.0f;
    private FloatBuffer buffer = BufferUtils.createFloatBuffer(16384);
    private float worldPerPixel;
    private final Vector3f scratchCamera = new Vector3f();

    public GridOverlay() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 1L, GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
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

    public void render(Matrix4f viewProjection, Vector3f cameraPosition, int pixelWidth, int pixelHeight,
                       float lineThicknessScale, float minorFadeDistance) {
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            vertexCount = 0;
            return;
        }
        scratchCamera.set(cameraPosition);
        thicknessScale = lineThicknessScale;
        worldPerPixel = estimateWorldPerPixel(viewProjection, pixelWidth);
        buildGeometry();
        framebuffer.ensureSize(pixelWidth, pixelHeight);
        framebuffer.bind();
        glViewport(0, 0, pixelWidth, pixelHeight);
        configureState();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        uploadBuffer();
        draw(viewProjection, minorFadeDistance);
    }

    private void buildGeometry() {
        buffer.clear();
        float extent = HALF_LINE_COUNT * MINOR_SPACING;
        float centerX = Math.round(scratchCamera.x / MAJOR_EVERY) * (float) MAJOR_EVERY;
        float centerZ = Math.round(scratchCamera.z / MAJOR_EVERY) * (float) MAJOR_EVERY;
        appendLines(centerX, centerZ, extent, false);
        minorVertexCount = writtenVertexCount();
        appendLines(centerX, centerZ, extent, true);
        buffer.flip();
        vertexCount = buffer.remaining() / FLOATS_PER_VERTEX;
    }

    private int writtenVertexCount() {
        return buffer.position() / FLOATS_PER_VERTEX;
    }

    private void appendLines(float centerX, float centerZ, float extent, boolean emphasizedPass) {
        for (int i = -HALF_LINE_COUNT; i <= HALF_LINE_COUNT; i++) {
            float offset = i * MINOR_SPACING;
            appendLineX(centerX, centerZ, offset, extent, emphasizedPass);
            appendLineZ(centerX, centerZ, offset, extent, emphasizedPass);
        }
    }

    private void appendLineX(float centerX, float centerZ, float offset, float extent, boolean emphasizedPass) {
        float z = centerZ + offset;
        boolean axis = Math.abs(z) < MINOR_SPACING * 0.25f;
        boolean major = Math.round(z / MINOR_SPACING) % MAJOR_EVERY == 0;
        if ((axis || major) != emphasizedPass) {
            return;
        }
        Vector3f color = axis ? AXIS_X_COLOR : (major ? MAJOR_COLOR : MINOR_COLOR);
        float halfWidth = pixelHalfWidth(axis || major);
        putQuad(centerX - extent, z - halfWidth, centerX + extent, z + halfWidth, color);
    }

    private void appendLineZ(float centerX, float centerZ, float offset, float extent, boolean emphasizedPass) {
        float x = centerX + offset;
        boolean axis = Math.abs(x) < MINOR_SPACING * 0.25f;
        boolean major = Math.round(x / MINOR_SPACING) % MAJOR_EVERY == 0;
        if ((axis || major) != emphasizedPass) {
            return;
        }
        Vector3f color = axis ? AXIS_Z_COLOR : (major ? MAJOR_COLOR : MINOR_COLOR);
        float halfWidth = pixelHalfWidth(axis || major);
        putQuad(x - halfWidth, centerZ - extent, x + halfWidth, centerZ + extent, color);
    }

    private float pixelHalfWidth(boolean emphasized) {
        float pixels = emphasized ? MAJOR_HALF_WIDTH_PIXELS : MINOR_HALF_WIDTH_PIXELS;
        return Math.max(MINIMUM_HALF_WIDTH_WORLD, pixels * thicknessScale * worldPerPixel);
    }

    private void putQuad(float minX, float minZ, float maxX, float maxZ, Vector3f color) {
        putVertex(minX, minZ, color);
        putVertex(maxX, minZ, color);
        putVertex(maxX, maxZ, color);
        putVertex(minX, minZ, color);
        putVertex(maxX, maxZ, color);
        putVertex(minX, maxZ, color);
    }

    private void putVertex(float x, float z, Vector3f color) {
        ensureCapacity(FLOATS_PER_VERTEX);
        buffer.put(x).put(0.0f).put(z).put(color.x).put(color.y).put(color.z);
    }

    private void ensureCapacity(int floats) {
        if (buffer.remaining() >= floats) {
            return;
        }
        FloatBuffer grown = BufferUtils.createFloatBuffer(buffer.capacity() * 2);
        buffer.flip();
        grown.put(buffer);
        buffer = grown;
    }

    private void configureState() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        GL11.glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void uploadBuffer() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (buffer.capacity() > capacityFloats) {
            capacityFloats = buffer.capacity();
            glBufferData(GL_ARRAY_BUFFER, (long) capacityFloats * Float.BYTES, GL_DYNAMIC_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void draw(Matrix4f viewProjection, float minorFadeDistance) {
        float extent = HALF_LINE_COUNT * MINOR_SPACING;
        float minorFade = Math.min(minorFadeDistance, extent);
        float majorFade = Math.min(minorFadeDistance * MAJOR_FADE_MULTIPLIER, extent);
        shader.bind(viewProjection, GRID_ALPHA, 1.0f, scratchCamera, minorFade);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, minorVertexCount);
        shader.setFade(scratchCamera, majorFade);
        glDrawArrays(GL_TRIANGLES, minorVertexCount, vertexCount - minorVertexCount);
        glBindVertexArray(0);
        shader.unbind();
    }

    private static float estimateWorldPerPixel(Matrix4f viewProjection, int pixelWidth) {
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        Vector3f center = unproject(inverse, 0.0f, 0.0f);
        Vector3f right = unproject(inverse, 2.0f / Math.max(1, pixelWidth), 0.0f);
        float distance = center.distance(right);
        return Float.isFinite(distance) && distance > 1e-8f ? distance : 0.01f;
    }

    private static Vector3f unproject(Matrix4f inverse, float ndcX, float ndcY) {
        Vector4f point = new Vector4f(ndcX, ndcY, 0.0f, 1.0f);
        inverse.transform(point);
        if (Math.abs(point.w) < 1e-6f) {
            return new Vector3f();
        }
        return new Vector3f(point.x / point.w, point.y / point.w, point.z / point.w);
    }

    @Override
    public void close() {
        shader.close();
        framebuffer.close();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
