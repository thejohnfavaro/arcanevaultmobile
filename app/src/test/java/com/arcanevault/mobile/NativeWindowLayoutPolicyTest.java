package com.arcanevault.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

/** Contract tests for live Android window sizes (not device model labels). */
public class NativeWindowLayoutPolicyTest {
    @Test public void widthBreakpoints() {
        assertEquals(NativeWindowLayoutPolicy.Size.COMPACT, NativeWindowLayoutPolicy.size(320));
        assertEquals(NativeWindowLayoutPolicy.Size.COMPACT, NativeWindowLayoutPolicy.size(599));
        assertEquals(NativeWindowLayoutPolicy.Size.MEDIUM, NativeWindowLayoutPolicy.size(600));
        assertEquals(NativeWindowLayoutPolicy.Size.MEDIUM, NativeWindowLayoutPolicy.size(839));
        assertEquals(NativeWindowLayoutPolicy.Size.EXPANDED, NativeWindowLayoutPolicy.size(840));
        assertFalse(NativeWindowLayoutPolicy.twoPaneCommander(599));
        assertTrue(NativeWindowLayoutPolicy.twoPaneCommander(600));
    }
    @Test public void artworkColumnsRespectActualWindowWidth() {
        int[][] samples={
            {280,1},{299,1},{300,2},{360,2},{519,2},
            {520,3},{700,3},{759,3},{760,4},
            {1019,4},{1020,5},{1279,5},{1280,6}
        };
        for(int[] sample:samples){
            assertEquals("width "+sample[0],sample[1],
                NativeWindowLayoutPolicy.catalogColumns(sample[0]));
        }
        for(int width=300;width<1500;width+=25)
            assertTrue("card would shrink at "+width,
                NativeWindowLayoutPolicy.minimumArtworkWidthDp(width)>=100);
    }
}
