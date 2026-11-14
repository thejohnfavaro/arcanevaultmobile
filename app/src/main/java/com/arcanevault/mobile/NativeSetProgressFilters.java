package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Filters/sorts existing on-device Set Progress results; never changes the catalog. */
final class NativeSetProgressFilters {
    static final String RECENT="recent";
    static final String OLDEST="oldest";
    static final String NAME="name";
    static final String COMPLETION="completion";
    static final String[] SORTS={RECENT,OLDEST,NAME,COMPLETION};

    private NativeSetProgressFilters(){}

    static List<JSONObject> select(JSONArray sets,boolean hideEmpty,String order){
        List<JSONObject> result=new ArrayList<>();
        for(int i=0;i<sets.length();i++){
            JSONObject item=sets.optJSONObject(i);
            if(item!=null && (!hideEmpty || item.optInt("owned",0)>0))
                result.add(item);
        }
        Comparator<JSONObject> name=(a,b)->a.optString("name","")
            .compareToIgnoreCase(b.optString("name",""));
        Comparator<JSONObject> latest=(a,b)->{
            String ad=a.optString("releaseDate","");
            String bd=b.optString("releaseDate","");
            // Undated sets always appear last in either release-date order.
            if(ad.isEmpty()!=bd.isEmpty())return ad.isEmpty()?1:-1;
            if(ad.isEmpty())return name.compare(a,b);
            int compared="oldest".equals(order)?ad.compareTo(bd):bd.compareTo(ad);
            return compared!=0?compared:name.compare(a,b);
        };
        if(NAME.equals(order))result.sort(name);
        else if(COMPLETION.equals(order))result.sort((a,b)->{
            int compare=Double.compare(b.optDouble("percentage",0),a.optDouble("percentage",0));
            return compare!=0?compare:name.compare(a,b);
        });
        else result.sort(latest);
        return result;
    }

    static String title(String sort){
        switch(sort){
            case OLDEST:return "Oldest first";
            case NAME:return "Set name A–Z";
            case COMPLETION:return "Most complete";
            default:return "Newest first";
        }
    }
}
