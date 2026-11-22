package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public final class NativeDeckHealthTest {
    @Test public void countsRolesFromOracleWithoutPretendingLandsAreRamp(){
        boolean[] land=NativeDeckHealth.roles("Basic Land — Island","{T}: Add {U}.");
        assertTrue(land[0]);
        assertFalse(land[1]);
        boolean[] ramp=NativeDeckHealth.roles("Artifact","{T}: Add {G}.");
        assertTrue(ramp[1]);
        assertFalse(ramp[0]);
    }
    @Test public void identifiesBroadInteractionAndDraw(){
        boolean[] roles=NativeDeckHealth.roles("Instant",
            "Destroy target creature. Draw a card.");
        assertTrue(roles[2]);
        assertTrue(roles[3]);
        assertFalse(roles[4]);
    }
    @Test public void protectsPartialAndUnknownDraftFromDeficiencyClaims(){
        assertEquals("Draft building",NativeDeckHealth.state(1,10,34,0));
        assertEquals("Provisional",NativeDeckHealth.state(0,10,99,3));
        assertEquals("Below suggested target",NativeDeckHealth.state(5,10,99,0));
        assertEquals("At starting target",NativeDeckHealth.state(10,10,99,0));
    }
    @Test public void strategyPrioritiesChangeFocus(){
        assertEquals(1,NativeDeckHealth.priority(1)[0]);
        assertEquals(2,NativeDeckHealth.priority(2)[0]);
        assertEquals(3,NativeDeckHealth.priority(3)[0]);
        assertEquals(0,NativeDeckHealth.priority(0)[0]);
    }
}
