package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.net.replication.Replicated;
import fr.epistudio.epysia.net.replication.ReplicationTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class ReplicationTableHashTest {
    @Test
    void identicalTablesHashIdentically() {
        int first = ReplicationTable.builder().addComponentType(ReplicatedStats.class).build().hash();
        int second = ReplicationTable.builder().addComponentType(ReplicatedStats.class).build().hash();
        assertEquals(first, second);
    }

    @Test
    void anExtraFieldChangesTheHash() {
        int narrow = ReplicationTable.builder().addComponentType(ReplicatedStats.class).build().hash();
        int wide = ReplicationTable.builder()
                .addComponentType(ReplicatedStats.class)
                .addComponentType(ExtraField.class)
                .build()
                .hash();
        assertNotEquals(narrow, wide);
    }

    @Test
    void registrationOrderDoesNotChangeTheHash() {
        int forward = ReplicationTable.builder()
                .addComponentType(ReplicatedStats.class)
                .addComponentType(ExtraField.class)
                .build()
                .hash();
        int reversed = ReplicationTable.builder()
                .addComponentType(ExtraField.class)
                .addComponentType(ReplicatedStats.class)
                .build()
                .hash();
        assertEquals(forward, reversed);
    }

    static final class ExtraField extends Component {
        @Replicated
        private float charge;

        float charge() {
            return charge;
        }
    }
}
