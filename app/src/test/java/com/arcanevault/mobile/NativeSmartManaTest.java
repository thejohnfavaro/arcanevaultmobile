package com.arcanevault.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeSmartManaTest {
    @Test public void oneTapSwapsOnlyLandsAndPreservesCardCount() throws Exception {
        NativeSmartMana.Plan plan=new NativeSmartMana.Plan();
        plan.swaps.add(new NativeSmartMana.Swap(
            "Island","Hallowed Fountain","owned shock land"));
        plan.swaps.add(new NativeSmartMana.Swap(
            "Plains","Seachrome Coast","owned untapped land"));
        String original="4 Island\n4 Plains\n1 Counterspell\n1 Sol Ring";
        String updated=NativeSmartMana.apply(original,plan);
        assertTrue(updated.contains("3 Island"));
        assertTrue(updated.contains("3 Plains"));
        assertTrue(updated.contains("1 Hallowed Fountain"));
        assertTrue(updated.contains("1 Seachrome Coast"));
        assertTrue(updated.contains("1 Counterspell"));
        assertTrue(updated.contains("1 Sol Ring"));
        int count=0;
        org.json.JSONArray rows=NativeDeckDraft.parseEntries(updated);
        for(int i=0;i<rows.length();i++)count+=rows.getJSONObject(i).getInt("qty");
        assertEquals(10,count);
        assertEquals("4 Island\n4 Plains\n1 Counterspell\n1 Sol Ring",original);
    }
    @Test public void refusesMissingBasicInsteadOfSilentlyModifyingDraft() throws Exception {
        NativeSmartMana.Plan plan=new NativeSmartMana.Plan();
        plan.swaps.add(new NativeSmartMana.Swap(
            "Plains","Hallowed Fountain","owned shock land"));
        try{
            NativeSmartMana.apply("1 Island\n1 Counterspell",plan);
            fail("Missing basic must be rejected");
        }catch(IllegalArgumentException expected){
            assertTrue(expected.getMessage().contains("Source copy"));
        }
    }
}
