package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.graph.BuiltinNodes;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.GraphVariable;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.StateNodes;
import fr.epistudio.epysia.graph.shader.ShaderNodes;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

final class GraphNodePalette {

    static final String NODE_PAYLOAD = "graph/node-type";

    private static final char PAYLOAD_SEPARATOR = '|';
    private static final int HEADER_FLAGS = ImGuiTreeNodeFlags.SpanAvailWidth;
    private static final float ENTRY_INDENT = 8.0f;

    private final String identifier;
    private final ImString filter = new ImString(128);

    GraphNodePalette(String identifier) {
        this.identifier = identifier;
    }

    void render(float width, float height, GraphAsset asset, GraphNodeRegistry registry,
                Consumer<Entry> onCreate) {
        ImGui.pushID(identifier);
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##palette-filter",
                I18n.translate(TextKey.EDITOR_GRAPH_NODE_PALETTE_SEARCH), filter);
        ImGui.beginChild("##palette-entries", width, height, false);
        renderGroups(asset, registry, onCreate);
        ImGui.endChild();
        ImGui.popID();
    }

    private void renderGroups(GraphAsset asset, GraphNodeRegistry registry, Consumer<Entry> onCreate) {
        String query = filter.get().replace("\0", "").strip().toLowerCase(Locale.ROOT);
        Map<String, List<Entry>> groups = groups(asset, registry, query);
        if (groups.isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_GRAPH_NODE_PALETTE_NO_MATCH));
            return;
        }
        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            renderGroup(group.getKey(), group.getValue(), !query.isEmpty(), onCreate);
        }
    }

    private static void renderGroup(String category, List<Entry> entries, boolean forceOpen,
                                    Consumer<Entry> onCreate) {
        if (forceOpen) {
            ImGui.setNextItemOpen(true);
        }
        if (!ImGui.collapsingHeader(category + "##palette-group-" + category, HEADER_FLAGS)) {
            return;
        }
        ImGui.indent(ENTRY_INDENT);
        for (Entry entry : entries) {
            renderEntry(entry, onCreate);
        }
        ImGui.unindent(ENTRY_INDENT);
    }

    private static void renderEntry(Entry entry, Consumer<Entry> onCreate) {
        if (ImGui.selectable(entry.displayName() + "##palette-entry-" + entry.payload())) {
            onCreate.accept(entry);
        }
        renderDragSource(entry);
    }

    private static void renderDragSource(Entry entry) {
        if (!ImGui.beginDragDropSource()) {
            return;
        }
        ImGui.setDragDropPayload(NODE_PAYLOAD, entry.payload());
        ImGui.textUnformatted(entry.displayName());
        ImGui.endDragDropSource();
    }

    private static Map<String, List<Entry>> groups(GraphAsset asset, GraphNodeRegistry registry,
                                                   String query) {
        Map<String, List<Entry>> groups = new TreeMap<>();
        for (Entry entry : variableEntries(asset)) {
            addIfMatching(groups, I18n.translate(TextKey.EDITOR_GRAPH_NODE_PALETTE_VARIABLES), entry, query);
        }
        for (NodeDefinition definition : registry.all()) {
            if (isVariableNode(definition.typeKey()) || !allowedInKind(asset.kind(), definition)) {
                continue;
            }
            addIfMatching(groups, definition.category(),
                    new Entry(definition.typeKey(), "", definition.displayName()), query);
        }
        return groups;
    }

    private static void addIfMatching(Map<String, List<Entry>> groups, String category,
                                      Entry entry, String query) {
        if (!matches(category, entry.displayName(), query)) {
            return;
        }
        groups.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
    }

    private static boolean matches(String category, String displayName, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return (category + " " + displayName).toLowerCase(Locale.ROOT).contains(query);
    }

    private static List<Entry> variableEntries(GraphAsset asset) {
        List<Entry> entries = new ArrayList<>();
        if (asset.kind().isShader()) {
            return entries;
        }
        for (GraphVariable variable : new ArrayList<>(asset.variables())) {
            entries.add(new Entry(BuiltinNodes.VARIABLE_GET, variable.name(),
                    I18n.translate(TextKey.EDITOR_GRAPH_NODE_PALETTE_GET_VARIABLE, variable.name())));
            entries.add(new Entry(BuiltinNodes.VARIABLE_SET, variable.name(),
                    I18n.translate(TextKey.EDITOR_GRAPH_NODE_PALETTE_SET_VARIABLE, variable.name())));
        }
        return entries;
    }

    private static boolean isVariableNode(String typeKey) {
        return typeKey.equals(BuiltinNodes.VARIABLE_GET) || typeKey.equals(BuiltinNodes.VARIABLE_SET);
    }

    static boolean allowedInKind(GraphKind kind, NodeDefinition definition) {
        String typeKey = definition.typeKey();
        if (kind == GraphKind.SHADER_SURFACE) {
            return ShaderNodes.isShaderNode(typeKey) && !ShaderNodes.isPostOnly(typeKey);
        }
        if (kind == GraphKind.SHADER_POST) {
            return ShaderNodes.isShaderNode(typeKey) && !ShaderNodes.isSurfaceOnly(typeKey);
        }
        if (ShaderNodes.isShaderNode(typeKey)) {
            return false;
        }
        return kind != GraphKind.LOGIC || !definition.category().equals(StateNodes.CATEGORY);
    }

    static Optional<Entry> parsePayload(String payload) {
        int separator = payload.indexOf(PAYLOAD_SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        String typeKey = payload.substring(0, separator);
        return typeKey.isEmpty()
                ? Optional.empty()
                : Optional.of(new Entry(typeKey, payload.substring(separator + 1), typeKey));
    }

    record Entry(String typeKey, String variableName, String displayName) {

        String payload() {
            return typeKey + PAYLOAD_SEPARATOR + variableName;
        }
    }
}
