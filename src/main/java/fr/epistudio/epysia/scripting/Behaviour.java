package fr.epistudio.epysia.scripting;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.input.InputState;

public abstract class Behaviour extends Component {

    public void onStart(EngineServices services) {
    }

    public void onUpdate(InputState input, float deltaTimeSeconds) {
    }

    public void onDestroy() {
    }
}
