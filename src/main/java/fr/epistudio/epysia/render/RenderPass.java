package fr.epistudio.epysia.render;

public final class RenderPass {

    private final String name;
    private final int order;
    private final int index;

    RenderPass(String name, int order, int index) {
        this.name = name;
        this.order = order;
        this.index = index;
    }

    public String name() {
        return name;
    }

    public int order() {
        return order;
    }

    public int index() {
        return index;
    }

    @Override
    public String toString() {
        return name;
    }
}
