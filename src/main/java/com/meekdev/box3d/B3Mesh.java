package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3MeshDef;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** baked triangle mesh, reusable across worlds

    meshes belong on static or kinematic bodies, a dynamic body with only a
    mesh gets no mass, use hulls or a decomposition for dynamic props */
public final class B3Mesh implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment data;
    private boolean closed;

    private B3Mesh(Arena arena, MemorySegment data) {
        this.arena = arena;
        this.data = data;
    }

    // vertices are xyz triples, indices are 3 per triangle
    public static B3Mesh bake(float[] vertices, int[] indices) {
        return bake(vertices, indices, null);
    }

    // material indices are one byte per triangle, they select from the shape's material list
    public static B3Mesh bake(float[] vertices, int[] indices, byte[] materialIndices) {
        if (vertices.length % 3 != 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("vertices and indices must come in triples");
        }
        NativeLoader.load();

        Arena arena = Arena.ofConfined();
        try (Arena temp = Arena.ofConfined()) {
            int vertexCount = vertices.length / 3;
            int triangleCount = indices.length / 3;

            MemorySegment verts = temp.allocate(b3Vec3.layout(), vertexCount);
            for (int i = 0; i < vertexCount; i++) {
                MemorySegment v = b3Vec3.asSlice(verts, i);
                b3Vec3.x(v, vertices[i * 3]);
                b3Vec3.y(v, vertices[i * 3 + 1]);
                b3Vec3.z(v, vertices[i * 3 + 2]);
            }
            MemorySegment idx = temp.allocateFrom(ValueLayout.JAVA_INT, indices);

            MemorySegment def = temp.allocate(b3MeshDef.layout());
            b3MeshDef.vertices(def, verts);
            b3MeshDef.indices(def, idx);
            b3MeshDef.materialIndices(def, materialIndices == null ? MemorySegment.NULL
                    : temp.allocateFrom(ValueLayout.JAVA_BYTE, materialIndices));
            b3MeshDef.weldTolerance(def, 0f);
            b3MeshDef.vertexCount(def, vertexCount);
            b3MeshDef.triangleCount(def, triangleCount);
            b3MeshDef.weldVertices(def, false);
            b3MeshDef.useMedianSplit(def, false);
            // adjacency info keeps movers from catching on internal edges
            b3MeshDef.identifyEdges(def, true);

            MemorySegment degenerate = temp.allocate(ValueLayout.JAVA_INT, triangleCount);
            MemorySegment data = box3d_h.b3CreateMesh(def, degenerate, triangleCount);
            if (data.equals(MemorySegment.NULL)) {
                arena.close();
                throw new IllegalArgumentException("mesh bake failed");
            }
            return new B3Mesh(arena, data);
        }
    }

    MemorySegment data() {
        if (closed) {
            throw new IllegalStateException("mesh already destroyed");
        }
        return data;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        box3d_h.b3DestroyMesh(data);
        arena.close();
        closed = true;
    }
}
