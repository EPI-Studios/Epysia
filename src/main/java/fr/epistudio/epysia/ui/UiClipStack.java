package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.ScissorRect;

import java.util.ArrayDeque;
import java.util.Deque;

public final class UiClipStack {
    private final Deque<UiRect> stack = new ArrayDeque<>();
    private UiRect viewport = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private float scale = 1.0f;

    public void reset(UiRect newViewport) {
        stack.clear();
        viewport = newViewport;
        scale = 1.0f;
    }

    public void setScale(float newScale) {
        this.scale = newScale;
    }

    public boolean push(UiRect rect) {
        UiRect clipped = intersect(stack.isEmpty() ? viewport : stack.peek(), rect);
        stack.push(clipped);
        return true;
    }

    public void pop() {
        stack.pop();
    }

    public ScissorRect current() {
        if (stack.isEmpty()) {
            return ScissorRect.disabled();
        }
        UiRect rect = stack.peek();
        return ScissorRect.of(Math.round(rect.x() * scale), Math.round(rect.y() * scale),
                Math.round(rect.width() * scale), Math.round(rect.height() * scale));
    }

    public boolean contains(float pointX, float pointY) {
        return stack.isEmpty() || stack.peek().contains(pointX, pointY);
    }

    private static UiRect intersect(UiRect first, UiRect second) {
        float minX = Math.max(first.x(), second.x());
        float minY = Math.max(first.y(), second.y());
        float maxX = Math.min(first.x() + first.width(), second.x() + second.width());
        float maxY = Math.min(first.y() + first.height(), second.y() + second.height());
        return new UiRect(minX, minY, Math.max(0.0f, maxX - minX), Math.max(0.0f, maxY - minY));
    }
}
