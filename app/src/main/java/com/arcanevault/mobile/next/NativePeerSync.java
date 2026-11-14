package com.arcanevault.mobile.next;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Lossless, offline peer snapshot and dry-run reconciliation. Importing a
 * snapshot cannot modify the collection, decks or artwork. No auto-merge or
 * conflict approval is inferred from timestamps or device names.
 */
public final class NativePeerSync {
    public static final String FORMAT="arcane-vault-peer-sync";
    public static final int SCHEMA=1;
    public static final int MAX_RECORDS=150000;
    public static final int MAX_BYTES=30*1024*1024;
    private NativePeerSync() {}

    private static String hash(byte[] content)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder value=new StringBuilder();
        for(byte b:digest)value.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return value.toString();
    }

    private static JSONArray parseRows(String value)throws Exception {
        JSONArray rows=new JSONArray(value==null?"[]":value);
        if(rows.length()>MAX_RECORDS)throw new IllegalArgumentException("Inventory too large");
        return rows;
    }

    public static JSONObject export(SharedPreferences storage,String deviceId,
            String deckJson,String artworkJson)throws Exception {
        if(deviceId==null||deviceId.isEmpty()||
            new UUID(0,0).equals(UUID.fromString(deviceId)))
            throw new IllegalArgumentException("Invalid stable device identifier");
        JSONArray physical=parseRows(storage.getString("vault_physicalCollectionV1","[]"));
        JSONArray arena=parseRows(storage.getString("vault_cards","[]"));
        JSONArray decks=parseRows(deckJson);
        JSONObject preferred=new JSONObject(artworkJson==null?"{}":artworkJson);
        JSONObject data=new JSONObject().put("schema",2)
            .put("cards",arena).put("physicalCards",physical)
            .put("deckLibrary",decks).put("preferredArtwork",preferred)
            .put("source","arcane-vault-android-peer-snapshot");
        byte[] source=data.toString().getBytes(StandardCharsets.UTF_8);
        if(source.length>MAX_BYTES)throw new IllegalArgumentException("Peer snapshot too large");
        return new JSONObject().put("format",FORMAT).put("schema",SCHEMA)
            .put("deviceId",UUID.fromString(deviceId).toString())
            .put("revision",hash(source)).put("createdUtc",System.currentTimeMillis())
            .put("data",data);
    }

    private static JSONObject require(JSONObject root)throws Exception {
        if(!FORMAT.equals(root.optString("format",""))||root.optInt("schema")!=SCHEMA)
            throw new IllegalArgumentException("Unsupported Arcane Vault peer file");
        UUID peer=UUID.fromString(root.getString("deviceId"));
        if(peer.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid peer ID");
        String revision=root.getString("revision");
        if(!revision.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("Missing peer revision");
        JSONObject data=root.getJSONObject("data");
        if(data.getInt("schema")!=2)throw new IllegalArgumentException("Unexpected workspace schema");
        for(String group:new String[]{"cards","physicalCards","deckLibrary"}){
            if(data.getJSONArray(group).length()>MAX_RECORDS)
                throw new IllegalArgumentException("Too many "+group);
        }
        data.getJSONObject("preferredArtwork");
        return data;
    }

    private static String norm(String value){
        return value==null?"":value.trim().toLowerCase(Locale.ROOT);
    }
    private static String key(String group,JSONObject row){
        String result;
        if(group.equals("cards"))result=norm(row.optString("name",""));
        else if(group.equals("deckLibrary"))result=norm(row.optString("id",""));
        else {
            StringBuilder key=new StringBuilder();
            for(String column:new String[]{"scryfallId","name","set","number","finish",
                    "language","condition","location","purchasePrice"}){
                if(key.length()>0)key.append("|");
                String part=norm(row.optString(column,""));
                key.append(part.length()).append(':').append(part);
            }
            result=key.toString();
        }
        if(result.replace("|","").isEmpty())
            throw new IllegalArgumentException("Peer record has no stable identity");
        return result;
    }
    private static Map<String,JSONObject> index(String group,JSONArray rows){
        Map<String,JSONObject> records=new HashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)throw new IllegalArgumentException("Invalid "+group+" record");
            String identity=key(group,row);
            if(records.putIfAbsent(identity,row)!=null)
                throw new IllegalArgumentException("Duplicate "+group+" identity: "+identity);
        }
        return records;
    }
    /** Stable across Windows and Android JSON object property ordering. */
    private static String canonical(Object value)throws Exception {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){
            JSONObject object=(JSONObject)value;
            Set<String> keys=new TreeSet<>();
            for(java.util.Iterator<String> it=object.keys();it.hasNext();)keys.add(it.next());
            StringBuilder result=new StringBuilder("{");
            for(String key:keys){
                if(result.length()>1)result.append(',');
                result.append(JSONObject.quote(key)).append(':')
                    .append(canonical(object.get(key)));
            }
            return result.append('}').toString();
        }
        if(value instanceof JSONArray){
            JSONArray array=(JSONArray)value;
            StringBuilder result=new StringBuilder("[");
            for(int i=0;i<array.length();i++){
                if(i>0)result.append(',');
                result.append(canonical(array.get(i)));
            }
            return result.append(']').toString();
        }
        if(value instanceof String)return JSONObject.quote((String)value);
        if(value instanceof Boolean || value instanceof Number)return value.toString();
        throw new IllegalArgumentException("Unsupported peer JSON value");
    }
    private static boolean equivalent(Object a,Object b)throws Exception {
        if(a==null||b==null)return a==b;
        return canonical(a).equals(canonical(b));
    }

    private static Map<String,String> flatten(JSONObject data)throws Exception {
        Map<String,String> result=new HashMap<>();
        for(String group:new String[]{"cards","physicalCards","deckLibrary"}){
            Map<String,JSONObject> records=index(group,data.getJSONArray(group));
            for(Map.Entry<String,JSONObject> record:records.entrySet())
                result.put(group+String.valueOf((char)31)+record.getKey(),canonical(record.getValue()));
        }
        JSONObject artwork=data.getJSONObject("preferredArtwork");
        for(java.util.Iterator<String> it=artwork.keys();it.hasNext();){
            String key=it.next();
            result.put("preferredArtwork"+String.valueOf((char)31)+key,canonical(artwork.get(key)));
        }
        return result;
    }

    /** Advanced three-way classification. A supplied baseline is not proof
     *  of authenticated pairing, so this method never enables automatic apply. */
    public static JSONObject compareWithBaseline(JSONObject local,JSONObject incoming,
            JSONObject baseline)throws Exception {
        Map<String,String> own=flatten(require(local));
        Map<String,String> remote=flatten(require(incoming));
        Map<String,String> base=flatten(require(baseline));
        Set<String> all=new TreeSet<>();
        all.addAll(own.keySet());all.addAll(remote.keySet());all.addAll(base.keySet());
        JSONArray changes=new JSONArray();
        int incomingOnly=0,localOnly=0,conflicts=0;
        for(String combined:all){
            String localValue=own.get(combined);
            String remoteValue=remote.get(combined);
            String original=base.get(combined);
            if(java.util.Objects.equals(localValue,remoteValue))continue;
            String decision;
            if(java.util.Objects.equals(localValue,original)){
                decision="Incoming only";incomingOnly++;
            } else if(java.util.Objects.equals(remoteValue,original)){
                decision="Local only";localOnly++;
            } else {
                decision="Conflict";conflicts++;
            }
            int separator=combined.indexOf(String.valueOf((char)31));
            changes.put(new JSONObject()
                .put("group",combined.substring(0,separator))
                .put("key",combined.substring(separator+1))
                .put("decision",decision)
                .put("incomingDeletion",remoteValue==null));
        }
        return new JSONObject().put("incomingOnly",incomingOnly)
            .put("localOnly",localOnly).put("conflicts",conflicts)
            .put("records",changes).put("safeToAutoApply",false);
    }

    /** Overview only; never writes either local storage or SQLite. */
    public static JSONObject preview(JSONObject local,JSONObject incoming)throws Exception {
        JSONObject data=require(incoming);
        JSONObject own=require(local);
        JSONObject summary=new JSONObject();
        JSONArray changes=new JSONArray();
        int equal=0,conflicts=0;
        for(String group:new String[]{"cards","physicalCards","deckLibrary"}){
            Map<String,JSONObject> before=index(group,own.getJSONArray(group));
            Map<String,JSONObject> after=index(group,data.getJSONArray(group));
            Set<String> keys=new TreeSet<>();keys.addAll(before.keySet());keys.addAll(after.keySet());
            for(String key:keys){
                JSONObject a=before.get(key),b=after.get(key);
                if(equivalent(a,b)){equal++;continue;}
                String action=a==null?"Add":b==null?"Absent in peer":"Different";
                if(!"Add".equals(action))conflicts++;
                changes.put(new JSONObject().put("group",group)
                    .put("key",key).put("action",action));
            }
        }
        JSONObject before=own.getJSONObject("preferredArtwork");
        JSONObject after=data.getJSONObject("preferredArtwork");
        Set<String> keys=new TreeSet<>();
        for(java.util.Iterator<String> it=before.keys();it.hasNext();)keys.add(it.next());
        for(java.util.Iterator<String> it=after.keys();it.hasNext();)keys.add(it.next());
        for(String key:keys){
            Object a=before.opt(key),b=after.opt(key);
            if(equivalent(a,b)){equal++;continue;}
            String action=a==null?"Add":b==null?"Absent in peer":"Different";
            if(!"Add".equals(action))conflicts++;
            changes.put(new JSONObject().put("group","preferredArtwork")
                .put("key",key).put("action",action));
        }
        return summary.put("peerDevice",incoming.getString("deviceId"))
            .put("revision",incoming.getString("revision")).put("equal",equal)
            .put("conflicts",conflicts).put("changes",changes)
            .put("safeToAutoApply",false);
    }
}
