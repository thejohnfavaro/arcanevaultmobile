package com.arcanevault.mobile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android launcher snapshots are derived read-only projections. Inventories
 * remain authoritative in native collection preferences, set progress in the
 * existing SQLite catalog, and Scryfall quotes in the legacy price cache. */
final class VaultWidgets {
    static final String PREF="arcane_vault_widget_snapshots_v1";
    static final String TARGET="com.arcanevault.mobile.widget.target";
    static final String SET_CODE="com.arcanevault.mobile.widget.set";
    static final String SOURCE="com.arcanevault.mobile.widget.source";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private VaultWidgets(){}

    static SharedPreferences prefs(Context context){
        return context.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static String configuredSource(Context context,int id){
        return prefs(context).getString("source_"+id,"physical");
    }
    static String configuredSet(Context context,int id){
        return prefs(context).getString("code_"+id,"");
    }
    static void saveSetChoice(Context context,int id,String code,String name,String source){
        if(!code.matches("[A-Za-z0-9]{2,12}") ||
                (!"physical".equals(source)&&!"arena".equals(source)))
            throw new IllegalArgumentException("Invalid widget set selection");
        boolean stored=prefs(context).edit()
            .putString("code_"+id,code.toLowerCase(Locale.ROOT))
            .putString("name_"+id,name).putString("source_"+id,source)
            .remove("snapshot_"+id).commit();
        if(!stored)throw new IllegalStateException("Android could not save the widget selection");
    }
    static void removeSetChoice(Context context,int id){
        prefs(context).edit().remove("code_"+id).remove("name_"+id)
            .remove("source_"+id).remove("snapshot_"+id).apply();
    }

    /** Accept only computed Scryfall quote totals from the existing physical
     * collection UI. A purchase price is NOT a market quote; unpriced inventory
     * rows are excluded and shown explicitly as coverage, never priced at $0. */
    static boolean saveValueSnapshot(Context context,String payload){
        try{
            if(payload==null||payload.length()>1024)return false;
            JSONObject v=new JSONObject(payload);
            double usd=v.getDouble("value");
            int priced=v.getInt("pricedRows"),total=v.getInt("totalRows");
            long copies=v.getLong("totalCopies");
            String at=v.optString("updatedAt","");
            boolean hasQuotes=v.optBoolean("hasQuotes",priced>0);
            if(!Double.isFinite(usd)||usd<0||usd>10_000_000_000.0||
                total<0||total>250000||priced<0||priced>total||
                copies<0||copies>10_000_000||at.length()>80)return false;
            prefs(context).edit()
                .putLong("value_cents",Math.round(usd*100))
                .putInt("priced_rows",priced).putInt("total_rows",total)
                .putLong("total_copies",copies).putString("quote_updated",at)
                .putBoolean("quotes_present",hasQuotes)
                .putBoolean("value_valid",true).commit();
            if(!prefs(context).getBoolean("value_valid",false))return false;
            refreshValue(context);
            return true;
        }catch(Exception error){return false;}
    }

    static void invalidateValue(Context context){
        prefs(context).edit().putBoolean("value_valid",false).apply();
        refreshValue(context);
    }

    static void refreshValue(Context context){
        AppWidgetManager mgr=AppWidgetManager.getInstance(context);
        int[] ids=mgr.getAppWidgetIds(new ComponentName(context,VaultCollectionValueWidget.class));
        if(ids.length==0)return;
        SharedPreferences data=prefs(context);
        boolean valid=data.getBoolean("value_valid",false);
        int priced=data.getInt("priced_rows",0),total=data.getInt("total_rows",0);
        long copies=data.getLong("total_copies",0);
        long cents=data.getLong("value_cents",0);
        boolean hasQuotes=data.getBoolean("quotes_present",priced>0);
        String amount=valid&&priced>0?
            NumberFormat.getCurrencyInstance(Locale.US).format(cents/100.0):"—";
        String coverage=!valid?"Open Arcane Vault to load saved price quotes":
            total==0?"No physical cards inventoried":
            !hasQuotes?"No Scryfall quotes cached · refresh prices in app":
            priced==0?"Quotes do not match owned printings · refresh prices":
            String.format(Locale.US,"%d / %d printings priced · %,d cards",
                priced,total,copies);
        String when=data.getString("quote_updated","");
        String subtitle="Scryfall USD · cached estimate";
        if(valid&&!when.isEmpty()){
            String day=when.length()>=10?when.substring(0,10):when;
            subtitle="Scryfall USD · quotes "+day;
        }
        for(int id:ids){
            RemoteViews rv=new RemoteViews(context.getPackageName(),
                R.layout.widget_collection_value);
            rv.setTextViewText(R.id.widget_value_amount,amount);
            rv.setTextViewText(R.id.widget_value_coverage,coverage);
            rv.setTextViewText(R.id.widget_value_updated,subtitle);
            rv.setOnClickPendingIntent(R.id.widget_value_root,
                openApp(context,id,"physical","","physical"));
            mgr.updateAppWidget(id,rv);
        }
    }

    static PendingIntent openApp(Context context,int id,String target,
            String set,String source){
        Intent intent=new Intent(context,MainActivity.class);
        intent.setAction("com.arcanevault.mobile.WIDGET_OPEN");
        intent.setData(Uri.parse("arcanevault://widget/open/"+id+"/"+target));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(TARGET,target);
        intent.putExtra(SET_CODE,set);
        intent.putExtra(SOURCE,source);
        return PendingIntent.getActivity(context,id,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent configure(Context context,int id){
        Intent intent=new Intent(context,VaultSetWidgetConfigActivity.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setData(Uri.parse("arcanevault://widget/config/"+id));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        return PendingIntent.getActivity(context,10000+id,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    /** Render cached state immediately, then let a single worker refresh from
     * local SQLite. Widgets never request network images or live prices. */
    static void refreshSets(Context context){
        AppWidgetManager mgr=AppWidgetManager.getInstance(context);
        int[] ids=mgr.getAppWidgetIds(new ComponentName(context,VaultSetProgressWidget.class));
        if(ids.length>0)updateSets(context,mgr,ids,null);
    }

    static void updateSets(Context context,AppWidgetManager manager,int[] ids,
            android.content.BroadcastReceiver.PendingResult pending){
        try{
            for(int id:ids)drawSet(context,manager,id);
            WORKER.execute(()->{
                try{rebuildSetSnapshots(context,manager,ids);}
                finally{if(pending!=null)pending.finish();}
            });
        }catch(RuntimeException failure){
            if(pending!=null)pending.finish();
        }
    }

    private static void rebuildSetSnapshots(Context context,AppWidgetManager manager,int[] ids){
        Map<String,Map<String,JSONObject>> bySource=new HashMap<>();
        CatalogStore catalog=null;
        try{
            catalog=new CatalogStore(context.getApplicationContext());
            for(int id:ids){
                String code=configuredSet(context,id),source=configuredSource(context,id);
                if(code.isEmpty())continue;
                Map<String,JSONObject> rows=bySource.get(source);
                if(rows==null){
                    rows=new HashMap<>();
                    Map<String,String> args=new HashMap<>();
                    args.put("source",source);
                    if("arena".equals(source))args.put("arena","arena");
                    JSONObject result=catalog.sets(args);
                    JSONArray sets=result.optJSONArray("sets");
                    if(sets!=null)for(int i=0;i<sets.length();i++){
                        JSONObject row=sets.optJSONObject(i);
                        if(row!=null)rows.put(row.optString("code","").toLowerCase(Locale.ROOT),row);
                    }
                    bySource.put(source,rows);
                }
                JSONObject row=rows.get(code.toLowerCase(Locale.ROOT));
                if(row==null)continue; // Missing catalog is unknown, not 0% ownership.
                String representative=catalog.representativeSetArt(code,"arena".equals(source));
                if(!representative.isEmpty())row.put("artUrl",representative);
                prefs(context).edit().putString("snapshot_"+id,row.toString()).apply();
                drawSet(context,manager,id);
            }
        }catch(Exception ignored){
            // Index unavailable during Scryfall sync: keep prior valid snapshot.
        }finally{
            if(catalog!=null)catalog.close();
        }
    }

    /** Explicit one-off asset warmup after a user selects a set.
     * Scheduled redraw/rebuild remains entirely offline. */
    static void prefetchSelectedSet(Context context,int widgetId){
        Context app=context.getApplicationContext();
        String code=configuredSet(app,widgetId);
        String source=configuredSource(app,widgetId);
        if(!code.matches("[A-Za-z0-9]{2,12}"))return;
        WORKER.execute(()->{
            CatalogStore catalog=null;
            try{
                catalog=new CatalogStore(app);
                String art=catalog.representativeSetArt(code,"arena".equals(source));
                VaultSetArtwork.prefetch(app,art,code);
            }catch(Exception ignored){
                // Offline setup remains valid; the tile keeps its gradient.
            }finally{
                if(catalog!=null)catalog.close();
            }
            drawSet(app,AppWidgetManager.getInstance(app),widgetId);
        });
    }

    static void drawSet(Context context,AppWidgetManager manager,int id){
        if(configuredSet(context,id).isEmpty())return;
        SharedPreferences data=prefs(context);
        JSONObject selected=null;
        String encoded=data.getString("snapshot_"+id,null);
        if(encoded!=null)try{selected=new JSONObject(encoded);}catch(Exception ignored){}
        if(selected==null){
            selected=new JSONObject();
            try{
                selected.put("name",data.getString("name_"+id,"Catalog not synced"));
                selected.put("code",configuredSet(context,id));
            }catch(Exception ignored){}
        }
        Bundle options=manager.getAppWidgetOptions(id);
        float dp=context.getResources().getDisplayMetrics().density;
        int w=options==null?260:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,260);
        int h=options==null?145:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,145);
        Bitmap picture=VaultSetProgressCard.render(context,selected,configuredSource(context,id),
            Math.round(Math.max(200,w)*dp),Math.round(Math.max(115,h)*dp));
        RemoteViews rv=new RemoteViews(context.getPackageName(),R.layout.widget_set_progress);
        rv.setImageViewBitmap(R.id.widget_set_card,picture);
        rv.setContentDescription(R.id.widget_set_card,
            selected.optString("name","Set")+" · "+
            selected.optInt("owned",0)+" of "+selected.optInt("total",0)+" "+
            configuredSource(context,id)+" cards");
        rv.setOnClickPendingIntent(R.id.widget_set_card,
            openApp(context,id,"set",configuredSet(context,id),configuredSource(context,id)));
        rv.setOnClickPendingIntent(R.id.widget_set_configure,configure(context,id));
        manager.updateAppWidget(id,rv);
    }
}
