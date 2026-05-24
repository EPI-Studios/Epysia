package fr.epistudio.epysia.render.backend;

import java.util.List;

public record VertexLayout(List<VertexAttribute> attributes, int byteStride) {

    public VertexLayout {
        attributes = List.copyOf(attributes);
    }
}
