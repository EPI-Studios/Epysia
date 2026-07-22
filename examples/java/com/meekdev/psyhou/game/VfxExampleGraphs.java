package com.meekdev.psyhou.game;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.vfx.VfxGraphCompiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class VfxExampleGraphs {

    public static final List<String> NAMES = List.of("Fire", "Smoke", "Sparks", "MagicSwirl",
            "ThunderStrike");

    private static final String DIRECTORY = "examples/resources/vfx/";
    private static final String EXTENSION = ".epygraph";

    private VfxExampleGraphs() {
    }

    public static Path fileOf(String name) {
        Path file = Path.of(DIRECTORY + name + EXTENSION).toAbsolutePath();
        if (!Files.isRegularFile(file)) {
            throw new EpysiaException("Example VFX graph " + name + " is missing at " + file + ".");
        }
        return file;
    }

    public static void validateAll() {
        GraphNodeRegistry registry = GraphNodeRegistry.withBuiltins();
        VfxGraphCompiler compiler = new VfxGraphCompiler(shaderSource("particle_common.glsl"),
                shaderSource("particle_shapes.glsl"), shaderSource("particle_noise.glsl"));
        for (String name : NAMES) {
            validate(registry, compiler, name);
        }
    }

    private static void validate(GraphNodeRegistry registry, VfxGraphCompiler compiler, String name) {
        GraphAsset asset = read(name);
        if (asset.kind() != GraphKind.VFX) {
            throw new EpysiaException(name + " is not a VFX graph.");
        }
        checkNodeTypes(registry, asset, name);
        checkEdges(registry, asset, name);
        VfxGraphCompiler.VfxCompiledSources sources = compiler.compile(asset, name + EXTENSION);
        System.out.println("[vfx-examples] " + name + " compiled, spawn rate "
                + sources.spawnRatePerSecond() + "/s, spawn source "
                + sources.spawnCompute().length() + " chars");
    }

    private static void checkNodeTypes(GraphNodeRegistry registry, GraphAsset asset, String name) {
        for (GraphNode node : asset.nodes()) {
            if (registry.find(node.typeKey()).isEmpty()) {
                throw new EpysiaException(name + " uses unknown node type " + node.typeKey() + ".");
            }
        }
    }

    private static void checkEdges(GraphNodeRegistry registry, GraphAsset asset, String name) {
        for (GraphEdge edge : asset.edges()) {
            requirePin(registry, asset, edge.fromNode(), edge.fromPin(), true, name);
            requirePin(registry, asset, edge.toNode(), edge.toPin(), false, name);
        }
    }

    private static void requirePin(GraphNodeRegistry registry, GraphAsset asset, int nodeId,
                                   String pinName, boolean output, String name) {
        Optional<GraphNode> node = asset.findNode(nodeId);
        if (node.isEmpty()) {
            throw new EpysiaException(name + " has an edge on missing node " + nodeId + ".");
        }
        NodeDefinition definition = registry.find(node.get().typeKey())
                .orElseThrow(() -> new EpysiaException(name + " uses unknown node type."));
        List<PinDefinition> pins = output ? definition.outputPins() : definition.inputPins();
        if (pins.stream().noneMatch(pin -> pin.name().equals(pinName))) {
            throw new EpysiaException(name + " wires pin " + pinName + " which node "
                    + nodeId + " does not have.");
        }
    }

    private static GraphAsset read(String name) {
        try {
            return new GraphJsonCodec().read(Files.readString(fileOf(name)));
        } catch (IOException failure) {
            throw new EpysiaException("Failed to read " + name + EXTENSION + ".", failure);
        }
    }

    private static String shaderSource(String fileName) {
        try (InputStream stream = VfxExampleGraphs.class.getResourceAsStream("/shaders/vfx/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new EpysiaException("Failed to read shader " + fileName + ".", failure);
        }
    }
}
