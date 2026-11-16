package com.arcanevault.mobile.next;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only differential protocol foundation. Never applies changes.
 * Transfer only after the Windows peer supports per-record revisions and
 * verifies the full base revision; otherwise retain the full snapshot path.
 */
public final class NativePeerDeltaManifest {
    private NativePeerDeltaManifest() {}

    private static String canonical(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){
            JSONObject o=(JSONObject)value;
            TreeSet<String> keys=new TreeSet<>();
            for(java.util.Iterator<String> it=o.keys();it.hasNext();)keys.add(it.next());
            StringBuilder result=new StringBuilder("{");
            for(String key:keys){
                if(result.length()>1)result.append(',');
                result.append(JSONObject.quote(key)).append(':').append(canonical(o.get(key)));
            }
            return result.append('}').toString();
        }
        if(value instanceof JSONArray){
            JSONArray a=(JSONArray)value;
            StringBuilder result=new StringBuilder("[");
            for(int i=0;i<a.length();i++){
                if(i>0)result.append(',');
                result.append(canonical(a.get(i)));
            }
            return result.append(']').toString();
        }
        return value instanceof String?JSONObject.quote((String)value):value.toString();
    }

    private static String sha(String text) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();
        for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }

    private static String id(String group,JSONObject row) {
        if(group.equals("cards"))return row.optString("name","").trim().toLowerCase(java.util.Locale.ROOT);
        if(group.equals("deckLibrary"))return row.optString("id","").trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder key=new StringBuilder();
        for(String column:new String[]{"scryfallId","name","set","number","finish",
                "language","condition","location","purchasePrice"}){
            String value=row.optString(column,"").trim().toLowerCase(java.util.Locale.ROOT);
            if(key.length()>0)key.append('|');
            key.append(value.length()).append(':').append(value);
        }
        return key.toString();
    }

    private static Map<String,String> fingerprints(JSONObject data,String group)throws Exception {
        Map<String,String> records=new TreeMap<>();
        if(group.equals("preferredArtwork")){
            JSONObject art=data.getJSONObject(group);
            for(java.util.Iterator<String> it=art.keys();it.hasNext();){
                String key=it.next();
                if(records.put(key,sha(canonical(art.get(key))))!=null)
                    throw new IllegalArgumentException("Duplicate artwork identity");
            }
        }else{
            JSONArray rows=data.getJSONArray(group);
            if(rows.length()>NativePeerSync.MAX_RECORDS)
                throw new IllegalArgumentException("Too many peer records");
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                String key=id(group,row);
                if(key.isEmpty()||records.putIfAbsent(key,sha(canonical(row)))!=null)
                    throw new IllegalArgumentException("Duplicate or empty "+group+" identity: "+key);
            }
        }
        return records;
    }

    /** Returns only changed identities and revision hashes, not card contents.
     * A changed key means transfer is needed, never authorization to delete.
     */
    public static JSONObject compare(JSONObject base,JSONObject current)throws Exception {
        JSONObject older=base.getJSONObject("data"),newer=current.getJSONObject("data");
        if(older.getInt("schema")!=2||newer.getInt("schema")!=2)
            throw new IllegalArgumentException("Unsupported workspace schema");
        JSONObject groups=new JSONObject();
        int changed=0;
        for(String group:new String[]{"cards","physicalCards","deckLibrary","preferredArtwork"}){
            Map<String,String> before=fingerprints(older,group);
            Map<String,String> after=fingerprints(newer,group);
            TreeSet<String> ids=new TreeSet<>();
            ids.addAll(before.keySet());ids.addAll(after.keySet());
            JSONArray differences=new JSONArray();
            for(String key:ids){
                String old=before.get(key),now=after.get(key);
                if(java.util.Objects.equals(old,now))continue;
                JSONObject entry=new JSONObject().put("id",key)
                    .put("state",now==null?"missing":old==null?"added":"modified");
                if(old!=null)entry.put("baseHash",old);
                if(now!=null)entry.put("currentHash",now);
                differences.put(entry);changed++;
            }
            groups.put(group,differences);
        }
        return new JSONObject().put("schema",1)
            .put("baseRevision",base.getString("revision"))
            .put("targetRevision",current.getString("revision"))
            .put("changedEntries",changed).put("groups",groups);
    }
}
