package com.arcanevault.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeDeckSubstitutionsTest {
    @Test public void batchDraftEditsAreOneForOneAndUnsaved() throws Exception {
        NativeDeckSubstitutions.Plan plan=new NativeDeckSubstitutions.Plan();
        plan.swaps.add(new NativeDeckSubstitutions.Swap(
            "Missing Draw","Owned Draw","matching draw role"));
        plan.swaps.add(new NativeDeckSubstitutions.Swap(
            "Missing Draw","Second Draw","matching draw role"));
        String original="2 Missing Draw\n1 Sol Ring";
        String edited=NativeDeckSubstitutions.applyToText(original,plan);
        assertEquals(3,NativeDeckDraft.parseEntries(edited).getJSONObject(0).optInt("qty",0) +
            NativeDeckDraft.parseEntries(edited).getJSONObject(1).optInt("qty",0) +
            NativeDeckDraft.parseEntries(edited).getJSONObject(2).optInt("qty",0));
        assertTrue(edited.contains("1 owned draw"));
        assertTrue(edited.contains("1 second draw"));
        assertTrue(original.contains("2 Missing Draw"));
    }
    @Test public void staleSourceCannotApply() throws Exception {
        NativeDeckSubstitutions.Plan plan=new NativeDeckSubstitutions.Plan();
        plan.swaps.add(new NativeDeckSubstitutions.Swap(
            "Missing Draw","Owned Draw","matching role"));
        boolean rejected=false;
        try {NativeDeckSubstitutions.applyToText("1 Sol Ring",plan);}
        catch(IllegalArgumentException expected){rejected=true;}
        assertTrue(rejected);
    }
}
