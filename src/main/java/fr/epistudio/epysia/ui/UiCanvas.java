package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import fr.epistudio.epysia.components.Component;

@EpysiaComponent(name = "Ui Canvas", category = "UI",
        description = "Root of a UI tree. Sets the reference resolution its elements scale to.")
@RequiresComponent(Transform3D.class)
public final class UiCanvas extends Component {
    @Export(label = "Reference width", min = 0.0f, max = 7680.0f, step = 1.0f)
    private float referenceWidth;
    @Export(label = "Reference height", min = 0.0f, max = 4320.0f, step = 1.0f)
    private float referenceHeight;
    @Export(label = "Visible")
    private boolean visible = true;
    @Export(label = "Space")
    private UiCanvasSpace space = UiCanvasSpace.SCREEN;
    @Export(label = "World size", min = 1.0f, step = 1.0f)
    private final Vector2f worldSize = new Vector2f(400.0f, 200.0f);
    @Export(label = "Pixels per metre", min = 1.0f, step = 1.0f)
    private float pixelsPerMetre = 100.0f;
    @Export(label = "Max distance", min = 0.0f, step = 1.0f)
    private float maxDistance;
    @Export(label = "Always on top")
    private boolean alwaysOnTop;

    private float scaleFactor = 1.0f;
    private UiRect viewport = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    public boolean visible() {
        return visible;
    }

    public UiCanvasSpace space() {
        return space;
    }

    public UiCanvas setSpace(UiCanvasSpace value) {
        space = value;
        return this;
    }

    public boolean inWorld() {
        return space != UiCanvasSpace.SCREEN;
    }

    public Vector2f worldSize() {
        return worldSize;
    }

    public UiCanvas setWorldSize(float width, float height) {
        worldSize.set(Math.max(width, 1.0f), Math.max(height, 1.0f));
        return this;
    }

    public float pixelsPerMetre() {
        return Math.max(pixelsPerMetre, 1.0f);
    }

    public UiCanvas setPixelsPerMetre(float value) {
        pixelsPerMetre = Math.max(value, 1.0f);
        return this;
    }

    public float maxDistance() {
        return maxDistance;
    }

    public UiCanvas setMaxDistance(float value) {
        maxDistance = Math.max(value, 0.0f);
        return this;
    }

    public boolean alwaysOnTop() {
        return alwaysOnTop;
    }

    public UiCanvas setAlwaysOnTop(boolean value) {
        alwaysOnTop = value;
        return this;
    }

    public float scaleFactor() {
        return scaleFactor;
    }

    public UiRect viewport() {
        return viewport;
    }

    public List<UiElement> roots() {
        List<UiElement> found = new ArrayList<>();
        Optional<Transform3D> transform = owner().flatMap(owner -> owner.getComponent(Transform3D.class));
        if (transform.isEmpty()) {
            return found;
        }
        for (Transform3D child : transform.get().children()) {
            child.owner().flatMap(owner -> owner.getComponent(UiElement.class)).ifPresent(found::add);
        }
        found.sort((first, second) -> Integer.compare(first.zIndex(), second.zIndex()));
        return found;
    }

    public void layout(float width, float height, UiTextMeasurer textMeasurer) {
        scaleFactor = computeScaleFactor(width, height);
        viewport = new UiRect(0.0f, 0.0f, width / scaleFactor, height / scaleFactor);
        for (UiElement root : roots()) {
            root.layout(viewport, textMeasurer);
        }
    }

    private float computeScaleFactor(float width, float height) {
        if (referenceWidth <= 0.0f || referenceHeight <= 0.0f) {
            return 1.0f;
        }
        return Math.min(width / referenceWidth, height / referenceHeight);
    }

    public Optional<UiElement> find(String name) {
        for (UiElement root : roots()) {
            Optional<UiElement> found = findIn(root, name);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public <T extends UiElement> Optional<T> find(String name, Class<T> type) {
        return find(name).filter(type::isInstance).map(type::cast);
    }

    private Optional<UiElement> findIn(UiElement element, String name) {
        if (element.owner().map(GameObject::name).filter(name::equals).isPresent()) {
            return Optional.of(element);
        }
        for (UiElement child : element.children()) {
            Optional<UiElement> found = findIn(child, name);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
