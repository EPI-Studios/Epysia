package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.IComponent;

public interface EditorTickable extends IComponent {
    void onEditorUpdate(EngineServices services, float deltaTimeSeconds);
}
