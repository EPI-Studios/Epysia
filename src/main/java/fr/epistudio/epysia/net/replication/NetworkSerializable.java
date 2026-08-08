package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;

public interface NetworkSerializable {
    void writeState(NetWriter writer);

    void readState(NetReader reader);
}
