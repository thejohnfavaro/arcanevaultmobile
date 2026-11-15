package com.arcanevault.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Small public Scryfall set *directory*, distinct from the local card index.
 * Names/codes are official and cached for 7 days. A directory entry alone
 * never asserts the phone has cards indexed or owns anything from that set. */
final class VaultSetDirectory {
    private static final String PREF="arcane_vault_set_directory_v1";
    private static final long FRESH_MS=7L*24*60*60*1000;
    private VaultSetDirectory(){}

    static JSONArray official(Context context){
        SharedPreferences preferences=context.getApplicationContext()
            .getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String cached=preferences.getString("sets","[]");
        JSONArray fallback;
        try{fallback=new JSONArray(cached);}catch(Exception ignored){fallback=new JSONArray();}
        long last=preferences.getLong("last_fetch",0);
        if(fallback.length()>0&&System.currentTimeMillis()-last<FRESH_MS)return fallback;
        HttpURLConnection connection=null;
        try{
            URL url=new URL("https://api.scryfall.com/sets");
            connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","ArcaneVaultAndroid/2.1 (set picker)");
            if(connection.getResponseCode()!=200)return fallback;
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] buffer=new byte[8192];
                int n;
                while((n=input.read(buffer))>=0){
                    if(output.size()+n>2_000_000)return fallback;
                    output.write(buffer,0,n);
                }
            }
            JSONArray data=new JSONObject(output.toString(StandardCharsets.UTF_8.name()))
                .optJSONArray("data");
            if(data==null||data.length()==0)return fallback;
            JSONArray verified=new JSONArray();
            for(int i=0;i<data.length()&&i<3500;i++){
                JSONObject item=data.optJSONObject(i);
                if(item==null)continue;
                String code=item.optString("code","").toLowerCase(Locale.ROOT);
                String name=item.optString("name","").trim();
                if(!code.matches("[a-z0-9]{2,12}")||
                    name.isEmpty()||name.length()>160)continue;
                verified.put(new JSONObject().put("code",code)
                    .put("name",name).put("indexed",false));
            }
            if(verified.length()==0)return fallback;
            preferences.edit().putString("sets",verified.toString())
                .putLong("last_fetch",System.currentTimeMillis()).apply();
            return verified;
        }catch(Exception ignored){return fallback;}
        finally{if(connection!=null)connection.disconnect();}
    }

    /** An indexed set takes precedence, with official names filling gaps.
     * There is no provisional/free-text set creation. */
    static JSONArray merge(JSONArray indexed,JSONArray official){
        LinkedHashMap<String,JSONObject> byCode=new LinkedHashMap<>();
        for(JSONArray source:new JSONArray[]{official,indexed}){
            if(source==null)continue;
            for(int i=0;i<source.length();i++){
                JSONObject item=source.optJSONObject(i);if(item==null)continue;
                String code=item.optString("code","").toLowerCase(Locale.ROOT);
                if(code.matches("[a-z0-9]{2,12}"))byCode.put(code,item);
            }
        }
        JSONArray result=new JSONArray();
        for(JSONObject row:byCode.values())result.put(row);
        return result;
    }
}
