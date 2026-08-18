package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.replication.NetworkObject;

@EpysiaComponent(name = "Voice Chat", category = "Networking",
        description = "Captures the owner's microphone and plays other peers back in 3D.")
@RequiresComponent(NetworkObject.class)
public final class VoiceChatComponent extends Component {
    @Export(label = "Follow Network Owner")
    private boolean followNetworkOwner = true;
    @Export(label = "Speaker Peer", step = 1.0f)
    private int speakerPeer = NetworkObject.SERVER_PEER;
    @Export(label = "Gain", min = 0.0f, max = 2.0f)
    private float gain = 1.0f;

    public int effectiveSpeakerPeer() {
        if (!followNetworkOwner) {
            return speakerPeer;
        }
        GameObject owner = ownerOrNull();
        if (owner == null) {
            return speakerPeer;
        }
        NetworkObject networkObject = owner.getComponentOrNull(NetworkObject.class);
        return networkObject == null ? speakerPeer : networkObject.ownerPeer();
    }

    public float gain() {
        return gain;
    }

    public VoiceChatComponent setGain(float value) {
        this.gain = Math.max(0.0f, value);
        return this;
    }

    public VoiceChatComponent setSpeakerPeer(int peer) {
        this.followNetworkOwner = false;
        this.speakerPeer = peer;
        return this;
    }
}
