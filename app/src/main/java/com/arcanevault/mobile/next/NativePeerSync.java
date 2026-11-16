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
        // A key made only of empty length-prefixed fields ("0:|0:...")
        // is not a stable printing identity and can corrupt a peer merge.
        if(group.equals("physicalCards")&&
            norm(row.optString("name","")).isEmpty()&&
            norm(row.optString("scryfallId","")).isEmpty())
            throw new IllegalArgumentException(
                "Physical peer record needs a card name or printing ID");
        if(result.isEmpty())
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
    /** Display verified values for a genuine three-way Arena conflict. */
    public static JSONObject describeArenaConflict(JSONObject local,JSONObject remote,
            JSONObject baseline,String id)throws Exception {
        JSONObject own=require(local),peer=require(remote),base=require(baseline);
        String name=norm(id);
        JSONObject l=index("cards",own.getJSONArray("cards")).get(name);
        JSONObject r=index("cards",peer.getJSONArray("cards")).get(name);
        JSONObject b=index("cards",base.getJSONArray("cards")).get(name);
        if(l==null||r==null||equivalent(l,r)||
                equivalent(l,b)||equivalent(r,b))
            throw new IllegalArgumentException(
                "The Arena conflict changed. Re-run the peer review.");
        return new JSONObject().put("key",name)
            .put("name",l.optString("name",r.optString("name",name)))
            .put("baseline",b==null?0:quantity(b))
            .put("android",quantity(l))
            .put("windows",quantity(r));
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

    /**
     * Merge source records using a manually supplied common base. Never
     * interpret a missing row as deletion: that requires a signed tombstone.
     * Unknown workspace keys remain local, but conflicting opaque edits stop
     * the merge. This only prepares a candidate, never mutates storage.
     */
    public static JSONObject prepareMerge(JSONObject local,JSONObject remote,
            JSONObject baseline)throws Exception {
        return prepareMerge(local,remote,baseline,java.util.Collections.emptyMap());
    }

    /** Explicit per-card conflict choices. Never silently combine or sum
     * independently edited Arena quantities; unresolved conflicts still fail. */
    public static JSONObject prepareMerge(JSONObject local,JSONObject remote,
            JSONObject baseline,Map<String,String> arenaChoices)throws Exception {
        if(arenaChoices==null)throw new IllegalArgumentException("Missing conflict choices");
        JSONObject own=require(local), peer=require(remote), base=require(baseline);
        Set<String> usedChoices=new HashSet<>();
        if(local.getString("deviceId").equals(remote.getString("deviceId")))
            throw new IllegalArgumentException("Peer snapshot belongs to this same device");
        JSONObject proposed=new JSONObject(own.toString());
        int incoming=0,onlyLocal=0;
        for(String group:new String[]{"cards","physicalCards","deckLibrary"}){
            Map<String,JSONObject> a=index(group,own.getJSONArray(group));
            Map<String,JSONObject> b=index(group,base.getJSONArray(group));
            Map<String,JSONObject> c=index(group,peer.getJSONArray(group));
            Set<String> keys=new TreeSet<>();
            keys.addAll(a.keySet());keys.addAll(b.keySet());keys.addAll(c.keySet());
            Map<String,JSONObject> merged=new HashMap<>();
            for(String id:keys){
                JSONObject l=a.get(id),old=b.get(id),r=c.get(id);
                if(old!=null&&l!=null&&r==null)
                    throw new IllegalArgumentException("Peer absence needs tombstone: "+group+"/"+id);
                if(old!=null&&r!=null&&l==null)
                    throw new IllegalArgumentException("Local absence needs tombstone: "+group+"/"+id);
                boolean ownBase=equivalent(l,old),peerBase=equivalent(r,old);
                boolean conflict=!equivalent(l,r)&&!ownBase&&!peerBase;
                String decision=arenaChoices.get(id);
                if(conflict) {
                    if(!group.equals("cards")||decision==null)
                        throw new IllegalArgumentException("Conflicting edits: "+group+"/"+id);
                    if(!decision.equals("android")&&!decision.equals("windows"))
                        throw new IllegalArgumentException("Invalid Arena conflict selection");
                    if(l==null||r==null)
                        throw new IllegalArgumentException(
                            "Arena removal/identity conflict requires manual review: "+id);
                    usedChoices.add(id);
                }
                JSONObject chosen;
                if(conflict && decision.equals("windows")){
                    incoming++;
                    chosen=r;
                } else if(ownBase&&!peerBase){
                    incoming++;
                    chosen=r;
                } else {
                    if(!ownBase&&!equivalent(l,r))onlyLocal++;
                    chosen=l;
                }
                if(chosen!=null)merged.put(id,new JSONObject(chosen.toString()));
            }
            JSONArray combined=new JSONArray();
            for(JSONArray src:new JSONArray[]{own.getJSONArray(group),peer.getJSONArray(group)}){
                for(int i=0;i<src.length();i++){
                    JSONObject row=src.getJSONObject(i);
                    JSONObject match=merged.remove(key(group,row));
                    if(match!=null)combined.put(match);
                }
            }
            if(!merged.isEmpty())throw new IllegalArgumentException("Unmatched peer entries");
            proposed.put(group,combined);
        }
        if(!usedChoices.equals(arenaChoices.keySet()))
            throw new IllegalArgumentException(
                "A conflict choice no longer matches the current records. Review again.");
        JSONObject localArt=own.getJSONObject("preferredArtwork");
        JSONObject baseArt=base.getJSONObject("preferredArtwork");
        JSONObject peerArt=peer.getJSONObject("preferredArtwork");
        JSONObject art=new JSONObject(localArt.toString());
        Set<String> artKeys=new TreeSet<>();
        for(JSONObject obj:new JSONObject[]{localArt,baseArt,peerArt})
            for(java.util.Iterator<String> it=obj.keys();it.hasNext();)artKeys.add(it.next());
        for(String id:artKeys){
            boolean hasLocal=localArt.has(id),hasBase=baseArt.has(id),hasPeer=peerArt.has(id);
            if(hasBase&&(!hasLocal||!hasPeer))
                throw new IllegalArgumentException("Artwork deletion needs tombstone: "+id);
            Object l=hasLocal?localArt.get(id):null;
            Object b=hasBase?baseArt.get(id):null;
            Object r=hasPeer?peerArt.get(id):null;
            boolean ownBase=equivalent(l,b),peerBase=equivalent(r,b);
            if(!equivalent(l,r)&&!ownBase&&!peerBase)
                throw new IllegalArgumentException("Conflicting artwork: "+id);
            if(ownBase&&!peerBase){art.put(id,r);incoming++;}
            else if(!ownBase&&!equivalent(l,r))onlyLocal++;
        }
        proposed.put("preferredArtwork",art);
        for(java.util.Iterator<String> it=peer.keys();it.hasNext();){
            String field=it.next();
            if(field.equals("cards")||field.equals("physicalCards")||
                field.equals("deckLibrary")||field.equals("preferredArtwork")||
                field.equals("schema")||field.equals("source")||field.equals("exported"))continue;
            if(base.has(field)&&!equivalent(base.opt(field),peer.opt(field)) &&
                own.has(field)&&!equivalent(own.opt(field),peer.opt(field)))
                throw new IllegalArgumentException("Unreconciled workspace field: "+field);
        }
        if(proposed.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Merged snapshot too large");
        return new JSONObject().put("candidate",proposed)
            .put("incomingChanges",incoming).put("localChanges",onlyLocal)
            .put("remoteDevice",remote.getString("deviceId"))
            .put("localRevision",local.getString("revision"))
            .put("safeToAutoApply",false);
    }


    /** Per-printing/per-card change summary for reviewed Android inventory apply.
     * The candidate was already derived by the guarded three-way merge.
     * This comparison is relative to Android's current state, NOT a sum of
     * whole collection totals. Replaying an unchanged peer is a no-op.
     */
    public static JSONObject inventoryDelta(JSONObject local,JSONObject proposal)throws Exception {
        JSONObject current=require(local);
        JSONObject candidate=proposal.getJSONObject("candidate");
        JSONArray changes=new JSONArray();
        long added=0,removed=0;
        for(String group:new String[]{"physicalCards","cards"}){
            Map<String,JSONObject> before=index(group,current.getJSONArray(group));
            Map<String,JSONObject> after=index(group,candidate.getJSONArray(group));
            Set<String> ids=new TreeSet<>();
            ids.addAll(before.keySet());
            ids.addAll(after.keySet());
            for(String id:ids){
                JSONObject a=before.get(id),b=after.get(id);
                if(equivalent(a,b))continue;
                if(a!=null&&b==null)
                    throw new IllegalArgumentException("Inventory deletion requires an explicit tombstone");
                int oldQty=quantity(a);
                int newQty=quantity(b);
                long delta=(long)newQty-oldQty;
                if(delta>0)added+=delta;
                else if(delta<0)removed-=delta;
                changes.put(new JSONObject()
                    .put("group",group)
                    .put("name",b!=null?b.optString("name","Unknown"):a.optString("name","Unknown"))
                    .put("set",b!=null?b.optString("set",""):a.optString("set",""))
                    .put("number",b!=null?b.optString("number",""):a.optString("number",""))
                    .put("from",oldQty).put("to",newQty).put("delta",delta)
                    .put("metadataChanged",!equivalent(withoutQuantity(a),withoutQuantity(b))));
            }
        }
        return new JSONObject().put("changes",changes)
            .put("addedCopies",added).put("removedCopies",removed)
            .put("changedEntries",changes.length());
    }

    private static JSONObject withoutQuantity(JSONObject row)throws Exception {
        if(row==null)return null;
        JSONObject copy=new JSONObject(row.toString());
        copy.remove("qty");
        copy.remove("quantity");
        return copy;
    }
    private static int quantity(JSONObject row)throws Exception {
        if(row==null)return 0;
        Object raw=row.has("qty")?row.get("qty"):row.opt("quantity");
        if(!(raw instanceof Number))throw new IllegalArgumentException(
            "Inventory entry has no numeric quantity: "+row.optString("name"));
        long value=((Number)raw).longValue();
        if(value<0||value>Integer.MAX_VALUE ||
            ((Number)raw).doubleValue()!=value)
            throw new IllegalArgumentException("Invalid inventory quantity");
        return (int)value;
    }

    /** Human-readable dry-run domain summary, not a merge authorization. */
    public static String describeGroups(JSONObject preview){
        JSONObject groups=preview==null?null:preview.optJSONObject("groups");
        if(groups==null)return "No categorized changes";
        java.util.List<String> keys=new java.util.ArrayList<>();
        for(java.util.Iterator<String> it=groups.keys();it.hasNext();)keys.add(it.next());
        java.util.Collections.sort(keys);
        java.util.List<String> lines=new java.util.ArrayList<>();
        for(String key:keys){
            JSONObject values=groups.optJSONObject(key);
            if(values==null)continue;
            lines.add(key+": "+values.optInt("added")+" new, "+
                values.optInt("different")+" changed, "+
                values.optInt("absent")+" absent on peer");
        }
        return (lines.isEmpty()?"No record differences":String.join("\n",lines))+
            "\nPeer quantity difference: +"+
            preview.optLong("peerMoreCopies",0)+" / −"+
            preview.optLong("peerFewerCopies",0)+
            " copies (preview only; nothing applied)";
    }

    /** Overview only; never writes either local storage or SQLite. */
    public static JSONObject preview(JSONObject local,JSONObject incoming)throws Exception {
        JSONObject data=require(incoming);
        JSONObject own=require(local);
        JSONObject summary=new JSONObject();
        JSONArray changes=new JSONArray();
        int equal=0,conflicts=0;
        long incomingCopies=0,absentCopies=0;
        for(String group:new String[]{"cards","physicalCards","deckLibrary"}){
            Map<String,JSONObject> before=index(group,own.getJSONArray(group));
            Map<String,JSONObject> after=index(group,data.getJSONArray(group));
            Set<String> keys=new TreeSet<>();keys.addAll(before.keySet());keys.addAll(after.keySet());
            for(String key:keys){
                JSONObject a=before.get(key),b=after.get(key);
                if(equivalent(a,b)){equal++;continue;}
                String action=a==null?"Add":b==null?"Absent in peer":"Different";
                if(!"Add".equals(action))conflicts++;
                if(!"deckLibrary".equals(group)){
                    long delta=(long)quantity(b)-quantity(a);
                    if(delta>0)incomingCopies=Math.addExact(incomingCopies,delta);
                    if(delta<0)absentCopies=Math.addExact(absentCopies,-delta);
                }
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
        JSONObject groups=new JSONObject();
        for(int i=0;i<changes.length();i++){
            JSONObject item=changes.getJSONObject(i);
            String group=item.optString("group","");
            JSONObject count=groups.optJSONObject(group);
            if(count==null){count=new JSONObject().put("added",0)
                .put("different",0).put("absent",0);groups.put(group,count);}
            String action=item.optString("action","");
            String column="Add".equals(action)?"added":
                "Different".equals(action)?"different":"absent";
            count.put(column,count.optInt(column,0)+1);
        }
        return summary.put("peerDevice",incoming.getString("deviceId"))
            .put("revision",incoming.getString("revision")).put("equal",equal)
            .put("conflicts",conflicts).put("changes",changes)
            .put("groups",groups)
            .put("peerMoreCopies",incomingCopies)
            .put("peerFewerCopies",absentCopies)
            .put("safeToAutoApply",false);
    }
}
