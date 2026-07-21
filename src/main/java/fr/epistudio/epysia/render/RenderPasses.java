package fr.epistudio.epysia.render;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RenderPasses {

    public static final int PRE_3D_ORDER = 100;
    public static final int OPAQUE_3D_ORDER = 200;
    public static final int TRANSPARENT_3D_ORDER = 300;
    public static final int WORLD_2D_ORDER = 400;
    public static final int POST_ORDER = 500;
    public static final int UI_ORDER = 600;

    private static final List<RenderPass> registered = new ArrayList<>();
    private static final Map<String, RenderPass> byName = new HashMap<>();
    private static List<RenderPass> orderedCache = List.of();

    public static final RenderPass PRE_3D = register("PRE_3D", PRE_3D_ORDER);
    public static final RenderPass OPAQUE_3D = register("OPAQUE_3D", OPAQUE_3D_ORDER);
    public static final RenderPass TRANSPARENT_3D = register("TRANSPARENT_3D", TRANSPARENT_3D_ORDER);
    public static final RenderPass WORLD_2D = register("WORLD_2D", WORLD_2D_ORDER);
    public static final RenderPass POST = register("POST", POST_ORDER);
    public static final RenderPass UI = register("UI", UI_ORDER);

    private RenderPasses() {
    }

    public static synchronized RenderPass register(String name, int order) {
        RenderPass existing = byName.get(name);
        if (existing != null) {
            if (existing.order() != order) {
                throw new EpysiaException("Render pass '" + name + "' already registered at order "
                        + existing.order() + ", cannot re-register at order " + order);
            }
            return existing;
        }
        RenderPass pass = new RenderPass(name, order, registered.size());
        registered.add(pass);
        byName.put(name, pass);
        orderedCache = registered.stream()
                .sorted(Comparator.comparingInt(RenderPass::order).thenComparingInt(RenderPass::index))
                .toList();
        return pass;
    }

    public static synchronized List<RenderPass> ordered() {
        return orderedCache;
    }

    public static synchronized int capacity() {
        return registered.size();
    }

    public static synchronized RenderPass byName(String name) {
        RenderPass pass = byName.get(name);
        if (pass == null) {
            throw new EpysiaException("Unknown render pass: " + name);
        }
        return pass;
    }
}
