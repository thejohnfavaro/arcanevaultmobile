package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public final class NativeDeckManaPipsTest {
    @Test public void countsStrictAndGenericCosts(){
        double[] values=NativeDeckManaPips.count("{2}{W}{W}{U}{C}{X}");
        assertArrayEquals(new double[]{2,1,0,0,0},values,0.00001);
    }

    @Test public void splitsHybridAndDiscountsAlternativeCosts(){
        double[] values=NativeDeckManaPips.count("{W/U}{2/W}{B/P}{R/G}");
        assertArrayEquals(new double[]{1,0.5,0.5,0.5,0.5},values,0.00001);
    }

    @Test public void examinesOnlyFirstSplitCost(){
        double[] values=NativeDeckManaPips.count("{1}{U} // {3}{R}{R}");
        assertArrayEquals(new double[]{0,1,0,0,0},values,0.00001);
    }

    @Test public void ignoresMissingOrUntrustedCosts(){
        assertArrayEquals(new double[5],NativeDeckManaPips.count(null),0.0);
        assertArrayEquals(new double[5],NativeDeckManaPips.count("{Q}{G/X}{8}{P}"),0.0);
        assertArrayEquals(new double[5],
            NativeDeckManaPips.count("{"+"W".repeat(600)+"}"),0.0);
    }
}
