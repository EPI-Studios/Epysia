package fr.epistudio.epysia.editor.ui;

import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiDir;
import imgui.internal.flag.ImGuiDockNodeFlags;
import imgui.type.ImInt;

public final class DockLayout {

    private static final String DOCKSPACE_ID = "EpysiaDockSpace";
    private static final float LEFT_RATIO = 0.18f;
    private static final float RIGHT_RATIO = 0.24f;
    private static final float BOTTOM_RATIO = 0.28f;

    private boolean layoutRequested;

    public void requestDefaultLayout() {
        layoutRequested = true;
    }

    public int dockspaceId() {
        return ImGui.getID(DOCKSPACE_ID);
    }

    public void buildIfRequested(ImGuiViewport viewport) {
        if (!layoutRequested) {
            return;
        }
        layoutRequested = false;
        int rootId = dockspaceId();
        imgui.internal.ImGui.dockBuilderRemoveNode(rootId);
        imgui.internal.ImGui.dockBuilderAddNode(rootId, ImGuiDockNodeFlags.DockSpace);
        imgui.internal.ImGui.dockBuilderSetNodeSize(rootId, viewport.getWorkSizeX(), viewport.getWorkSizeY());
        splitAndDock(rootId);
        imgui.internal.ImGui.dockBuilderFinish(rootId);
    }

    private void splitAndDock(int rootId) {
        ImInt centerId = new ImInt(rootId);
        ImInt leftId = new ImInt();
        ImInt rightId = new ImInt();
        ImInt bottomId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(centerId.get(), ImGuiDir.Left, LEFT_RATIO, leftId, centerId);
        imgui.internal.ImGui.dockBuilderSplitNode(centerId.get(), ImGuiDir.Right, RIGHT_RATIO, rightId, centerId);
        imgui.internal.ImGui.dockBuilderSplitNode(centerId.get(), ImGuiDir.Down, BOTTOM_RATIO, bottomId, centerId);
        imgui.internal.ImGui.dockBuilderDockWindow(HierarchyView.WINDOW_TITLE, leftId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(InspectorView.WINDOW_TITLE, rightId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(ViewportView.WINDOW_TITLE, centerId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(ScriptEditorView.WINDOW_TITLE, centerId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(ConsoleView.WINDOW_TITLE, bottomId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(AssetBrowserView.WINDOW_TITLE, bottomId.get());
    }
}
