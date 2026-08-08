package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.session.ConnectionBudget;
import fr.epistudio.epysia.net.session.JoinToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConnectionBudgetTest {
    private static final int PACKET_LIMIT = 10;
    private static final int BYTE_LIMIT = 1_000;
    private static final float ONE_SECOND = 1.0f;

    @Test
    void packetsWithinTheBudgetAreAccepted() {
        ConnectionBudget budget = new ConnectionBudget(PACKET_LIMIT, BYTE_LIMIT);
        for (int packet = 0; packet < PACKET_LIMIT; packet++) {
            assertTrue(budget.accept(10), "packet " + packet + " should fit the budget");
        }
        assertFalse(budget.accept(10), "the packet past the limit should be refused");
    }

    @Test
    void aByteFloodIsRefusedEvenWithFewPackets() {
        ConnectionBudget budget = new ConnectionBudget(PACKET_LIMIT, BYTE_LIMIT);
        assertTrue(budget.accept(900));
        assertFalse(budget.accept(900));
    }

    @Test
    void theBudgetRefillsEachWindow() {
        ConnectionBudget budget = new ConnectionBudget(PACKET_LIMIT, BYTE_LIMIT);
        for (int packet = 0; packet <= PACKET_LIMIT; packet++) {
            budget.accept(10);
        }
        budget.advance(ONE_SECOND);
        assertTrue(budget.accept(10), "a new window should accept traffic again");
    }

    @Test
    void sustainedBreachesMarkTheConnectionAbusive() {
        ConnectionBudget budget = new ConnectionBudget(PACKET_LIMIT, BYTE_LIMIT);
        for (int window = 0; window < 3; window++) {
            for (int packet = 0; packet <= PACKET_LIMIT; packet++) {
                budget.accept(10);
            }
            budget.advance(ONE_SECOND);
        }
        assertTrue(budget.isAbusive());
        assertEquals(3L, budget.droppedPackets());
    }

    @Test
    void oneBadWindowAloneIsNotAbusive() {
        ConnectionBudget budget = new ConnectionBudget(PACKET_LIMIT, BYTE_LIMIT);
        for (int packet = 0; packet <= PACKET_LIMIT; packet++) {
            budget.accept(10);
        }
        budget.advance(ONE_SECOND);
        assertFalse(budget.isAbusive());
    }

    @Test
    void theJoinTokenSeparatesSecretsAndIsAbsentWhenUnset() {
        assertEquals(JoinToken.ABSENT, JoinToken.of(""));
        assertNotEquals(JoinToken.of("alpha"), JoinToken.of("beta"));
        assertEquals(JoinToken.of("alpha"), JoinToken.of("alpha"));
    }
}
