import fr.epistudio.epysia.EngineServices
import fr.epistudio.epysia.components.EpysiaComponent
import fr.epistudio.epysia.components.Export
import fr.epistudio.epysia.input.InputState
import fr.epistudio.epysia.scripting.Behaviour

@EpysiaComponent(name = "{{ClassName}}", category = "Scripts")
class {{ClassName}} : Behaviour() {

    @field:Export(label = "Speed")
    private var speed = 1.0f

    override fun onStart(services: EngineServices) {
    }

    override fun onUpdate(input: InputState, deltaTimeSeconds: Float) {
    }
}
