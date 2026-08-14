package fr.epistudio.epysia.profiling;

import java.util.List;

public record ProfileNode(String name, long totalNanos, long selfNanos, int calls,
                          List<ProfileNode> children) {

    public boolean isLeaf() {
        return children.isEmpty();
    }
}
