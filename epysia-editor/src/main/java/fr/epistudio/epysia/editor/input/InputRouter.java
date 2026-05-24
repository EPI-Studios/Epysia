package fr.epistudio.epysia.editor.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class InputRouter {

    private final List<InputLayer> layers = new ArrayList<>();
    private boolean dirty;

    public void addLayer(InputLayer layer) {
        if (layer == null || layers.contains(layer)) {
            return;
        }
        layers.add(layer);
        dirty = true;
    }

    public void removeLayer(InputLayer layer) {
        layers.remove(layer);
    }

    public boolean dispatch(MouseEvent event) {
        ensureSorted();
        for (InputLayer layer : layers) {
            if (!layer.enabled()) {
                continue;
            }
            if (layer.onMouse(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean dispatch(KeyEvent event) {
        ensureSorted();
        for (InputLayer layer : layers) {
            if (!layer.enabled()) {
                continue;
            }
            if (layer.onKey(event)) {
                return true;
            }
        }
        return false;
    }

    private void ensureSorted() {
        if (!dirty) {
            return;
        }
        layers.sort(Comparator.comparingInt(InputLayer::priority).reversed());
        dirty = false;
    }
}
