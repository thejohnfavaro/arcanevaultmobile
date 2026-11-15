package com.arcanevault.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded presentation-only analytics projected from the authoritative physical
 * inventory and exact-printing Scryfall quote cache. Never changes ownership. */
final class VaultCollectionInsights {
    private static final String PREF="arcane_collection_insights_v1";
    private VaultCollectionInsights(){}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static boolean save(Context c,String json){
        if(json==null||json.length()>160000)return false;
        try{
            JSONObject o=new JSONObject(json);
            JSONArray top=o.optJSONArray("top"),unpriced=o.optJSONArray("unpriced");
            if(top==null||unpriced==null||top.length()>50||unpriced.length()>50)return false;
            for(int i=0;i<top.length();i++){
                JSONObject item=top.getJSONObject(i);
                if(item.optString("name","").isEmpty()||item.optString("name","").length()>400||
                    !Double.isFinite(item.optDouble("value",Double.NaN))||
                    item.optDouble("value")<=0||item.optDouble("value")>1e10)return false;
            }
            String normalized=new JSONObject().put("top",top).put("unpriced",unpriced)
                .put("pricedRows",o.optInt("pricedRows",0))
                .put("totalRows",o.optInt("totalRows",0)).toString();
            SharedPreferences existing=prefs(c);
            if(normalized.equals(existing.getString("snapshot","")))return true;
            return existing.edit().putString("snapshot",normalized).commit();
        }catch(Exception error){
            android.util.Log.w("ArcaneVault","Collection insights snapshot invalid",error);
            return false;
        }
    }
    static JSONObject read(Context c){
        try{return new JSONObject(prefs(c).getString("snapshot","{}"));}
        catch(Exception ignored){return new JSONObject();}
    }
    static List<String> topCards(Context c){
        JSONObject data=read(c);
        JSONArray top=data.optJSONArray("top");
        List<String> result=new ArrayList<>();
        if(top==null)return result;
        for(int i=0;i<Math.min(10,top.length());i++){
            JSONObject row=top.optJSONObject(i);if(row==null)continue;
            result.add((i+1)+". "+row.optString("name")+" · "+
                row.optString("set","").toUpperCase(Locale.ROOT)+" · "+
                row.optString("finish","nonfoil")+" · "+
                String.format(Locale.US,"$%,.2f",row.optDouble("value",0)));
        }
        return result;
    }
    static List<String> topSets(Context c){
        JSONArray rows=new JSONArray();
        try{
            rows=new JSONObject(c.getApplicationContext()
                .getSharedPreferences("arcane_set_valuations_v1",Context.MODE_PRIVATE)
                .getString("sets","{}")).names();
        }catch(Exception ignored){}
        if(rows==null)return new ArrayList<>();
        JSONObject all;
        try{
            all=new JSONObject(c.getApplicationContext()
                .getSharedPreferences("arcane_set_valuations_v1",Context.MODE_PRIVATE)
                .getString("sets","{}"));
        }catch(Exception error){return new ArrayList<>();}
        List<String> codes=new ArrayList<>();
        for(int i=0;i<rows.length();i++)codes.add(rows.optString(i));
        codes.sort((a,b)->{
            long x=all.optJSONObject(a)==null?0:all.optJSONObject(a).optLong("cents",0);
            long y=all.optJSONObject(b)==null?0:all.optJSONObject(b).optLong("cents",0);
            return Long.compare(y,x);
        });
        List<String> result=new ArrayList<>();
        for(String code:codes){
            JSONObject row=all.optJSONObject(code);
            if(row==null||row.optInt("pricedRows",0)==0)continue;
            result.add(code.toUpperCase(Locale.ROOT)+" · "+
                String.format(Locale.US,"$%,.2f",row.optLong("cents",0)/100.0)+
                " · "+row.optInt("pricedRows",0)+"/"+
                row.optInt("totalRows",0)+" priced");
            if(result.size()==10)break;
        }
        return result;
    }
    static List<String> unpriced(Context c){
        JSONObject data=read(c);
        JSONArray rows=data.optJSONArray("unpriced");
        List<String> out=new ArrayList<>();
        if(rows!=null)for(int i=0;i<Math.min(rows.length(),10);i++)
            out.add(rows.optString(i));
        return out;
    }
}
