package fr.epistudio.epysia.render.backend;

public enum VertexFormat {
    FLOAT(1, 4),
    FLOAT2(2, 8),
    FLOAT3(3, 12),
    FLOAT4(4, 16),
    UINT16X4(4, 8, true),
    UINT32(1, 4, true);

    private final int componentCount;
    private final int byteSize;
    private final boolean integer;

    VertexFormat(int componentCount, int byteSize) {
        this(componentCount, byteSize, false);
    }

    VertexFormat(int componentCount, int byteSize, boolean integer) {
        this.componentCount = componentCount;
        this.byteSize = byteSize;
        this.integer = integer;
    }

    public int componentCount() {
        return componentCount;
    }

    public int byteSize() {
        return byteSize;
    }

    public boolean integer() {
        return integer;
    }

    public boolean wideInteger() {
        return this == UINT32;
    }
}
