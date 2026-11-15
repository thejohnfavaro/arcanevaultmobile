package com.arcanevault.mobile;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Quick read-only storage audit. A missing printing ID means unverifiable
 * edition, not automatically corrupted inventory. No catalog SQL/UI blocking. */
final class VaultCollectionAudit {
    private VaultCollectionAudit(){}
    static final class Result {
        final int physicalRows,arenaRows,invalidQuantities,missingIdentities,possibleDuplicates;
        final long physicalCopies,arenaCopies;
        Result(int p,int a,long pc,long ac,int invalid,int missing,int duplicates){
            physicalRows=p;arenaRows=a;physicalCopies=pc;arenaCopies=ac;
            invalidQuantities=invalid;missingIdentities=missing;possibleDuplicates=duplicates;
        }
        String summary(){
            return physicalCopies+" physical copies / "+physicalRows+" entries · "+
                arenaCopies+" Arena copies / "+arenaRows+" entries"+
                "\n"+invalidQuantities+" invalid quantities · "+
                missingIdentities+" physical rows without a set-specific printing ID · "+
                possibleDuplicates+" possible duplicate inventory records";
        }
    }
    static Result inspect(SharedPreferences prefs)throws Exception{
        JSONArray physical=new JSONArray(prefs.getString("vault_physicalCollectionV1","[]"));
        JSONArray arena=new JSONArray(prefs.getString("vault_cards","[]"));
        if(physical.length()>150000||arena.length()>150000)
            throw new IllegalArgumentException("Inventory exceeds audit limits");
        int bad=0,missing=0,duplicates=0;
        long physicalCopies=0,arenaCopies=0;
        Set<String> identities=new HashSet<>();
        for(int i=0;i<physical.length();i++){
            JSONObject row=physical.optJSONObject(i);
            if(row==null){bad++;continue;}
            int qty=row.optInt("qty",0);
            if(qty<1||qty>99999){bad++;continue;}
            physicalCopies=Math.addExact(physicalCopies,qty);
            String id=row.optString("scryfallId","").trim().toLowerCase(Locale.ROOT);
            String set=row.optString("set","").trim().toLowerCase(Locale.ROOT);
            String number=row.optString("number","").trim().toLowerCase(Locale.ROOT);
            if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||
                set.isEmpty()||number.isEmpty())missing++;
            String key=id+"|"+set+"|"+number+"|"+
                row.optString("finish","").toLowerCase(Locale.ROOT)+"|"+
                row.optString("location","").toLowerCase(Locale.ROOT)+"|"+
                row.optString("condition","").toLowerCase(Locale.ROOT)+"|"+
                row.optString("name","").toLowerCase(Locale.ROOT);
            if(!identities.add(key))duplicates++;
        }
        for(int i=0;i<arena.length();i++){
            JSONObject row=arena.optJSONObject(i);
            if(row==null){bad++;continue;}
            int qty=row.optInt("qty",0);
            if(qty<1||qty>99999){bad++;continue;}
            arenaCopies=Math.addExact(arenaCopies,qty);
        }
        return new Result(physical.length(),arena.length(),
            physicalCopies,arenaCopies,bad,missing,duplicates);
    }
}
