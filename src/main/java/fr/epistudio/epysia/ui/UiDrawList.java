package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.ScissorRect;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class UiDrawList {
    public static final int VERTEX_BYTES = 32;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int INDICES_PER_QUAD = 6;

    private static final int INITIAL_QUADS = 512;

    public static final class Command {
        private final PipelineHandle pipeline;
        private final TextureHandle texture;
        private final ScissorRect scissor;
        private final int firstIndex;
        private int indexCount;

        private Command(PipelineHandle pipeline, TextureHandle texture, ScissorRect scissor, int firstIndex) {
            this.pipeline = pipeline;
            this.texture = texture;
            this.scissor = scissor;
            this.firstIndex = firstIndex;
        }

        public PipelineHandle pipeline() {
            return pipeline;
        }

        public TextureHandle texture() {
            return texture;
        }

        public ScissorRect scissor() {
            return scissor;
        }

        public int firstIndex() {
            return firstIndex;
        }

        public int indexCount() {
            return indexCount;
        }

        private boolean matches(PipelineHandle otherPipeline, TextureHandle otherTexture, ScissorRect otherScissor) {
            return pipeline.equals(otherPipeline) && texture.equals(otherTexture) && scissor.equals(otherScissor);
        }
    }

    private final List<Command> commands = new ArrayList<>();
    private ByteBuffer vertices = BufferUtils.createByteBuffer(INITIAL_QUADS * VERTICES_PER_QUAD * VERTEX_BYTES);
    private ByteBuffer indices = BufferUtils.createByteBuffer(INITIAL_QUADS * INDICES_PER_QUAD * Integer.BYTES);
    private int vertexCount;
    private int indexCount;
    private float scale = 1.0f;
    private final Deque<float[]> transformStack = new ArrayDeque<>();
    private float rotationCos = 1.0f;
    private float rotationSin;
    private float pivotX;
    private float pivotY;

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void pushRotation(float centerX, float centerY, float radians) {
        transformStack.push(new float[]{rotationCos, rotationSin, pivotX, pivotY});
        rotationCos = (float) Math.cos(radians);
        rotationSin = (float) Math.sin(radians);
        pivotX = centerX;
        pivotY = centerY;
    }

    public void popTransform() {
        float[] restored = transformStack.poll();
        if (restored == null) {
            return;
        }
        rotationCos = restored[0];
        rotationSin = restored[1];
        pivotX = restored[2];
        pivotY = restored[3];
    }

    public boolean isRotated() {
        return rotationSin != 0.0f || rotationCos != 1.0f;
    }

    public void clear() {
        transformStack.clear();
        rotationCos = 1.0f;
        rotationSin = 0.0f;
        pivotX = 0.0f;
        pivotY = 0.0f;
        scale = 1.0f;
        commands.clear();
        vertices.clear();
        indices.clear();
        vertexCount = 0;
        indexCount = 0;
    }

    public List<Command> commands() {
        return commands;
    }

    public boolean isEmpty() {
        return indexCount == 0;
    }

    public ByteBuffer vertexData() {
        return vertices.duplicate().position(0).limit(vertexCount * VERTEX_BYTES);
    }

    public ByteBuffer indexData() {
        return indices.duplicate().position(0).limit(indexCount * Integer.BYTES);
    }

    public void setState(PipelineHandle pipeline, TextureHandle texture, ScissorRect scissor) {
        if (!commands.isEmpty()) {
            Command last = commands.get(commands.size() - 1);
            if (last.matches(pipeline, texture, scissor)) {
                return;
            }
            if (last.indexCount == 0) {
                commands.remove(commands.size() - 1);
            }
        }
        commands.add(new Command(pipeline, texture, scissor, indexCount));
    }

    public void addQuad(UiRect rect, float uvMinX, float uvMinY, float uvMaxX, float uvMaxY, UiColor color) {
        float maxX = rect.x() + rect.width();
        float maxY = rect.y() + rect.height();
        addQuadCorners(rect.x(), rect.y(), maxX, maxY, uvMinX, uvMinY, uvMaxX, uvMaxY, color);
    }

    public void addQuadCorners(float minX, float minY, float maxX, float maxY,
                               float uvMinX, float uvMinY, float uvMaxX, float uvMaxY, UiColor color) {
        reserve();
        int base = vertexCount;
        appendVertex(minX, minY, uvMinX, uvMinY, color);
        appendVertex(maxX, minY, uvMaxX, uvMinY, color);
        appendVertex(maxX, maxY, uvMaxX, uvMaxY, color);
        appendVertex(minX, maxY, uvMinX, uvMaxY, color);
        appendIndices(base);
    }

    public void addConvexPolygon(float[] points, float fringe, UiColor color) {
        int pointCount = points.length / 2;
        reserve(pointCount * 2, (pointCount - 2) * 3 + pointCount * 6);
        float[] normals = vertexNormals(points, false);
        int inner = vertexCount;
        appendOffsetRing(points, normals, -fringe * 0.5f, color, color.alpha());
        int outer = vertexCount;
        appendOffsetRing(points, normals, fringe * 0.5f, color, 0.0f);
        for (int index = 2; index < pointCount; index++) {
            appendTriangle(inner, inner + index - 1, inner + index);
        }
        appendBand(inner, outer, pointCount);
    }

    public void addRing(float[] outerPoints, float[] innerPoints, float fringe, UiColor color) {
        int pointCount = outerPoints.length / 2;
        reserve(pointCount * 4, pointCount * 18);
        float[] outerNormals = vertexNormals(outerPoints, false);
        float[] innerNormals = vertexNormals(innerPoints, true);
        int outerFade = vertexCount;
        appendOffsetRing(outerPoints, outerNormals, fringe * 0.5f, color, 0.0f);
        int outerSolid = vertexCount;
        appendOffsetRing(outerPoints, outerNormals, -fringe * 0.5f, color, color.alpha());
        int innerSolid = vertexCount;
        appendOffsetRing(innerPoints, innerNormals, -fringe * 0.5f, color, color.alpha());
        int innerFade = vertexCount;
        appendOffsetRing(innerPoints, innerNormals, fringe * 0.5f, color, 0.0f);
        appendBand(outerSolid, outerFade, pointCount);
        appendBand(innerSolid, outerSolid, pointCount);
        appendBand(innerFade, innerSolid, pointCount);
    }

    private void appendOffsetRing(float[] points, float[] normals, float distance, UiColor color, float alpha) {
        UiColor faded = new UiColor(color.red(), color.green(), color.blue(), alpha);
        for (int index = 0; index < points.length; index += 2) {
            float x = points[index] + normals[index] * distance;
            float y = points[index + 1] + normals[index + 1] * distance;
            appendVertex(x, y, 0.5f, 0.5f, faded);
        }
    }

    private void appendBand(int firstRing, int secondRing, int pointCount) {
        for (int index = 0; index < pointCount; index++) {
            int next = (index + 1) % pointCount;
            appendTriangle(firstRing + index, firstRing + next, secondRing + next);
            appendTriangle(firstRing + index, secondRing + next, secondRing + index);
        }
    }

    private static float[] vertexNormals(float[] points, boolean inward) {
        int pointCount = points.length / 2;
        float[] edgeNormals = new float[pointCount * 2];
        for (int index = 0; index < pointCount; index++) {
            int next = (index + 1) % pointCount;
            float deltaX = points[next * 2] - points[index * 2];
            float deltaY = points[next * 2 + 1] - points[index * 2 + 1];
            float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            float inverse = length > 0.0f ? 1.0f / length : 0.0f;
            edgeNormals[index * 2] = deltaY * inverse;
            edgeNormals[index * 2 + 1] = -deltaX * inverse;
        }
        float sign = inward ? -1.0f : 1.0f;
        float[] normals = new float[pointCount * 2];
        for (int index = 0; index < pointCount; index++) {
            int previous = (index + pointCount - 1) % pointCount;
            float averageX = (edgeNormals[previous * 2] + edgeNormals[index * 2]) * 0.5f;
            float averageY = (edgeNormals[previous * 2 + 1] + edgeNormals[index * 2 + 1]) * 0.5f;
            float lengthSquared = Math.max(averageX * averageX + averageY * averageY, 0.000001f);
            float stretch = Math.min(1.0f / lengthSquared, 100.0f);
            normals[index * 2] = averageX * stretch * sign;
            normals[index * 2 + 1] = averageY * stretch * sign;
        }
        return normals;
    }

    private void appendTriangle(int first, int second, int third) {
        putIndex(first);
        putIndex(second);
        putIndex(third);
        if (!commands.isEmpty()) {
            commands.get(commands.size() - 1).indexCount += 3;
        }
    }

    private void appendVertex(float x, float y, float u, float v, UiColor color) {
        int offset = vertexCount * VERTEX_BYTES;
        float localX = x - pivotX;
        float localY = y - pivotY;
        float rotatedX = pivotX + localX * rotationCos - localY * rotationSin;
        float rotatedY = pivotY + localX * rotationSin + localY * rotationCos;
        vertices.putFloat(offset, rotatedX * scale).putFloat(offset + 4, rotatedY * scale)
                .putFloat(offset + 8, u).putFloat(offset + 12, v)
                .putFloat(offset + 16, color.red()).putFloat(offset + 20, color.green())
                .putFloat(offset + 24, color.blue()).putFloat(offset + 28, color.alpha());
        vertexCount++;
    }

    private void appendIndices(int baseVertex) {
        putIndex(baseVertex);
        putIndex(baseVertex + 1);
        putIndex(baseVertex + 2);
        putIndex(baseVertex);
        putIndex(baseVertex + 2);
        putIndex(baseVertex + 3);
        if (!commands.isEmpty()) {
            commands.get(commands.size() - 1).indexCount += INDICES_PER_QUAD;
        }
    }

    private void putIndex(int value) {
        indices.putInt(indexCount * Integer.BYTES, value);
        indexCount++;
    }

    private void reserve() {
        reserve(VERTICES_PER_QUAD, INDICES_PER_QUAD);
    }

    private void reserve(int extraVertices, int extraIndices) {
        while ((vertexCount + extraVertices) * VERTEX_BYTES > vertices.capacity()) {
            vertices = grow(vertices, vertexCount * VERTEX_BYTES);
        }
        while ((indexCount + extraIndices) * Integer.BYTES > indices.capacity()) {
            indices = grow(indices, indexCount * Integer.BYTES);
        }
    }

    private static ByteBuffer grow(ByteBuffer source, int usedBytes) {
        ByteBuffer enlarged = BufferUtils.createByteBuffer(source.capacity() * 2);
        enlarged.put(0, source, 0, usedBytes);
        return enlarged;
    }
}
