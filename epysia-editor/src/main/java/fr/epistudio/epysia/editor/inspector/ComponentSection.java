package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;

public interface ComponentSection {

    boolean handles(IComponent component);

    void render(GameObject gameObject, IComponent component);
}
