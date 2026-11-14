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
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.graphics.Color;
import java.util.List;
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
    private int setRequestId=0;
    private String setQuery="";
    private String setSource="physical";
    private boolean hideEmptySets=true;
    private boolean hideUnownedSetSlots=false;
    private String setSort=NativeSetProgressFilters.RECENT;
    private static final String PREF_SET_HIDE_EMPTY="native_set_hide_empty_v1";
    private static final String PREF_SET_SORT="native_set_sort_v1";
    private static final String PREF_SET_SCOPE="native_set_source_v1";
    private static final String PREF_SET_HIDE_UNOWNED="native_set_hide_unowned_v1";

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
            hideEmptySets=prefs.getBoolean(PREF_SET_HIDE_EMPTY,true);
            hideUnownedSetSlots=prefs.getBoolean(PREF_SET_HIDE_UNOWNED,false);
            setSort=prefs.getString(PREF_SET_SORT,NativeSetProgressFilters.RECENT);
            if(!java.util.Arrays.asList(NativeSetProgressFilters.SORTS).contains(setSort))
                setSort=NativeSetProgressFilters.RECENT;
            setSource=prefs.getString(PREF_SET_SCOPE,"physical");
            if(!setSource.equals("physical")&&!setSource.equals("arena"))
                setSource="physical";
            LinearLayout settings=VaultNativeStyle.column(activity);
            settings.setPadding(0,dp(7),0,dp(6));
            TextView scopeLabel=VaultNativeStyle.label(activity,"COLLECTION",11,VaultNativeStyle.SUBTEXT,true);
            settings.addView(scopeLabel);
            LinearLayout sourceRow=VaultNativeStyle.row(activity);
            for(String target:new String[]{"physical","arena"}){
                Button select=VaultNativeStyle.button(activity,
                    target.equals("physical")?"Physical printings":"Arena card names",
                    target.equals(setSource));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
                lp.rightMargin=dp(7);
                sourceRow.addView(select,lp);
                select.setOnClickListener(v->{
                    if(setSource.equals(target))return;
                    setSource=target;
                    prefs.edit().putString(PREF_SET_SCOPE,setSource).apply();
                    // Re-render the selected buttons, keeping current search and sort.
                    for(int i=0;i<sourceRow.getChildCount();i++){
                        Button b=(Button)sourceRow.getChildAt(i);
                        boolean selected=(i==0)==setSource.equals("physical");
                        b.setTextColor(selected?VaultNativeStyle.BACKGROUND:VaultNativeStyle.TEXT);
                        b.setBackground(VaultNativeStyle.rounded(activity,
                            selected?VaultNativeStyle.PRIMARY:VaultNativeStyle.ELEVATED,
                            selected?Color.TRANSPARENT:VaultNativeStyle.BORDER,11));
                    }
                    setProgress(setQuery,0);
                });
            }
            settings.addView(sourceRow);
            CheckBox hide=new CheckBox(activity);
            hide.setText("Hide sets with no progress");
            hide.setTextColor(VaultNativeStyle.TEXT);
            hide.setButtonTintList(android.content.res.ColorStateList.valueOf(VaultNativeStyle.PRIMARY));
            hide.setChecked(hideEmptySets);
            hide.setOnCheckedChangeListener((b,checked)->{
                hideEmptySets=checked;
                prefs.edit().putBoolean(PREF_SET_HIDE_EMPTY,checked).apply();
                setProgress(setQuery,0);
            });
            settings.addView(hide);
            TextView sortLabel=VaultNativeStyle.label(activity,"SORT SETS",11,VaultNativeStyle.SUBTEXT,true);
            settings.addView(sortLabel);
            Spinner sort=new Spinner(activity);
            String[] names={"Newest release first","Oldest release first","Set name A–Z","Most complete"};
            ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sort.setAdapter(adapter);
            int selectedIndex=java.util.Arrays.asList(NativeSetProgressFilters.SORTS).indexOf(setSort);
            sort.setSelection(Math.max(0,selectedIndex));
            sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                public void onNothingSelected(AdapterView<?> parent){}
                public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                    String requested=NativeSetProgressFilters.SORTS[position];
                    if(requested.equals(setSort))return;
                    setSort=requested;
                    prefs.edit().putString(PREF_SET_SORT,setSort).apply();
                    setProgress(setQuery,0);
                }
            });
            settings.addView(sort,new LinearLayout.LayoutParams(-1,dp(46)));
            root.addView(settings,2);
            EditText search=new EditText(activity);
            VaultNativeStyle.search(activity,search);
            search.setHint("Search sets by name or code");
            root.addView(search,3,new LinearLayout.LayoutParams(-1,dp(48)));
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){
                    setQuery=s.toString();
                    setProgress(setQuery,0);
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
        page=pageNumber;
        setQuery=filter;
        final int request=++setRequestId;
        final String source=setSource;
        final String order=setSort;
        final boolean hide=hideEmptySets;
        status.setText("Reading "+source+" set progress…");
        executor.execute(()->{
            try{
                Map<String,String> p=new HashMap<>();
                p.put("source",source);
                p.put("arena",source.equals("arena")?"arena":"");
                p.put("q",filter);
                JSONObject result=catalog.sets(p);
                JSONArray sets=result.optJSONArray("sets");
                if(sets==null)sets=new JSONArray();
                List<JSONObject> filtered=NativeSetProgressFilters.select(sets,hide,order);
                int allSets=sets.length();
                ui.post(()->{
                    if(closed||activity.isFinishing()||request!=setRequestId)return;
                    content.removeAllViews();
                    TextView hint=VaultNativeStyle.label(activity,
                        source.equals("physical")?
                        "Verified physical editions only. Name-only ownership cannot establish a set.":
                        "Arena tracks owned card names in Arena-available sets, not exact printings.",
                        12,VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,hint,10,activity);
                    if(filtered.isEmpty())
                        row("No sets match these filters",
                            "Try showing empty sets, changing collection, or clearing the search.",null);
                    int start=pageNumber*40;
                    for(int i=start;i<Math.min(filtered.size(),start+40);i++){
                        JSONObject set=filtered.get(i);
                        String code=set.optString("code","");
                        String name=set.optString("name","Unknown set");
                        int owned=set.optInt("owned",0);
                        int total=set.optInt("total",0);
                        String release=set.optString("releaseDate","");
                        row(name+" · "+code.toUpperCase(java.util.Locale.ROOT),
                            owned+"/"+total+
                            (source.equals("physical")?" verified printings":" Arena card names")+
                            " · "+set.optDouble("percentage",0)+"%"+
                            (release.isEmpty()?"":" · "+release),
                            ()->showSet(code,name,0));
                    }
                    if(start+40<filtered.size())
                        button("Next sets ("+(start+40)+" / "+filtered.size()+")",
                            ()->setProgress(filter,pageNumber+1));
                    if(start>0)button("Previous sets",()->setProgress(filter,pageNumber-1));
                    status.setText(filtered.size()+" shown / "+allSets+" sets · "+
                        NativeSetProgressFilters.title(order));
                });
            }catch(Exception error){
                ui.post(()->{
                    if(!closed&&request==setRequestId)
                        status.setText("Set progress unavailable: "+error.getMessage());
                });
            }
        });
    }
    private void showSet(String set,String title,int pageNumber){
        final int request=++setRequestId;
        final String source=setSource;
        final boolean hideUnowned=hideUnownedSetSlots;
        status.setText("Reading "+title+" printings…");
        executor.execute(()->{
            try {
                Map<String,String> args=new HashMap<>();
                args.put("set",set);args.put("page",Integer.toString(pageNumber));
                args.put("size","48");
                args.put("source",source);
                JSONObject response=source.equals("physical")?
                    catalog.setPrintings(args):catalog.setCards(args);
                JSONArray printings=response.optJSONArray(source.equals("physical")?"printings":"cards");
                if(printings==null)printings=new JSONArray();
                final JSONArray entries=printings;
                ui.post(()->{
                    if(closed||activity.isFinishing()||request!=setRequestId)return;
                    content.removeAllViews();
                    button("← Back to sets",()->setProgress(setQuery,0));
                    CheckBox ownedOnly=new CheckBox(activity);
                    ownedOnly.setText(source.equals("physical")?
                        "Hide unowned printings":"Hide unowned Arena cards");
                    ownedOnly.setTextColor(VaultNativeStyle.TEXT);
                    ownedOnly.setButtonTintList(android.content.res.ColorStateList.valueOf(VaultNativeStyle.PRIMARY));
                    ownedOnly.setChecked(hideUnowned);
                    ownedOnly.setOnCheckedChangeListener((v,checked)->{
                        hideUnownedSetSlots=checked;
                        prefs.edit().putBoolean(PREF_SET_HIDE_UNOWNED,checked).apply();
                        showSet(set,title,0);
                    });
                    content.addView(ownedOnly);
                    TextView hint=VaultNativeStyle.label(activity,
                        "Confirmed copies are based on printing identity, not card name.",
                        12,VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,hint,12,activity);
                    for(int i=0;i<entries.length();i++){
                        JSONObject p=entries.optJSONObject(i);if(p==null)continue;
                        int qty=source.equals("physical")?
                            p.optInt("confirmedPhysicalCopies",0):p.optInt("qty",0);
                        if(hideUnowned&&qty<=0)continue;
                        String name=p.optString("name","");
                        String collector=p.optString("collectorNumber","");
                        row(name+" · #"+collector,
                            (qty>0?"Owned ×"+qty:"Missing")+
                                " · "+p.optString("rarity",""),
                            ()->new AlertDialog.Builder(activity).setTitle(name)
                                .setMessage(title+" #"+collector+"\n"+
                                    (source.equals("physical")?"Confirmed physical copies: ":"Arena copies: ")+qty+"\n"+
                                    "Printing ID: "+p.optString("id",""))
                                .setPositiveButton("Close",null).show());
                    }
                    if((source.equals("physical") && (pageNumber+1)*48<response.optInt("total",0)) ||
                        (source.equals("arena") && response.optBoolean("hasMore",false)))
                        button("Next printings",()->showSet(set,title,pageNumber+1));
                    status.setText(title+" · "+(source.equals("physical")?"Physical printings":"Arena card names"));
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
