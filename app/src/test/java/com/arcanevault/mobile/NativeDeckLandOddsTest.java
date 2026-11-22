package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public final class NativeDeckLandOddsTest {
    @Test public void edgeCases(){
        assertEquals(0.0,NativeDeckLandOdds.probability(0,3),0.000001);
        assertEquals(1.0,NativeDeckLandOdds.probability(99,3),0.000001);
    }

    @Test public void moreLandsImproveNaturalLandDropOdds(){
        double low=NativeDeckLandOdds.probability(28,3);
        double typical=NativeDeckLandOdds.probability(36,3);
        double high=NativeDeckLandOdds.probability(44,3);
        assertTrue(low>0&&low<1);
        assertTrue(low<typical);
        assertTrue(typical<high);
        assertTrue(high<1);
    }

    @Test public void rejectsInvalidCountsAndTurns(){
        try{
            NativeDeckLandOdds.probability(100,3);
            fail("Too many lands should be invalid");
        }catch(IllegalArgumentException expected){}
        try{
            NativeDeckLandOdds.probability(35,8);
            fail("Unsupported turn should be invalid");
        }catch(IllegalArgumentException expected){}
    }
}
