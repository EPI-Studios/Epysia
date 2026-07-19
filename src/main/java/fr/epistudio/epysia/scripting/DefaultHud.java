package fr.epistudio.epysia.scripting;

import java.util.ArrayList;
import java.util.List;

public final class DefaultHud implements Hud {

    public record Entry(float x, float y, String message) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void text(float x, float y, String message) {
        if (message != null && !message.isEmpty()) {
            entries.add(new Entry(x, y, message));
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public void clear() {
        entries.clear();
    }
}
