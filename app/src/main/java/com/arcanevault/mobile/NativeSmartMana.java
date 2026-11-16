package com.arcanevault.mobile;

import android.database.Cursor;
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

/** One-tap, owned-only mana optimization for an UNSAVED Commander/Brawl draft.
 * Never changes a spell, existing nonbasic land, saved deck or inventory.
 * A missing commander/legality record fails closed rather than guessing. */
final class NativeSmartMana {
    private NativeSmartMana(){}

    static final class Swap {
        final String remove,add,reason;
        Swap(String remove,String add,String reason){
            this.remove=remove;this.add=add;this.reason=reason;
        }
    }
    static final class Plan {
        final List<Swap> swaps=new ArrayList<>();
        final List<String> warnings=new ArrayList<>();
        String summary(){
            if(swaps.isEmpty())return "No eligible owned land improvements found."+
                (warnings.isEmpty()?"":" "+String.join("; ",warnings));
            return swaps.size()+" land swaps · shock → untapped → conditional → tapped priority";
        }
    }
    static final class Land {
        String name,type,oracle,colors,legality;
        boolean arena;
        int owned;
        int produced;
        boolean basic;
        int tier;
    }
    private static String norm(String v){
        return v==null?"":v.trim().toLowerCase(Locale.ROOT);
    }
    private static int bits(String v){
        int bits=0;
        if(v==null)return 0;
        for(char c:v.toUpperCase(Locale.ROOT).toCharArray()){
            int index="WUBRG".indexOf(c);
            if(index>=0)bits|=1<<index;
        }
        return bits;
    }
    private static Map<String,Integer> count(JSONArray rows){
        Map<String,Integer> counts=new HashMap<>();
        if(rows==null)return counts;
        for(int i=0;i<Math.min(rows.length(),150000);i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null||row.optBoolean("proxy",false)||
                row.optBoolean("isProxy",false)||
                "proxy".equalsIgnoreCase(row.optString("ownershipType",""))||
                "proxy".equalsIgnoreCase(row.optString("source","")))continue;
            String name=norm(row.optString("name",""));
            int qty=row.optInt("qty",0);
            if(name.isEmpty()||qty<=0||qty>99999)continue;
            counts.put(name,Math.addExact(counts.getOrDefault(name,0),qty));
        }
        return counts;
    }
    private static boolean isLand(String type){
        return norm(type).matches("(?s).*\\bland\\b.*");
    }
    private static boolean isBasic(String type){
        return norm(type).contains("basic land");
    }
    private static int produced(String type,String oracle,int identity){
        String t=norm(type),o=norm(oracle);
        int value=0;
        String[] basics={"plains","island","swamp","mountain","forest"};
        for(int i=0;i<basics.length;i++)
            if(t.contains(basics[i]))value|=1<<i;
        // Mana symbols only in Add clauses, never in arbitrary rules text.
        for(String sentence:o.split("[.\\n]")){
            int pos=sentence.indexOf("add ");
            if(pos<0)continue;
            String add=sentence.substring(pos);
            for(int i=0;i<5;i++)
                if(add.contains("{"+Character.toLowerCase("WUBRG".charAt(i))+"}"))
                    value|=1<<i;
            if(add.contains("any color")||add.contains("any one color"))
                value|=identity;
        }
        return value&identity;
    }
    private static int tier(String name,String oracle){
        String n=norm(name),o=norm(oracle);
        // Require true pay-two-life shock wording; "shock land" is not
        // inferred from color identity or its dual basic land types alone.
        if(o.contains("pay 2 life")&&o.contains("enters tapped"))return 0;
        if(o.contains("enters tapped")){
            if(o.contains("unless")||o.contains("if you control")||
                o.contains("if you have")||o.contains("if you reveal"))
                return 2;
            return 4;
        }
        if(o.contains("enters the battlefield tapped"))return 4;
        if(o.contains("enters")&&o.contains("tapped"))return 3;
        return 1;
    }
    private static boolean legal(String json,boolean arena){
        try{
            JSONObject obj=new JSONObject(json);
            String value=obj.optString(arena?"brawl":"commander","");
            if(arena&&value.isEmpty())value=obj.optString("historicbrawl","");
            return "legal".equalsIgnoreCase(value);
        }catch(Exception ex){return false;}
    }
    /** Caller passes catalog-backed owned inventory, not cards from a remote search.
     * Only established land slots are swapped, so the land count never changes. */
    static Plan analyze(CatalogStore catalog,JSONObject deck,String mainboard,
                        JSONArray physical,JSONArray arenaCards) throws Exception {
        if(catalog==null||deck==null)throw new IllegalArgumentException("Catalog/deck required");
        String format=norm(deck.optString("format",""));
        if(!format.contains("commander")&&!format.contains("brawl"))
            throw new IllegalArgumentException("Smart Mana currently supports Commander/Brawl");
        boolean arena=NativeDeckFit.arenaFormat(format);
        String commander=norm(deck.optString("commander",""));
        if(commander.isEmpty())throw new IllegalArgumentException("Select a commander");
        JSONArray draftRows=NativeDeckDraft.parseEntries(mainboard);
        Map<String,Integer> draft=new HashMap<>();
        for(int i=0;i<draftRows.length();i++){
            JSONObject row=draftRows.getJSONObject(i);
            draft.put(norm(row.getString("name")),row.getInt("qty"));
        }
        Map<String,Integer> owned=count(arena?arenaCards:physical);
        Set<String> names=new HashSet<>(owned.keySet());
        names.addAll(draft.keySet());
        names.add(commander);
        Map<String,Land> cards=new HashMap<>();
        try(Cursor c=catalog.getReadableDatabase().rawQuery(
            "SELECT name,type,oracle,colors,legalities,arena FROM cards",null)){
            while(c.moveToNext()){
                String name=c.getString(0);
                if(name==null||!names.contains(norm(name)))continue;
                Land land=new Land();
                land.name=name;
                land.type=c.isNull(1)?"":c.getString(1);
                land.oracle=c.isNull(2)?"":c.getString(2);
                land.colors=c.isNull(3)?"":c.getString(3);
                land.legality=c.isNull(4)?"":c.getString(4);
                land.arena=!c.isNull(5)&&c.getInt(5)>0;
                land.basic=isBasic(land.type);
                land.owned=owned.getOrDefault(norm(name),0);
                cards.put(norm(name),land);
            }
        }
        Land leader=cards.get(commander);
        if(leader==null)throw new IllegalArgumentException("Commander metadata missing");
        int identity=bits(leader.colors);
        Plan plan=new Plan();
        int landCount=0;
        for(Map.Entry<String,Integer> entry:draft.entrySet()){
            Land card=cards.get(entry.getKey());
            if(card==null)throw new IllegalArgumentException(
                "Missing catalog metadata for "+entry.getKey());
            if(isLand(card.type))landCount+=entry.getValue();
        }
        if(landCount==0){
            plan.warnings.add("No land slots to optimize");return plan;
        }
        List<Land> options=new ArrayList<>();
        for(Land card:cards.values()){
            String key=norm(card.name);
            if(!isLand(card.type)||card.basic||card.owned<=draft.getOrDefault(key,0)||
                key.equals(commander)||!legal(card.legality,arena)||
                (arena&&!card.arena)||(bits(card.colors)&~identity)!=0)continue;
            String oracle=norm(card.oracle);
            if(oracle.contains("search your library for")||
                oracle.contains("spend this mana only")||
                oracle.contains("spend this mana to"))continue;
            card.produced=produced(card.type,card.oracle,identity);
            if(identity!=0&&card.produced==0)continue;
            card.tier=tier(card.name,card.oracle);
            options.add(card);
        }
        options.sort(Comparator.comparingInt((Land l)->l.tier)
            .thenComparingInt(l->-Integer.bitCount(l.produced))
            .thenComparing(l->l.name,String.CASE_INSENSITIVE_ORDER));
        // Preserve every existing nonbasic; never remove the last existing
        // copy of any basic type, so fetch targets remain minimally present.
        int swaps=0;
        for(Land candidate:options){
            if(swaps>=12)break;
            String key=norm(candidate.name);
            if(draft.getOrDefault(key,0)>0)continue; // singleton Commander/Brawl
            String remove=null;
            int count=0;
            for(Map.Entry<String,Integer> entry:draft.entrySet()){
                Land basic=cards.get(entry.getKey());
                if(basic==null||!basic.basic||entry.getValue()<=1)continue;
                int coverage=produced(basic.type,basic.oracle,identity);
                // Never exchange a basic for an always-tapped land with
                // no additional color coverage: that's a strict downgrade.
                if(candidate.tier>=4&&
                    Integer.bitCount(candidate.produced)<=Integer.bitCount(coverage))
                    continue;
                if(entry.getValue()>count){
                    remove=entry.getKey();count=entry.getValue();
                }
            }
            if(remove==null)break;
            draft.put(remove,draft.get(remove)-1);
            draft.put(key,1);
            plan.swaps.add(new Swap(cards.get(remove).name,candidate.name,
                candidate.tier==0?"owned shock land":
                candidate.tier==1?"owned untapped land":
                candidate.tier==4?"owned tapped fallback":"owned conditional untapped land"));
            swaps++;
        }
        return plan;
    }
    static String apply(String source,Plan plan) throws Exception {
        NativeDeckSubstitutions.Plan changes=new NativeDeckSubstitutions.Plan();
        for(Swap swap:plan.swaps)
            changes.swaps.add(new NativeDeckSubstitutions.Swap(
                swap.remove,swap.add,swap.reason));
        return NativeDeckSubstitutions.applyToText(source,changes);
    }
}
