package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.editor.gl.OverlayShader;
import fr.epistudio.epysia.editor.gl.OverlayTarget;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CapsuleCollider;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.physics.components.SphereCollider;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.glLineWidth;
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

public final class ColliderWireframeOverlay implements AutoCloseable {

    private PhysicsSystem simulated;
    private final ColliderEdgeCache edgeCache = new ColliderEdgeCache();
    private long lastSignature = Long.MIN_VALUE;

    public void useSimulatedWorld(PhysicsSystem system) {
        this.simulated = system;
    }

    private static final Vector3f WIRE_COLOR = new Vector3f(0.30f, 0.95f, 0.45f);
    private static final float EDGE_HALF_WIDTH_PIXELS = 1.0f;
    private static final int SPHERE_SEGMENTS = 24;

    private static final float WIRE_ALPHA = 0.92f;

    private final OverlayTarget framebuffer = new OverlayTarget();
    private final OverlayShader shader = new OverlayShader();

    private final int vao;
    private final int vbo;
    private int capacityFloats;
    private int vertexCount;
    private FloatBuffer scratch = BufferUtils.createFloatBuffer(4096);

    public ColliderWireframeOverlay() {
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

    public void render(Scene scene, Matrix4f viewProjection, Vector3f cameraPosition,
                       int pixelWidth, int pixelHeight, float lineThicknessScale) {
        if (scene == null || pixelWidth <= 0 || pixelHeight <= 0) {
            vertexCount = 0;
            return;
        }
        boolean rebuilt = rebuildIfChanged(scene, viewProjection, cameraPosition,
                pixelWidth, lineThicknessScale);
        framebuffer.ensureSize(pixelWidth, pixelHeight);
        framebuffer.bind();
        glViewport(0, 0, pixelWidth, pixelHeight);
        configureState();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (rebuilt) {
            uploadBuffer();
        }
        draw(viewProjection);
    }

    private boolean rebuildIfChanged(Scene scene, Matrix4f viewProjection, Vector3f cameraPosition,
                                     int pixelWidth, float lineThicknessScale) {
        long signature = signatureOf(scene, cameraPosition, pixelWidth, lineThicknessScale);
        if (signature == lastSignature && scratch != null) {
            return false;
        }
        lastSignature = signature;
        buildGeometry(scene, viewProjection, cameraPosition, pixelWidth, lineThicknessScale);
        return true;
    }

    private long signatureOf(Scene scene, Vector3f cameraPosition, int pixelWidth,
                             float lineThicknessScale) {
        long signature = 17L;
        signature = signature * 31L + Float.floatToIntBits(cameraPosition.x);
        signature = signature * 31L + Float.floatToIntBits(cameraPosition.y);
        signature = signature * 31L + Float.floatToIntBits(cameraPosition.z);
        signature = signature * 31L + pixelWidth;
        signature = signature * 31L + Float.floatToIntBits(lineThicknessScale);
        signature = signature * 31L + (simulated != null ? 1L : 0L);
        for (GameObject gameObject : scene.gameObjects()) {
            for (var component : gameObject.components()) {
                if (component instanceof Collider collider) {
                    signature = signature * 31L + System.identityHashCode(collider);
                    signature = signature * 31L + shapeSignature(collider);
                    signature = signature * 31L + poseSignature(gameObject);
                }
            }
        }
        return signature;
    }

    private static long shapeSignature(Collider collider) {
        Vector3fc offset = collider.offset();
        long value = Float.floatToIntBits(offset.x());
        value = value * 31L + Float.floatToIntBits(offset.y());
        value = value * 31L + Float.floatToIntBits(offset.z());
        return switch (collider) {
            case BoxCollider box -> value * 31L + Float.floatToIntBits(box.halfExtents().x())
                    + Float.floatToIntBits(box.halfExtents().y()) * 7L
                    + Float.floatToIntBits(box.halfExtents().z()) * 13L;
            case SphereCollider sphere -> value * 31L + Float.floatToIntBits(sphere.radius());
            case CapsuleCollider capsule -> value * 31L + Float.floatToIntBits(capsule.radius())
                    + Float.floatToIntBits(capsule.halfHeight()) * 7L;
            default -> value;
        };
    }

    private static long poseSignature(GameObject gameObject) {
        return gameObject.getComponent(Transform3D.class)
                .map(transform -> {
                    Matrix4f world = transform.worldMatrix();
                    long value = Float.floatToIntBits(world.m30());
                    value = value * 31L + Float.floatToIntBits(world.m31());
                    value = value * 31L + Float.floatToIntBits(world.m32());
                    value = value * 31L + Float.floatToIntBits(world.m00());
                    return value * 31L + Float.floatToIntBits(world.m11());
                })
                .orElse(0L);
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
        if (scratch.capacity() > capacityFloats) {
            capacityFloats = scratch.capacity();
            glBufferData(GL_ARRAY_BUFFER, (long) capacityFloats * Float.BYTES, GL_DYNAMIC_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, scratch);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static final float EDGE_LINE_WIDTH = 1.0f;

    private void draw(Matrix4f viewProjection) {
        shader.bind(viewProjection, WIRE_ALPHA, 1.0f);
        glBindVertexArray(vao);
        glLineWidth(EDGE_LINE_WIDTH);
        glDrawArrays(GL_LINES, 0, vertexCount);
        glBindVertexArray(0);
        shader.unbind();
    }

    private void buildGeometry(Scene scene, Matrix4f viewProjection, Vector3f cameraPosition,
                               int pixelWidth, float lineThicknessScale) {
        EdgeWriter writer = new EdgeWriter(viewProjection, cameraPosition, pixelWidth, lineThicknessScale);
        if (simulated != null) {
            simulated.drawDebug((startX, startY, startZ, endX, endY, endZ, color) ->
                    writer.edge(new Vector3f(startX, startY, startZ), new Vector3f(endX, endY, endZ)));
        } else {
            List<GameObject> gameObjects = scene.gameObjects();
            for (GameObject gameObject : gameObjects) {
                appendColliders(gameObject, writer);
            }
        }
        scratch = writer.finish();
        vertexCount = scratch.remaining() / 6;
    }

    private void appendColliders(GameObject gameObject, EdgeWriter writer) {
        var transform = gameObject.getComponent(Transform3D.class);
        if (transform.isEmpty()) {
            return;
        }
        Matrix4f world = new Matrix4f(transform.get().worldMatrix());
        for (var component : gameObject.components()) {
            if (component instanceof Collider collider) {
                ColliderShapeWriter.write(writer, world, collider, edgeCache);
            }
        }
    }

    @Override
    public void close() {
        shader.close();
        framebuffer.close();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }

    static final class EdgeWriter {

        private final Vector3f cameraPosition;
        private final ScreenSpaceWidth screenSpaceWidth;
        private FloatBuffer buffer = BufferUtils.createFloatBuffer(4096);

        EdgeWriter(Matrix4f viewProjection, Vector3f cameraPosition, int pixelWidth, float thicknessScale) {
            this.cameraPosition = cameraPosition;
            this.screenSpaceWidth = new ScreenSpaceWidth(viewProjection, cameraPosition, pixelWidth, thicknessScale);
        }

        private final Vector3f midpoint = new Vector3f();
        private final Vector3f viewDirection = new Vector3f();
        private final Vector3f along = new Vector3f();
        private final Vector3f side = new Vector3f();
        private final Vector3f corner = new Vector3f();

        void edge(Vector3f a, Vector3f b) {
            edge(a.x, a.y, a.z, b.x, b.y, b.z);
        }

        void edge(float startX, float startY, float startZ, float endX, float endY, float endZ) {
            putVertex(corner.set(startX, startY, startZ));
            putVertex(corner.set(endX, endY, endZ));
        }

        private void putQuad(Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
            putVertex(a);
            putVertex(b);
            putVertex(c);
            putVertex(a);
            putVertex(c);
            putVertex(d);
        }

        private void putVertex(Vector3f point) {
            ensureCapacity(6);
            buffer.put(point.x).put(point.y).put(point.z)
                    .put(WIRE_COLOR.x).put(WIRE_COLOR.y).put(WIRE_COLOR.z);
        }

        private void ensureCapacity(int floats) {
            if (buffer.remaining() >= floats) {
                return;
            }
            FloatBuffer grown = BufferUtils.createFloatBuffer(Math.max(buffer.capacity() * 2, buffer.capacity() + floats));
            buffer.flip();
            grown.put(buffer);
            buffer = grown;
        }

        FloatBuffer finish() {
            buffer.flip();
            return buffer;
        }

        int sphereSegments() {
            return SPHERE_SEGMENTS;
        }
    }
}
