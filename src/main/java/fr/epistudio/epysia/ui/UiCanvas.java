package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import fr.epistudio.epysia.components.Component;

@EpysiaComponent(name = "Ui Canvas", category = "UI")
@RequiresComponent(Transform3D.class)
public final class UiCanvas extends Component {
    @Export(label = "Reference width", min = 0.0f, max = 7680.0f, step = 1.0f)
    private float referenceWidth;
    @Export(label = "Reference height", min = 0.0f, max = 4320.0f, step = 1.0f)
    private float referenceHeight;
    @Export(label = "Visible")
    private boolean visible = true;

    private float scaleFactor = 1.0f;
    private UiRect viewport = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    public boolean visible() {
        return visible;
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

    public void layout(float width, float height) {
        scaleFactor = computeScaleFactor(width, height);
        viewport = new UiRect(0.0f, 0.0f, width / scaleFactor, height / scaleFactor);
        for (UiElement root : roots()) {
            root.layout(viewport);
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
