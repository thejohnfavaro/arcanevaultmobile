package com.arcanevault.mobile;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservative, read-only substitution planner for Android Commander/Brawl decks.
 * Only existing owned physical or Arena names are considered; no proxies, purchases,
 * catalog writes or deck writes. All changes must be reviewed in the deck editor. */
final class NativeDeckSubstitutions {
    private NativeDeckSubstitutions(){}
    static final class Swap {
        final String source,target,reason;
        Swap(String source,String target,String reason){
            this.source=source;this.target=target;this.reason=reason;
        }
    }
    static final class Plan {
        final List<Swap> swaps=new ArrayList<>();
        final List<String> unresolved=new ArrayList<>();
        String summary(){
            return swaps.size()+" proposed owned swaps · "+
                unresolved.size()+" unresolved names";
        }
        String details(){
            List<String> rows=new ArrayList<>();
            for(Swap s:swaps)rows.add(s.source+" → "+s.target+" · "+s.reason);
            if(!unresolved.isEmpty()){
                rows.add("Unresolved:");
                rows.addAll(unresolved);
            }
            return rows.isEmpty()?"No eligible owned alternatives found.":String.join("\n",rows);
        }
        String fingerprint(){
            List<String> values=new ArrayList<>();
            for(Swap s:swaps)values.add(s.source+"\u0001"+s.target+"\u0001"+s.reason);
            values.addAll(unresolved);
            return String.join("\u0002",values);
        }
    }
    private static final class Card {
        String name,type,oracle,colors,legalities;
        double mv;
        boolean arena;
        Set<String> roles=new HashSet<>();
    }
    private static String norm(String v){
        return v==null?"":v.trim().toLowerCase(Locale.ROOT);
    }
    private static Map<String,Integer> owned(JSONArray arr){
        Map<String,Integer> map=new HashMap<>();
        if(arr==null)return map;
        for(int i=0;i<Math.min(arr.length(),150000);i++){
            JSONObject card=arr.optJSONObject(i);
            if(card==null)continue;
            String name=norm(card.optString("name",""));
            int qty=card.optInt("qty",0);
            if(name.isEmpty()||qty<=0||qty>99999)continue;
            map.put(name,Math.addExact(map.getOrDefault(name,0),qty));
        }
        return map;
    }
    private static Map<String,Integer> draft(JSONArray arr){
        Map<String,Integer> map=new HashMap<>();
        if(arr==null)return map;
        for(int i=0;i<arr.length();i++){
            JSONObject row=arr.optJSONObject(i);if(row==null)continue;
            String name=norm(row.optString("name",""));
            int qty=row.optInt("qty",0);
            if(name.isEmpty()||qty<1||qty>250)continue;
            map.put(name,Math.addExact(map.getOrDefault(name,0),qty));
        }
        return map;
    }
    private static boolean has(String hay,String needle){
        return hay.contains(needle);
    }
    private static void roles(Card c){
        String o=norm(c.oracle),t=norm(c.type);
        if(has(t,"land"))c.roles.add("land");
        if((!has(t,"land")&&(has(o,"add {")||has(o,"add one mana")))||
            has(o,"search your library for a basic land")||
            has(o,"search your library for a land")||has(o,"treasure token"))
            c.roles.add("ramp");
        if(has(o,"draw a card")||has(o,"draw two cards")||
            has(o,"draw three cards")||has(o,"draw x cards"))
            c.roles.add("draw");
        if(has(o,"destroy target")||has(o,"exile target")||
            has(o,"damage to target creature"))c.roles.add("removal");
        if(has(o,"destroy all")||has(o,"exile all")||
            has(o,"damage to each creature"))c.roles.add("wipe");
        if(has(o,"search your library for a card")||
            has(o,"search your library for an artifact")||
            has(o,"search your library for a creature"))c.roles.add("tutor");
        if(has(o,"hexproof")||has(o,"indestructible")||has(o,"ward "))
            c.roles.add("protection");
        if(has(o,"create")&&has(o,"token"))c.roles.add("tokens");
    }
    private static int colorBits(String value){
        int bits=0;
        for(char c:value.toUpperCase(Locale.ROOT).toCharArray()){
            int i="WUBRG".indexOf(c);
            if(i>=0)bits|=1<<i;
        }
        return bits;
    }
    private static boolean legal(Card c,boolean arena,String format){
        if(arena&&!c.arena)return false;
        JSONObject data;
        try{data=new JSONObject(c.legalities);}catch(Exception bad){return false;}
        String key=arena?"brawl":"commander";
        String status=data.optString(key,"");
        if(arena&&status.isEmpty())status=data.optString("historicbrawl","");
        return "legal".equalsIgnoreCase(status);
    }
    private static Map<String,Card> profiles(SQLiteDatabase db,Set<String> names){
        Map<String,Card> cards=new HashMap<>();
        try(Cursor cursor=db.rawQuery(
            "SELECT name,type,oracle,colors,legalities,arena,mv FROM cards",null)){
            int limit=0;
            while(cursor.moveToNext()){
                if(++limit>250000)throw new IllegalStateException("Catalog scan exceeds safety bound");
                String name=cursor.getString(0);
                if(name==null||!names.contains(norm(name)))continue;
                Card card=new Card();
                card.name=name;
                card.type=cursor.isNull(1)?"":cursor.getString(1);
                card.oracle=cursor.isNull(2)?"":cursor.getString(2);
                card.colors=cursor.isNull(3)?"":cursor.getString(3);
                card.legalities=cursor.isNull(4)?"":cursor.getString(4);
                card.arena=!cursor.isNull(5)&&cursor.getInt(5)>0;
                card.mv=cursor.isNull(6)?0:cursor.getDouble(6);
                roles(card);
                cards.put(norm(name),card);
            }
        }
        return cards;
    }
    static Plan analyze(CatalogStore store,JSONObject deck,String mainboard,
                        JSONArray physical,JSONArray arenaCards) throws Exception {
        if(store==null||deck==null)throw new IllegalArgumentException("Missing deck/catalog");
        String format=norm(deck.optString("format",""));
        boolean arena=NativeDeckFit.arenaFormat(format);
        if(!(format.contains("commander")||format.contains("brawl")))
            throw new IllegalArgumentException("Substitutions currently support Commander and Brawl only");
        String commander=norm(deck.optString("commander",""));
        if(commander.isEmpty())throw new IllegalArgumentException("Commander required");
        JSONArray parsed=NativeDeckDraft.parseEntries(mainboard);
        Map<String,Integer> counts=draft(parsed);
        Map<String,Integer> copies=owned(arena?arenaCards:physical);
        Set<String> needed=new HashSet<>(copies.keySet());
        needed.addAll(counts.keySet());needed.add(commander);
        Map<String,Card> meta=profiles(store.getReadableDatabase(),needed);
        Card leader=meta.get(commander);
        if(leader==null||leader.colors==null||leader.colors.isEmpty())
            throw new IllegalArgumentException("Commander color identity is unavailable; sync card metadata");
        int colors=colorBits(leader.colors);
        Plan plan=new Plan();
        List<String> sources=new ArrayList<>(counts.keySet());
        Collections.sort(sources);
        for(String source:sources){
            int missing=Math.max(0,counts.get(source)-copies.getOrDefault(source,0));
            if(missing==0)continue;
            Card original=meta.get(source);
            if(original==null||original.roles.isEmpty()){
                plan.unresolved.add(missing+" × "+source+" · source role metadata unavailable");
                continue;
            }
            int unfilled=missing;
            while(unfilled>0&&plan.swaps.size()<100){
                List<Card> options=new ArrayList<>();
                for(Map.Entry<String,Integer> entry:copies.entrySet()){
                    String key=entry.getKey();
                    if(key.equals(source)||key.equals(commander)||
                        entry.getValue()<=counts.getOrDefault(key,0))continue;
                    Card candidate=meta.get(key);
                    if(candidate==null||!legal(candidate,arena,format)||
                        (colorBits(candidate.colors)&~colors)!=0||
                        !candidate.roles.containsAll(original.roles))continue;
                    boolean sourceLand=original.roles.contains("land");
                    if(sourceLand!=candidate.roles.contains("land"))continue;
                    boolean basic=norm(candidate.type).contains("basic land") ||
                        norm(candidate.oracle).contains("a deck can have any number of cards named");
                    if(!basic&&counts.getOrDefault(key,0)>=1)continue;
                    if(Double.isFinite(candidate.mv)&&Double.isFinite(original.mv)&&
                        candidate.mv>original.mv+1.0)continue;
                    options.add(candidate);
                }
                if(options.isEmpty())break;
                options.sort(Comparator
                    .comparingInt((Card c)->Math.abs(c.roles.size()-original.roles.size()))
                    .thenComparingDouble(c->Math.abs(c.mv-original.mv))
                    .thenComparing(c->c.name,String.CASE_INSENSITIVE_ORDER));
                Card chosen=options.get(0);
                counts.put(source,counts.get(source)-1);
                String key=norm(chosen.name);
                counts.put(key,Math.addExact(counts.getOrDefault(key,0),1));
                plan.swaps.add(new Swap(original.name,chosen.name,
                    "same recognized roles, legal in selected format, in commander identity; " +
                    "mana value "+original.mv+" → "+chosen.mv));
                unfilled--;
            }
            if(unfilled>0)plan.unresolved.add(unfilled+" × "+source+
                " · no unused owned role-preserving legal alternative");
        }
        return plan;
    }
}
