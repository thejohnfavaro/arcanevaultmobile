package com.arcanevault.mobile;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Verified set picker: actual indexed sets + Scryfall's official directory.
 * Search displays clickable matches, never synthesizes a set from free text.
 * Only an indexed exact set can provide owned/total completion counts. */
public final class VaultSetWidgetConfigActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> knownSets=new ArrayList<>();
    private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private int loadTicket,previewTicket;
    private String source="physical",query="",configuredCode="";
    private JSONObject selected,selectedProgress;
    private LinearLayout matches;
    private Button save;
    private ImageView preview;
    private TextView status,helper;
    private EditText searchInput;
    private boolean destroyed;
    private boolean directoryLoading=true;
    private int indexedCount=0;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(widgetId);
        if(info!=null&&!new ComponentName(this,VaultSetProgressWidget.class)
            .equals(info.provider)){finish();return;}
        getWindow().setStatusBarColor(VaultNativeStyle.BACKGROUND);
        getWindow().setNavigationBarColor(VaultNativeStyle.BACKGROUND);
        source=VaultWidgets.configuredSource(this,widgetId);
        configuredCode=VaultWidgets.configuredSet(this,widgetId);

        ScrollView scroll=VaultNativeStyle.scroll(this);
        scroll.setFillViewport(true);             // no default-theme grey void
        scroll.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        scroll.setClipToPadding(false);
        NativeSystemInsets.apply(this,scroll);    // Android 15 system bars/cutouts
        LinearLayout body=VaultNativeStyle.column(this);
        body.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        body.setPadding(dp(20),dp(16),dp(20),dp(28));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);

        body.addView(VaultNativeStyle.label(this,"TRACK A MAGIC SET",23,
            VaultNativeStyle.TEXT,true));
        TextView intro=VaultNativeStyle.label(this,
            "Search official Magic set names or codes. Choose one of the results "+
            "to track its verified progress on your home screen.",13,
            VaultNativeStyle.SUBTEXT,false);
        intro.setPadding(0,dp(10),0,dp(16));
        body.addView(intro);
        body.addView(VaultNativeStyle.label(this,"COLLECTION",11,
            VaultNativeStyle.GOLD,true));
        Spinner collection=new Spinner(this);
        ArrayAdapter<String> sources=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,new String[]{
                "Physical · verified printings","Arena · card names"});
        sources.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        collection.setAdapter(sources);
        collection.setSelection("arena".equals(source)?1:0);
        body.addView(collection,new LinearLayout.LayoutParams(-1,dp(48)));

        searchInput=new EditText(this);
        VaultNativeStyle.search(this,searchInput);
        searchInput.setSingleLine(true);
        searchInput.setHint("Search set name or code (e.g. Star Trek or TRK)");
        LinearLayout.LayoutParams inputMargin=new LinearLayout.LayoutParams(-1,dp(50));
        inputMargin.topMargin=dp(12);
        body.addView(searchInput,inputMargin);
        helper=VaultNativeStyle.label(this,"Loading set directory…",12,
            VaultNativeStyle.SUBTEXT,false);
        helper.setPadding(0,dp(9),0,dp(8));
        body.addView(helper);
        matches=VaultNativeStyle.column(this);
        body.addView(matches);

        status=VaultNativeStyle.label(this,
            "Select a result to see the set's actual completion.",12,
            VaultNativeStyle.SUBTEXT,false);
        status.setPadding(0,dp(10),0,dp(10));
        body.addView(status);
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        body.addView(preview,new LinearLayout.LayoutParams(-1,dp(162)));

        save=VaultNativeStyle.button(this,"Select a set to add widget",true);
        save.setEnabled(false);
        LinearLayout.LayoutParams buttons=new LinearLayout.LayoutParams(-1,dp(52));
        buttons.topMargin=dp(16);
        body.addView(save,buttons);
        save.setOnClickListener(v->saveSelection());
        collection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View v,int position,long id){
                source=position==1?"arena":"physical";
                resolveProgress();
            }
        });
        searchInput.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                query=s.toString().trim();
                selected=null;selectedProgress=null;
                ++previewTicket;
                renderCandidates();
                updatePreview();
            }
            public void afterTextChanged(Editable s){}
        });
        loadDirectory();
    }

    private void loadDirectory(){
        final int ticket=++loadTicket;
        directoryLoading=true;
        worker.execute(()->{
            JSONArray indexed=new JSONArray();
            CatalogStore catalog=null;
            try{
                catalog=new CatalogStore(getApplicationContext());
                indexed=catalog.indexedSetNames();
            }catch(Exception ignored){}
            finally{if(catalog!=null)catalog.close();}
            final JSONArray local=indexed;
            main.post(()->{
                if(destroyed||ticket!=loadTicket||isFinishing())return;
                indexedCount=local.length();
                applyDirectory(local);
                helper.setText(indexedCount>0?
                    indexedCount+" indexed sets · checking official set names…":
                    "No local set index yet · fetching official set names…");
            });
            JSONArray official=VaultSetDirectory.official(getApplicationContext());
            final JSONArray merged=VaultSetDirectory.merge(local,official);
            main.post(()->{
                if(destroyed||ticket!=loadTicket||isFinishing())return;
                directoryLoading=false;
                applyDirectory(merged);
                if(knownSets.isEmpty())
                    helper.setText("Set directory unavailable. Connect to the internet "+
                        "or open Arcane Vault → Data & Sync to download the catalog.");
                else
                    helper.setText(knownSets.size()+" verified set names available · "+
                        indexedCount+" indexed locally");
            });
        });
    }

    private void applyDirectory(JSONArray items){
        String chosen=selected!=null?selected.optString("code",""):
            (query.isEmpty()?configuredCode:"");
        knownSets.clear();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&item.optString("code","").matches("[a-z0-9]{2,12}"))
                knownSets.add(item);
        }
        knownSets.sort(Comparator.comparing(x->
            x.optString("name","").toLowerCase(Locale.ROOT)));
        if(!chosen.isEmpty())for(JSONObject set:knownSets){
            if(chosen.equalsIgnoreCase(set.optString("code",""))){
                selected=set;resolveProgress();break;
            }
        }
        renderCandidates();
        updatePreview();
    }

    private int relevance(JSONObject row,String text){
        String code=row.optString("code","").toLowerCase(Locale.ROOT);
        String name=row.optString("name","").toLowerCase(Locale.ROOT);
        if(text.equals(code))return 0;
        if(code.startsWith(text))return 1;
        if(name.startsWith(text))return 2;
        return name.contains(text)?3:99;
    }

    private void renderCandidates(){
        if(matches==null)return;
        matches.removeAllViews();
        if(query.isEmpty()){
            if(selected==null)helper.setText(directoryLoading?
                "Loading official Magic set names…":"Type a set name or code above");
            return;
        }
        List<JSONObject> found=new ArrayList<>();
        for(JSONObject row:knownSets)
            if(relevance(row,query.toLowerCase(Locale.ROOT))<99)found.add(row);
        found.sort((a,b)->{
            int cmp=Integer.compare(relevance(a,query.toLowerCase(Locale.ROOT)),
                relevance(b,query.toLowerCase(Locale.ROOT)));
            return cmp!=0?cmp:a.optString("name","")
                .compareToIgnoreCase(b.optString("name",""));
        });
        if(found.isEmpty()){
            String help=directoryLoading?"Searching local catalog; loading official sets…":
                "No official set matched \""+query+"\". Try a set name or its actual code.";
            if("stk".equalsIgnoreCase(query))
                help="Star Trek's main set is TRK (Commander is TRC), not STK.";
            helper.setText(help);
            return;
        }
        helper.setText(found.size()+" matches · select a set below");
        int show=Math.min(8,found.size());
        for(int i=0;i<show;i++){
            JSONObject row=found.get(i);
            String code=row.optString("code","").toUpperCase(Locale.ROOT);
            String title=row.optString("name",code);
            String label=title+"   ·   "+code;
            Button candidate=VaultNativeStyle.button(this,label,false);
            candidate.setTextSize(13);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(43));
            params.bottomMargin=dp(4);
            matches.addView(candidate,params);
            candidate.setOnClickListener(v->selectSet(row));
        }
        if(found.size()>show){
            TextView more=VaultNativeStyle.label(this,
                "Showing top "+show+" matches. Refine the search to narrow results.",
                11,VaultNativeStyle.SUBTEXT,false);
            matches.addView(more);
        }
    }

    private void selectSet(JSONObject item){
        selected=item;
        selectedProgress=null;
        resolveProgress();
    }

    private void resolveProgress(){
        final int ticket=++previewTicket;
        if(selected==null){updatePreview();return;}
        String code=selected.optString("code","");
        String tracking=source;
        status.setText("Checking indexed "+tracking+" progress for "+
            selected.optString("name",code)+"…");
        updatePreview();
        worker.execute(()->{
            JSONObject progress=null;
            CatalogStore db=null;
            try{
                db=new CatalogStore(getApplicationContext());
                progress=db.indexedSetProgress(code,tracking);
            }catch(Exception error){
                android.util.Log.w("ArcaneVault","Set widget preview could not read index",error);
            }finally{if(db!=null)db.close();}
            final JSONObject verified=progress;
            main.post(()->{
                if(destroyed||ticket!=previewTicket||isFinishing())return;
                selectedProgress=verified;
                if(verified==null){
                    status.setText("Set name verified. Card progress is unavailable "+
                        "until Android's Scryfall catalog is synced in Data & Sync.");
                }else{
                    status.setText(verified.optInt("owned",0)+" / "+
                        verified.optInt("total",0)+" "+
                        ("arena".equals(tracking)?"Arena card names":"verified physical printings")+
                        " owned");
                }
                updatePreview();
            });
        });
    }

    private void updatePreview(){
        if(preview==null||save==null)return;
        save.setEnabled(selected!=null);
        save.setText(selected==null?"Select a set to add widget":
            "Add Set Progress widget · "+selected.optString("code","").toUpperCase(Locale.ROOT));
        JSONObject card=selectedProgress!=null?selectedProgress:selected;
        int screen=getResources().getDisplayMetrics().widthPixels;
        preview.setImageBitmap(VaultSetProgressCard.render(this,card,source,
            Math.max(dp(230),screen-dp(40)),dp(162)));
    }

    private void saveSelection(){
        if(selected==null){status.setText("Select an official search result first.");return;}
        String code=selected.optString("code",""),name=selected.optString("name","");
        if(!code.matches("[a-z0-9]{2,12}")){
            status.setText("The selected set code is invalid.");return;
        }
        save.setEnabled(false);
        try{
            VaultWidgets.saveSetChoice(this,widgetId,code,name,source);
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            VaultWidgets.updateSets(this,manager,new int[]{widgetId},null);
            VaultWidgets.prefetchSelectedSet(this,widgetId);
            Intent result=new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
            setResult(RESULT_OK,result);
            finish();
        }catch(Exception failure){
            save.setEnabled(true);
            status.setText("Could not add widget: "+failure.getMessage());
        }
    }

    private int dp(float units){return VaultNativeStyle.dp(this,units);}
    @Override protected void onDestroy(){
        destroyed=true;++loadTicket;++previewTicket;
        worker.shutdownNow();
        super.onDestroy();
    }
}
