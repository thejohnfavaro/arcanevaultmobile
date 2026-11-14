package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativePeerInventoryApplyTest {
    @Test public void canonicalCompareIgnoresPropertyOrder()throws Exception {
        JSONArray original=new JSONArray().put(new JSONObject()
            .put("name","Sol Ring").put("qty",2).put("set","CMM"));
        JSONArray reordered=new JSONArray().put(new JSONObject()
            .put("set","CMM").put("qty",2).put("name","Sol Ring"));
        assertTrue(NativePeerInventoryApply.canonicalEqual(original,reordered));
        reordered.getJSONObject(0).put("qty",3);
        assertFalse(NativePeerInventoryApply.canonicalEqual(original,reordered));
    }
    @Test public void listOrderStillMatters()throws Exception {
        JSONArray a=new JSONArray().put(new JSONObject().put("name","A"))
            .put(new JSONObject().put("name","B"));
        JSONArray b=new JSONArray().put(new JSONObject().put("name","B"))
            .put(new JSONObject().put("name","A"));
        assertFalse(NativePeerInventoryApply.canonicalEqual(a,b));
    }
}
