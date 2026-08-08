package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record SpawnRecord(int networkId, String prefabGuid, int ownerPeer,
                          Vector3f position, Quaternionf rotation) {
    public void write(NetWriter writer) {
        writer.writeVarInt(networkId);
        writer.writeString(prefabGuid);
        writer.writeVarInt(ownerPeer);
        writer.writeFloat(position.x).writeFloat(position.y).writeFloat(position.z);
        writer.writeFloat(rotation.x).writeFloat(rotation.y).writeFloat(rotation.z).writeFloat(rotation.w);
    }

    public static SpawnRecord read(NetReader reader) {
        int networkId = reader.readVarInt();
        String prefabGuid = reader.readString();
        int ownerPeer = reader.readVarInt();
        Vector3f position = new Vector3f(reader.readFloat(), reader.readFloat(), reader.readFloat());
        Quaternionf rotation = new Quaternionf(reader.readFloat(), reader.readFloat(),
                reader.readFloat(), reader.readFloat());
        return new SpawnRecord(networkId, prefabGuid, ownerPeer, position, rotation);
    }

    public void applyTo(Transform3D transform) {
        transform.setPosition(position.x, position.y, position.z);
        transform.setRotation(rotation);
    }
}
