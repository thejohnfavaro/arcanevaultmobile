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
    private final Runnable pendingSearch=()->schedule();
    private final EditText search;
    // Open the Android catalog on the cards users physically own.
    // Switching to Arena, All or Missing remains an explicit in-session choice.
    private String source = "physical", owned = "owned", query = "";
    private static final String PREF_SOURCE="native_catalog_source_v2";
    private static final String PREF_OWNED="native_catalog_owned_v2";
    private static final String PREF_VIEW="native_catalog_view_mode_v2";
    private static final String PREF_PAGE_SIZE="native_catalog_page_size_v2";
    private static final String PREF_COLOR="native_catalog_color_v2";
    private boolean onlyOwnedPhysicalPrintings = false;
    private String setCode="",typeFilter="",oracleFilter="",rarityFilter="",roleFilter="",sortMode="",oracleMatch="all";
    private String manaValueMin="",manaValueMax="";
    private String exactColorIdentity="";
    private boolean advancedOpen=false;
    private boolean artGrid=true;
    private int viewMode=0; // 0 compact art, 1 detailed list, 2 text-only
    private LinearLayout manaRow;
    private boolean colorsOpen=false;
    private Button colorToggle;
    private static final String PREF_COMPACT_GRID="native_catalog_compact_grid_v1";
    private Button viewModeButton;
    private LinearLayout searchRow,toolRow;
    private LinearLayout advancedFilters;
    private LinearLayout quickFilters;
    private Button advancedButton;
    private final java.util.Map<String,NativeManaFilterButton> identityButtons=
        new java.util.LinkedHashMap<>();
    private static final String[] RARITY_NAMES={"All rarities","Common","Uncommon","Rare","Mythic","Special","Bonus"};
    private static final String[] RARITY_VALUES={"","common","uncommon","rare","mythic","special","bonus"};
    private static final String[] SORT_NAMES={"Name A–Z","Most owned","Mana value (low first)","Rarity (highest first)"};
    private static final String[] SORT_VALUES={"","qty","mv","rarity"};
    private static final String[] ROLE_NAMES={"All roles","Ramp","Removal","Board Wipe","Discard","Counterspell",
        "Card Draw","Card Selection","Lifegain","Tutor","Stax","Haste","Protection","Recursion",
        "Sacrifice","Tokens","Graveyard Hate","Finisher","Equipment"};
    private Button printingFilter;
    private LinearLayout sourceFilters, ownershipFilters;
    private Button[] sourceShortcuts;
    private int page = 0;
    private int pageSize=40;
    // Last page is retained for visual-only reflow: no new catalog query,
    // filter reset, or collection write on rotate/fold/split screen.
    private JSONObject lastRenderedPage;
    private int lastRenderedPageIndex;
    private String lastRenderedSource;
    private boolean lastRenderedPrinting;
    private int lastRenderedColumns = -1;
    private volatile boolean disposed;
    private boolean filtersReady=false;

    NativeCatalogScreen(Activity activity, CatalogStore catalog, ArtworkCache artwork, SharedPreferences inventory) {
        this.activity = activity;
        this.catalog = catalog;
        this.inventory = inventory;
        this.artwork = artwork;
        artGrid=inventory.getBoolean(PREF_COMPACT_GRID,true);
        viewMode=Math.max(0,Math.min(2,inventory.getInt(PREF_VIEW,artGrid?0:1)));
        pageSize=inventory.getInt(PREF_PAGE_SIZE,40);
        if(pageSize!=20&&pageSize!=40&&pageSize!=80)pageSize=40;
        artGrid=viewMode==0;
        String restoredSource=inventory.getString(PREF_SOURCE,"physical");
        source=restoredSource.equals("arena")||restoredSource.equals("both")?restoredSource:"physical";
        String restoredOwned=inventory.getString(PREF_OWNED,"owned");
        owned=restoredOwned.equals("all")||restoredOwned.equals("unowned")?restoredOwned:"owned";
        exactColorIdentity=inventory.getString(PREF_COLOR,"");
        if(!exactColorIdentity.matches("C|[WUBRG]{0,5}"))exactColorIdentity="";
        layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12),dp(3),dp(12),dp(6));
        layout.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        status = text("Searching catalog…",11,false);
        status.setTextColor(MUTED);
        status.setPadding(dp(3),dp(2),dp(3),dp(4));
        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search cards, types, abilities…");
        VaultNativeStyle.search(activity,search);
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,dp(48));
        searchParams.topMargin=dp(3);searchParams.bottomMargin=dp(6);
        searchRow=VaultNativeStyle.row(activity);
        searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));
        Button clearSearch=VaultNativeStyle.button(activity,"×",false);
        clearSearch.setContentDescription("Clear card catalog search");
        clearSearch.setVisibility(View.GONE);
        LinearLayout.LayoutParams clearParams=new LinearLayout.LayoutParams(dp(40),dp(46));
        clearParams.leftMargin=dp(4);
        searchRow.addView(clearSearch,clearParams);
        clearSearch.setOnClickListener(v->search.setText(""));
        Button filterShortcut=VaultNativeStyle.button(activity,"☷",false);
        filterShortcut.setContentDescription("Open filters and sorting");
        LinearLayout.LayoutParams shortcutParams=new LinearLayout.LayoutParams(dp(48),dp(46));
        shortcutParams.leftMargin=dp(6);
        searchRow.addView(filterShortcut,shortcutParams);
        layout.addView(searchRow,searchParams);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                query = s.toString(); page = 0;
                clearSearch.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);
                scheduleSearch();
            }
            public void afterTextChanged(Editable s) {}
        });
        // Group high-density collection filters in a collapsed drawer so the
        // screen is used for actual card results rather than permanent controls.
        quickFilters=VaultNativeStyle.column(activity);
        TextView sourceLabel=text("COLLECTION",11,true);
        sourceLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        quickFilters.addView(sourceLabel);
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
                inventory.edit().putString(PREF_SOURCE,source).apply();
                if(!"physical".equals(source))onlyOwnedPhysicalPrintings=false;
                updateCollectionFilters();schedule();
            });
        }
        HorizontalScrollView sourceScroll=VaultNativeStyle.horizontalScroll(activity);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.addView(filters);
        quickFilters.addView(sourceScroll);
        TextView ownedLabel=text("OWNERSHIP",11,true);
        ownedLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        quickFilters.addView(ownedLabel);
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
                inventory.edit().putString(PREF_OWNED,owned).apply();
                onlyOwnedPhysicalPrintings=false;
                updateCollectionFilters();schedule();
            });
        }
        HorizontalScrollView ownedScroll=VaultNativeStyle.horizontalScroll(activity);
        ownedScroll.setHorizontalScrollBarEnabled(false);
        ownedScroll.addView(ownership);
        LinearLayout.LayoutParams ownedParams=new LinearLayout.LayoutParams(-1,-2);
        ownedParams.bottomMargin=dp(14);
        quickFilters.addView(ownedScroll,ownedParams);
        printingFilter=VaultNativeStyle.button(activity,"Owned physical printings",false);
        LinearLayout.LayoutParams printingParams=new LinearLayout.LayoutParams(-1,dp(44));
        printingParams.bottomMargin=dp(12);
        quickFilters.addView(printingFilter,printingParams);
        printingFilter.setOnClickListener(v -> {
            onlyOwnedPhysicalPrintings=!onlyOwnedPhysicalPrintings;
            if(onlyOwnedPhysicalPrintings){source="physical";owned="owned";}
            page=0;updateCollectionFilters();schedule();
        });
        toolRow=VaultNativeStyle.row(activity);
        Button physicalShortcut=VaultNativeStyle.button(activity,"Physical",source.equals("physical"));
        Button arenaShortcut=VaultNativeStyle.button(activity,"Arena",source.equals("arena"));
        Button allShortcut=VaultNativeStyle.button(activity,"All",source.equals("both"));
        for(Button choice:new Button[]{physicalShortcut,arenaShortcut,allShortcut}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(37),1);
            lp.rightMargin=dp(3);
            toolRow.addView(choice,lp);
        }
        physicalShortcut.setOnClickListener(v->{source="physical";inventory.edit().putString(PREF_SOURCE,source).apply();page=0;updateCollectionFilters();schedule();});
        arenaShortcut.setOnClickListener(v->{source="arena";onlyOwnedPhysicalPrintings=false;inventory.edit().putString(PREF_SOURCE,source).apply();page=0;updateCollectionFilters();schedule();});
        allShortcut.setOnClickListener(v->{source="both";onlyOwnedPhysicalPrintings=false;inventory.edit().putString(PREF_SOURCE,source).apply();page=0;updateCollectionFilters();schedule();});
        sourceShortcuts=new Button[]{physicalShortcut,arenaShortcut,allShortcut};
        viewModeButton=VaultNativeStyle.button(activity,viewModeLabel(),false);
        viewModeButton.setContentDescription("Switch catalog view: card art, detailed list, text only");
        toolRow.addView(viewModeButton,new LinearLayout.LayoutParams(dp(88),dp(37)));
        viewModeButton.setOnClickListener(v->{
            viewMode=(viewMode+1)%3;
            artGrid=viewMode==0;
            viewModeButton.setText(viewModeLabel());
            inventory.edit().putInt(PREF_VIEW,viewMode).putBoolean(PREF_COMPACT_GRID,artGrid).apply();
            schedule();
        });
        layout.addView(toolRow,new LinearLayout.LayoutParams(-1,dp(39)));
        advancedButton=VaultNativeStyle.button(activity,"Filters & Sort  ▾",false);
        advancedButton.setVisibility(View.GONE);
        advancedFilters=VaultNativeStyle.column(activity);
        advancedFilters.setPadding(dp(10),dp(5),dp(10),dp(7));
        VaultNativeStyle.surface(activity,advancedFilters,false);
        buildAdvancedFilters();
        advancedFilters.addView(quickFilters,0);
        buildQuickManaFilters();
        advancedFilters.setVisibility(View.GONE);
        Runnable toggleFilters=()->{
            advancedOpen=!advancedOpen;
            advancedFilters.setVisibility(advancedOpen?View.VISIBLE:View.GONE);
            if(advancedOpen)scrollFiltersToTop();
        };
        filterShortcut.setOnClickListener(v->toggleFilters.run());
        advancedButton.setOnClickListener(v->toggleFilters.run());
        updateCollectionFilters();
        browseScroll = VaultNativeStyle.scroll(activity);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        results.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            if(disposed || !artGrid || lastRenderedPage==null || results.getWidth()<=0 ||
                browseScroll.getVisibility()!=View.VISIBLE)return;
            int columns=catalogColumns();
            if(columns!=lastRenderedColumns){
                final JSONObject samePage=lastRenderedPage;
                final int sameIndex=lastRenderedPageIndex;
                final String sameSource=lastRenderedSource;
                final boolean samePrinting=lastRenderedPrinting;
                lastRenderedColumns=columns;
                results.post(()->{
                    if(!disposed && artGrid && samePage==lastRenderedPage &&
                        browseScroll.getVisibility()==View.VISIBLE)
                        render(samePage,sameIndex,sameSource,samePrinting);
                });
            }
        });
        LinearLayout browseContent=VaultNativeStyle.column(activity);
        browseContent.addView(advancedFilters);
        browseContent.addView(status);
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
    /** AI Review presents the inspector inside its own scrolling workspace.
     * Let that outer scroll own vertical scrolling and size the entire card,
     * rather than clipping it inside a second, fixed-height scroll viewport. */
    void useEmbeddedInspectorLayout(){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        inspectorScroll.setLayoutParams(params);
        inspectorScroll.setNestedScrollingEnabled(false);
        inspectorScroll.setVerticalScrollBarEnabled(false);
        browseScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0));
        layout.setMinimumHeight(0);
    }
    void close() {
        disposed = true;
        ui.removeCallbacks(pendingSearch);
        request.incrementAndGet();
        inspectorRequest++;
        worker.shutdownNow();
        artworkWorker.shutdownNow();
        pagePrefetchWorker.shutdownNow();
        pageCache.clear();queuedPageArt.clear();
    }
    private void scrollFiltersToTop() {
        browseScroll.post(()->browseScroll.smoothScrollTo(0,0));
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
        for(java.util.Map.Entry<String,NativeManaFilterButton> entry:identityButtons.entrySet()){
            boolean selected=entry.getKey().equals("C")?
                "C".equals(exactColorIdentity):
                exactColorIdentity.contains(entry.getKey());
            entry.getValue().setSelectedMana(selected);
        }
    }

    /** Keep color identity immediately available as a search control, using
     * the same official mana SVGs as the desktop search and commander browser.
     * Long-form filters stay collapsed, so two artwork columns remain visible. */
    private void buildQuickManaFilters(){
        LinearLayout quickManaRow=VaultNativeStyle.row(activity);
        TextView colorLabel=text("COLOR",10,true);
        colorLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        quickManaRow.addView(colorLabel,new LinearLayout.LayoutParams(dp(63),dp(42)));
        LinearLayout colorRow=VaultNativeStyle.row(activity);
        for(String color:new String[]{"W","U","B","R","G","C"}){
            NativeManaFilterButton button=new NativeManaFilterButton(activity,artwork,color);
            identityButtons.put(color,button);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);
            lp.rightMargin=dp(3);
            colorRow.addView(button,lp);
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
                updateIdentityButtons();inventory.edit().putString(PREF_COLOR,exactColorIdentity).apply();page=0;schedule();
            });
        }
        quickManaRow.addView(colorRow,new LinearLayout.LayoutParams(0,dp(42),1));
        manaRow=quickManaRow;
        manaRow.setVisibility(View.GONE);
        colorToggle=VaultNativeStyle.button(activity,"Colors  ▾",false);
        colorToggle.setOnClickListener(v->{
            colorsOpen=!colorsOpen;
            manaRow.setVisibility(colorsOpen?View.VISIBLE:View.GONE);
            colorToggle.setText(colorsOpen?"Colors  ▴":"Colors  ▾");
        });
        layout.addView(colorToggle,new LinearLayout.LayoutParams(-1,dp(34)));
        layout.addView(manaRow,new LinearLayout.LayoutParams(-1,dp(45)));
        updateIdentityButtons();
    }

    private void buildAdvancedFilters(){
        filterField("SET CODE","Exact set code (e.g. MH3)",value->setCode=value);
        filterField("TYPE CONTAINS","Creature, Wizard, Enchantment…",value->typeFilter=value);
        TextView mechanicHeader=text("KEYWORD / RULES TEXT",11,true);
        mechanicHeader.setTextColor(VaultNativeStyle.SUBTEXT);
        advancedFilters.addView(mechanicHeader);
        advancedFilters.addView(new VaultKeywordTokens(activity,value->{
            oracleFilter=value;page=0;schedule();
        }).view());
        filterPicker("MECHANIC MATCH",new String[]{"Match All","Match Any"},
            new String[]{"all","any"},value->oracleMatch=value);
        filterPicker("RARITY",RARITY_NAMES,RARITY_VALUES,value->rarityFilter=value);
        filterPicker("UTILIZATION TAG",ROLE_NAMES,ROLE_NAMES,value->
            roleFilter="All roles".equals(value)?"":value);
        filterField("MANA VALUE / CMC MIN","Minimum mana value (0 or more)",value->manaValueMin=value);
        filterField("MANA VALUE / CMC MAX","Maximum; same as min for exact",value->manaValueMax=value);
        filterPicker("SORT BY",SORT_NAMES,SORT_VALUES,value->sortMode=value);
        Button reset=VaultNativeStyle.button(activity,"Reset advanced filters",false);
        advancedFilters.addView(reset,new LinearLayout.LayoutParams(-1,dp(42)));
        Button count=VaultNativeStyle.button(activity,"Cards per page: "+pageSize+"  ▾",false);
        advancedFilters.addView(count,new LinearLayout.LayoutParams(-1,dp(42)));
        count.setOnClickListener(v->{
            pageSize=pageSize==20?40:pageSize==40?80:20;
            inventory.edit().putInt(PREF_PAGE_SIZE,pageSize).apply();
            count.setText("Cards per page: "+pageSize+"  ▾");
            page=0;schedule();
        });
        reset.setOnClickListener(v->{
            setCode="";typeFilter="";oracleFilter="";oracleMatch="all";rarityFilter="";roleFilter="";
            sortMode="";exactColorIdentity="";manaValueMin="";manaValueMax="";
            advancedFilters.removeAllViews();
            buildAdvancedFilters();
            advancedFilters.addView(quickFilters,0);
            updateIdentityButtons();inventory.edit().putString(PREF_COLOR,"").apply();page=0;schedule();
        });
    }

    private String viewModeLabel(){return viewMode==0?"Art ▦":viewMode==1?"List ☷":"Text ≡";}

    private void updateCollectionFilters() {
        if(sourceShortcuts!=null){
            String[] shortcutSources={"physical","arena","both"};
            for(int i=0;i<sourceShortcuts.length;i++){
                boolean selected=source.equals(shortcutSources[i]);
                sourceShortcuts[i].setTextColor(selected?VaultNativeStyle.BACKGROUND:VaultNativeStyle.TEXT);
                sourceShortcuts[i].setBackground(VaultNativeStyle.rounded(activity,
                    selected?VaultNativeStyle.PRIMARY:VaultNativeStyle.ELEVATED,
                    selected?Color.TRANSPARENT:VaultNativeStyle.BORDER,10));
            }
        }
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

    /** Coalesce rapid typing while invalidating in-flight results immediately. */
    private void scheduleSearch(){
        if(!filtersReady||disposed)return;
        request.incrementAndGet();
        ui.removeCallbacks(pendingSearch);
        ui.postDelayed(pendingSearch,180);
    }

    private void schedule() {
        ui.removeCallbacks(pendingSearch);
        if(!filtersReady||disposed)return;
        final int id=request.incrementAndGet();
        final String q=query, src=source, scope=owned;
        final String selectedSet=setCode, selectedType=typeFilter,
            selectedOracle=oracleFilter,
            selectedRarity=rarityFilter, selectedRole=roleFilter,
            selectedSort=sortMode, selectedIdentity=exactColorIdentity,
            selectedOracleMatch=oracleMatch,
            selectedMvMin=manaValueMin,selectedMvMax=manaValueMax;
        final boolean printingMode=onlyOwnedPhysicalPrintings && "physical".equals(src);
        final int selectedPage=page;
        final int selectedPageSize=pageSize;
        status.setText("Searching your catalog…");
        worker.execute(() -> {
            try {
                Map<String,String> options=new HashMap<>();
                options.put("q",q);options.put("scope",scope);
                options.put("set",selectedSet);options.put("type",selectedType);
                options.put("oracle",selectedOracle);
                options.put("oracleMatch",selectedOracleMatch);
                options.put("rarity",selectedRarity);options.put("role",selectedRole);
                options.put("sort",selectedSort);options.put("colors",selectedIdentity);
                options.put("mvMin",selectedMvMin);options.put("mvMax",selectedMvMax);
                options.put("source",src);
                // A physical collection is not the same as cards unavailable on Arena.
                // All Cards searches the full catalog and ownership across both inventories.
                options.put("arena","");
                options.put("page",Integer.toString(selectedPage));
                options.put("size",Integer.toString(selectedPageSize));
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
        for(int i=0;i<rows.length()&&i<80;i++){
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
        int displayWidth=width>0?width:
            activity.getResources().getDisplayMetrics().widthPixels-dp(44);
        int displayHeight=height>0?height:dp(440);
        boolean fullPreview=width<=0;
        loadThumbnail(image,front,displayWidth,displayHeight,fullPreview);
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
                loadThumbnail(image,showingBack[0]?reverse:front,
                    displayWidth,displayHeight,fullPreview);
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
        lastRenderedPage=response;
        lastRenderedPageIndex=selectedPage;
        lastRenderedSource=src;
        lastRenderedPrinting=printingMode;
        lastRenderedColumns=artGrid?catalogColumns():-1;
        results.removeAllViews();
        int total=response.optInt("total",0);
        JSONArray rows=response.optJSONArray("cards");
        int count=rows==null?0:rows.length();
        int pageCount=Math.max(1,(total+pageSize-1)/pageSize);
        status.setText(total+(printingMode?" confirmed owned printings":" cards found")+
                " • page "+(selectedPage+1)+" of "+pageCount
                +("physical".equals(src)?" • Physical Collection":"arena".equals(src)?" • Arena Collection":" • Both Collections"));
        if(total==0){
            results.addView(text(printingMode?
                "No confirmed printings found. Name-only physical cards stay in Physical → Owned, "+
                "but are not assigned to a set. Sync printing metadata or restore verified IDs in Legacy tools.":
                "No cards match the current search and filters. Clear the search or adjust Filters & Sort. If your catalog is empty, refresh card data in Data & Sync.",14,false));
            return;
        }
        GridLayout gallery=null;
        if(artGrid){
            gallery=new GridLayout(activity);
            gallery.setColumnCount(catalogColumns());
            gallery.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            results.addView(gallery,new LinearLayout.LayoutParams(-1,-2));
        }
        for(int i=0;i<count;i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
            if(printingMode){
                if(gallery!=null)renderOwnedPrintingTile(gallery,row,i);
                else renderOwnedPrintingRow(row);
                continue;
            }
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
            entry.setPadding(dp(9),dp(viewMode==2?5:8),dp(9),dp(viewMode==2?5:8));
            VaultNativeStyle.surface(activity,entry,false);
            String imageUrl=meta==null?"":meta.optString("image","");
            String imageBack=meta==null?"":meta.optString("imageBack","");
            if(viewMode!=2&&hasOfficialArt(imageUrl)){
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
            entry.setOnClickListener(v -> showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty,
                null,"","",0,meta,null));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(5);
            results.addView(entry,lp);
        }
        LinearLayout pager=new LinearLayout(activity);
        Button back=button("Previous");
        Button next=button("Next");
        pager.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        pager.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        back.setEnabled(selectedPage>0);
        next.setEnabled((selectedPage+1)*pageSize<total);
        back.setOnClickListener(v -> { page=Math.max(0,page-1); schedule();
            browseScroll.post(()->browseScroll.smoothScrollTo(0,0)); });
        next.setOnClickListener(v -> { page++; schedule();
            browseScroll.post(()->browseScroll.smoothScrollTo(0,0)); });
        results.addView(pager);
    }
    /** Width of the actual catalog content, not the physical screen: this also
     * accounts for Android navigation insets, padding and multi-window mode. */
    private int galleryWidth() {
        return results.getWidth() > 0 ? results.getWidth() :
            activity.getResources().getDisplayMetrics().widthPixels-dp(36);
    }

    /** Window-width based layout, including compact split-screen and tablets. */
    private int catalogColumns() {
        int contentDp=Math.round(galleryWidth() /
            activity.getResources().getDisplayMetrics().density);
        return NativeWindowLayoutPolicy.catalogColumns(contentDp);
    }

    private int catalogTileWidth() {
        int columns=catalogColumns();
        return Math.max(dp(96),(galleryWidth()-columns*dp(6))/columns);
    }

    /** A responsive card-art browser with the same inspect action as
     * detailed rows. Images come only from the existing guarded cache. */
    private void renderArtworkTile(GridLayout gallery,JSONObject row,int index) {
        JSONObject meta=row.optJSONObject("meta");
        String name=row.optString("name","Unknown card");
        String imageUrl=meta==null?"":meta.optString("image","");
        String type=meta==null?"":meta.optString("type","");
        String oracle=meta==null?"":meta.optString("oracle","");
        String rarity=meta==null?"":meta.optString("rarity","");
        int arenaQty=row.optInt("arenaQty",0),physicalQty=row.optInt("physicalQty",0);
        int columns=catalogColumns();
        int cardWidth=catalogTileWidth();
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
            showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty,
                null,"","",0,meta,null));
        GridLayout.LayoutParams cell=new GridLayout.LayoutParams();
        cell.width=cardWidth;
        cell.height=-2;
        cell.rowSpec=GridLayout.spec(index/columns);
        cell.columnSpec=GridLayout.spec(index%columns);
        cell.setMargins(dp(3),dp(3),dp(3),dp(3));
        gallery.addView(tile,cell);
    }

    /** Physical printings remain distinct by verified set/collector/finish even
     * in the compact two-column gallery. Tapping opens the exact edition. */
    private void renderOwnedPrintingTile(GridLayout gallery,JSONObject row,int index) {
        JSONObject printing=row.optJSONObject("printing");
        if(printing==null)return;
        String name=row.optString("name","Unknown card");
        String set=printing.optString("set","").toUpperCase(java.util.Locale.ROOT);
        String collector=printing.optString("collectorNumber","");
        String imageUrl=printing.optString("image","");
        int copies=printing.optInt("confirmedPhysicalCopies",0);
        int width=catalogTileWidth();
        LinearLayout tile=VaultNativeStyle.column(activity);
        tile.setPadding(dp(7),dp(7),dp(7),dp(8));
        VaultNativeStyle.surface(activity,tile,false);
        if(hasOfficialArt(imageUrl)) {
            FrameLayout art=flippableArt(name,imageUrl,
                printing.optString("imageBack",""),width-dp(14),
                Math.round((width-dp(14))*1.36f));
            tile.addView(art,new LinearLayout.LayoutParams(-1,
                Math.round((width-dp(14))*1.36f)));
        }
        TextView title=text(name,12,true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(title);
        TextView edition=text(set+" #"+collector+"  ·  ×"+copies,11,false);
        edition.setTextColor(VaultNativeStyle.SUBTEXT);
        edition.setMaxLines(1);
        tile.addView(edition);
        tile.setContentDescription(name+", "+set+" #"+collector+
            ", physical copies "+copies+". Tap to inspect exact printing");
        tile.setOnClickListener(v->showInlinePrintingDetail(name,printing,()->closeInspector()));
        GridLayout.LayoutParams cell=new GridLayout.LayoutParams();
        cell.width=width;
        cell.height=-2;
        int columns=catalogColumns();
        cell.rowSpec=GridLayout.spec(index/columns);
        cell.columnSpec=GridLayout.spec(index%columns);
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
        if(viewMode!=2&&hasOfficialArt(imageUrl)){
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

    /** Fetch normal card art for grid tiles and large Scryfall art for
     * expanded full-card previews. Dynamic sampling uses actual display pixels
     * so text remains legible on high-density Android screens. */
    private void loadThumbnail(ImageView target,String imageUrl,int width,
            int height,boolean fullPreview) {
        target.setTag(imageUrl);
        if(!NativeArtworkQuality.supported(imageUrl))return;
        artworkWorker.execute(() -> {
            try {
                Bitmap bitmap=NativeArtworkQuality.load(artwork,imageUrl,
                    width,height,fullPreview);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed || activity.isFinishing() ||
                        !imageUrl.equals(target.getTag())) {
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
    private Runnable inspectorBack;
    /** Android system Back follows the same card inspector hierarchy as the
     * inline Back controls; it never dismisses the entire native activity. */
    boolean onBack(){
        if(disposed || inspectorScroll.getVisibility()!=View.VISIBLE)return false;
        if(inspectorBack!=null)inspectorBack.run();
        else closeInspector();
        return true;
    }

    private void openInspector(String heading) {
        inspectorRequest++;
        inspectorContent.removeAllViews();
        browseScroll.setVisibility(View.GONE);
        searchRow.setVisibility(View.GONE);
        toolRow.setVisibility(View.GONE);
        colorToggle.setVisibility(View.GONE);
        manaRow.setVisibility(View.GONE);
        inspectorScroll.setVisibility(View.VISIBLE);
        inspectorScroll.scrollTo(0,0);
        Button back=VaultNativeStyle.button(activity,"← Back to catalog",false);
        inspectorContent.addView(back,new LinearLayout.LayoutParams(-1,dp(46)));
        back.setOnClickListener(v->onBack());
        TextView title=text(heading,22,true);
        title.setMaxLines(3);
        inspectorContent.addView(title);
    }

    private void closeInspector() {
        inspectorBack=null;
        inspectorRequest++;
        inspectorScroll.setVisibility(View.GONE);
        searchRow.setVisibility(View.VISIBLE);
        toolRow.setVisibility(View.VISIBLE);
        colorToggle.setVisibility(View.VISIBLE);
        manaRow.setVisibility(colorsOpen?View.VISIBLE:View.GONE);
        browseScroll.setVisibility(View.VISIBLE);
    }

    private void addInspectorArtwork(String name,String imageUrl,String imageBack) {
        if(!hasOfficialArt(imageUrl))return;
        FrameLayout art=flippableArt(name,imageUrl,imageBack,-1,dp(330));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(330));
        params.topMargin=dp(8);params.bottomMargin=dp(8);
        inspectorContent.addView(art,params);
    }

    /** Enter the exact same full Catalog inspector from Set Progress.
     * The set row supplies verified printing ownership; card metadata comes
     * from the on-device catalog rather than a second reduced dialog. */
    void inspectSetPrinting(JSONObject printing,String setName,String source,
            int confirmedQty,Runnable returnToSet) {
        String name=printing.optString("name","").trim();
        if(name.isEmpty()){status.setText("Printing has no card name");return;}
        openInspector(name);
        inspectorBack=returnToSet;
        inspectorContent.addView(text("Loading official card details…",14,false));
        final int token=inspectorRequest;
        worker.execute(()->{
            try {
                JSONObject row=catalog.cardDetails(name);
                JSONObject exactPrinting=printing;
                if("physical".equals(source)&&!printing.optString("id","").isEmpty()){
                    Map<String,String> variantArgs=new HashMap<>();
                    variantArgs.put("name",name);
                    JSONArray variants=catalog.variants(variantArgs).getJSONArray("variants");
                    for(int i=0;i<variants.length();i++){
                        JSONObject variant=variants.optJSONObject(i);
                        if(variant!=null&&printing.optString("id","")
                                .equalsIgnoreCase(variant.optString("id",""))){
                            exactPrinting=variant;
                            break;
                        }
                    }
                }
                final JSONObject selected=exactPrinting;
                ui.post(()->{
                    if(disposed||activity.isFinishing()||token!=inspectorRequest)return;
                    JSONObject meta=row.optJSONObject("meta");
                    if(meta==null)meta=new JSONObject();
                    showDetails(name,meta.optString("type",""),meta.optString("oracle",""),
                        meta.optString("rarity",""),meta.optString("image",""),
                        meta.optString("imageBack",""),row.optInt("arenaQty",0),
                        row.optInt("physicalQty",0),selected,setName,source,
                        confirmedQty,meta,returnToSet);
                });
            }catch(Exception error){
                ui.post(()->{
                    if(disposed||token!=inspectorRequest)return;
                    inspectorContent.removeAllViews();
                    Button back=VaultNativeStyle.button(activity,"← Back to set",false);
                    inspectorContent.addView(back);
                    back.setOnClickListener(v->returnToSet.run());
                    inspectorContent.addView(text(
                        "Card details unavailable: "+error.getMessage(),14,false));
                });
            }
        });
    }

    /** Full existing Catalog details from AI Review, including artwork,
     * oracle text, formats and ownership. Return preserves the AI result. */
    void inspectAiCard(String requestedName,Runnable returnToReview) {
        String name=NativeArenaCardName.identity(requestedName);
        openInspector(name);
        inspectorBack=returnToReview;
        Button back=(Button)inspectorContent.getChildAt(0);
        back.setText("← Back to AI Review");
        inspectorContent.addView(text("Loading local card details…",14,false));
        final int token=inspectorRequest;
        worker.execute(()->{
            try{
                JSONObject row=catalog.cardDetails(name);
                ui.post(()->{
                    if(disposed||activity.isFinishing()||token!=inspectorRequest)return;
                    JSONObject meta=row.optJSONObject("meta");
                    if(meta==null)meta=new JSONObject();
                    showDetails(name,meta.optString("type",""),meta.optString("oracle",""),
                        meta.optString("rarity",""),meta.optString("image",""),
                        meta.optString("imageBack",""),row.optInt("arenaQty",0),
                        row.optInt("physicalQty",0),null,"","",0,meta,returnToReview);
                    Button top=(Button)inspectorContent.getChildAt(0);
                    top.setText("← Back to AI Review");
                });
            }catch(Exception error){
                ui.post(()->{
                    if(disposed||activity.isFinishing()||token!=inspectorRequest)return;
                    inspectorContent.removeAllViews();
                    Button backButton=VaultNativeStyle.button(activity,"← Back to AI Review",false);
                    inspectorContent.addView(backButton);
                    backButton.setOnClickListener(v->returnToReview.run());
                    inspectorContent.addView(text("Card details unavailable: "+
                        error.getMessage(),14,false));
                });
            }
        });
    }

    /** Compact official Scryfall mana glyphs shared by Catalog and Set Progress.
     * Numeric/hybrid tokens remain literal when no approved SVG is available;
     * no approximated or duplicate color symbols are invented. */
    private void showReadOnlyMana(JSONObject metadata){
        if(metadata==null)return;
        String mana=metadata.optString("manaCost","");
        if(!mana.isEmpty()){
            inspectorContent.addView(text("MANA COST (EXACT)",11,true));
            LinearLayout glyphs=VaultNativeStyle.row(activity);
            java.util.regex.Matcher tokens=java.util.regex.Pattern
                .compile("\\{([^{}]{1,16})\\}").matcher(mana);
            int count=0;
            while(tokens.find()&&count++<24){
                String symbol=tokens.group(1).toUpperCase(java.util.Locale.ROOT);
                if(symbol.matches("[WUBRGC]")){
                    NativeManaFilterButton icon=new NativeManaFilterButton(activity,artwork,symbol);
                    icon.setReadOnlyDisplay();
                    glyphs.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
                }else{
                    TextView literal=text("{"+symbol+"}",14,true);
                    literal.setGravity(Gravity.CENTER);
                    literal.setContentDescription(symbol+" mana cost");
                    glyphs.addView(literal,new LinearLayout.LayoutParams(dp(46),dp(40)));
                }
            }
            if(count==0)glyphs.addView(text(mana,14,false));
            HorizontalScrollView scroll=VaultNativeStyle.horizontalScroll(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(glyphs);
            inspectorContent.addView(scroll);
        }
        JSONArray identity=metadata.optJSONArray("colors");
        if(identity!=null){
            inspectorContent.addView(text("COLOR IDENTITY",11,true));
            LinearLayout glyphs=VaultNativeStyle.row(activity);
            boolean any=false;
            for(int i=0;i<identity.length()&&i<5;i++){
                String code=identity.optString(i,"").toUpperCase(java.util.Locale.ROOT);
                if(!code.matches("[WUBRG]"))continue;
                NativeManaFilterButton icon=new NativeManaFilterButton(activity,artwork,code);
                icon.setReadOnlyDisplay();
                glyphs.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
                any=true;
            }
            if(!any)glyphs.addView(text("Colorless",14,false));
            inspectorContent.addView(glyphs);
        }
    }

    private void showDetails(String name,String type,String oracle,String rarity,
            String imageUrl,String imageBack,int arenaQty,int physicalQty) {
        showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty,
            null,"","",0,null,null);
    }

    private void showDetails(String name,String type,String oracle,String rarity,
            String imageUrl,String imageBack,int arenaQty,int physicalQty,
            JSONObject printing,String setName,String source,int selectedCopies,
            JSONObject metadata,Runnable returnToSet) {
        openInspector(name);
        inspectorBack=returnToSet;
        if(returnToSet!=null){
            Button back=(Button)inspectorContent.getChildAt(0);
            back.setText("← Back to set");
        }
        String front=printing!=null?printing.optString("image",imageUrl):imageUrl;
        String backArt=printing!=null?printing.optString("imageBack",imageBack):imageBack;
        addInspectorArtwork(name,front,backArt);
        if(printing!=null){
            String edition=source.equals("physical")?
                setName+" · #"+printing.optString("collectorNumber",""):
                setName+" · Arena card-name coverage";
            TextView selected=text(edition+"\n"+(source.equals("physical")?
                "Confirmed edition copies: ":"Arena copies: ")+selectedCopies+
                (source.equals("physical")?
                "\nPrinting ID: "+printing.optString("id","")+
                (printing.optString("released","").isEmpty()?"": 
                    "\nPrinting released: "+printing.optString("released","")):""),13,true);
            selected.setTextColor(VaultNativeStyle.PRIMARY);
            inspectorContent.addView(selected);
        }
        if(metadata!=null){
            if(metadata.has("mv")){
                TextView mv=text("Mana value: "+metadata.optDouble("mv",0),
                    13,false);
                mv.setTextColor(VaultNativeStyle.SUBTEXT);
                inspectorContent.addView(mv);
            }
            showReadOnlyMana(metadata);
        }
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
        TextView rules=NativeOracleSymbols.render(activity,artwork,
            oracle.isEmpty()?"No rules text":oracle,15);
        rules.setTextColor(VaultNativeStyle.TEXT);
        rules.setLineSpacing(dp(3),1f);
        oraclePanel.addView(rules);
        VaultNativeStyle.margin(inspectorContent,oraclePanel,10,activity);
        // Keep the Oracle panel visible above the fold. The full legalities
        // list remains available in a collapsible, scrollable inspector area.
        JSONObject legalities=metadata==null?null:metadata.optJSONObject("legalities");
        if(legalities!=null&&legalities.length()>0){
            java.util.ArrayList<String> formats=new java.util.ArrayList<>();
            for(java.util.Iterator<String> it=legalities.keys();it.hasNext();)
                formats.add(it.next());
            java.util.Collections.sort(formats);
            StringBuilder statuses=new StringBuilder();
            for(String format:formats)statuses.append(format).append(": ")
                .append(legalities.optString(format,"unknown")).append("\n");
            Button toggle=VaultNativeStyle.button(activity,
                "FORMAT LEGALITIES (cached) · "+formats.size()+" formats ▸",false);
            inspectorContent.addView(toggle,new LinearLayout.LayoutParams(-1,dp(46)));
            TextView list=text(statuses.toString().trim()+
                "\nCached Scryfall status; verify current bans before play.",13,false);
            list.setTextColor(VaultNativeStyle.SUBTEXT);
            list.setVisibility(View.GONE);
            inspectorContent.addView(list);
            toggle.setOnClickListener(v->{
                boolean open=list.getVisibility()!=View.VISIBLE;
                list.setVisibility(open?View.VISIBLE:View.GONE);
                toggle.setText("FORMAT LEGALITIES (cached) · "+formats.size()+
                    (open?" formats ▾":" formats ▸"));
            });
        }

        // A native star edits the SAME vault_favorites list used by Legacy
        // tools and Windows backups; never invent a parallel bookmark store.
        final int shownRequest=inspectorRequest;
        Button star=VaultNativeStyle.button(activity,"☆ Add to Shortlist",false);
        inspectorContent.addView(star,new LinearLayout.LayoutParams(-1,dp(46)));
        final boolean[] shortlisted={false};
        star.setEnabled(false);
        ((MainActivity)activity).loadNativeShortlist(favorites->{
            if(disposed||activity.isFinishing()||shownRequest!=inspectorRequest)return;
            if(favorites==null){
                star.setText("Shortlist unavailable until data is ready");
                return;
            }
            for(int i=0;i<favorites.length();i++){
                if(name.equalsIgnoreCase(favorites.optString(i,""))){
                    shortlisted[0]=true;break;
                }
            }
            star.setText(shortlisted[0]?"★ Remove from Shortlist":"☆ Add to Shortlist");
            star.setEnabled(true);
        });
        star.setOnClickListener(v->{
            boolean add=!shortlisted[0];
            star.setEnabled(false);
            ((MainActivity)activity).setNativeShortlist(name,add,(ok,message)->{
                if(disposed||activity.isFinishing()||shownRequest!=inspectorRequest)return;
                star.setEnabled(true);
                if(ok){
                    shortlisted[0]=add;
                    star.setText(add?"★ Remove from Shortlist":"☆ Add to Shortlist");
                }else{
                    status.setText("Shortlist not updated: "+message);
                }
            });
        });
        Button quickAdd=VaultNativeStyle.button(activity,
            "＋ Add to Physical Collection",true);
        inspectorContent.addView(quickAdd,new LinearLayout.LayoutParams(-1,dp(48)));
        quickAdd.setOnClickListener(v->showInlinePrintings(name,
            ()->showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty,
                printing,setName,source,selectedCopies,metadata,returnToSet)));

        Button browsePrintings=VaultNativeStyle.button(activity,
            "View printings & edit physical ownership",false);
        inspectorContent.addView(browsePrintings,new LinearLayout.LayoutParams(-1,dp(48)));
        browsePrintings.setOnClickListener(v->showInlinePrintings(name,
            ()->showDetails(name,type,oracle,rarity,imageUrl,imageBack,arenaQty,physicalQty,
                printing,setName,source,selectedCopies,metadata,returnToSet)));
    }

    private void showInlinePrintings(String name,Runnable previous) {
        openInspector("Printings · "+name);
        inspectorBack=previous;
        Button back=VaultNativeStyle.button(activity,"← Card details",false);
        inspectorContent.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
        back.setOnClickListener(v->onBack());
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
        inspectorBack=previous;
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
                Bitmap bitmap=NativeArtworkQuality.load(artwork,imageUrl,
                    activity.getResources().getDisplayMetrics().widthPixels-dp(56),
                    dp(400),true);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed || !dialog.isShowing()){bitmap.recycle();return;}
                    cardArt.setImageBitmap(bitmap);
                });
            } catch(Exception ignored) { /* Text-only inspector stays available offline. */ }
        });
    }

}
