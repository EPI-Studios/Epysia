package fr.epistudio.epysia.graph;

@FunctionalInterface
public interface NodeBehavior {

    void run(NodeContext context);
}
