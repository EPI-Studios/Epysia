package fr.epistudio.epysia.render.backend;

public enum VertexFormat {
    FLOAT(1, 4),
    FLOAT2(2, 8),
    FLOAT3(3, 12),
    FLOAT4(4, 16);

    private final int componentCount;
    private final int byteSize;

    VertexFormat(int componentCount, int byteSize) {
        this.componentCount = componentCount;
        this.byteSize = byteSize;
    }

    public int componentCount() {
        return componentCount;
    }

    public int byteSize() {
        return byteSize;
    }
}
