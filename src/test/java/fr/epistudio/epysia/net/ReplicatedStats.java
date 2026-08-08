package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.net.replication.Replicated;
import fr.epistudio.epysia.net.replication.ReplicationCondition;
import org.joml.Vector3f;

@EpysiaComponent(name = "Replicated Stats", category = "Testing")
public final class ReplicatedStats extends Component {
    @Replicated
    private int health = 100;
    @Replicated(sendRate = 5)
    private String displayName = "";
    @Replicated(interpolate = true)
    private final Vector3f aim = new Vector3f();
    @Replicated(condition = ReplicationCondition.OWNER_ONLY)
    private int ammunition = 30;

    public int health() {
        return health;
    }

    public ReplicatedStats setHealth(int value) {
        this.health = value;
        return this;
    }

    public String displayName() {
        return displayName;
    }

    public ReplicatedStats setDisplayName(String value) {
        this.displayName = value;
        return this;
    }

    public Vector3f aim() {
        return aim;
    }

    public int ammunition() {
        return ammunition;
    }

    public ReplicatedStats setAmmunition(int value) {
        this.ammunition = value;
        return this;
    }
}
