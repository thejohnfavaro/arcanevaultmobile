package com.arcanevault.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import com.arcanevault.mobile.next.NativePrintingOwnership;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.GridLayout;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Read-only native Android catalog backed directly by the existing Scryfall SQLite DB. */
final class NativeCatalogScreen {
    private static final int BG = Color.rgb(13,16,23), PANEL = Color.rgb(27,32,45),
            TEXT = Color.rgb(240,244,251), MUTED = Color.rgb(171,186,205);
    private final Activity activity;
    private final CatalogStore catalog;
    private final SharedPreferences inventory;
    private final ArtworkCache artwork;
    private final LinearLayout layout;
    private final LinearLayout results;
    private final ScrollView browseScroll;
    private final ScrollView inspectorScroll;
    private final LinearLayout inspectorContent;
    private int inspectorRequest=0;
    private final TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkWorker = Executors.newFixedThreadPool(2);
    private final ExecutorService pagePrefetchWorker=Executors.newSingleThreadExecutor();
    // Read-only page results, keyed by the full filter snapshot. Only retain
    // three pages to avoid holding thousands of JSON objects in memory.
    private final java.util.Map<String,JSONObject> pageCache=
        java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<String,JSONObject>(4,0.75f,true) {
            @Override protected boolean removeEldestEntry(
                    java.util.Map.Entry<String,JSONObject> eldest) {
                return size()>3;
            }
        });
    private final java.util.Set<String> queuedPageArt=
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final AtomicInteger request = new AtomicInteger();
    private final EditText search;
    private String source = "both", owned = "all", query = "";
    private boolean onlyOwnedPhysicalPrintings = false;
    private String setCode="",typeFilter="",oracleFilter="",rarityFilter="",roleFilter="",sortMode="";
    private String manaValueMin="",manaValueMax="";
    private String exactColorIdentity="";
    private boolean advancedOpen=false;
    private boolean artGrid=false;
    private Button viewModeButton;
    private LinearLayout advancedFilters;
    private Button advancedButton;
    private final java.util.Map<String,Button> identityButtons=new java.util.LinkedHashMap<>();
    private static final String[] RARITY_NAMES={"All rarities","Common","Uncommon","Rare","Mythic","Special","Bonus"};
    private static final String[] RARITY_VALUES={"","common","uncommon","rare","mythic","special","bonus"};
    private static final String[] SORT_NAMES={"Name A–Z","Most owned","Mana value (low first)","Rarity (highest first)"};
    private static final String[] SORT_VALUES={"","qty","mv","rarity"};
    private static final String[] ROLE_NAMES={"All roles","Ramp","Removal","Board Wipe","Discard","Counterspell",
        "Card Draw","Card Selection","Lifegain","Tutor","Stax","Haste","Protection","Recursion",
        "Sacrifice","Tokens","Graveyard Hate","Finisher","Equipment"};
    private Button printingFilter;
    private LinearLayout sourceFilters, ownershipFilters;
    private int page = 0;
    private volatile boolean disposed;
    private boolean filtersReady=false;

    NativeCatalogScreen(Activity activity, CatalogStore catalog, ArtworkCache artwork, SharedPreferences inventory) {
        this.activity = activity;
        this.catalog = catalog;
        this.inventory = inventory;
        this.artwork = artwork;
        layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(12),dp(18),dp(10));
        layout.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        TextView title = text("Card Catalog",26,true);
        layout.addView(title);
        status = text("Searching on-device Scryfall catalog…",12,false);
        status.setTextColor(MUTED);
        layout.addView(status);
        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search cards, types, abilities…");
        VaultNativeStyle.search(activity,search);
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,dp(48));
        searchParams.topMargin=dp(12);searchParams.bottomMargin=dp(12);
        layout.addView(search,searchParams);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                query = s.toString(); page = 0; schedule();
            }
            public void afterTextChanged(Editable s) {}
        });
        TextView sourceLabel=text("COLLECTION",11,true);
        sourceLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        layout.addView(sourceLabel);
        LinearLayout filters = new LinearLayout(activity);
        sourceFilters=filters;
        filters.setOrientation(LinearLayout.HORIZONTAL);
        for (String item : new String[]{"All Cards", "Arena", "Physical"}) {
            String selection=item.equals("All Cards")?"both":
                item.equals("Arena")?"arena":"physical";
            Button button=VaultNativeStyle.button(activity,item,source.equals(selection));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.rightMargin=dp(7);filters.addView(button,lp);
            button.setOnClickListener(v -> {
                source=selection;page=0;
                if(!"physical".equals(source))onlyOwnedPhysicalPrintings=false;
                updateCollectionFilters();schedule();
            });
        }
        HorizontalScrollView sourceScroll=VaultNativeStyle.horizontalScroll(activity);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.addView(filters);
        layout.addView(sourceScroll);
        TextView ownedLabel=text("OWNERSHIP",11,true);
        ownedLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        layout.addView(ownedLabel);
        LinearLayout ownership = new LinearLayout(activity);
        ownershipFilters=ownership;
        ownership.setOrientation(LinearLayout.HORIZONTAL);
        for(String item : new String[]{"All", "Owned", "Missing"}) {
            String selection=item.equals("Owned")?"owned":item.equals("Missing")?"unowned":"all";
            Button button=VaultNativeStyle.button(activity,item,owned.equals(selection));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.rightMargin=dp(7);ownership.addView(button,lp);
            button.setOnClickListener(v -> {
                owned=selection;page=0;
                onlyOwnedPhysicalPrintings=false;
                updateCollectionFilters();schedule();
            });
        }
        HorizontalScrollView ownedScroll=VaultNativeStyle.horizontalScroll(activity);
        ownedScroll.setHorizontalScrollBarEnabled(false);
        ownedScroll.addView(ownership);
        LinearLayout.LayoutParams ownedParams=new LinearLayout.LayoutParams(-1,-2);
        ownedParams.bottomMargin=dp(14);
        layout.addView(ownedScroll,ownedParams);
        printingFilter=VaultNativeStyle.button(activity,"Owned physical printings",false);
        LinearLayout.LayoutParams printingParams=new LinearLayout.LayoutParams(-1,dp(44));
        printingParams.bottomMargin=dp(12);
        layout.addView(printingFilter,printingParams);
        printingFilter.setOnClickListener(v -> {
            onlyOwnedPhysicalPrintings=!onlyOwnedPhysicalPrintings;
            if(onlyOwnedPhysicalPrintings){source="physical";owned="owned";}
            page=0;updateCollectionFilters();schedule();
        });
        LinearLayout toolRow=VaultNativeStyle.row(activity);
        viewModeButton=VaultNativeStyle.button(activity,"Card Art",false);
        toolRow.addView(viewModeButton,new LinearLayout.LayoutParams(0,dp(42),1));
        viewModeButton.setOnClickListener(v->{
            artGrid=!artGrid;
            viewModeButton.setText(artGrid?"Detailed List":"Card Art");
            schedule();
        });
        layout.addView(toolRow);
        advancedButton=VaultNativeStyle.button(activity,"Filters & Sort  ▾",false);
        LinearLayout.LayoutParams advancedButtonParams=new LinearLayout.LayoutParams(-1,dp(42));
        advancedButtonParams.bottomMargin=dp(6);
        layout.addView(advancedButton,advancedButtonParams);
        advancedFilters=VaultNativeStyle.column(activity);
        advancedFilters.setPadding(dp(10),dp(5),dp(10),dp(7));
        VaultNativeStyle.surface(activity,advancedFilters,false);
        buildAdvancedFilters();
        advancedFilters.setVisibility(View.GONE);
        advancedButton.setOnClickListener(v->{
            advancedOpen=!advancedOpen;
            advancedFilters.setVisibility(advancedOpen?View.VISIBLE:View.GONE);
            advancedButton.setText(advancedOpen?"Filters & Sort  ▴":"Filters & Sort  ▾");
        });
        updateCollectionFilters();
        browseScroll = VaultNativeStyle.scroll(activity);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout browseContent=VaultNativeStyle.column(activity);
        browseContent.addView(advancedFilters);
        browseContent.addView(results);
        browseScroll.addView(browseContent);
        layout.addView(browseScroll,new LinearLayout.LayoutParams(-1,0,1));
        inspectorContent=VaultNativeStyle.column(activity);
        inspectorContent.setPadding(dp(8),dp(5),dp(8),dp(20));
        inspectorScroll=VaultNativeStyle.scroll(activity);
        inspectorScroll.setFillViewport(false);
        inspectorScroll.addView(inspectorContent);
        inspectorScroll.setVisibility(View.GONE);
        layout.addView(inspectorScroll,new LinearLayout.LayoutParams(-1,0,1));
        filtersReady=true;
        schedule();
    }

    View view() { return layout; }
    void close() {
        disposed = true;
        request.incrementAndGet();
        inspectorRequest++;
        worker.shutdownNow();
        artworkWorker.shutdownNow();
        pagePrefetchWorker.shutdownNow();
        pageCache.clear();queuedPageArt.clear();
    }
    private int dp(float n) { return (int)(activity.getResources().getDisplayMetrics().density*n+.5f); }
    private TextView text(String value,int size,boolean bold) {
        TextView t=VaultNativeStyle.label(activity,value,size,VaultNativeStyle.TEXT,bold);
        t.setPadding(dp(4),dp(8),dp(4),dp(8));
        return t;
    }
    private Button button(String value) {
        return VaultNativeStyle.button(activity,value,false);
    }

    private EditText filterField(String heading,String hint,
            java.util.function.Consumer<String> onChange) {
        TextView label=text(heading,11,true);
        label.setTextColor(VaultNativeStyle.SUBTEXT);
        advancedFilters.addView(label);
        EditText input=new EditText(activity);
        input.setSingleLine(true);
        VaultNativeStyle.search(activity,input);
        input.setHint(hint);
        advancedFilters.addView(input,new LinearLayout.LayoutParams(-1,dp(42)));
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                onChange.accept(s.toString().trim());
                page=0;schedule();
            }
            public void afterTextChanged(Editable e){}
        });
        return input;
    }

    private void filterPicker(String label,String[] titles,String[] options,
            java.util.function.Consumer<String> onChange) {
        TextView header=text(label,11,true);
        header.setTextColor(VaultNativeStyle.SUBTEXT);
        advancedFilters.addView(header);
        Spinner picker=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item,titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int index,long id){
                onChange.accept(options[index]);
                page=0;schedule();
            }
        });
        advancedFilters.addView(picker,new LinearLayout.LayoutParams(-1,dp(44)));
    }

    private void updateIdentityButtons(){
        for(java.util.Map.Entry<String,Button> entry:identityButtons.entrySet()){
            boolean selected=entry.getKey().equals("C")?
                "C".equals(exactColorIdentity):
                exactColorIdentity.contains(entry.getKey());
            Button button=entry.getValue();
            button.setTextColor(selected?VaultNativeStyle.BACKGROUND:VaultNativeStyle.TEXT);
            button.setBackground(VaultNativeStyle.rounded(activity,
                selected?VaultNativeStyle.PRIMARY:VaultNativeStyle.ELEVATED,
                selected?Color.TRANSPARENT:VaultNativeStyle.BORDER,10));
        }
    }

    private void buildAdvancedFilters(){
        filterField("SET CODE","Exact set code (e.g. MH3)",value->setCode=value);
        filterField("TYPE CONTAINS","Creature, Wizard, Enchantment…",value->typeFilter=value);
        filterField("KEYWORD / RULES TEXT","Banding, Connive, Replicate…",value->oracleFilter=value);
        filterPicker("RARITY",RARITY_NAMES,RARITY_VALUES,value->rarityFilter=value);
        filterPicker("UTILIZATION TAG",ROLE_NAMES,ROLE_NAMES,value->
            roleFilter="All roles".equals(value)?"":value);
        filterField("MANA VALUE / CMC MIN","Minimum mana value (0 or more)",value->manaValueMin=value);
        filterField("MANA VALUE / CMC MAX","Maximum; same as min for exact",value->manaValueMax=value);
        filterPicker("SORT BY",SORT_NAMES,SORT_VALUES,value->sortMode=value);
        TextView colorLabel=text("COLOR IDENTITY · EXACT MATCH",11,true);
        colorLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        advancedFilters.addView(colorLabel);
        LinearLayout colorRow=VaultNativeStyle.row(activity);
        for(String color:new String[]{"W","U","B","R","G","C"}){
            Button button=VaultNativeStyle.button(activity,color,false);
            identityButtons.put(color,button);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);
            lp.rightMargin=dp(4);
            colorRow.addView(button,lp);
            button.setContentDescription(("C".equals(color)?"Colorless":color)+
                " exact color identity toggle");
            button.setOnClickListener(v->{
                if("C".equals(color))
                    exactColorIdentity="C".equals(exactColorIdentity)?"":"C";
                else{
                    String previous="C".equals(exactColorIdentity)?"":exactColorIdentity;
                    StringBuilder selected=new StringBuilder();
                    for(char value:"WUBRG".toCharArray())
                        if((String.valueOf(value).equals(color)) !=
                            (previous.indexOf(value)>=0))
                            selected.append(value);
                    exactColorIdentity=selected.toString();
                }
                updateIdentityButtons();page=0;schedule();
            });
        }
        advancedFilters.addView(colorRow);
        updateIdentityButtons();
        Button reset=VaultNativeStyle.button(activity,"Reset advanced filters",false);
        advancedFilters.addView(reset,new LinearLayout.LayoutParams(-1,dp(42)));
        reset.setOnClickListener(v->{
            setCode="";typeFilter="";oracleFilter="";rarityFilter="";roleFilter="";
            sortMode="";exactColorIdentity="";manaValueMin="";manaValueMax="";
            advancedFilters.removeAllViews();identityButtons.clear();
            buildAdvancedFilters();page=0;schedule();
        });
    }

    private void updateCollectionFilters() {
        printingFilter.setVisibility("physical".equals(source)?View.VISIBLE:View.GONE);
        printingFilter.setText(onlyOwnedPhysicalPrintings?
            "✓ Owned physical printings (exact editions)" : "Show owned physical printings");
        printingFilter.setTextColor(onlyOwnedPhysicalPrintings?
            VaultNativeStyle.BACKGROUND:VaultNativeStyle.TEXT);
        printingFilter.setBackground(VaultNativeStyle.rounded(activity,
            onlyOwnedPhysicalPrintings?VaultNativeStyle.PRIMARY:VaultNativeStyle.ELEVATED,
            onlyOwnedPhysicalPrintings?Color.TRANSPARENT:VaultNativeStyle.BORDER,11));
        String[] sources={"both","arena","physical"};
        for(int i=0;i<sourceFilters.getChildCount();i++) {
            Button item=(Button)sourceFilters.getChildAt(i);
            boolean active=source.equals(sources[i]);
            item.setTextColor(active?VaultNativeStyle.BACKGROUND:VaultNativeStyle.TEXT);
            item.setBackground(VaultNativeStyle.rounded(activity,
                active?VaultNativeStyle.PRIMARY:VaultNativeStyle.ELEVATED,
                active?Color.TRANSPARENT:VaultNativeStyle.BORDER,11));
        }
        String[] scopes={"all","owned","unowned"};
        for(int i=0;i<ownershipFilters.getChildCount();i++) {
            Button item=(Button)ownershipFilters.getChildAt(i);
            boolean active=owned.equals(scopes[i]);
            item.setTextColor(active?VaultNativeStyle.BACKGROUND:VaultNativeStyle.TEXT);
            item.setBackground(VaultNativeStyle.rounded(activity,
                active?VaultNativeStyle.PRIMARY:VaultNativeStyle.ELEVATED,
                active?Color.TRANSPARENT:VaultNativeStyle.BORDER,11));
        }
    }

    private void schedule() {
        if(!filtersReady)return;
        final int id=request.incrementAndGet();
        final String q=query, src=source, scope=owned;
        final String selectedSet=setCode, selectedType=typeFilter,
            selectedOracle=oracleFilter,
            selectedRarity=rarityFilter, selectedRole=roleFilter,
            selectedSort=sortMode, selectedIdentity=exactColorIdentity,
            selectedMvMin=manaValueMin,selectedMvMax=manaValueMax;
        final boolean printingMode=onlyOwnedPhysicalPrintings && "physical".equals(src);
        final int selectedPage=page;
        status.setText("Searching your catalog…");
        worker.execute(() -> {
            try {
                Map<String,String> options=new HashMap<>();
                options.put("q",q);options.put("scope",scope);
                options.put("set",selectedSet);options.put("type",selectedType);
                options.put("oracle",selectedOracle);
                options.put("rarity",selectedRarity);options.put("role",selectedRole);
                options.put("sort",selectedSort);options.put("colors",selectedIdentity);
                options.put("mvMin",selectedMvMin);options.put("mvMax",selectedMvMax);
                options.put("source",src);
                // A physical collection is not the same as cards unavailable on Arena.
                // All Cards searches the full catalog and ownership across both inventories.
                options.put("arena","");
                options.put("page",Integer.toString(selectedPage));
                options.put("size","40");
                // All 40 record rows are fetched in one query and retained for
                // back/forward navigation. Never reuse a stale filter result.
                String cacheKey=new java.util.TreeMap<>(options).toString()+
                    "|confirmed="+printingMode;
                JSONObject response=pageCache.get(cacheKey);
                if(response==null) {
                    response=printingMode?catalog.ownedPhysicalPrintings(options):catalog.search(options);
                    pageCache.put(cacheKey,response);
                }
                if(disposed || id!=request.get())return;
                prefetchPageArtwork(cacheKey,response,printingMode);
                final JSONObject pageResponse=response;
                ui.post(() -> {
                    if(disposed || id!=request.get() || activity.isFinishing())return;
                    render(pageResponse,selectedPage,src,printingMode);
                });
            }catch(Exception error){
                ui.post(() -> {
                    if(disposed || id!=request.get() || activity.isFinishing())return;
                    results.removeAllViews();
                    status.setText("Catalog unavailable: "+error.getMessage()+". Use Legacy tools → Data & Sync to refresh.");
                });
            }
        });
    }

    /** Warm the existing on-disk, 200 MiB Scryfall artwork cache for the
     * entire 40-row page. Work is sequential and bounded off the UI thread;
     * foreground thumbnail requests continue to use their own executor. */
    private void prefetchPageArtwork(String cacheKey,JSONObject response,
            boolean printingMode) {
        if(!queuedPageArt.add(cacheKey))return;
        JSONArray rows=response.optJSONArray("cards");
        if(rows==null)return;
        final java.util.ArrayList<String> urls=new java.util.ArrayList<>();
        for(int i=0;i<rows.length()&&i<40;i++){
            JSONObject item=rows.optJSONObject(i);
            if(item==null)continue;
            JSONObject imageData=printingMode?item.optJSONObject("printing"):
                item.optJSONObject("meta");
            String url=imageData==null?"":imageData.optString("image","");
            if(url.startsWith("https://cards.scryfall.io/")&&!urls.contains(url))
                urls.add(url);
        }
        pagePrefetchWorker.execute(()->{
            for(String url:urls) {
                if(disposed||Thread.currentThread().isInterrupted())return;
                try { artwork.get(url); }
                catch(Exception ignored) { /* Offline browsing keeps all text. */ }
            }
        });
    }

    private void invalidatePageCache(){
        pageCache.clear();
        queuedPageArt.clear();
    }

    /** Official, printing-matched alternate face. Badge occupies the bottom
     * right of the artwork and never changes catalog selection or ownership. */
    private boolean hasOfficialArt(String url) {
        return url!=null && url.startsWith("https://cards.scryfall.io/");
    }

    private FrameLayout flippableArt(String name,String front,String reverse,
            int width,int height) {
        FrameLayout container=new FrameLayout(activity);
        ImageView image=new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Official card artwork for "+name);
        image.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.ELEVATED,VaultNativeStyle.BORDER,8));
        container.addView(image,new FrameLayout.LayoutParams(-1,-1));
        loadThumbnail(image,front);
        boolean hasBack=front.startsWith("https://cards.scryfall.io/") &&
            reverse.startsWith("https://cards.scryfall.io/") &&
            !front.equals(reverse);
        if(hasBack) {
            final boolean[] showingBack={false};
            Button flip=VaultNativeStyle.button(activity,"⟳",false);
            flip.setTextSize(19);
            flip.setAllCaps(false);
            flip.setContentDescription("Flip double-faced card "+name);
            FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(
                dp(43),dp(43),Gravity.BOTTOM|Gravity.RIGHT);
            position.setMargins(dp(4),dp(4),dp(4),dp(4));
            container.addView(flip,position);
            flip.setOnClickListener(v->{
                showingBack[0]=!showingBack[0];
                image.setImageDrawable(null);
                loadThumbnail(image,showingBack[0]?reverse:front);
                image.setContentDescription((showingBack[0]?"Alternate":"Front")+
                    " card face for "+name);
                flip.setContentDescription("Show "+(showingBack[0]?"front":"alternate")+
                    " card face for "+name);
            });
        }
        container.setContentDescription(name+
            (hasBack?", double-faced card with flip control":""));
        return container;
    }

    private void render(JSONObject response,int selectedPage,String src,boolean printingMode) {
        results.removeAllViews();
        int total=response.optInt("total",0);
        JSONArray rows=response.optJSONArray("cards");
        int count=rows==null?0:rows.length();
        status.setText(total+(printingMode?" confirmed owned printings":" cards found")+
                " • page "+(selectedPage+1)
                +("physical".equals(src)?" • Physical Collection":"arena".equals(src)?" • Arena Collection":" • Both Collections"));
        if(total==0){
            results.addView(text(printingMode?
                "No confirmed printings found. Name-only physical cards stay in Physical → Owned, "+
                "but are not assigned to a set. Sync printing metadata or restore verified IDs in Legacy tools.":
                "No cards found. If your catalog is empty, sync Scryfall data in Legacy tools.",14,false));
            return;
        }
        GridLayout gallery=null;
        if(artGrid&&!printingMode){
            gallery=new GridLayout(activity);
            gallery.setColumnCount(2);
            gallery.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            results.addView(gallery,new LinearLayout.LayoutParams(-1,-2));
        }
        for(int i=0;i<count;i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
            if(printingMode){renderOwnedPrintingRow(row);continue;}
            if(gallery!=null){
                renderArtworkTile(gallery,row,i);
                continue;
            }
            String name=row.optString("name","Unknown card");
            JSONObject meta=row.optJSONObject("meta");
            String type=meta==null?"":meta.optString("type","");
            String rarity=meta==null?"":meta.optString("rarity","");
            String oracle=meta==null?"":meta.optString("oracle","");
            String colors=meta==null?"":meta.optJSONArray("colors")==null?"":meta.optJSONArray("colors").toString();
            LinearLayout entry=VaultNativeStyle.row(activity);
            entry.setPadding(dp(10),dp(10),dp(12),dp(10));
            VaultNativeStyle.surface(activity,entry,false);
            String imageUrl=meta==null?"":meta.optString("image","");
            String imageBack=meta==null?"":meta.optString("imageBack","");
            if(hasOfficialArt(imageUrl)){
                FrameLayout thumb=flippableArt(name,imageUrl,imageBack,dp(66),dp(93));
                LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(66),dp(93));
                imageParams.rightMargin=dp(14);entry.addView(thumb,imageParams);
            }
            LinearLayout detail=VaultNativeStyle.column(activity);
            TextView nameView=text(name,16,true);
            nameView.setMaxLines(2);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            detail.addView(nameView);
            TextView typeView=text(type,12,false);
            typeView.setMaxLines(1);
            typeView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            typeView.setTextColor(VaultNativeStyle.SUBTEXT);
            detail.addView(typeView);
            int arenaQty=row.optInt("arenaQty",0);
            int physicalQty=row.optInt("physicalQty",0);
            String rarityLabel=rarity.isEmpty()?"Card":rarity.substring(0,1).toUpperCase(java.util.Locale.ROOT)+rarity.substring(1);
            TextView metadata=text(rarityLabel+
                (physicalQty>0?"   •   Physical ×"+physicalQty:"")+
                (arenaQty>0?"   •   Arena ×"+arenaQty:""),12,true);
            metadata.setTextColor(physicalQty>0?VaultNativeStyle.PRIMARY:VaultNativeStyle.GOLD);
            detail.addView(metadata);
            entry.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
            entry.setContentDescription(name+", "+type+", "+rarityLabel+
                (physicalQty>0?", Physical copies "+physicalQty:"")+
                (arenaQty>0?", Arena copies "+arenaQty:"")+". Tap to inspect");
            entry.setOnClickListener(v -> showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(9);
            results.addView(entry,lp);
        }
        LinearLayout pager=new LinearLayout(activity);
        Button back=button("Previous");
        Button next=button("Next");
        pager.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        pager.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        back.setEnabled(selectedPage>0);
        next.setEnabled((selectedPage+1)*40<total);
        back.setOnClickListener(v -> { page=Math.max(0,page-1); schedule();
            browseScroll.post(()->browseScroll.smoothScrollTo(0,0)); });
        next.setOnClickListener(v -> { page++; schedule();
            browseScroll.post(()->browseScroll.smoothScrollTo(0,0)); });
        results.addView(pager);
    }
    /** A compact, 2-column card-art browser with the same inspect action as
     * detailed rows. Images come only from the existing guarded cache. */
    private void renderArtworkTile(GridLayout gallery,JSONObject row,int index) {
        JSONObject meta=row.optJSONObject("meta");
        String name=row.optString("name","Unknown card");
        String imageUrl=meta==null?"":meta.optString("image","");
        String type=meta==null?"":meta.optString("type","");
        String oracle=meta==null?"":meta.optString("oracle","");
        String rarity=meta==null?"":meta.optString("rarity","");
        int arenaQty=row.optInt("arenaQty",0),physicalQty=row.optInt("physicalQty",0);
        int screenWidth=activity.getResources().getDisplayMetrics().widthPixels;
        int cardWidth=Math.max(dp(120),(screenWidth-dp(72))/2);
        LinearLayout tile=VaultNativeStyle.column(activity);
        tile.setPadding(dp(8),dp(8),dp(8),dp(9));
        VaultNativeStyle.surface(activity,tile,false);
        String imageBack=meta==null?"":meta.optString("imageBack","");
        if(hasOfficialArt(imageUrl)){
            FrameLayout image=flippableArt(name,imageUrl,imageBack,cardWidth-dp(16),
                Math.round((cardWidth-dp(16))*1.4f));
            tile.addView(image,new LinearLayout.LayoutParams(-1,
                Math.round((cardWidth-dp(16))*1.4f)));
        }
        TextView label=text(name,13,true);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(label);
        TextView ownedInfo=text(
            (physicalQty>0?"Physical ×"+physicalQty+" ":"")+
            (arenaQty>0?"Arena ×"+arenaQty:""),11,false);
        ownedInfo.setTextColor(VaultNativeStyle.SUBTEXT);
        ownedInfo.setMaxLines(1);
        tile.addView(ownedInfo);
        tile.setContentDescription(name+", "+(physicalQty+arenaQty)+
            " owned copies. Tap to inspect");
        tile.setOnClickListener(v->
            showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty));
        GridLayout.LayoutParams cell=new GridLayout.LayoutParams();
        cell.width=cardWidth;
        cell.height=-2;
        cell.rowSpec=GridLayout.spec(index/2);
        cell.columnSpec=GridLayout.spec(index%2);
        cell.setMargins(dp(3),dp(3),dp(3),dp(3));
        gallery.addView(tile,cell);
    }

    private void renderOwnedPrintingRow(JSONObject row) {
        JSONObject printing=row.optJSONObject("printing");
        if(printing==null)return;
        String name=row.optString("name","Unknown card");
        String set=printing.optString("set","").toUpperCase(java.util.Locale.ROOT);
        String collector=printing.optString("collectorNumber","");
        String imageUrl=printing.optString("image","");
        int confirmed=printing.optInt("confirmedPhysicalCopies",0);
        LinearLayout card=VaultNativeStyle.row(activity);
        card.setPadding(dp(10),dp(10),dp(12),dp(10));
        VaultNativeStyle.surface(activity,card,false);
        if(hasOfficialArt(imageUrl)){
            FrameLayout art=flippableArt(name,imageUrl,
                printing.optString("imageBack",""),dp(66),dp(93));
            LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(66),dp(93));
            imageParams.rightMargin=dp(14);
            card.addView(art,imageParams);
        }
        LinearLayout labels=VaultNativeStyle.column(activity);
        TextView title=text(name,16,true);title.setMaxLines(2);labels.addView(title);
        TextView setInfo=text(set+" #"+collector+" • "+printing.optString("setName",""),12,false);
        setInfo.setMaxLines(2);setInfo.setTextColor(VaultNativeStyle.SUBTEXT);
        labels.addView(setInfo);
        TextView copies=text("Physical ×"+confirmed+" • "+printing.optString("rarity",""),12,true);
        copies.setTextColor(VaultNativeStyle.PRIMARY);labels.addView(copies);
        card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        card.setContentDescription(name+", "+set+" collector "+collector+
            ", confirmed physical copies "+confirmed+". Tap to inspect edition");
        card.setOnClickListener(v -> showInlinePrintingDetail(name,printing,()->closeInspector()));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(9);
        results.addView(card,lp);
    }

    /** Artwork is loaded off-thread, using the existing guarded Scryfall cache. */
    private void loadThumbnail(ImageView target,String imageUrl) {
        target.setTag(imageUrl);
        if(!imageUrl.startsWith("https://cards.scryfall.io/"))return;
        artworkWorker.execute(() -> {
            try {
                byte[] encoded=artwork.get(imageUrl);
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=4;
                Bitmap bitmap=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed || activity.isFinishing() || !imageUrl.equals(target.getTag())) {
                        bitmap.recycle();return;
                    }
                    target.setImageBitmap(bitmap);
                });
            } catch(Exception ignored) {
                // Offline users retain searchable card names and metadata.
            }
        });
    }

    /** In-workspace card inspection preserves the active query, filters, page,
     * and list scroll position. Returning does not re-fetch the catalog. */
    private void openInspector(String heading) {
        inspectorRequest++;
        inspectorContent.removeAllViews();
        browseScroll.setVisibility(View.GONE);
        inspectorScroll.setVisibility(View.VISIBLE);
        inspectorScroll.scrollTo(0,0);
        Button back=VaultNativeStyle.button(activity,"← Back to catalog",false);
        inspectorContent.addView(back,new LinearLayout.LayoutParams(-1,dp(46)));
        back.setOnClickListener(v->closeInspector());
        TextView title=text(heading,22,true);
        title.setMaxLines(3);
        inspectorContent.addView(title);
    }

    private void closeInspector() {
        inspectorRequest++;
        inspectorScroll.setVisibility(View.GONE);
        browseScroll.setVisibility(View.VISIBLE);
    }

    private void addInspectorArtwork(String name,String imageUrl,String imageBack) {
        if(!hasOfficialArt(imageUrl))return;
        FrameLayout art=flippableArt(name,imageUrl,imageBack,-1,dp(330));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(330));
        params.topMargin=dp(8);params.bottomMargin=dp(8);
        inspectorContent.addView(art,params);
    }

    private void showDetails(String name,String type,String oracle,String rarity,
            String imageUrl,String imageBack,int arenaQty,int physicalQty) {
        openInspector(name);
        addInspectorArtwork(name,imageUrl,imageBack);
        TextView typeLine=text(type,14,true);
        typeLine.setTextColor(VaultNativeStyle.SUBTEXT);
        inspectorContent.addView(typeLine);
        TextView copies=text("Physical ×"+physicalQty+"   ·   Arena ×"+arenaQty+
            "   ·   "+rarity,13,true);
        copies.setTextColor(VaultNativeStyle.PRIMARY);
        inspectorContent.addView(copies);
        LinearLayout oraclePanel=VaultNativeStyle.column(activity);
        oraclePanel.setPadding(dp(12),dp(10),dp(12),dp(12));
        VaultNativeStyle.surface(activity,oraclePanel,false);
        TextView rulesHeader=text("ORACLE TEXT",11,true);
        rulesHeader.setTextColor(VaultNativeStyle.GOLD);
        oraclePanel.addView(rulesHeader);
        TextView rules=text(oracle.isEmpty()?"No rules text":oracle,15,false);
        rules.setLineSpacing(dp(3),1f);
        oraclePanel.addView(rules);
        VaultNativeStyle.margin(inspectorContent,oraclePanel,10,activity);

        Button browsePrintings=VaultNativeStyle.button(activity,
            "View printings & edit physical ownership",true);
        inspectorContent.addView(browsePrintings,new LinearLayout.LayoutParams(-1,dp(48)));
        browsePrintings.setOnClickListener(v->showInlinePrintings(name,
            ()->showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty)));
    }

    private void showInlinePrintings(String name,Runnable previous) {
        openInspector("Printings · "+name);
        Button back=VaultNativeStyle.button(activity,"← Card details",false);
        inspectorContent.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
        back.setOnClickListener(v->previous.run());
        TextView loading=text("Checking official printings and confirmed ownership…",13,false);
        inspectorContent.addView(loading);
        final int token=inspectorRequest;
        worker.execute(()->{
            try{
                Map<String,String> params=new HashMap<>();
                params.put("name",name);
                JSONObject response=catalog.variants(params);
                JSONArray variants=response.optJSONArray("variants");
                JSONArray ownership=new JSONArray(inventory.getString(
                    "vault_physicalCollectionV1","[]"));
                if(ownership.length()>150000)
                    throw new IllegalStateException("Physical inventory exceeds safety limit");
                JSONObject projection=NativePrintingOwnership.inspect(name,ownership,
                    variants==null?new JSONArray():variants);
                ui.post(()->{
                    if(disposed||activity.isFinishing()||token!=inspectorRequest)return;
                    inspectorContent.removeView(loading);
                    JSONArray rows=projection.optJSONArray("printings");
                    if(rows==null)rows=new JSONArray();
                    int total=rows.length();
                    TextView summary=text("Physical copies: "+
                        projection.optInt("physicalCopies",0)+" · Confirmed: "+
                        projection.optInt("confirmedCopies",0)+" · Unresolved: "+
                        projection.optInt("unresolvedCopies",0),13,true);
                    inspectorContent.addView(summary);
                    TextView note=text("Ownership uses exact Scryfall printing IDs "+
                        "or unambiguous set and collector numbers. Name-only "+
                        "cards are never assigned to an edition.",12,false);
                    note.setTextColor(VaultNativeStyle.SUBTEXT);
                    inspectorContent.addView(note);
                    if(total==0)inspectorContent.addView(text(
                        "No indexed printings. Refresh Scryfall metadata in Data & Sync.",14,false));
                    final JSONArray printings=rows;
                    final int[] shown={0};
                    final LinearLayout list=VaultNativeStyle.column(activity);
                    inspectorContent.addView(list);
                    Runnable append=new Runnable(){
                        public void run(){
                            int target=Math.min(total,shown[0]+32);
                            for(int i=shown[0];i<target;i++){
                                JSONObject edition=printings.optJSONObject(i);
                                if(edition==null)continue;
                                String code=edition.optString("set","").toUpperCase(java.util.Locale.ROOT);
                                String label=code+" #"+edition.optString("collectorNumber","")+
                                    " · "+edition.optString("setName","");
                                int qty=edition.optInt("confirmedPhysicalCopies",0);
                                Button select=VaultNativeStyle.button(activity,label+
                                    (qty>0?"  ·  Owned ×"+qty:"  ·  Not owned"),false);
                                select.setAllCaps(false);
                                select.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                                select.setTextSize(12);
                                VaultNativeStyle.margin(list,select,5,activity);
                                select.setOnClickListener(v->showInlinePrintingDetail(
                                    name,edition,()->showInlinePrintings(name,previous)));
                            }
                            shown[0]=target;
                            if(shown[0]<total){
                                Button more=VaultNativeStyle.button(activity,
                                    "Show next printings ("+shown[0]+" / "+total+")",false);
                                list.addView(more);
                                more.setOnClickListener(v->{
                                    list.removeView(more);
                                    run();
                                });
                            }
                        }
                    };
                    append.run();
                });
            }catch(Exception error){
                ui.post(()->{
                    if(disposed||token!=inspectorRequest)return;
                    loading.setText("Cannot load printings: "+error.getMessage());
                });
            }
        });
    }

    private void showInlinePrintingDetail(String name,JSONObject printing,Runnable previous) {
        String code=printing.optString("set","").toUpperCase(java.util.Locale.ROOT);
        String collector=printing.optString("collectorNumber","");
        openInspector(name+" · "+code+" #"+collector);
        Button back=VaultNativeStyle.button(activity,"← All printings",false);
        inspectorContent.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
        back.setOnClickListener(v->previous.run());
        String imageUrl=printing.optString("image","");
        addInspectorArtwork(name,imageUrl,printing.optString("imageBack",""));
        inspectorContent.addView(text(printing.optString("setName","")+
            " · "+printing.optString("rarity","")+
            "\\nConfirmed physical copies: "+printing.optInt("confirmedPhysicalCopies",0)+
            "\\nPrinting ID: "+printing.optString("id",""),13,false));
        Button edit=VaultNativeStyle.button(activity,"Edit physical copies",true);
        inspectorContent.addView(edit,new LinearLayout.LayoutParams(-1,dp(48)));
        edit.setOnClickListener(v->new NativePhysicalEditor(activity,inventory,catalog)
            .edit(name,printing,()->{
                // The local inventory changed; no stale owned counts may remain.
                invalidatePageCache();
                schedule();
                previous.run();
            }));
        if(imageUrl.startsWith("https://cards.scryfall.io/")){
            Button preference=VaultNativeStyle.button(activity,
                "Set preferred artwork",false);
            inspectorContent.addView(preference,new LinearLayout.LayoutParams(-1,dp(48)));
            preference.setOnClickListener(v->((MainActivity)activity)
                .savePreferredArtwork(name,printing.optString("id",""),imageUrl));
        }
    }

    private void loadPrintings(String name) {
        // The authoritative native SharedPreferences inventory is read without
        // altering anything. SQLite variants are the catalog, not a second ledger.
        final AlertDialog loading=new AlertDialog.Builder(activity)
            .setTitle("Printings of "+name)
            .setMessage("Checking official printings and confirmed physical ownership…")
            .setNegativeButton("Close",null).create();
        loading.show();
        worker.execute(() -> {
            try {
                Map<String,String> parameters=new HashMap<>();
                parameters.put("name",name);
                JSONObject response=catalog.variants(parameters);
                JSONArray variants=response.optJSONArray("variants");
                String raw=inventory.getString("vault_physicalCollectionV1","[]");
                JSONArray physical=new JSONArray(raw==null?"[]":raw);
                if(physical.length()>150000)throw new IllegalStateException("Physical inventory exceeds safety limit");
                JSONObject projection=NativePrintingOwnership.inspect(name,physical,
                    variants==null?new JSONArray():variants);
                ui.post(() -> {
                    if(disposed || activity.isFinishing() || !loading.isShowing())return;
                    loading.dismiss();
                    showPrintings(name,projection);
                });
            } catch(Exception error) {
                ui.post(() -> {
                    if(disposed || !loading.isShowing())return;
                    loading.setMessage("Printing data unavailable: "+error.getMessage()+
                        ". Your collection has not changed.");
                });
            }
        });
    }

    private void showPrintings(String name,JSONObject projection) {
        JSONArray rows=projection.optJSONArray("printings");
        if(rows==null)rows=new JSONArray();
        final JSONArray printings=rows;
        int confirmed=projection.optInt("confirmedCopies",0);
        int unresolved=projection.optInt("unresolvedCopies",0);
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12),dp(4),dp(12),dp(12));
        String summary="Physical copies: "+projection.optInt("physicalCopies",0)
            +" • Confirmed here: "+confirmed+" • Unresolved: "+unresolved;
        body.addView(text(summary,14,true));
        body.addView(text("Only matched Scryfall IDs or unique set + collector numbers count. "
            +"Name-only cards are never assigned to an edition. "
            +"Different finishes remain separate in your collection ledger.",12,false));
        if(printings.length()==0)
            body.addView(text("No official printing metadata is indexed yet. "
                +"Refresh Scryfall catalog in Legacy tools.",13,false));
        LinearLayout list=new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        final int[] visible={0};
        Runnable more=new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                visible[0]=Math.min(printings.length(),visible[0]+40);
                for(int i=0;i<visible[0];i++) {
                    JSONObject printing=printings.optJSONObject(i);
                    if(printing==null)continue;
                    String set=printing.optString("set","").toUpperCase(java.util.Locale.ROOT);
                    String collector=printing.optString("collectorNumber","");
                    String title=set+" #"+collector+" · "+printing.optString("setName","");
                    int copies=printing.optInt("confirmedPhysicalCopies",0);
                    Button choice=button(title+"\n"+(copies>0?"Owned: "+copies+" confirmed":"No confirmed copies"));
                    choice.setAllCaps(false);
                    choice.setTextSize(12);
                    choice.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                    choice.setPadding(dp(10),dp(8),dp(8),dp(8));
                    list.addView(choice,new LinearLayout.LayoutParams(-1,dp(68)));
                    String image=printing.optString("image","");
                    choice.setOnClickListener(v -> showPrintingDetail(name,printing,image));
                }
                if(visible[0]<printings.length()) {
                    Button next=button("Show next 40 printings");
                    list.addView(next);
                    next.setOnClickListener(v -> run());
                }
            }
        };
        more.run();
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        scroll.addView(body);
        new AlertDialog.Builder(activity).setTitle("Choose a printing")
            .setView(scroll).setPositiveButton("Done",null).show();
    }

    private void showPrintingDetail(String name,JSONObject printing,String imageUrl) {
        String detail=printing.optString("setName","")
            +" ("+printing.optString("set","").toUpperCase(java.util.Locale.ROOT)+")"
            +" #"+printing.optString("collectorNumber","")
            +"\nRarity: "+printing.optString("rarity","")
            +"\nConfirmed physical copies: "+printing.optInt("confirmedPhysicalCopies",0)
            +"\nPrinting ID: "+printing.optString("id","");
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(text(detail,14,false));
        ImageView cardArt=new ImageView(activity);
        cardArt.setAdjustViewBounds(true);
        cardArt.setContentDescription("Official artwork for "+name+" "+printing.optString("set",""));
        layout.addView(cardArt,new LinearLayout.LayoutParams(-1,dp(330)));
        final AlertDialog[] dialogHolder=new AlertDialog[1];
        Button edit=VaultNativeStyle.button(activity,"Edit physical copies (nonfoil)",true);
        layout.addView(edit,new LinearLayout.LayoutParams(-1,dp(48)));
        edit.setOnClickListener(v->new NativePhysicalEditor(activity,inventory,catalog)
            .edit(name,printing,()->{
                schedule();
                if(dialogHolder[0]!=null)dialogHolder[0].dismiss();
            }));
        if(imageUrl.startsWith("https://cards.scryfall.io/")){
            Button preference=VaultNativeStyle.button(activity,"Set preferred artwork",false);
            layout.addView(preference,new LinearLayout.LayoutParams(-1,dp(48)));
            preference.setOnClickListener(v->((MainActivity)activity)
                .savePreferredArtwork(name,printing.optString("id",""),imageUrl));
        }
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(name)
            .setView(scroll).setPositiveButton("Close",null).show();
        dialogHolder[0]=dialog;
        if(!imageUrl.startsWith("https://cards.scryfall.io/"))return;
        worker.execute(() -> {
            try {
                byte[] bytes=artwork.get(imageUrl);
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed || !dialog.isShowing()){bitmap.recycle();return;}
                    cardArt.setImageBitmap(bitmap);
                });
            } catch(Exception ignored) { /* Text-only inspector stays available offline. */ }
        });
    }

}
