package fr.epistudio.epysia.render.mesh;

public record ArenaMesh(int vertexOffset, int vertexCount, int indexOffset, int indexCount) {

    public int vertexEnd() {
        return vertexOffset + vertexCount;
    }

    public int indexEnd() {
        return indexOffset + indexCount;
    }
}
