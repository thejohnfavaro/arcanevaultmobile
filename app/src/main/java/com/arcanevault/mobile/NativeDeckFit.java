package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only, per-pool collection-aware deck coverage. Never merges Arena
 * and physical counts and never claims card-name matches are format legality. */
final class NativeDeckFit {
    private NativeDeckFit(){}
    static final class Result {
        final int required,covered;
        final boolean arena;
        final List<String> missing;
        Result(int required,int covered,boolean arena,List<String> missing){
            this.required=required;this.covered=covered;this.arena=arena;this.missing=missing;
        }
        int missingCopies(){return required-covered;}
        String summary(){
            return covered+"/"+required+" owned · "+missingCopies()+" missing"+
                (arena?" · Arena inventory":" · Physical inventory");
        }
    }
    private static String normalized(String name){
        return name==null?"":name.trim().toLowerCase(Locale.ROOT);
    }
    static boolean arenaFormat(String format){
        String value=normalized(format);
        return value.contains("brawl")||value.contains("arena")||
            value.equals("historic")||value.equals("alchemy")||
            value.equals("explorer")||value.equals("timeless");
    }
    static Result check(JSONObject deck,JSONArray physical,JSONArray arenaCards){
        if(deck==null)return new Result(0,0,false,Collections.emptyList());
        boolean arena=arenaFormat(deck.optString("format",""));
        JSONArray pool=arena?arenaCards:physical;
        Map<String,Integer> owned=new HashMap<>();
        if(pool!=null)for(int i=0;i<Math.min(pool.length(),150000);i++){
            JSONObject row=pool.optJSONObject(i);if(row==null)continue;
            String name=normalized(row.optString("name",""));
            int qty=row.optInt("qty",0);
            if(name.isEmpty()||qty<=0||qty>99999)continue;
            owned.put(name,Math.addExact(owned.getOrDefault(name,0),qty));
        }
        Map<String,Integer> need=new HashMap<>();
        JSONArray entries=deck.optJSONArray("entries");
        if(entries!=null)for(int i=0;i<Math.min(entries.length(),2000);i++){
            JSONObject row=entries.optJSONObject(i);if(row==null)continue;
            String name=normalized(row.optString("name",""));
            int qty=row.optInt("qty",0);
            if(name.isEmpty()||qty<=0||qty>999)continue;
            need.put(name,Math.addExact(need.getOrDefault(name,0),qty));
        }
        String format=normalized(deck.optString("format",""));
        String commander=normalized(deck.optString("commander",""));
        boolean commanderFormat=format.contains("commander")||format.contains("brawl");
        if(commanderFormat&&!commander.isEmpty()&&!need.containsKey(commander))
            need.put(commander,1);
        int required=0,covered=0;
        List<String> missing=new ArrayList<>();
        for(Map.Entry<String,Integer> item:need.entrySet()){
            int n=item.getValue(),copies=owned.getOrDefault(item.getKey(),0);
            required=Math.addExact(required,n);
            covered=Math.addExact(covered,Math.min(n,copies));
            if(copies<n)missing.add((n-copies)+" × "+item.getKey());
        }
        missing.sort(String.CASE_INSENSITIVE_ORDER);
        return new Result(required,covered,arena,missing);
    }
    static List<String> rank(JSONArray decks,JSONArray physical,JSONArray arenaCards){
        List<String> ranked=new ArrayList<>();
        List<JSONObject> source=new ArrayList<>();
        for(int i=0;i<Math.min(decks==null?0:decks.length(),500);i++){
            JSONObject deck=decks.optJSONObject(i);
            if(deck!=null)source.add(deck);
        }
        source.sort(Comparator.<JSONObject>comparingInt(deck->{
            Result result=check(deck,physical,arenaCards);
            return result.required==0?Integer.MAX_VALUE:result.missingCopies();
        }).thenComparing(deck->deck.optString("name","")));
        for(JSONObject deck:source){
            Result fit=check(deck,physical,arenaCards);
            if(fit.required==0)continue;
            ranked.add(deck.optString("name","Saved deck")+" · "+fit.summary()+
                (fit.missingCopies()>0?"\nMissing: "+
                    String.join(", ",fit.missing.subList(0,Math.min(5,fit.missing.size()))):""));
            if(ranked.size()==8)break;
        }
        return ranked;
    }
}
