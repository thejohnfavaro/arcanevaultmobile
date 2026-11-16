package com.arcanevault.mobile.next;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativePeerSyncTest {
    private JSONObject peer(String device,int copies)throws Exception {
        JSONObject data=new JSONObject().put("schema",2)
            .put("cards",new JSONArray().put(new JSONObject()
                .put("name","Lightning Bolt").put("qty",copies)))
            .put("physicalCards",new JSONArray().put(new JSONObject()
                .put("name","Sol Ring").put("qty",2).put("set","CMM")
                .put("number","397").put("finish","nonfoil")
                .put("scryfallId","11111111-1111-4111-8111-111111111111")))
            .put("deckLibrary",new JSONArray().put(new JSONObject()
                .put("id","deck_1").put("name","Commander Sample")
                .put("commander","Sample Commander")
                .put("entries",new JSONArray())))
            .put("preferredArtwork",new JSONObject()
                .put("sol ring",new JSONObject().put("image",
                    "https://cards.scryfall.io/test.jpg")));
        return new JSONObject().put("format",NativePeerSync.FORMAT)
            .put("schema",NativePeerSync.SCHEMA)
            .put("deviceId",device)
            .put("revision","a".repeat(64)).put("data",data);
    }
    @Test public void anonymousPhysicalRowsCannotBeMatchedAcrossDevices()throws Exception {
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",4);
        windows.getJSONObject("data").getJSONArray("physicalCards").put(
            new JSONObject().put("qty",1).put("set","").put("number",""));
        try {
            NativePeerSync.preview(android,windows);
            fail("anonymous physical row should not be treated as a printing");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("card name or printing ID"));
        }
        JSONObject baseline=peer("00000000-0000-4000-8000-000000000702",4);
        try {
            NativePeerSync.prepareMerge(android,windows,baseline);
            fail("anonymous physical row should never merge into local inventory");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("card name or printing ID"));
        }
    }

    @Test public void identicalPeerIsNoop()throws Exception{
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject other=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject result=NativePeerSync.preview(local,other);
        assertEquals(0,result.getJSONArray("changes").length());
        assertEquals(4,result.getInt("equal"));
        assertFalse(result.getBoolean("safeToAutoApply"));
    }
    @Test public void printingAndArenaDifferencesNeverApply()throws Exception{
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject other=peer("00000000-0000-4000-8000-000000000702",5);
        other.getJSONObject("data").getJSONArray("physicalCards")
            .put(new JSONObject().put("name","Counterspell").put("qty",1)
                .put("set","2XM").put("number","47")
                .put("scryfallId","22222222-2222-4222-8222-222222222222"));
        String before=local.toString();
        JSONObject preview=NativePeerSync.preview(local,other);
        assertEquals(2,preview.getJSONArray("changes").length());
        assertEquals(1,preview.getInt("conflicts"));
        assertEquals(before,local.toString());
    }
    @Test public void serializerPropertyOrderDoesNotCreateConflicts()throws Exception {
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject remote=peer("00000000-0000-4000-8000-000000000702",4);
        JSONArray ordered=new JSONArray().put(new JSONObject()
            .put("qty",4).put("name","Lightning Bolt"));
        remote.getJSONObject("data").put("cards",ordered);
        remote.getJSONObject("data").put("preferredArtwork",
            new JSONObject().put("sol ring",new JSONObject()
                .put("image","https://cards.scryfall.io/test.jpg")));
        JSONObject comparison=NativePeerSync.preview(local,remote);
        assertEquals(0,comparison.getJSONArray("changes").length());
    }

    @Test public void threeWayDistinguishesIndependentChangesAndConflicts()throws Exception {
        JSONObject base=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",5);
        JSONObject remote=peer("00000000-0000-4000-8000-000000000702",4);
        remote.getJSONObject("data").getJSONArray("physicalCards")
            .put(new JSONObject().put("name","Counterspell").put("qty",2)
                .put("set","2XM").put("number","47")
                .put("scryfallId","22222222-2222-4222-8222-222222222222"));
        JSONObject clean=NativePeerSync.compareWithBaseline(local,remote,base);
        assertEquals(1,clean.getInt("localOnly"));
        assertEquals(1,clean.getInt("incomingOnly"));
        assertEquals(0,clean.getInt("conflicts"));
        assertFalse(clean.getBoolean("safeToAutoApply"));
        remote.getJSONObject("data").getJSONArray("cards")
            .getJSONObject(0).put("qty",6);
        JSONObject divergent=NativePeerSync.compareWithBaseline(local,remote,base);
        assertEquals(1,divergent.getInt("conflicts"));
        assertFalse(divergent.getBoolean("safeToAutoApply"));
    }

    @Test public void candidateMergesIndependentRecordsWithoutMutatingInputs()throws Exception {
        JSONObject baseline=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",5);
        JSONObject remote=peer("00000000-0000-4000-8000-000000000702",4);
        remote.getJSONObject("data").getJSONArray("physicalCards").put(new JSONObject()
            .put("name","Counterspell").put("qty",1).put("set","2XM")
            .put("number","47").put("scryfallId","22222222-2222-4222-8222-222222222222"));
        String localBefore=local.toString();
        JSONObject plan=NativePeerSync.prepareMerge(local,remote,baseline);
        assertEquals(1,plan.getInt("incomingChanges"));
        assertEquals(1,plan.getInt("localChanges"));
        assertFalse(plan.getBoolean("safeToAutoApply"));
        assertEquals(2,plan.getJSONObject("candidate").getJSONArray("physicalCards").length());
        assertEquals(5,plan.getJSONObject("candidate").getJSONArray("cards")
            .getJSONObject(0).getInt("qty"));
        assertEquals(localBefore,local.toString());
    }
    @Test public void explicitlyChosenArenaConflictIsScopedAndBackedByBaseline()throws Exception {
        JSONObject baseline=peer("00000000-0000-4000-8000-000000000702",2);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",3);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",4);
        windows.getJSONObject("data").getJSONArray("physicalCards").put(
            new JSONObject().put("name","Counterspell").put("qty",1)
                .put("set","2XM").put("number","47")
                .put("scryfallId","22222222-2222-4222-8222-222222222222"));
        JSONObject conflict=NativePeerSync.describeArenaConflict(
            android,windows,baseline,"lightning bolt");
        assertEquals(2,conflict.getInt("baseline"));
        assertEquals(3,conflict.getInt("android"));
        assertEquals(4,conflict.getInt("windows"));
        String before=android.toString();
        JSONObject keepAndroid=NativePeerSync.prepareMerge(android,windows,baseline,
            java.util.Collections.singletonMap("lightning bolt","android"));
        assertEquals(3,keepAndroid.getJSONObject("candidate")
            .getJSONArray("cards").getJSONObject(0).getInt("qty"));
        assertEquals(2,keepAndroid.getJSONObject("candidate")
            .getJSONArray("physicalCards").length());
        JSONObject useWindows=NativePeerSync.prepareMerge(android,windows,baseline,
            java.util.Collections.singletonMap("lightning bolt","windows"));
        assertEquals(4,useWindows.getJSONObject("candidate")
            .getJSONArray("cards").getJSONObject(0).getInt("qty"));
        JSONObject delta=NativePeerSync.inventoryDelta(android,useWindows);
        assertEquals(2L,delta.getLong("addedCopies"));
        assertEquals(before,android.toString());
        assertFalse(useWindows.getBoolean("safeToAutoApply"));
        try {
            NativePeerSync.prepareMerge(android,windows,baseline,
                java.util.Collections.singletonMap("lightning bolt","add both"));
            fail("Unapproved automatic quantity arithmetic must be rejected");
        }catch(IllegalArgumentException expected){}
        try {
            NativePeerSync.prepareMerge(android,windows,baseline,
                java.util.Collections.singletonMap("other card","windows"));
            fail("A decision for an unrelated identity must be rejected");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void conflictAndMissingPeerRowsAreRejected()throws Exception {
        JSONObject baseline=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",5);
        JSONObject conflicting=peer("00000000-0000-4000-8000-000000000702",6);
        try {NativePeerSync.prepareMerge(local,conflicting,baseline);fail("conflict accepted");}
        catch(IllegalArgumentException expected){}
        JSONObject absence=peer("00000000-0000-4000-8000-000000000702",4);
        absence.getJSONObject("data").put("physicalCards",new JSONArray());
        try {NativePeerSync.prepareMerge(local,absence,baseline);fail("absence deleted printing");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void windowsOneExtraCopyOnlyAddsOneToAndroid()throws Exception {
        JSONObject baseline=peer("00000000-0000-4000-8000-000000000702",2);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",2);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",3);
        String untouched=android.getJSONObject("data").getJSONArray("physicalCards")
            .getJSONObject(0).toString();
        JSONObject proposal=NativePeerSync.prepareMerge(android,windows,baseline);
        JSONObject delta=NativePeerSync.inventoryDelta(android,proposal);
        assertEquals(1,delta.getInt("changedEntries"));
        assertEquals(1L,delta.getLong("addedCopies"));
        assertEquals(0L,delta.getLong("removedCopies"));
        JSONObject entry=delta.getJSONArray("changes").getJSONObject(0);
        assertEquals("Lightning Bolt",entry.getString("name"));
        assertEquals(2,entry.getInt("from"));
        assertEquals(3,entry.getInt("to"));
        assertEquals(1L,entry.getLong("delta"));
        assertEquals(3,proposal.getJSONObject("candidate").getJSONArray("cards")
            .getJSONObject(0).getInt("qty"));
        assertEquals(untouched,proposal.getJSONObject("candidate")
            .getJSONArray("physicalCards").getJSONObject(0).toString());
    }
    @Test public void repeatedIdenticalWindowsVersionDoesNotDuplicateCards()throws Exception {
        JSONObject baseline=peer("00000000-0000-4000-8000-000000000702",2);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",3);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",3);
        JSONObject proposal=NativePeerSync.prepareMerge(android,windows,baseline);
        assertEquals(0,NativePeerSync.inventoryDelta(android,proposal)
            .getInt("changedEntries"));
        assertEquals(3,proposal.getJSONObject("candidate").getJSONArray("cards")
            .getJSONObject(0).getInt("qty"));
    }
    @Test public void additionalPrintingDoesNotChangeOtherPrinting()throws Exception {
        JSONObject base=peer("00000000-0000-4000-8000-000000000702",2);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",2);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",2);
        windows.getJSONObject("data").getJSONArray("physicalCards").put(
            new JSONObject().put("name","Sol Ring").put("set","2XM")
                .put("number","277").put("finish","foil").put("qty",1)
                .put("scryfallId","22222222-2222-4222-8222-222222222222"));
        JSONObject proposal=NativePeerSync.prepareMerge(android,windows,base);
        JSONObject delta=NativePeerSync.inventoryDelta(android,proposal);
        assertEquals(1,delta.getInt("changedEntries"));
        assertEquals(1L,delta.getLong("addedCopies"));
        assertEquals("2XM",delta.getJSONArray("changes").getJSONObject(0).getString("set"));
        assertEquals(2,proposal.getJSONObject("candidate").getJSONArray("physicalCards")
            .getJSONObject(0).getInt("qty"));
    }
    @Test public void malformedAndDuplicateDecksRejected()throws Exception{
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject peer=peer("00000000-0000-4000-8000-000000000702",4);
        peer.put("format","other");
        try{NativePeerSync.preview(local,peer);fail("should reject format");}
        catch(IllegalArgumentException expected){}
        JSONObject duplicated=peer("00000000-0000-4000-8000-000000000702",4);
        duplicated.getJSONObject("data").getJSONArray("deckLibrary")
            .put(new JSONObject().put("id","deck_1").put("name","Conflict"));
        try{NativePeerSync.preview(local,duplicated);fail("should reject duplicate deck IDs");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void incomingDeckEditPreservesLocalInventoryAndPrinting()throws Exception {
        JSONObject base=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject edited=windows.getJSONObject("data").getJSONArray("deckLibrary").getJSONObject(0);
        edited.put("entries",new JSONArray().put(new JSONObject()
            .put("name","Sol Ring").put("quantity",1)
            .put("printingId","11111111-1111-4111-8111-111111111111")));
        String before=android.getJSONObject("data").getJSONArray("physicalCards").toString();
        JSONObject plan=NativePeerSync.prepareMerge(android,windows,base);
        assertEquals(1,plan.getInt("incomingChanges"));
        JSONObject target=plan.getJSONObject("candidate");
        assertEquals(before,target.getJSONArray("physicalCards").toString());
        assertEquals("11111111-1111-4111-8111-111111111111",
            target.getJSONArray("deckLibrary").getJSONObject(0)
                .getJSONArray("entries").getJSONObject(0).getString("printingId"));
        assertEquals(0,NativePeerSync.inventoryDelta(android,plan).getInt("changedEntries"));
    }

    @Test public void divergentEditsToSameDeckCannotSilentlyOverwrite()throws Exception {
        JSONObject base=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",4);
        android.getJSONObject("data").getJSONArray("deckLibrary").getJSONObject(0)
            .put("name","Android deck edit");
        windows.getJSONObject("data").getJSONArray("deckLibrary").getJSONObject(0)
            .put("name","Windows deck edit");
        JSONObject differences=NativePeerSync.compareWithBaseline(android,windows,base);
        assertEquals(1,differences.getInt("conflicts"));
        try {
            NativePeerSync.prepareMerge(android,windows,base);
            fail("simultaneous deck edits were accepted");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("deckLibrary"));
        }
    }

    @Test public void deckRoundTripWithUpdatedCommonBaselineDoesNotReplayEdits()throws Exception {
        JSONObject base=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject android=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject windows=peer("00000000-0000-4000-8000-000000000702",4);
        windows.getJSONObject("data").getJSONArray("deckLibrary").getJSONObject(0)
            .put("name","Windows renamed deck");
        JSONObject first=NativePeerSync.prepareMerge(android,windows,base);
        JSONObject androidAfter=peer("00000000-0000-4000-8000-000000000701",4);
        androidAfter.put("data",new JSONObject(first.getJSONObject("candidate").toString()));
        JSONObject second=NativePeerSync.prepareMerge(androidAfter,windows,base);
        assertEquals(0,second.getInt("incomingChanges"));
        JSONObject shared=peer("00000000-0000-4000-8000-000000000702",4);
        shared.put("data",new JSONObject(first.getJSONObject("candidate").toString()));
        JSONObject androidNew=peer("00000000-0000-4000-8000-000000000701",4);
        androidNew.put("data",new JSONObject(first.getJSONObject("candidate").toString()));
        androidNew.getJSONObject("data").getJSONArray("deckLibrary").getJSONObject(0)
            .put("commander","Android changed commander");
        JSONObject back=NativePeerSync.prepareMerge(windows,androidNew,shared);
        assertEquals("Android changed commander",
            back.getJSONObject("candidate").getJSONArray("deckLibrary")
                .getJSONObject(0).getString("commander"));
    }

}
