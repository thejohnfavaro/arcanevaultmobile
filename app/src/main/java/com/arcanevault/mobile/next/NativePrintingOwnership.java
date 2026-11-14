package com.arcanevault.mobile.next;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only reconciliation for Android-native printing inspection.
 * Never attributes name-only rows to a particular set or printing.
 * Identity-bearing rows must match a known variant without contradictions.
 */
public final class NativePrintingOwnership {
    private NativePrintingOwnership() {}

    public static JSONObject inspect(String cardName, JSONArray inventory, JSONArray variants) throws Exception {
        Map<String,JSONObject> byId = new HashMap<>();
        Map<String,JSONObject> byNumber = new HashMap<>();
        Map<String,Integer> setNumberMatches = new HashMap<>();
        for (int i=0; i<variants.length(); i++) {
            JSONObject v = variants.optJSONObject(i);
            if (v==null) continue;
            String id=norm(v.optString("id",""));
            String key=pair(v.optString("set",""),v.optString("collectorNumber",""));
            if (!id.isEmpty()) byId.put(id,v);
            if (!key.equals("|")) {
                setNumberMatches.put(key,setNumberMatches.getOrDefault(key,0)+1);
                byNumber.put(key,v);
            }
        }
        Map<String,Integer> copies = new HashMap<>();
        int unresolved=0, confirmed=0, total=0;
        for (int i=0;i<inventory.length();i++) {
            JSONObject item=inventory.optJSONObject(i);
            if(item==null || !cardName.equalsIgnoreCase(item.optString("name","").trim())) continue;
            int qty=item.optInt("qty",0);
            if(qty<=0)continue;
            total+=qty;
            String id=norm(item.optString("scryfallId",""));
            String set=norm(item.optString("set",""));
            String number=norm(item.optString("number",""));
            String suppliedKey=pair(set,number);
            JSONObject chosen=null;
            if(!id.isEmpty()) {
                chosen=byId.get(id);
                // An unknown ID or conflicting supplemental set/collector value is NOT
                // rescued by name or a convenient set match.
                if(chosen!=null && (!set.isEmpty()&&!set.equals(norm(chosen.optString("set",""))) ||
                    !number.isEmpty()&&!number.equals(norm(chosen.optString("collectorNumber","")))))
                    chosen=null;
            } else if(!set.isEmpty() && !number.isEmpty() &&
                      setNumberMatches.getOrDefault(suppliedKey,0)==1) {
                chosen=byNumber.get(suppliedKey);
            }
            if(chosen==null){unresolved+=qty;continue;}
            String key=norm(chosen.optString("id",""));
            if(key.isEmpty()){unresolved+=qty;continue;}
            copies.put(key,copies.getOrDefault(key,0)+qty);
            confirmed+=qty;
        }
        JSONArray result=new JSONArray();
        for(int i=0;i<variants.length();i++) {
            JSONObject variant=variants.optJSONObject(i);
            if(variant==null)continue;
            JSONObject entry=new JSONObject(variant.toString());
            entry.put("confirmedPhysicalCopies",copies.getOrDefault(norm(entry.optString("id","")),0));
            result.put(entry);
        }
        return new JSONObject().put("name",cardName).put("physicalCopies",total)
            .put("confirmedCopies",confirmed).put("unresolvedCopies",unresolved)
            .put("printings",result);
    }
    private static String norm(String value) {
        return value==null?"":value.trim().toLowerCase(Locale.ROOT);
    }
    private static String pair(String set,String number) {return norm(set)+"|"+norm(number);}
}
