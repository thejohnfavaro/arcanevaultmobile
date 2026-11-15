package com.arcanevault.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared dated USD reference FX cache. Rates never change stored USD values. */
final class VaultWidgetExchangeRates {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final long DAY=24L*60*60*1000;
    private static final String[] SUPPORTED={"EUR","GBP","CAD","AUD","JPY"};
    private VaultWidgetExchangeRates(){}
    static Double rate(Context context,String code){
        if("USD".equals(code))return 1.0;
        SharedPreferences p=VaultWidgets.prefs(context);
        long at=p.getLong("fx_updated_at",0);
        if(at<=0 || System.currentTimeMillis()-at>7*DAY)return null;
        String date=p.getString("fx_date","");
        double value=Double.longBitsToDouble(p.getLong("fx_"+code,0L));
        return date.matches("\\d{4}-\\d{2}-\\d{2}")&&
            Double.isFinite(value)&&value>0?value:null;
    }
    static String date(Context context){
        return VaultWidgets.prefs(context).getString("fx_date","");
    }
    static void refreshIfNeeded(Context context){
        Context app=context.getApplicationContext();
        SharedPreferences p=VaultWidgets.prefs(app);
        long now=System.currentTimeMillis();
        if(now-p.getLong("fx_last_attempt",0)<6L*60*60*1000)return;
        if(now-p.getLong("fx_updated_at",0)<DAY)return;
        if(!p.edit().putLong("fx_last_attempt",now).commit())return;
        WORKER.execute(()->{
            HttpURLConnection connection=null;
            try{
                URL url=new URL("https://api.frankfurter.dev/v2/rates?base=usd&quotes=eur,gbp,cad,aud,jpy");
                connection=(HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                if(connection.getResponseCode()!=200)return;
                StringBuilder json=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),StandardCharsets.UTF_8))){
                    String line;
                    while((line=reader.readLine())!=null){
                        json.append(line);
                        if(json.length()>10000)return;
                    }
                }
                org.json.JSONArray rows=new org.json.JSONArray(json.toString());
                java.util.HashMap<String,Double> rates=new java.util.HashMap<>();
                String date="";
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    if(!"USD".equalsIgnoreCase(row.optString("base")))continue;
                    String quote=row.optString("quote","").toUpperCase(Locale.ROOT);
                    boolean supported=false;
                    for(String currency:SUPPORTED)if(currency.equals(quote))supported=true;
                    if(!supported)continue;
                    double value=row.optDouble("rate",Double.NaN);
                    String rowDate=row.optString("date","");
                    if(!Double.isFinite(value)||value<=0||value>100000||
                        !rowDate.matches("\\d{4}-\\d{2}-\\d{2}"))return;
                    if(!date.isEmpty()&&!date.equals(rowDate))return;
                    date=rowDate;
                    rates.put(quote,value);
                }
                if(rates.size()!=SUPPORTED.length)return;
                SharedPreferences.Editor editor=p.edit();
                for(String currency:SUPPORTED)
                    editor.putLong("fx_"+currency,Double.doubleToLongBits(rates.get(currency)));
                editor.putString("fx_date",date)
                    .putLong("fx_updated_at",System.currentTimeMillis());
                if(editor.commit())VaultWidgets.refreshValue(app);
            }catch(Exception error){
                android.util.Log.w("ArcaneVault","Widget reference FX unavailable",error);
            }finally{if(connection!=null)connection.disconnect();}
        });
    }
}
