package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scripting.PhysicsEventListener;
import org.joml.Vector3fc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@EpysiaComponent(name = "Graph", category = "Logic")
public final class GraphComponent extends Component implements PhysicsEventListener {

    @Export(label = "Graph Path")
    private String graphPath = "";
    private final Map<String, Object> variableOverrides = new LinkedHashMap<>();
    private transient GraphRuntimeLink runtimeLink;

    public String graphPath() {
        return graphPath;
    }

    public void setGraphPath(String graphPath) {
        this.graphPath = graphPath == null ? "" : graphPath;
    }

    public Map<String, Object> variableOverrides() {
        return variableOverrides;
    }

    public void attachRuntime(GraphRuntimeLink link) {
        this.runtimeLink = link;
    }

    public void detachRuntime() {
        this.runtimeLink = null;
    }

    public Optional<GraphInstance> runtimeInstance() {
        return runtimeLink == null ? Optional.empty() : Optional.of(runtimeLink.instance());
    }

    @Override
    public void onTriggerEnter(GameObject other) {
        fire(BuiltinNodes.EVENT_ON_TRIGGER_ENTER, other);
    }

    @Override
    public void onTriggerExit(GameObject other) {
        fire(BuiltinNodes.EVENT_ON_TRIGGER_EXIT, other);
    }

    @Override
    public void onCollision(GameObject other, Vector3fc point, Vector3fc normal, float impulse) {
        fire(BuiltinNodes.EVENT_ON_COLLISION, other);
    }

    private void fire(String typeKey, GameObject other) {
        if (runtimeLink != null) {
            runtimeLink.fire(typeKey, Map.of(BuiltinNodes.OTHER_PIN, other));
        }
    }
}
