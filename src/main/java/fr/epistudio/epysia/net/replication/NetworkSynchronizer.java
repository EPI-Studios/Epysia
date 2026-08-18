package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;

import java.util.ArrayList;
import java.util.List;

@EpysiaComponent(name = "Network Synchronizer", category = "Networking",
        description = "Replicates the fields you list from the server to every client.")
@RequiresComponent(NetworkObject.class)
public final class NetworkSynchronizer extends Component {
    @Export(label = "Properties")
    private final List<SynchronizedProperty> properties = new ArrayList<>();

    public List<SynchronizedProperty> properties() {
        return properties;
    }

    public NetworkSynchronizer synchronize(Class<?> componentType, String fieldName) {
        properties.add(new SynchronizedProperty()
                .setComponentType(componentType.getName())
                .setFieldName(fieldName));
        return this;
    }
}
