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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native set browser, safe deck editing, and offline-first data/sync tools. */
final class NativeWorkspaceScreen {
    interface DeckLoader { void fetch(DeckCallback callback); }
    interface DeckCallback { void accept(String json); }
    interface DeckSaver { void save(JSONObject original, JSONObject revised, DeckSaveCallback callback); }
    interface DeckSaveCallback { void accept(boolean success, String message); }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final CatalogStore catalog;
    private final ArtworkCache artwork;
    private final Runnable legacy;
    private final DeckLoader decks;
    private final DeckSaver deckSaver;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ExecutorService coverWorkers=Executors.newFixedThreadPool(2);
    private final ArrayList<DeckCoverTarget> deckCoverTargets=new ArrayList<>();
    private final Set<String> coverInFlight=new HashSet<>();
    private final Set<String> coverUnavailable=new HashSet<>();
    private final LinkedHashMap<String,Bitmap> coverBitmaps=new LinkedHashMap<String,Bitmap>(40,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> eldest){
            return size()>32;
        }
    };
    private final ScrollView workspaceScroll;
    private final LinearLayout root;
    private final LinearLayout content;
    private final TextView status;
    private volatile boolean closed;
    private int page=0;
    private String syncSection="devices";
    private String deckCommanderFilter="";
    private int deckPageGeneration;
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
            ArtworkCache artwork,Runnable legacy,DeckLoader decks,DeckSaver deckSaver,String section,String initialCommander){
        this.activity=activity;this.prefs=prefs;this.catalog=catalog;this.artwork=artwork;
        this.deckCommanderFilter=initialCommander==null?"":initialCommander.trim();
        this.legacy=legacy;this.decks=decks;this.deckSaver=deckSaver;
        root=VaultNativeStyle.column(activity);
        root.setPadding(dp(18),dp(14),dp(18),dp(14));
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        String title=section.equals("sets")?"Set Progress":section.equals("decks")?"Deck Library":"Data & Sync";
        root.addView(VaultNativeStyle.label(activity,title,24,VaultNativeStyle.TEXT,true));
        status=VaultNativeStyle.label(activity,"Loading local data…",12,VaultNativeStyle.SUBTEXT,false);
        status.setPadding(0,dp(9),0,dp(11));
        root.addView(status);
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        workspaceScroll=scroll;
        workspaceScroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->loadVisibleDeckCovers());
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
            ArtworkCache artwork,Runnable legacy,DeckLoader decks,DeckSaver saver,String section){
        return create(activity,prefs,catalog,artwork,legacy,decks,saver,section,"");
    }
    static NativeWorkspaceScreen create(Activity activity,SharedPreferences prefs,CatalogStore catalog,
            ArtworkCache artwork,Runnable legacy,DeckLoader decks,DeckSaver saver,String section,String initialCommander){
        return new NativeWorkspaceScreen(activity,prefs,catalog,artwork,legacy,decks,saver,section,initialCommander);
    }
    View view(){return root;}
    void close(){closed=true;executor.shutdownNow();coverWorkers.shutdownNow();deckCoverTargets.clear();coverInFlight.clear();}
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
        if(action!=null){
            entry.setClickable(true);
            entry.setFocusable(true);
            entry.setContentDescription(title+(detail==null?"":". "+detail));
            entry.setOnClickListener(v->action.run());
        }
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
        status.setText("Reading "+title+(source.equals("physical")?" printings":" Arena cards")+"…");
        executor.execute(()->{
            try {
                Map<String,String> args=new HashMap<>();
                args.put("set",set);args.put("page",Integer.toString(pageNumber));
                args.put("size","48");
                args.put("source",source);
                args.put("arena",source.equals("arena")?"arena":"");
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
                        source.equals("physical")?
                            "Confirmed copies use exact printing identity, not card name.":
                            "Arena set coverage uses owned card names, not physical printings.",
                        12,VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,hint,12,activity);
                    for(int i=0;i<entries.length();i++){
                        JSONObject p=entries.optJSONObject(i);if(p==null)continue;
                        int qty=source.equals("physical")?
                            p.optInt("confirmedPhysicalCopies",0):p.optInt("qty",0);
                        if(hideUnowned&&qty<=0)continue;
                        String name=p.optString("name","");
                        String collector=p.optString("collectorNumber","");
                        row(name+(source.equals("physical")?" · #"+collector:""),
                            (qty>0?"Owned ×"+qty:"Missing")+
                                " · "+p.optString("rarity",""),
                            ()->new AlertDialog.Builder(activity).setTitle(name)
                                .setMessage(title+(source.equals("physical")?" #"+collector:"")+"\n"+
                                    (source.equals("physical")?"Confirmed physical copies: ":"Arena copies: ")+qty+"\n"+
                                    (source.equals("physical")?"Printing ID: "+p.optString("id",""):"Arena card-name ownership"))
                                .setPositiveButton("Close",null).show());
                    }
                    if((source.equals("physical") && (pageNumber+1)*48<response.optInt("total",0)) ||
                        (source.equals("arena") && response.optBoolean("hasMore",false)))
                        button(source.equals("physical")?"Next printings":"Next Arena cards",()->showSet(set,title,pageNumber+1));
                    status.setText(title+" · "+(source.equals("physical")?"Physical printings":"Arena card names"));
                });
            }catch(Exception e){ui.post(()->{if(!closed)status.setText("Set details unavailable: "+e.getMessage());});}
        });
    }

    /** Card-art deck boxes share the existing local catalog and bounded
     * Scryfall artwork cache. The cover represents the commander, not a
     * verified owned printing or a change to saved deck data. */
    private static final class DeckCoverTarget {
        final ImageView image;
        final String commander;
        boolean loaded;
        DeckCoverTarget(ImageView image,String commander){
            this.image=image;this.commander=commander;
        }
    }

    private void deckCoverRow(JSONObject deck,int count,int generation){
        String name=deck.optString("name","Untitled deck");
        String commander=deck.optString("commander","").trim();
        String format=deck.optString("format","").trim();
        if(format.isEmpty())format=commander.isEmpty()?"Saved Deck":"Commander";
        String folder=deck.optString("folder","Unfiled");
        LinearLayout entry=VaultNativeStyle.row(activity);
        entry.setPadding(dp(10),dp(10),dp(12),dp(10));
        VaultNativeStyle.surface(activity,entry,false);
        entry.setClickable(true);
        entry.setFocusable(true);
        entry.setContentDescription(name+", commander "+
            (commander.isEmpty()?"not specified":commander)+", "+count+
            " cards. Open saved deck");

        // Same deck-box proportions and bottom format ribbon as Windows.
        FrameLayout cover=new FrameLayout(activity);
        cover.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.ELEVATED,VaultNativeStyle.BORDER,9));
        cover.setClipToOutline(true);
        cover.setClipChildren(true);
        TextView placeholder=VaultNativeStyle.label(activity,"✦",35,
            VaultNativeStyle.PRIMARY,true);
        placeholder.setGravity(Gravity.CENTER);
        cover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        ImageView art=new ImageView(activity);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setContentDescription("Illustrative commander card artwork for "+
            (commander.isEmpty()?name:commander)+", not proof of owned printing");
        cover.addView(art,new FrameLayout.LayoutParams(-1,-1));
        TextView ribbon=VaultNativeStyle.label(activity,format,10,Color.WHITE,true);
        ribbon.setGravity(Gravity.CENTER);
        ribbon.setMaxLines(1);
        ribbon.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ribbon.setBackgroundColor(Color.argb(218,13,17,26));
        FrameLayout.LayoutParams ribbonParams=new FrameLayout.LayoutParams(
            -1,dp(25),Gravity.BOTTOM);
        cover.addView(ribbon,ribbonParams);
        LinearLayout.LayoutParams coverParams=new LinearLayout.LayoutParams(dp(94),dp(128));
        coverParams.rightMargin=dp(13);
        entry.addView(cover,coverParams);

        LinearLayout details=VaultNativeStyle.column(activity);
        TextView title=VaultNativeStyle.label(activity,name,17,VaultNativeStyle.TEXT,true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(title);
        TextView leader=VaultNativeStyle.label(activity,
            commander.isEmpty()?"Commander not specified":commander,12,
            VaultNativeStyle.PRIMARY,false);
        leader.setPadding(0,dp(6),0,dp(5));
        leader.setMaxLines(2);
        leader.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(leader);
        TextView summary=VaultNativeStyle.label(activity,count+" cards · "+folder,
            11,VaultNativeStyle.SUBTEXT,false);
        summary.setMaxLines(2);
        details.addView(summary);
        entry.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        entry.setOnClickListener(v->showDeck(deck));
        VaultNativeStyle.margin(content,entry,9,activity);
        if(!commander.isEmpty())
            deckCoverTargets.add(new DeckCoverTarget(art,commander));
    }

    /** Only request covers whose ImageView is actually inside the scrolling
     * viewport. No 300-image prefetch when a large deck library opens.
     * This must run on the UI thread. */
    private void loadVisibleDeckCovers(){
        if(closed||activity.isFinishing()||deckCoverTargets.isEmpty())return;
        final int pageGeneration=deckPageGeneration;
        Rect visible=new Rect();
        for(DeckCoverTarget target:deckCoverTargets){
            if(target.loaded || !target.image.isAttachedToWindow() ||
                    !target.image.getGlobalVisibleRect(visible) ||
                    visible.isEmpty())continue;
            Bitmap cached=coverBitmaps.get(target.commander);
            if(cached!=null){
                target.image.setImageBitmap(cached);
                target.loaded=true;
                continue;
            }
            if(coverUnavailable.contains(target.commander) ||
                    coverInFlight.contains(target.commander))continue;
            if(coverInFlight.size()>=8)break;
            String commander=target.commander;
            coverInFlight.add(commander);
            coverWorkers.execute(()->{
                Bitmap decoded=null;
                try {
                    String url=catalog.commanderCoverUrl(commander);
                    if(!url.isEmpty()){
                        byte[] bytes=artwork.get(url);
                        BitmapFactory.Options options=new BitmapFactory.Options();
                        options.inSampleSize=4;
                        decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    }
                } catch(Exception ignored) {
                    // Offline or unknown commander: keep the deck-box fallback.
                }
                final Bitmap result=decoded;
                ui.post(()->{
                    coverInFlight.remove(commander);
                    if(result!=null)coverBitmaps.put(commander,result);
                    else coverUnavailable.add(commander);
                    if(!closed&&pageGeneration==deckPageGeneration)
                        loadVisibleDeckCovers();
                });
            });
        }
    }

    private void deckLibrary(){
        final int pageGeneration=++deckPageGeneration;
        deckCoverTargets.clear();
        status.setText("Reading saved deck library from the existing app…");
        decks.fetch(json->ui.post(()->{
            if(closed||activity.isFinishing()||pageGeneration!=deckPageGeneration)return;
            content.removeAllViews();
            try{
                JSONArray library=new JSONArray(json);
                String selectedCommander=deckCommanderFilter;
                status.setText(library.length()+" saved decks · native workspace"+
                    (selectedCommander.isEmpty()?"":" · Commander: "+selectedCommander));
                if(!selectedCommander.isEmpty()){
                    button("Show all saved decks",()->{
                        deckCommanderFilter="";deckLibrary();
                    });
                }
                if(library.length()==0)row("No saved decks","Save decks in Legacy tools to see them here.",null);
                int matching=0;
                for(int i=0;i<Math.min(library.length(),300);i++){
                    JSONObject d=library.optJSONObject(i);if(d==null)continue;
                    String listedCommander=d.optString("commander","").trim();
                    if(!selectedCommander.isEmpty() &&
                        !selectedCommander.equalsIgnoreCase(listedCommander))continue;
                    matching++;
                    JSONArray entries=d.optJSONArray("entries");
                    int qty=1;
                    if(entries!=null)for(int n=0;n<entries.length();n++)
                        qty+=entries.optJSONObject(n)==null?0:entries.optJSONObject(n).optInt("qty",0);
                    deckCoverRow(d,qty,pageGeneration);
                }
                if(matching==0 && !selectedCommander.isEmpty())
                    row("No saved decks for this commander",
                        "You can open the existing editor to build a deck. No deck was created automatically.",
                        null);
                button("Open legacy deck editing tools",legacy);
                workspaceScroll.post(this::loadVisibleDeckCovers);
            }catch(Exception e){
                status.setText("Could not load deck library: "+e.getMessage());
                button("Open Legacy deck library",legacy);
            }
        }));
    }
    private void editDeck(JSONObject deck){
        final int pageGeneration=++deckPageGeneration;
        deckCoverTargets.clear();
        // Stay in the same Decks workspace: the content area, rather than
        // another modal, owns the name, mainboard, search and save controls.
        content.removeAllViews();
        status.setText("Editing "+deck.optString("name","saved deck")+" · unsaved changes");
        button("← Back to deck (discard changes)",()->showDeck(deck));
        LinearLayout fields=VaultNativeStyle.column(activity);
        fields.setPadding(dp(16),dp(8),dp(16),dp(8));
        TextView commander=VaultNativeStyle.label(activity,
            "Commander: "+deck.optString("commander","")+" (unchanged)",13,
            VaultNativeStyle.SUBTEXT,false);
        fields.addView(commander);
        TextView nameLabel=VaultNativeStyle.label(activity,
            "DECK NAME",11,VaultNativeStyle.SUBTEXT,true);
        fields.addView(nameLabel);
        EditText name=new EditText(activity);
        VaultNativeStyle.search(activity,name);
        name.setText(deck.optString("name","Untitled deck"));
        name.setHint("Deck name");
        fields.addView(name,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView hint=VaultNativeStyle.label(activity,
            "Edit the mainboard: one entry per line, e.g. 2 Sol Ring. "+
            "This changes your saved deck, not physical or Arena ownership.",12,
            VaultNativeStyle.SUBTEXT,false);
        fields.addView(hint);
        EditText lines=new EditText(activity);
        TextView boardLabel=VaultNativeStyle.label(activity,
            "MAINBOARD",11,VaultNativeStyle.SUBTEXT,true);
        boardLabel.setPadding(0,dp(10),0,dp(4));
        fields.addView(boardLabel);
        lines.setTextColor(VaultNativeStyle.TEXT);
        lines.setHintTextColor(VaultNativeStyle.DIM);
        lines.setTextSize(14);
        lines.setPadding(dp(12),dp(12),dp(12),dp(12));
        lines.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.SURFACE,VaultNativeStyle.BORDER,12));
        lines.setGravity(android.view.Gravity.TOP);
        lines.setMinLines(8);
        lines.setMaxLines(16);
        lines.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        lines.setText(NativeDeckDraft.formatEntries(deck.optJSONArray("entries")));
        fields.addView(lines,new LinearLayout.LayoutParams(-1,dp(260)));
        // Add cards without typing exact names or leaving the native workspace.
        // Only query the existing local Scryfall catalog; the selection modifies
        // this unsaved draft, never physical or Arena ownership.
        LinearLayout addRow=VaultNativeStyle.row(activity);
        EditText findCard=new EditText(activity);
        VaultNativeStyle.search(activity,findCard);
        findCard.setSingleLine(true);
        findCard.setHint("Find card to add");
        addRow.addView(findCard,new LinearLayout.LayoutParams(0,dp(46),1));
        Button addCard=VaultNativeStyle.button(activity,"Find",false);
        LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(dp(82),dp(46));
        addParams.leftMargin=dp(8);
        addRow.addView(addCard,addParams);
        fields.addView(addRow);
        addCard.setOnClickListener(v->{
            String term=findCard.getText().toString().trim();
            if(term.length()<2) {
                info("Enter at least two characters to find a card.");return;
            }
            addCard.setEnabled(false);
            executor.execute(()->{
                try{
                    Map<String,String> options=new HashMap<>();
                    options.put("q",term);
                    options.put("source","both");
                    options.put("page","0");
                    options.put("size","16");
                    JSONObject result=catalog.search(options);
                    JSONArray cards=result.optJSONArray("cards");
                    java.util.ArrayList<String> names=new java.util.ArrayList<>();
                    if(cards!=null)for(int i=0;i<cards.length();i++){
                        JSONObject card=cards.optJSONObject(i);
                        if(card==null)continue;
                        String candidate=card.optString("name","").trim();
                        if(!candidate.isEmpty()&&!names.contains(candidate))names.add(candidate);
                    }
                    ui.post(()->{
                        addCard.setEnabled(true);
                        if(closed||activity.isFinishing()||pageGeneration!=deckPageGeneration)return;
                        if(names.isEmpty()){info("No local catalog matches for "+term);return;}
                        new AlertDialog.Builder(activity)
                            .setTitle("Add card to draft")
                            .setItems(names.toArray(new String[0]),(d,which)->{
                                String chosen=names.get(which);
                                try{
                                    String prior=lines.getText().toString().trim();
                                    JSONArray parsed=NativeDeckDraft.parseEntries(
                                        prior+(prior.isEmpty()?"":"\\n")+"1 "+chosen);
                                    lines.setText(NativeDeckDraft.formatEntries(parsed));
                                    findCard.setText("");
                                }catch(Exception error){
                                    info("Cannot add until deck text is corrected: "+error.getMessage());
                                }
                            })
                            .setNegativeButton("Cancel",null).show();
                    });
                }catch(Exception error){
                    ui.post(()->{addCard.setEnabled(true);
                        if(!closed&&pageGeneration==deckPageGeneration)info("Card search unavailable: "+error.getMessage());});
                }
            });
        });
        content.addView(fields);
        LinearLayout actions=VaultNativeStyle.row(activity);
        Button discard=VaultNativeStyle.button(activity,"Discard",false);
        Button review=VaultNativeStyle.button(activity,"Review & Save",true);
        LinearLayout.LayoutParams actionLeft=new LinearLayout.LayoutParams(0,dp(49),1);
        actionLeft.rightMargin=dp(8);
        actions.addView(discard,actionLeft);
        actions.addView(review,new LinearLayout.LayoutParams(0,dp(49),1));
        VaultNativeStyle.margin(content,actions,14,activity);
        discard.setOnClickListener(v->showDeck(deck));
        review.setOnClickListener(v->{
            try {
                JSONObject revised=NativeDeckDraft.revised(deck,
                    name.getText().toString(),lines.getText().toString());
                JSONArray entries=revised.getJSONArray("entries");
                int count=1;
                for(int i=0;i<entries.length();i++)
                    count+=entries.getJSONObject(i).getInt("qty");
                String confirmation="Save "+revised.getString("name")+
                    " with "+count+" total cards (including commander)?"+
                    "\\n\\nA private recovery copy will be kept. "+
                    "The original deck must still match the version you opened.";
                new AlertDialog.Builder(activity).setTitle("Confirm deck edit")
                    .setMessage(confirmation)
                    .setNegativeButton("Back",null)
                    .setPositiveButton("Save",(d,w)->{
                        review.setEnabled(false);
                        deckSaver.save(deck,revised,(ok,message)->ui.post(()->{
                            if(closed||activity.isFinishing()||pageGeneration!=deckPageGeneration)return;
                            if(ok){
                                status.setText("Saved · "+revised.optString("name","Deck"));
                                deckLibrary();
                            } else {
                                review.setEnabled(true);
                                info(message);
                            }
                        }));
                    }).show();
            }catch(Exception error){
                info("Deck not changed: "+error.getMessage());
            }
        });
    }

    private void showDeck(JSONObject deck){
        final int pageGeneration=++deckPageGeneration;
        deckCoverTargets.clear();
        status.setText("Reviewing "+deck.optString("name","saved deck")+"…");
        executor.execute(()->{
            try{
                Map<String,Integer> owned=new HashMap<>();
                for(String key:new String[]{"vault_physicalCollectionV1","vault_cards"}){
                    JSONArray cards=new JSONArray(prefs.getString(key,"[]"));
                    if(cards.length()>150000)
                        throw new IllegalStateException("Inventory safety limit exceeded");
                    for(int i=0;i<cards.length();i++){
                        JSONObject card=cards.optJSONObject(i);if(card==null)continue;
                        String name=card.optString("name","").toLowerCase(java.util.Locale.ROOT);
                        owned.put(name,owned.getOrDefault(name,0)+
                            Math.max(0,card.optInt("qty",0)));
                    }
                }
                JSONArray entries=deck.optJSONArray("entries");
                java.util.ArrayList<String[]> cards=new java.util.ArrayList<>();
                int missing=0, total=1;
                if(entries!=null)for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.optJSONObject(i);
                    if(entry==null)continue;
                    String name=entry.optString("name","");
                    int qty=Math.max(0,entry.optInt("qty",0));
                    int have=owned.getOrDefault(name.toLowerCase(java.util.Locale.ROOT),0);
                    int need=Math.max(0,qty-have);
                    missing+=need;total+=qty;
                    cards.add(new String[]{qty+" × "+name,
                        need>0?"Missing "+need+" · recorded copies "+have:
                            "Owned · recorded copies "+have});
                }
                int missingCount=missing,totalCount=total;
                ui.post(()->{
                    if(closed||activity.isFinishing()||pageGeneration!=deckPageGeneration)return;
                    content.removeAllViews();
                    status.setText(totalCount+" cards · "+missingCount+
                        " not covered by recorded physical/Arena inventory");
                    button("← Back to Deck Library",this::deckLibrary);
                    TextView title=VaultNativeStyle.label(activity,
                        deck.optString("name","Untitled deck"),22,VaultNativeStyle.TEXT,true);
                    VaultNativeStyle.margin(content,title,8,activity);
                    row("COMMANDER",deck.optString("commander",""),null);
                    TextView note=VaultNativeStyle.label(activity,
                        "Recorded ownership is informational. Unowned cards stay in the deck; "+
                        "no collection quantities or printings are changed.",12,
                        VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,note,12,activity);
                    button("Edit this deck",()->editDeck(deck));
                    TextView mainboard=VaultNativeStyle.label(activity,
                        "MAINBOARD  ·  "+cards.size()+" card names",12,
                        VaultNativeStyle.GOLD,true);
                    VaultNativeStyle.margin(content,mainboard,10,activity);
                    for(String[] card:cards)row(card[0],card[1],null);
                });
            }catch(Exception e){
                ui.post(()->{if(!closed&&pageGeneration==deckPageGeneration)info("Deck details unavailable: "+e.getMessage());});
            }
        });
    }

    private void dataSync(){
        content.removeAllViews();
        status.setText("Windows ↔ Android · review before applying changes");
        LinearLayout categories=VaultNativeStyle.row(activity);
        HorizontalScrollView categoriesScroll=VaultNativeStyle.horizontalScroll(activity);
        categoriesScroll.setHorizontalScrollBarEnabled(false);
        String[] sections={"devices","snapshots","imports","updates"};
        String[] labels={"Device Sync","Snapshots","Import & Export","Updates"};
        for(int i=0;i<sections.length;i++){
            final String target=sections[i];
            Button tab=VaultNativeStyle.button(activity,labels[i],target.equals(syncSection));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(45));
            params.rightMargin=dp(7);
            categories.addView(tab,params);
            tab.setOnClickListener(v->{syncSection=target;dataSync();});
        }
        categoriesScroll.addView(categories);
        content.addView(categoriesScroll);
        TextView introText=VaultNativeStyle.label(activity,
            syncSection.equals("devices")?"Compare and review changes before applying to either device.":
            syncSection.equals("snapshots")?"Create one-tap backups and restore only with confirmation.":
            syncSection.equals("imports")?"Move verified collection exports and legacy data.":
            "Sync card metadata and install signed app updates.",
            13,VaultNativeStyle.SUBTEXT,false);
        introText.setPadding(0,dp(10),0,dp(16));
        content.addView(introText);
        if(syncSection.equals("snapshots")) {
        row("Collection storage","Physical and Arena inventories are stored separately in native Android storage.",null);
        row("Safe recovery snapshot",prefs.contains("vault_nativePhysicalBackupV1")?
            "A prior physical collection snapshot is available. Restore only if needed.":
            "No native edit recovery snapshot yet.",null);
        button("Save Sync Snapshot (One Tap)",
            ()->((MainActivity)activity).createSyncSnapshot());
        row("Local sync snapshot",
            "Instantly saves the current collection, decks and artwork locally. "+
            "Previous snapshots are retained. A local snapshot is not automatically "+
            "a confirmed common baseline shared with Windows.",null);
        }
        if(syncSection.equals("imports")) {
        button("Export physical inventory backup",()->shareInventory("physical"));
        button("Export Arena inventory backup",()->shareInventory("arena"));
        }
        if(syncSection.equals("snapshots")) {
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
        }
        if(syncSection.equals("updates")) {
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
        }
        if(syncSection.equals("devices")) {
        row("PC ↔ Android collection and deck sync",
            "Compare Windows and Android against their shared baseline, then review "+
            "inventory, saved-deck, and artwork differences before applying them to Android.",null);
        row("Wi-Fi peer exchange (trusted home network)",
            "Transfer snapshots wirelessly with a one-time link from Windows. "+
            "Five-minute one-use links. Review-only by default; applying a "+
            "merge requires a prior shared baseline and explicit confirmation.",null);
        row("USB cable peer sync (ADB)",
            "Connect one Android phone to Windows using a USB data cable. Enable "+
            "USB debugging and approve the computer. In Windows Data & Sync, choose "+
            "Share Sync to Phone (USB) or Receive & Review Phone Sync (USB). "+
            "Windows opens this app with a one-time localhost link. On the first "+
            "connection, choose First USB Sync / Create Shared Baseline on "+
            "Windows: Arcane Vault compares both devices, asks which data to "+
            "retain if they differ, then verifies and saves matching baselines "+
            "automatically. Future USB transfers use the normal reviewed delta "+
            "sync. Wi-Fi and QR codes are not required.",null);
        button("Scan Windows Wi-Fi QR code",
            ()->((MainActivity)activity).scanWirelessPeerQr());
        button("Scan QR & review Windows changes",
            ()->((MainActivity)activity).scanWirelessPeerQrForApply());
        button("Receive Windows sync over Wi-Fi",
            ()->((MainActivity)activity).wirelessPeerReview(false));
        button("Send phone sync over Wi-Fi",
            ()->((MainActivity)activity).wirelessPeerReview(true));
        button("Export peer sync file",()->((MainActivity)activity).exportPeerSync());
        button("Preview Windows / Android peer file",
            ()->((MainActivity)activity).previewPeerSync());
        row("Apply approved peer changes",
            "Opt-in inventory, saved-deck and artwork write-back. A previously "+
            "shared baseline is required; conflicts and ambiguous deletions "+
            "are refused. A full recovery snapshot is created before writing.",null);
        if(prefs.contains(NativePeerInventoryApply.LAST_BACKUP))
            button("Restore last peer-sync inventory backup",()->{
                new AlertDialog.Builder(activity)
                    .setTitle("Undo the last peer inventory sync?")
                    .setMessage("Restores the previously saved Physical and Arena inventories. "+
                        "A fresh backup of your CURRENT inventories will be kept so the undo "+
                        "can itself be reversed. Decks and artwork are not changed.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Restore backup",(dialog,which)->{
                        executor.execute(()->{
                            try{
                                String saved=NativePeerInventoryApply.restoreLast(
                                    activity,prefs,catalog);
                                ui.post(()->{
                                    ((MainActivity)activity).refreshAfterPeerInventory();
                                    info("Previous inventory restored. Reopen the Catalog "+
                                        "to refresh. Current inventory saved at "+saved);
                                });
                            }catch(Exception error){
                                ui.post(()->info("Inventory restore rejected: "+
                                    error.getMessage()));
                            }
                        });
                    }).show();
            });
        button("Review & apply Windows changes (including decks)",
            ()->((MainActivity)activity).reviewAndApplyPeerInventory());
        button("Restore latest full pre-sync snapshot",()->
            ((MainActivity)activity).restoreLatestFullPeerSnapshot());
        button("Three-way compare (known shared baseline)",
            ()->((MainActivity)activity).previewThreeWayPeerSync());
        }
        if(syncSection.equals("imports")) {
        button("Open advanced imports, QR and USB sync",legacy);
        }
        if(syncSection.equals("updates")) {
        row("Updater and security","Signed updates are opt-in. Do not dismiss Play Protect warnings without reviewing their details.",null);
        button("Check for app updates",()->new NativeAppUpdater(activity).check());
        }
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
