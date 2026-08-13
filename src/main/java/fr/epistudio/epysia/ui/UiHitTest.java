package fr.epistudio.epysia.ui;

import java.util.List;
public final class UiHitTest {
    private UiHitTest() {
    }

    public static UiHit topmost(UiElement element, float pointX, float pointY, UiRect clip) {
        if (!element.drawable() || !clip.contains(pointX, pointY)) {
            return UiHit.none();
        }
        if (element.rotated()) {
            return rotatedTopmost(element, pointX, pointY, clip);
        }
        UiRect childClip = element.clipChildren() ? intersect(clip, element.computedRect()) : clip;
        List<UiElement> children = element.children();
        for (int index = children.size() - 1; index >= 0; index--) {
            UiHit found = topmost(children.get(index), pointX, pointY, childClip);
            if (found.element() != null) {
                return found;
            }
        }
        if (!element.interactive() || !element.hitRect().contains(pointX, pointY)) {
            return UiHit.none();
        }
        return new UiHit(element, pointX - element.computedRect().x(),
                pointY - element.computedRect().y());
    }

    private static UiHit rotatedTopmost(UiElement element, float pointX, float pointY, UiRect clip) {
        UiRect rect = element.computedRect();
        float centerX = rect.x() + rect.width() * 0.5f;
        float centerY = rect.y() + rect.height() * 0.5f;
        float angle = -element.rotationRadians();
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float localX = pointX - centerX;
        float localY = pointY - centerY;
        float unrotatedX = centerX + localX * cos - localY * sin;
        float unrotatedY = centerY + localX * sin + localY * cos;
        UiHit found = untransformedTopmost(element, unrotatedX, unrotatedY, clip);
        return found.element() == null ? found : found;
    }

    private static UiHit untransformedTopmost(UiElement element, float pointX, float pointY, UiRect clip) {
        UiRect childClip = element.clipChildren() ? intersect(clip, element.computedRect()) : clip;
        List<UiElement> children = element.children();
        for (int index = children.size() - 1; index >= 0; index--) {
            UiHit found = topmost(children.get(index), pointX, pointY, childClip);
            if (found.element() != null) {
                return found;
            }
        }
        if (!element.interactive() || !element.hitRect().contains(pointX, pointY)) {
            return UiHit.none();
        }
        return new UiHit(element, pointX - element.computedRect().x(),
                pointY - element.computedRect().y());
    }

    private static UiRect intersect(UiRect first, UiRect second) {
        float minX = Math.max(first.x(), second.x());
        float minY = Math.max(first.y(), second.y());
        float maxX = Math.min(first.x() + first.width(), second.x() + second.width());
        float maxY = Math.min(first.y() + first.height(), second.y() + second.height());
        return new UiRect(minX, minY, Math.max(0.0f, maxX - minX), Math.max(0.0f, maxY - minY));
    }
}
