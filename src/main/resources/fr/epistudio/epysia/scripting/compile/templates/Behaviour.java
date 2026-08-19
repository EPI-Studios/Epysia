import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scripting.Behaviour;

@EpysiaComponent(name = "{{ClassName}}", category = "Scripts")
public final class {{ClassName}} extends Behaviour {

    @Export(label = "Speed")
    private float speed = 1.0f;

    @Override
    public void onStart(EngineServices services) {
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
    }
}
