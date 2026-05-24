package fr.epistudio.epysia.render.backend;

public enum IndexFormat {
    UINT16(2),
    UINT32(4);

    private final int byteSize;

    IndexFormat(int byteSize) {
        this.byteSize = byteSize;
    }

    public int byteSize() {
        return byteSize;
    }
}
