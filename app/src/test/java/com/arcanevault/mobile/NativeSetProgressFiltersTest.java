package com.arcanevault.mobile;

import static org.junit.Assert.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativeSetProgressFiltersTest {
    private JSONArray examples() throws Exception {
        return new JSONArray()
            .put(new JSONObject().put("name","Older Set").put("code","OLD")
                .put("releaseDate","2020-05-22").put("owned",5).put("percentage",20.0))
            .put(new JSONObject().put("name","Empty Set").put("code","EMP")
                .put("releaseDate","2026-06-01").put("owned",0).put("percentage",0.0))
            .put(new JSONObject().put("name","Newer Set").put("code","NEW")
                .put("releaseDate","2025-09-10").put("owned",10).put("percentage",70.0))
            .put(new JSONObject().put("name","Undated Set").put("code","UND")
                .put("releaseDate","").put("owned",1).put("percentage",5.0));
    }

    @Test public void hidesOnlyZeroProgressAndKeepsOriginalData()throws Exception {
        JSONArray source=examples();
        List<JSONObject> filtered=NativeSetProgressFilters.select(source,true,
            NativeSetProgressFilters.RECENT);
        assertEquals(3,filtered.size());
        assertEquals("Newer Set",filtered.get(0).getString("name"));
        assertEquals("Older Set",filtered.get(1).getString("name"));
        assertEquals("Undated Set",filtered.get(2).getString("name"));
        assertEquals(4,source.length());
    }
    @Test public void oldestAndNewestKeepUndatedLast()throws Exception {
        List<JSONObject> oldest=NativeSetProgressFilters.select(examples(),false,
            NativeSetProgressFilters.OLDEST);
        assertEquals("Older Set",oldest.get(0).getString("name"));
        assertEquals("Newer Set",oldest.get(1).getString("name"));
        assertEquals("Undated Set",oldest.get(3).getString("name"));
        List<JSONObject> latest=NativeSetProgressFilters.select(examples(),false,
            NativeSetProgressFilters.RECENT);
        assertEquals("Empty Set",latest.get(0).getString("name"));
        assertEquals("Undated Set",latest.get(3).getString("name"));
    }
    @Test public void nameAndCompletionOrdersAreStable()throws Exception {
        List<JSONObject> names=NativeSetProgressFilters.select(examples(),false,
            NativeSetProgressFilters.NAME);
        assertEquals("Empty Set",names.get(0).getString("name"));
        List<JSONObject> completion=NativeSetProgressFilters.select(examples(),false,
            NativeSetProgressFilters.COMPLETION);
        assertEquals("Newer Set",completion.get(0).getString("name"));
        assertEquals("Empty Set",completion.get(3).getString("name"));
    }
}
