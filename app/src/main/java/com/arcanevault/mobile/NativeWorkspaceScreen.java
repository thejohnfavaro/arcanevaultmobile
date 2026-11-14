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
        if(section.equals("sets")){
            EditText search=new EditText(activity);
            VaultNativeStyle.search(activity,search);
            search.setHint("Search physical set progress");
            root.addView(search,2,new LinearLayout.LayoutParams(-1,dp(48)));
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){
                    setProgress(s.toString(),0);
                }
                public void afterTextChanged(Editable e){}
            });
            setProgress("",0);
        }
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
                    int start=pageNumber*40;
                    for(int i=start;i<Math.min(finalSets.length(),start+40);i++){
                        JSONObject set=finalSets.optJSONObject(i);
                        if(set==null)continue;
                        row(set.optString("name","Unknown set")+" · "+
                            set.optString("code","").toUpperCase(),
                            set.optInt("owned",0)+"/"+set.optInt("total",0)+
                            " printings · "+set.optDouble("percentage",0)+"% · "+
                            set.optString("releaseDate",""),()->showSet(set.optString("code",""),
                                set.optString("name","Unknown set"),0));
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
    private void showSet(String set,String title,int pageNumber){
        status.setText("Reading "+title+" printings…");
        executor.execute(()->{
            try {
                Map<String,String> args=new HashMap<>();
                args.put("set",set);args.put("page",Integer.toString(pageNumber));
                args.put("size","48");
                JSONObject response=catalog.setPrintings(args);
                JSONArray printings=response.optJSONArray("printings");
                if(printings==null)printings=new JSONArray();
                final JSONArray entries=printings;
                ui.post(()->{
                    if(closed||activity.isFinishing())return;
                    content.removeAllViews();
                    button("← Back to sets",()->setProgress("",0));
                    TextView hint=VaultNativeStyle.label(activity,
                        "Confirmed copies are based on printing identity, not card name.",
                        12,VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,hint,12,activity);
                    for(int i=0;i<entries.length();i++){
                        JSONObject p=entries.optJSONObject(i);if(p==null)continue;
                        int qty=p.optInt("confirmedPhysicalCopies",0);
                        String name=p.optString("name","");
                        String collector=p.optString("collectorNumber","");
                        row(name+" · #"+collector,
                            (qty>0?"Owned ×"+qty:"Missing")+
                                " · "+p.optString("rarity",""),
                            ()->new AlertDialog.Builder(activity).setTitle(name)
                                .setMessage(title+" #"+collector+"\n"+
                                    "Confirmed physical copies: "+qty+"\n"+
                                    "Printing ID: "+p.optString("id",""))
                                .setPositiveButton("Close",null).show());
                    }
                    if((pageNumber+1)*48<response.optInt("total",0))
                        button("Next printings",()->showSet(set,title,pageNumber+1));
                    status.setText(title+" · "+response.optInt("total",0)+" printings");
                });
            }catch(Exception e){ui.post(()->{if(!closed)status.setText("Set details unavailable: "+e.getMessage());});}
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
                        ()->showDeck(d));
                }
                button("Open deck editing tools",legacy);
            }catch(Exception e){
                status.setText("Could not load deck library: "+e.getMessage());
                button("Open Legacy deck library",legacy);
            }
        }));
    }
    private void showDeck(JSONObject deck){
        executor.execute(()->{
            try{
                Map<String,Integer> owned=new HashMap<>();
                for(String key:new String[]{"vault_physicalCollectionV1","vault_cards"}){
                    JSONArray cards=new JSONArray(prefs.getString(key,"[]"));
                    if(cards.length()>150000)throw new IllegalStateException("Inventory safety limit exceeded");
                    for(int i=0;i<cards.length();i++){
                        JSONObject card=cards.optJSONObject(i);if(card==null)continue;
                        String name=card.optString("name","").toLowerCase(java.util.Locale.ROOT);
                        owned.put(name,owned.getOrDefault(name,0)+Math.max(0,card.optInt("qty",0)));
                    }
                }
                JSONArray entries=deck.optJSONArray("entries");
                StringBuilder list=new StringBuilder("Commander: "+
                    deck.optString("commander","")+
                    "\nCombined physical/Arena ownership · read-only\n\n");
                int missing=0;
                if(entries!=null)for(int i=0;i<entries.length();i++){
                    JSONObject e=entries.optJSONObject(i);
                    if(e==null)continue;
                    String name=e.optString("name","");
                    int qty=Math.max(0,e.optInt("qty",0));
                    int have=owned.getOrDefault(name.toLowerCase(java.util.Locale.ROOT),0);
                    int need=Math.max(0,qty-have);
                    missing+=need;
                    list.append(qty).append(" × ").append(name);
                    if(need>0)list.append("  · missing ").append(need);
                    list.append("\n");
                }
                int missingCount=missing;
                ui.post(()->{
                    if(closed||activity.isFinishing())return;
                    TextView text=VaultNativeStyle.label(activity,list.toString(),14,
                        VaultNativeStyle.TEXT,false);
                    text.setPadding(dp(18),dp(12),dp(18),dp(12));
                    ScrollView scroll=new ScrollView(activity);scroll.addView(text);
                    new AlertDialog.Builder(activity)
                        .setTitle(deck.optString("name","Untitled deck")+
                            (missingCount>0?" · "+missingCount+" missing":" · owned"))
                        .setView(scroll).setPositiveButton("Close",null)
                        .setNeutralButton("Edit in Legacy", (d,w)->legacy.run()).show();
                });
            }catch(Exception e){ui.post(()->info("Deck details unavailable: "+e.getMessage()));}
        });
    }

    private void dataSync(){
        content.removeAllViews();
        status.setText("Local-first tools · existing collection preserved");
        row("Collection storage","Physical and Arena inventories are stored separately in native Android storage.",null);
        row("Safe recovery snapshot",prefs.contains("vault_nativePhysicalBackupV1")?
            "A prior physical collection snapshot is available. Restore only if needed.":
            "No native edit recovery snapshot yet.",null);
        button("Export physical inventory backup",()->shareInventory("physical"));
        button("Export Arena inventory backup",()->shareInventory("arena"));
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
                            try {
                                catalog.physicalOwnership(new JSONObject().put("cards",values));
                            } catch(Exception failure) {
                                // Never leave native inventory and physical SQLite
                                // ownership mirror knowingly out of sync.
                                boolean reversed=prefs.edit()
                                    .putString("vault_physicalCollectionV1",current)
                                    .putString("vault_nativePhysicalBackupV1",old).commit();
                                if(!reversed)throw new IllegalStateException(
                                    "Recovery index failed and local rollback did not persist; keep existing backups",failure);
                                catalog.physicalOwnership(new JSONObject().put("cards",new JSONArray(current)));
                                throw new IllegalStateException("Recovery reindex failed; original inventory restored",failure);
                            }
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
        row("PC ↔ Android peer sync",
            "Export a lossless snapshot or preview incoming changes before synchronization. "+
            "Peer preview never overwrites your data.",null);
        row("Wi-Fi peer exchange (trusted home network)",
            "Transfer snapshots wirelessly with a one-time link from Windows. "+
            "Five-minute expiry; review-only and no automatic merging.",null);
        button("Receive Windows sync over Wi-Fi",
            ()->((MainActivity)activity).wirelessPeerReview(false));
        button("Send phone sync over Wi-Fi",
            ()->((MainActivity)activity).wirelessPeerReview(true));
        button("Export peer sync file",()->((MainActivity)activity).exportPeerSync());
        button("Preview Windows / Android peer file",
            ()->((MainActivity)activity).previewPeerSync());
        button("Three-way compare (known shared baseline)",
            ()->((MainActivity)activity).previewThreeWayPeerSync());
        button("Open advanced imports, QR and USB sync",legacy);
        row("Updater and security","Signed updates are opt-in. Do not dismiss Play Protect warnings without reviewing their details.",null);
        button("Check for app updates",()->new NativeAppUpdater(activity).check());
    }
    private void shareInventory(String kind){
        executor.execute(()->{
            try{
                String filename="physical".equals(kind)?"physical.json":"arena.json";
                String key="physical".equals(kind)?"vault_physicalCollectionV1":"vault_cards";
                String raw=prefs.getString(key,"[]");
                if(raw==null)raw="[]";
                if(raw.length()>20_000_000)throw new IllegalStateException("Backup exceeds 20 MB safety limit");
                new JSONArray(raw); // Never publish an invalid JSON backup.
                java.io.File dir=new java.io.File(activity.getCacheDir(),"native-backups");
                if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot stage backup");
                java.io.File output=new java.io.File(dir,filename);
                try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)){
                    stream.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    stream.getFD().sync();
                }
                android.net.Uri uri=android.net.Uri.parse("content://"+
                    activity.getPackageName()+".backups/"+filename);
                ui.post(()->{
                    if(closed||activity.isFinishing())return;
                    android.content.Intent share=new android.content.Intent(
                        android.content.Intent.ACTION_SEND);
                    share.setType("application/json");
                    share.putExtra(android.content.Intent.EXTRA_STREAM,uri);
                    share.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(android.content.Intent.createChooser(share,
                        "Save Arcane Vault "+kind+" backup"));
                });
            }catch(Exception e){ui.post(()->info("Could not prepare backup: "+e.getMessage()));}
        });
    }
    private void info(String message){
        new AlertDialog.Builder(activity).setTitle("Arcane Vault")
            .setMessage(message).setPositiveButton("OK",null).show();
    }
}
