package com.arcanevault.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native read-only set browser and offline-first data/sync tools. */
final class NativeWorkspaceScreen {
    interface DeckLoader { void fetch(DeckCallback callback); }
    interface DeckCallback { void accept(String json); }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final CatalogStore catalog;
    private final Runnable legacy;
    private final DeckLoader decks;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final LinearLayout root;
    private final LinearLayout content;
    private final TextView status;
    private volatile boolean closed;
    private int page=0;

    private NativeWorkspaceScreen(Activity activity,SharedPreferences prefs,CatalogStore catalog,
            Runnable legacy,DeckLoader decks,String section){
        this.activity=activity;this.prefs=prefs;this.catalog=catalog;
        this.legacy=legacy;this.decks=decks;
        root=VaultNativeStyle.column(activity);
        root.setPadding(dp(18),dp(14),dp(18),dp(14));
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        String title=section.equals("sets")?"Set Progress":section.equals("decks")?"Deck Library":"Data & Sync";
        root.addView(VaultNativeStyle.label(activity,title,24,VaultNativeStyle.TEXT,true));
        status=VaultNativeStyle.label(activity,"Loading local data…",12,VaultNativeStyle.SUBTEXT,false);
        status.setPadding(0,dp(9),0,dp(11));
        root.addView(status);
        ScrollView scroll=new ScrollView(activity);
        content=VaultNativeStyle.column(activity);
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(section.equals("sets"))setProgress("",0);
        else if(section.equals("decks"))deckLibrary();
        else dataSync();
    }
    static NativeWorkspaceScreen create(Activity activity,SharedPreferences prefs,CatalogStore catalog,
            Runnable legacy,DeckLoader decks,String section){
        return new NativeWorkspaceScreen(activity,prefs,catalog,legacy,decks,section);
    }
    View view(){return root;}
    void close(){closed=true;executor.shutdownNow();}
    private int dp(float x){return VaultNativeStyle.dp(activity,x);}
    private void row(String title,String detail,Runnable action){
        LinearLayout entry=VaultNativeStyle.column(activity);
        entry.setPadding(dp(14),dp(12),dp(14),dp(12));
        VaultNativeStyle.surface(activity,entry,false);
        TextView a=VaultNativeStyle.label(activity,title,15,VaultNativeStyle.TEXT,true);
        entry.addView(a);
        if(detail!=null&&!detail.isEmpty()){
            TextView b=VaultNativeStyle.label(activity,detail,12,VaultNativeStyle.SUBTEXT,false);
            b.setPadding(0,dp(5),0,0);entry.addView(b);
        }
        if(action!=null)entry.setOnClickListener(v->action.run());
        VaultNativeStyle.margin(content,entry,8,activity);
    }
    private Button button(String label,Runnable action){
        Button b=VaultNativeStyle.button(activity,label,false);
        VaultNativeStyle.margin(content,b,9,activity);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void setProgress(String filter,int pageNumber){
        page=pageNumber;status.setText("Reading official set progress…");
        executor.execute(()->{
            try{
                Map<String,String> p=new HashMap<>();
                p.put("source","physical");p.put("q",filter);
                JSONObject result=catalog.sets(p);
                JSONArray sets=result.optJSONArray("sets");
                if(sets==null)sets=new JSONArray();
                JSONArray finalSets=sets;
                ui.post(()->{
                    if(closed||activity.isFinishing())return;
                    content.removeAllViews();
                    TextView hint=VaultNativeStyle.label(activity,
                        "Physical ownership by verified printing. Name-only cards are not assigned to sets.",
                        12,VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,hint,10,activity);
                    EditText search=new EditText(activity);
                    VaultNativeStyle.search(activity,search);
                    search.setHint("Search sets");
                    search.setText(filter);
                    VaultNativeStyle.margin(content,search,10,activity);
                    search.addTextChangedListener(new TextWatcher(){
                        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                        public void onTextChanged(CharSequence s,int start,int before,int count){
                            setProgress(s.toString(),0);
                        }
                        public void afterTextChanged(Editable e){}
                    });
                    int start=pageNumber*40;
                    for(int i=start;i<Math.min(finalSets.length(),start+40);i++){
                        JSONObject set=finalSets.optJSONObject(i);
                        if(set==null)continue;
                        row(set.optString("name","Unknown set")+" · "+
                            set.optString("code","").toUpperCase(),
                            set.optInt("owned",0)+"/"+set.optInt("total",0)+
                            " printings · "+set.optDouble("percentage",0)+"% · "+
                            set.optString("releaseDate",""),null);
                    }
                    if(start+40<finalSets.length())
                        button("Show more sets",()->setProgress(filter,pageNumber+1));
                    status.setText(finalSets.length()+" sets · physical printing progress");
                });
            }catch(Exception error){
                ui.post(()->{if(!closed)status.setText("Set progress unavailable: "+error.getMessage());});
            }
        });
    }
    private void deckLibrary(){
        status.setText("Reading saved deck library from the existing app…");
        decks.fetch(json->ui.post(()->{
            if(closed||activity.isFinishing())return;
            content.removeAllViews();
            try{
                JSONArray library=new JSONArray(json);
                status.setText(library.length()+" saved decks · read-only");
                if(library.length()==0)row("No saved decks","Save decks in Legacy tools to see them here.",null);
                for(int i=0;i<Math.min(library.length(),300);i++){
                    JSONObject d=library.optJSONObject(i);if(d==null)continue;
                    JSONArray entries=d.optJSONArray("entries");
                    int qty=1;
                    if(entries!=null)for(int n=0;n<entries.length();n++)
                        qty+=entries.optJSONObject(n)==null?0:entries.optJSONObject(n).optInt("qty",0);
                    String name=d.optString("name","Untitled deck");
                    String commander=d.optString("commander","");
                    int count=qty;
                    row(name,commander+" · "+qty+" total cards · "+d.optString("folder","Unfiled"),
                        ()->new AlertDialog.Builder(activity).setTitle(name)
                            .setMessage("Commander: "+commander+"\n"+
                                count+" cards · "+(entries==null?0:entries.length())+" unique mainboard entries"+
                                "\n\nFull editing and import remain in Legacy tools.")
                            .setPositiveButton("Close",null).show());
                }
                button("Open deck editing tools",legacy);
            }catch(Exception e){
                status.setText("Could not load deck library: "+e.getMessage());
                button("Open Legacy deck library",legacy);
            }
        }));
    }
    private void dataSync(){
        content.removeAllViews();
        status.setText("Local-first tools · existing collection preserved");
        row("Collection storage","Physical and Arena inventories are stored separately in native Android storage.",null);
        row("Safe recovery snapshot",prefs.contains("vault_nativePhysicalBackupV1")?
            "A prior physical collection snapshot is available. Restore only if needed.":
            "No native edit recovery snapshot yet.",null);
        button("Export physical inventory backup",()->{
            String raw=prefs.getString("vault_physicalCollectionV1","[]");
            android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(android.content.Intent.EXTRA_SUBJECT,"Arcane Vault physical inventory backup");
            send.putExtra(android.content.Intent.EXTRA_TEXT,raw);
            activity.startActivity(android.content.Intent.createChooser(send,"Save physical collection backup"));
        });
        button("Export Arena inventory backup",()->{
            String raw=prefs.getString("vault_cards","[]");
            android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(android.content.Intent.EXTRA_SUBJECT,"Arcane Vault Arena inventory backup");
            send.putExtra(android.content.Intent.EXTRA_TEXT,raw);
            activity.startActivity(android.content.Intent.createChooser(send,"Save Arena collection backup"));
        });
        button("Restore pre-edit physical snapshot",()->{
            String old=prefs.getString("vault_nativePhysicalBackupV1",null);
            if(old==null){info("No native editing snapshot exists.");return;}
            new AlertDialog.Builder(activity).setTitle("Restore physical inventory?")
                .setMessage("This restores only the prior native physical edit snapshot, not Arena. "+
                    "A copy of the current collection will be kept as the new recovery snapshot.")
                .setNegativeButton("Cancel",null).setPositiveButton("Restore", (d,w)->{
                    executor.execute(()->{
                        try {
                            JSONArray values=new JSONArray(old);
                            if(values.length()>150000)throw new IllegalStateException("Inventory too large");
                            String current=prefs.getString("vault_physicalCollectionV1","[]");
                            if(!prefs.edit().putString("vault_physicalCollectionV1",old)
                                .putString("vault_nativePhysicalBackupV1",current).commit())
                                throw new IllegalStateException("Durable restoration did not complete");
                            catalog.physicalOwnership(new JSONObject().put("cards",values));
                            ui.post(()->info("Physical snapshot restored. Reopen the catalog to refresh."));
                        }catch(Exception e){ui.post(()->info("Restore failed: "+e.getMessage()));}
                    });
                }).show();
        });
        button("Full Scryfall catalog sync",()->new AlertDialog.Builder(activity)
            .setTitle("Sync official Scryfall catalog?")
            .setMessage("This downloads the official card catalog and printings. "+
                "It may require network data and does not intentionally clear your collections.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Sync", (d,w)->{
                boolean started=catalog.start();
                info(started?"Scryfall catalog synchronization started. Return here to check status.":
                    "Catalog sync is already running.");
            }).show());
        button("Check Scryfall sync status",()->{
            try {
                JSONObject state=catalog.status();
                info(state.optString("phase","Unknown")+"\n"+
                    state.optInt("imported",0)+" imported · "+
                    state.optInt("printings",0)+" printings"+
                    (state.optString("error","").isEmpty()?"":"\n"+state.optString("error","")));
            }catch(Exception e){info("Sync status unavailable: "+e.getMessage());}
        });
        button("Open advanced imports, QR and USB sync",legacy);
        row("Updater and security","Signed updates are opt-in. Do not dismiss Play Protect warnings without reviewing their details.",null);
        button("Check for app updates",()->new NativeAppUpdater(activity).check());
    }
    private void info(String message){
        new AlertDialog.Builder(activity).setTitle("Arcane Vault")
            .setMessage(message).setPositiveButton("OK",null).show();
    }
}
