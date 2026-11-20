package com.arcanevault.mobile;

import static org.junit.Assert.*;
import java.util.Random;
import org.junit.Test;

public final class TabletopRandomizerTest {
    @Test public void diceStayWithinBoundsAndSumMatchesIndividualFaces(){
        Random random=new Random(1234567L);
        for(int size:new int[]{6,20,100}){
            for(int count:new int[]{1,2,12}){
                for(int repeat=0;repeat<50;repeat++){
                    TabletopRandomizer.Roll result=TabletopRandomizer.roll(count,size,random);
                    assertEquals(count,result.faces.length);
                    int total=0;
                    for(int face:result.faces){
                        assertTrue(face>=1);
                        assertTrue(face<=size);
                        total+=face;
                    }
                    assertEquals(total,result.total);
                }
            }
        }
    }

    @Test public void startingPlayerNeverLeavesConfiguredRoster(){
        Random random=new Random(43L);
        for(int n=2;n<=4;n++){
            for(int i=0;i<300;i++){
                int player=TabletopRandomizer.chooseStartingPlayer(n,random);
                assertTrue(player>=0&&player<n);
            }
        }
    }

    @Test public void coinFlipsHaveOnlyTwoPossibleOutcomes(){
        Random random=new Random(32L);
        boolean heads=false,tails=false;
        for(int i=0;i<60;i++){
            String outcome=TabletopRandomizer.flipCoin(random);
            assertTrue(outcome.equals("Heads")||outcome.equals("Tails"));
            heads|=outcome.equals("Heads");
            tails|=outcome.equals("Tails");
        }
        assertTrue(heads&&tails);
    }

    @Test public void invalidInputsAreRejected(){
        try{TabletopRandomizer.roll(0,6,new Random());fail("Zero dice");}
        catch(IllegalArgumentException expected){}
        try{TabletopRandomizer.roll(13,6,new Random());fail("Too many dice");}
        catch(IllegalArgumentException expected){}
        try{TabletopRandomizer.chooseStartingPlayer(5,new Random());fail("Unsupported roster");}
        catch(IllegalArgumentException expected){}
    }
}
