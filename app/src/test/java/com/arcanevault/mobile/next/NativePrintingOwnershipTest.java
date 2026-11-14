package com.arcanevault.mobile.next;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativePrintingOwnershipTest {
    private static JSONArray variants() throws Exception {
        return new JSONArray()
            .put(new JSONObject().put("id","11111111-1111-4111-8111-111111111111")
                .put("set","abc").put("collectorNumber","010").put("setName","Set A"))
            .put(new JSONObject().put("id","22222222-2222-4222-8222-222222222222")
                .put("set","xyz").put("collectorNumber","002").put("setName","Set X"));
    }
    private static JSONObject line(String name,int qty,String id,String set,String num,String finish) throws Exception {
        return new JSONObject().put("name",name).put("qty",qty)
            .put("scryfallId",id).put("set",set).put("number",num).put("finish",finish);
    }

    @Test public void exactPrintingIdsAndFinishesConserveTotals() throws Exception {
        JSONArray owned = new JSONArray()
            .put(line("Sol Ring",2,"11111111-1111-4111-8111-111111111111","abc","010","foil"))
            .put(line("Sol Ring",3,"11111111-1111-4111-8111-111111111111","ABC","010","nonfoil"))
            .put(line("Sol Ring",1,"22222222-2222-4222-8222-222222222222","xyz","002","nonfoil"));
        JSONObject result=NativePrintingOwnership.inspect("Sol Ring",owned,variants());
        assertEquals(6,result.getInt("physicalCopies"));
        assertEquals(6,result.getInt("confirmedCopies"));
        assertEquals(0,result.getInt("unresolvedCopies"));
        assertEquals(5,result.getJSONArray("printings").getJSONObject(0).getInt("confirmedPhysicalCopies"));
        assertEquals(1,result.getJSONArray("printings").getJSONObject(1).getInt("confirmedPhysicalCopies"));
    }

    @Test public void nameOnlyContradictoryAndUnknownIdsRemainUnresolved() throws Exception {
        JSONArray owned = new JSONArray()
            .put(line("Sol Ring",4,"","","","nonfoil"))
            .put(line("Sol Ring",2,"11111111-1111-4111-8111-111111111111","xyz","002","foil"))
            .put(line("Sol Ring",3,"99999999-9999-4999-8999-999999999999","abc","010","foil"))
            .put(line("Sol Ring",1,"","abc","010","nonfoil"))
            .put(line("Other",12,"11111111-1111-4111-8111-111111111111","abc","010","foil"));
        JSONObject result=NativePrintingOwnership.inspect("Sol Ring",owned,variants());
        assertEquals(10,result.getInt("physicalCopies"));
        assertEquals(1,result.getInt("confirmedCopies"));
        assertEquals(9,result.getInt("unresolvedCopies"));
        assertEquals(1,result.getJSONArray("printings").getJSONObject(0).getInt("confirmedPhysicalCopies"));
        assertEquals(0,result.getJSONArray("printings").getJSONObject(1).getInt("confirmedPhysicalCopies"));
    }

    @Test public void ambiguousSetCollectorCannotBeGuessed() throws Exception {
        JSONArray printings=variants();
        printings.put(new JSONObject().put("id","33333333-3333-4333-8333-333333333333")
            .put("set","abc").put("collectorNumber","010"));
        JSONArray owned=new JSONArray().put(line("Sol Ring",3,"","abc","010","nonfoil"));
        JSONObject result=NativePrintingOwnership.inspect("Sol Ring",owned,printings);
        assertEquals(0,result.getInt("confirmedCopies"));
        assertEquals(3,result.getInt("unresolvedCopies"));
    }

    @Test public void sourceInventoryAndCatalogRemainUnmodified() throws Exception {
        JSONArray inventory=new JSONArray().put(line("Sol Ring",2,"","abc","010","foil"));
        JSONArray catalog=variants();
        String beforeInventory=inventory.toString(), beforeCatalog=catalog.toString();
        NativePrintingOwnership.inspect("Sol Ring",inventory,catalog);
        assertEquals(beforeInventory,inventory.toString());
        assertEquals(beforeCatalog,catalog.toString());
    }
}
