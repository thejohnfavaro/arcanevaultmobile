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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Configure each Set Progress widget independently from the existing
 * Scryfall catalog. No new inventory or set-completion ledger is created. */
public final class VaultSetWidgetConfigActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<JSONObject> allSets=new ArrayList<>();
    private final List<JSONObject> visibleSets=new ArrayList<>();
    private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private int requestVersion=0;
    private String source="physical",search="",configuredCode="";
    private Spinner setPicker;
    private ArrayAdapter<String> adapter;
    private Button save;
    private ImageView preview;
    private TextView status;
    private boolean destroyed;

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
        LinearLayout content=VaultNativeStyle.column(this);
        content.setPadding(dp(20),dp(18),dp(20),dp(28));
        content.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        scroll.addView(content);
        setContentView(scroll);
        content.addView(VaultNativeStyle.label(this,"TRACK A MAGIC SET",23,
            VaultNativeStyle.TEXT,true));
        TextView intro=VaultNativeStyle.label(this,
            "Choose a set and collection. Each Set Progress widget tracks its own selection. "+
            "All counts come from the existing offline Arcane Vault catalog.",
            13,VaultNativeStyle.SUBTEXT,false);
        intro.setPadding(0,dp(10),0,dp(16));
        content.addView(intro);
        content.addView(VaultNativeStyle.label(this,"COLLECTION",11,
            VaultNativeStyle.GOLD,true));
        Spinner sourcePicker=new Spinner(this);
        ArrayAdapter<String> sourceAdapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,new String[]{
                "Physical · verified printings","Arena · card names"});
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourcePicker.setAdapter(sourceAdapter);
        sourcePicker.setSelection("arena".equals(source)?1:0);
        content.addView(sourcePicker,new LinearLayout.LayoutParams(-1,dp(49)));
        EditText input=new EditText(this);
        VaultNativeStyle.search(this,input);
        input.setHint("Search sets by name or code");
        LinearLayout.LayoutParams inputParams=new LinearLayout.LayoutParams(-1,dp(49));
        inputParams.topMargin=dp(12);
        content.addView(input,inputParams);
        setPicker=new Spinner(this);
        adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
            new ArrayList<String>());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        setPicker.setAdapter(adapter);
        LinearLayout.LayoutParams pickerParams=new LinearLayout.LayoutParams(-1,dp(53));
        pickerParams.topMargin=dp(8);
        content.addView(setPicker,pickerParams);
        status=VaultNativeStyle.label(this,"Loading available sets…",12,
            VaultNativeStyle.SUBTEXT,false);
        status.setPadding(0,dp(9),0,dp(9));
        content.addView(status);
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_XY);
        content.addView(preview,new LinearLayout.LayoutParams(-1,dp(176)));
        save=VaultNativeStyle.button(this,"Add Set Progress widget",true);
        save.setEnabled(false);
        LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(-1,dp(52));
        buttonParams.topMargin=dp(15);
        content.addView(save,buttonParams);

        sourcePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,android.view.View view,
                    int position,long id){
                String next=position==0?"physical":"arena";
                if(next.equals(source)&&!allSets.isEmpty())return;
                source=next;loadSets();
            }
        });
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                search=s.toString().trim();filterSets();
            }
            public void afterTextChanged(Editable s){}
        });
        setPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,android.view.View view,
                    int position,long id){updatePreview();}
        });
        save.setOnClickListener(v->saveSelection());
        loadSets();
    }

    private void loadSets(){
        int ticket=++requestVersion;
        allSets.clear();visibleSets.clear();
        if(status!=null)status.setText("Reading "+source+" set progress from local catalog…");
        if(save!=null)save.setEnabled(false);
        String requested=source;
        worker.execute(()->{
            List<JSONObject> result=new ArrayList<>();
            String error=null;
            CatalogStore catalog=null;
            try{
                catalog=new CatalogStore(getApplicationContext());
                Map<String,String> args=new HashMap<>();
                args.put("source",requested);
                if("arena".equals(requested))args.put("arena","arena");
                JSONArray rows=catalog.sets(args).optJSONArray("sets");
                if(rows!=null)for(int i=0;i<rows.length();i++){
                    JSONObject item=rows.optJSONObject(i);
                    if(item!=null&&item.optString("code","").matches("[A-Za-z0-9]{2,12}"))
                        result.add(item);
                }
                result.sort((a,b)->a.optString("name","")
                    .compareToIgnoreCase(b.optString("name","")));
            }catch(Exception ex){error=ex.getMessage();}
            finally{if(catalog!=null)catalog.close();}
            final String failure=error;
            main.post(()->{
                if(destroyed||ticket!=requestVersion||isFinishing())return;
                allSets.clear();allSets.addAll(result);
                status.setText(failure==null?
                    allSets.size()+" sets available · choose one below":
                    "Catalog unavailable: "+failure+". Sync card data in Arcane Vault.");
                filterSets();
            });
        });
    }
    private void filterSets(){
        if(adapter==null)return;
        visibleSets.clear();adapter.clear();
        String q=search.toLowerCase(Locale.ROOT);
        for(JSONObject set:allSets){
            String code=set.optString("code","");
            String name=set.optString("name","");
            if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&
                    !code.toLowerCase(Locale.ROOT).contains(q))continue;
            visibleSets.add(set);
            adapter.add(name+" ("+code.toUpperCase(Locale.ROOT)+")");
        }
        adapter.notifyDataSetChanged();
        int pos=0;
        for(int i=0;i<visibleSets.size();i++)
            if(visibleSets.get(i).optString("code","").equalsIgnoreCase(configuredCode)){
                pos=i;break;
            }
        if(!visibleSets.isEmpty())setPicker.setSelection(pos);
        updatePreview();
    }
    private void updatePreview(){
        if(preview==null||save==null)return;
        int selected=setPicker.getSelectedItemPosition();
        boolean valid=selected>=0&&selected<visibleSets.size();
        save.setEnabled(valid);
        JSONObject set=valid?visibleSets.get(selected):null;
        preview.setImageBitmap(VaultSetProgressCard.render(this,set,source,
            dp(430),dp(176)));
    }
    private void saveSelection(){
        int idx=setPicker.getSelectedItemPosition();
        if(idx<0||idx>=visibleSets.size())return;
        JSONObject set=visibleSets.get(idx);
        String code=set.optString("code",""),name=set.optString("name","");
        try{
            VaultWidgets.saveSetChoice(this,widgetId,code,name,source);
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            VaultWidgets.updateSets(this,manager,new int[]{widgetId},null);
            // Fetch this one explicitly chosen set's official assets once.
            // Widget redrawing later is cache-only, including while offline.
            VaultWidgets.prefetchSelectedSet(this,widgetId);
            Intent result=new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
            setResult(RESULT_OK,result);
            finish();
        }catch(Exception error){
            status.setText("Could not save this set: "+error.getMessage());
        }
    }
    private int dp(float n){return VaultNativeStyle.dp(this,n);}
    @Override protected void onDestroy(){
        destroyed=true;requestVersion++;worker.shutdownNow();
        super.onDestroy();
    }
}
