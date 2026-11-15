package com.arcanevault.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Read-only, printing-priced valuations for OWNED physical cards by set.
 * Data is derived from the existing WebView Scryfall quote cache. It is never
 * inferred from set completion or arbitrary cards in the full catalog. */
final class VaultSetValues {
    private static final String PREF="arcane_set_valuations_v1";
    private VaultSetValues(){}
    private static SharedPreferences prefs(Context context){
        return context.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static boolean save(Context context,String payload){
        if(payload==null||payload.length()>180000)return false;
        try{
            JSONObject input=new JSONObject(payload);
            JSONArray rows=input.optJSONArray("sets");
            if(rows==null||rows.length()>2000)return false;
            String updated=input.optString("updatedAt","");
            if(updated.length()>80)return false;
            boolean hasQuotes=input.optBoolean("hasQuotes",false);
            JSONObject complete=new JSONObject();
            SharedPreferences prior=prefs(context);
            SharedPreferences.Editor changes=prior.edit();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null)return false;
                String code=row.optString("code","").toLowerCase(Locale.ROOT);
                if(!code.matches("[a-z0-9]{2,12}"))return false;
                double amount=row.optDouble("value",Double.NaN);
                int priced=row.optInt("pricedRows",-1),total=row.optInt("totalRows",-1);
                if(!Double.isFinite(amount)||amount<0||amount>10_000_000_000.0
                    ||priced<0||total<0||priced>total||total>250000)return false;
                long cents=Math.round(amount*100);
                JSONObject saved=new JSONObject()
                    .put("cents",cents).put("pricedRows",priced).put("totalRows",total);
                complete.put(code,saved);
                // A baseline starts only when a real, positive quote exists.
                // It remains unchanged when a card is added, sold or repriced.
                if(priced>0&&cents>0&&!prior.contains("baseline_"+code))
                    changes.putLong("baseline_"+code,cents);
            }
            String fresh=complete.toString();
            if(fresh.equals(prior.getString("sets","")) &&
                updated.equals(prior.getString("updatedAt","")) &&
                hasQuotes==prior.getBoolean("hasQuotes",false))
                return true;
            changes.putString("sets",fresh)
                .putString("updatedAt",updated)
                .putBoolean("hasQuotes",hasQuotes);
            if(!changes.commit())return false;
            VaultWidgets.refreshSets(context);
            return true;
        }catch(Exception error){
            android.util.Log.w("ArcaneVault","Set valuation snapshot rejected",error);
            return false;
        }
    }
    static JSONObject forSet(Context context,String code){
        if(code==null||!code.matches("[A-Za-z0-9]{2,12}"))return null;
        try{
            JSONObject all=new JSONObject(prefs(context).getString("sets","{}"));
            return all.optJSONObject(code.toLowerCase(Locale.ROOT));
        }catch(Exception ignored){return null;}
    }
    static String detail(Context context,String code){
        JSONObject data=forSet(context,code);
        if(data==null)return "Set value awaiting a priced collection snapshot";
        int priced=data.optInt("pricedRows",0),total=data.optInt("totalRows",0);
        if(total>0&&priced==0)return "No price quotes for owned printings in this set";
        if(total==0)return "No owned physical printings in this set";
        String amount=money(context,data.optLong("cents",0));
        String change=change(context,code,data);
        return "Owned set value: "+amount+
            (change.isEmpty()?"":" · "+change)+
            " · "+priced+"/"+total+" printings priced · Scryfall reference";
    }
    private static String currency(Context context){
        String code=VaultWidgets.prefs(context).getString("value_currency_global","USD");
        return code!=null&&code.matches("USD|EUR|GBP|CAD|AUD|JPY")?code:"USD";
    }
    private static Locale locale(String code){
        switch(code){
            case "EUR":return Locale.GERMANY;
            case "GBP":return Locale.UK;
            case "CAD":return Locale.CANADA;
            case "AUD":return new Locale("en","AU");
            case "JPY":return Locale.JAPAN;
            default:return Locale.US;
        }
    }
    private static String money(Context context,long cents){
        String code=currency(context);
        Double rate=VaultWidgetExchangeRates.rate(context,code);
        if(rate==null)return "";
        NumberFormat format=NumberFormat.getCurrencyInstance(locale(code));
        format.setCurrency(Currency.getInstance(code));
        return format.format(cents/100.0*rate);
    }
    private static String change(Context context,String code,JSONObject data){
        SharedPreferences stored=prefs(context);
        if(!stored.contains("baseline_"+code.toLowerCase(Locale.ROOT)))return "";
        long base=stored.getLong("baseline_"+code.toLowerCase(Locale.ROOT),0);
        if(base<=0)return "";
        long delta=data.optLong("cents",0)-base;
        String value=money(context,Math.abs(delta));
        if(value.isEmpty())return "";
        return (delta>0?"+":delta<0?"−":"±")+value+" ("+
            String.format(Locale.US,"%+.1f%%",100.0*delta/base)+") lifetime";
    }
    /** Attach presentation metadata, without modifying stored set progress.
     * No effect for Arena sets, which have no physical printing valuation. */
    static void decorate(Context context,JSONObject card,String source){
        if(card==null||!"physical".equals(source))return;
        String code=card.optString("code","").toLowerCase(Locale.ROOT);
        JSONObject item=forSet(context,code);
        if(item==null)return;
        int priced=item.optInt("pricedRows",0),total=item.optInt("totalRows",0);
        if(total>0&&priced==0)return;
        if(total==0)return;
        String money=money(context,item.optLong("cents",0));
        if(money.isEmpty())return;
        String variance=change(context,code,item);
        try{
            card.put("ownedValueLabel","OWNED "+money);
            card.put("ownedValueChange",variance);
            card.put("ownedPriceCoverage",priced+"/"+total+" priced");
            long baseline=prefs(context).getLong("baseline_"+code,0);
            card.put("ownedValueDirection",
                baseline<=0?0:Long.compare(item.optLong("cents",0),baseline));
        }catch(Exception ignored){}
    }
}
