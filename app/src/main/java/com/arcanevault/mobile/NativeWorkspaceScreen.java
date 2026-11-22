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
import android.widget.GridLayout;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Comparator;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
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
    private final ExecutorService setDetailsWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService coverWorkers=Executors.newFixedThreadPool(2);
    private final ExecutorService setArtWorkers=Executors.newFixedThreadPool(4);
    private final ArrayList<DeckCoverTarget> deckCoverTargets=new ArrayList<>();
    private final Set<String> coverInFlight=new HashSet<>();
    private final Set<String> coverUnavailable=new HashSet<>();
    // Draft artwork may become available after catalog indexing or an
    // intermittent image fetch; do not permanently blacklist that card.
    private final Map<String,Integer> draftArtFailures=new HashMap<>();
    private final Map<String,Long> draftArtRetryAfter=new HashMap<>();
    private final Set<String> setArtInFlight=new HashSet<>();
    private final LinkedHashMap<String,Bitmap> coverBitmaps=new LinkedHashMap<String,Bitmap>(40,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> eldest){
            return size()>32;
        }
    };
    private final ScrollView workspaceScroll;
    private final LinearLayout root;
    private final LinearLayout content;
    private final TextView status;
    private View setOverviewFilters;
    private View setOverviewSearch;
    private volatile boolean closed;
    private int page=0;
    private String syncSection="devices";
    private final ArrayDeque<String> syncHistory=new ArrayDeque<>();
    private String screenState="";
    private JSONObject editingDeck;
    private MobileChatGptClient aiClient;
    private NativeCatalogScreen aiCardInspector;
    private NativeCatalogScreen deckCardInspector;
    private final ArrayList<View> deckDetailsRetainedViews=new ArrayList<>();
    private int deckDetailsRetainedScroll;
    private String deckCardReturnState="deck-details";
    private View aiReviewRetainedView;
    private int aiReviewRetainedScroll;
    private final ArrayList<View> aiReturnViews=new ArrayList<>();
    private String aiReturnState="";
    private String aiReturnStatus="";
    private int aiReturnScroll=0;
    void setAiClient(MobileChatGptClient client){this.aiClient=client;}

    private EditText editorName,editorMainboard;
    private String editorOriginalName="",editorOriginalMainboard="";
    private String deckCommanderFilter="";
    private JSONArray deckSnapshot=new JSONArray();
    private LinearLayout deckListPanel;
    private String deckSearch="",deckSort="updated",deckFolder="",deckFormat="";
    private int deckLibraryRestoreScroll=-1;
    private final Runnable deckSearchPending=()->{
        if(!closed&&"deck-library".equals(screenState))renderDeckList();
    };
    private boolean deckFavoritesOnly=false;
    private LinearLayout shortlistPanel;
    private ArrayList<JSONObject> shortlistCards=new ArrayList<>();
    private String shortlistQuery="";
    private int shortlistPage=0;
    private int shortlistRestoreScrollY=0;
    private boolean shortlistNeedsScrollRestore=false;
    private static final int SHORTLIST_PAGE_SIZE=48;
    private final ArrayList<ShortlistArtTarget> shortlistArtTargets=new ArrayList<>();
    private final Set<String> shortlistArtInFlight=new HashSet<>();
    private final Set<String> shortlistArtUnavailable=new HashSet<>();
    private final LinkedHashMap<String,Bitmap> shortlistBitmaps=
        new LinkedHashMap<String,Bitmap>(32,0.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> entry){
                return size()>20;
            }
        };
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
        String title=section.equals("sets")?"Set Progress":section.equals("decks")?"Deck Library":
            section.equals("shortlist")?"My Shortlist":"Data & Sync";
        root.addView(VaultNativeStyle.label(activity,title,24,VaultNativeStyle.TEXT,true));
        status=VaultNativeStyle.label(activity,"Loading local data…",12,VaultNativeStyle.SUBTEXT,false);
        status.setPadding(0,dp(9),0,dp(11));
        root.addView(status);
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        workspaceScroll=scroll;
        workspaceScroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->{
            loadVisibleDeckCovers();
            loadVisibleShortlistArt();
        });
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
            setOverviewFilters=settings;
            EditText search=new EditText(activity);
            VaultNativeStyle.search(activity,search);
            search.setHint("Search sets by name or code");
            root.addView(search,3,new LinearLayout.LayoutParams(-1,dp(48)));
            setOverviewSearch=search;
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){
                    setQuery=s.toString();
                    setProgress(setQuery,0);
                }
                public void afterTextChanged(Editable e){}
            });
            if(initialCommander!=null&&initialCommander.matches("[A-Za-z0-9]{2,12}"))
                openWidgetSet(initialCommander,setSource);
            else setProgress("",0);
        }
        else if(section.equals("decks"))deckLibrary();
        else if(section.equals("shortlist"))shortlist();
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
    void close(){
        closed=true;
        ui.removeCallbacks(deckSearchPending);
        if(aiCardInspector!=null){aiCardInspector.close();aiCardInspector=null;}
        if(deckCardInspector!=null){deckCardInspector.close();deckCardInspector=null;}
        deckDetailsRetainedViews.clear();
        executor.shutdownNow();
        setDetailsWorker.shutdownNow();
        coverWorkers.shutdownNow();
        setArtWorkers.shutdownNow();
        deckCoverTargets.clear();
        coverInFlight.clear();
        setArtInFlight.clear();
        shortlistArtTargets.clear();
        shortlistArtInFlight.clear();
    }
    /** Handle one native workspace layer at a time. Never silently lose a
     * saved-deck draft; the same guard applies to bottom/tab navigation. */
    boolean onBack(){
        if(closed)return false;
        switch(screenState){
            case "ai-review":
                if(aiCardInspector!=null){
                    if(!aiCardInspector.onBack())returnFromAiCard();
                    return true;
                }
                returnFromAiReview();
                return true;
            case "deck-edit":
                if(deckCardInspector!=null){
                    if(!deckCardInspector.onBack())returnFromDeckCard();
                    return true;
                }
                requestLeaveEditor(()->showDeck(editingDeck));
                return true;
            case "deck-details":
                if(deckCardInspector!=null){
                    if(!deckCardInspector.onBack())returnFromDeckCard();
                    return true;
                }
                deckLibrary();
                return true;
            case "shortlist-preview":
                shortlist();
                return true;
            case "set-details":
                setProgress(setQuery,0);
                return true;
            case "sync":
                if(!syncHistory.isEmpty()){
                    syncSection=syncHistory.removeLast();
                    dataSync();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean editorChanged(){
        return ("deck-edit".equals(screenState) ||
            ("ai-review".equals(screenState) && "deck-edit".equals(aiReturnState))) && editorName!=null &&
            editorMainboard!=null &&
            (!editorOriginalName.equals(editorName.getText().toString()) ||
             !editorOriginalMainboard.equals(editorMainboard.getText().toString()));
    }

    /** Return true when an asynchronous leave confirmation owns navigation. */
    boolean confirmLeave(Runnable afterConfirmation){
        if(!editorChanged())return false;
        requestLeaveEditor(afterConfirmation);
        return true;
    }

    private void requestLeaveEditor(Runnable next){
        if(!editorChanged()){next.run();return;}
        new AlertDialog.Builder(activity)
            .setTitle("Discard unsaved deck changes?")
            .setMessage("Your edited deck name and mainboard have not been saved. "+
                "Keep editing, or discard this unsaved draft and go back.")
            .setNegativeButton("Keep editing",null)
            .setPositiveButton("Discard draft",(dialog,which)->{
                if(!closed && "deck-edit".equals(screenState)){
                    // Clear the draft guard before running cross-tab navigation,
                    // otherwise its callback would display the same dialog again.
                    screenState="leaving-editor";
                    editorName=null;editorMainboard=null;
                    next.run();
                }
            }).show();
    }

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
    private void copyText(String label,String value,String description){
        ClipboardManager board=(ClipboardManager)activity.getSystemService(
            Context.CLIPBOARD_SERVICE);
        if(board==null){info("Clipboard is unavailable.");return;}
        board.setPrimaryClip(ClipData.newPlainText(label,value));
        status.setText("Copied "+description+" to clipboard");
    }

    private Button button(String label,Runnable action){
        Button b=VaultNativeStyle.button(activity,label,false);
        VaultNativeStyle.margin(content,b,9,activity);
        b.setOnClickListener(v->action.run());
        return b;
    }
    /** Widget set tap selects an exact indexed set and reuses this same
     * read-only detail screen. Never infer a printing from card name. */
    void openWidgetSet(String code,String source){
        if(closed||code==null||!code.matches("[A-Za-z0-9]{2,12}"))return;
        if(!"arena".equals(source)&&!"physical".equals(source))return;
        setSource=source;
        // Do not queue behind a full Set Progress overview or wait for a
        // catalog-wide name lookup. The deep-link target is already verified.
        showSet(code,code.toUpperCase(java.util.Locale.ROOT),0);
    }

    /** The UI never waits for the indexer before showing a cached page. */
    private void setProgress(String filter,int pageNumber){
        screenState="set-overview";
        if(setOverviewFilters!=null)setOverviewFilters.setVisibility(View.VISIBLE);
        if(setOverviewSearch!=null)setOverviewSearch.setVisibility(View.VISIBLE);
        page=pageNumber;
        setQuery=filter;
        final int request=++setRequestId;
        final String source=setSource;
        final String order=setSort;
        final boolean hide=hideEmptySets;
        JSONObject cached=catalog.cachedSetOverview(source,filter);
        if(cached!=null){
            renderSetProgress(cached,request,source,filter,pageNumber,order,hide,true);
            if(!catalog.dirtySetCodes(source).isEmpty()){
                executor.execute(()->{
                    if(!catalog.refreshDirtySetProgress(source))return;
                    JSONObject refreshed=catalog.cachedSetOverview(source,filter);
                    if(refreshed!=null)ui.post(()->{
                        if(!closed&&request==setRequestId)
                            renderSetProgress(refreshed,request,source,filter,
                                pageNumber,order,hide,true);
                    });
                });
            }
            return;
        }

        // No previous completed snapshot. Populate a *non-valued* set
        // directory immediately from disk while we index in the worker.
        content.removeAllViews();
        JSONArray directory=VaultSetDirectory.cached(activity);
        if(hide){
            row("Preparing verified "+source+" set progress…",
                "Hide empty sets is enabled. Set cards appear once their "+
                "owned progress is verified; no unverified sets are shown.",null);
        }else renderCachedSetNames(directory,filter,source);
        status.setText("Preparing "+source+" set progress · cached progress pending…");
        executor.execute(()->{
            try{
                JSONObject result=catalog.indexedSetOverview(source,"");
                ui.post(()->{
                    if(closed||request!=setRequestId||activity.isFinishing())return;
                    JSONObject requested=catalog.cachedSetOverview(source,filter);
                    renderSetProgress(requested==null?result:requested,
                        request,source,filter,pageNumber,order,hide,false);
                });
            }catch(Exception error){
                ui.post(()->{
                    if(closed||request!=setRequestId)return;
                    status.setText("Set progress unavailable; saved set names remain accessible");
                    android.util.Log.w("ArcaneVault","Set Progress snapshot failed",error);
                });
            }
        });
    }

    private void renderCachedSetNames(JSONArray directory,String filter,String source){
        if(directory==null||directory.length()==0){
            row("Preparing set index…",
                "No cached directory yet. Set names will appear after indexing; "+
                "your saved inventory remains unchanged.",null);
            return;
        }
        String query=filter==null?"":filter.trim().toLowerCase(java.util.Locale.ROOT);
        int shown=0;
        for(int i=0;i<directory.length()&&shown<40;i++){
            JSONObject set=directory.optJSONObject(i);
            if(set==null)continue;
            String code=set.optString("code","");
            String name=set.optString("name",code);
            if(!code.matches("[a-z0-9]{2,12}"))continue;
            if(!query.isEmpty()&&!name.toLowerCase(java.util.Locale.ROOT).contains(query)
                &&!code.toLowerCase(java.util.Locale.ROOT).contains(query))continue;
            shown++;
            JSONObject card=new JSONObject();
            try{
                card.put("code",code).put("name",name);
                // Deliberately omit 'total' while the index is pending.
                // Never claim unknown ownership is 0% completion.
            }catch(Exception ignored){}
            addSetProgressCard(card,source,setRequestId);
        }
        if(shown==0)row("No cached set names match",
            "Clear the search or open Data & Sync to refresh the catalog.",null);
    }

    /** Exactly the same visual set-card in cached and first-load modes:
     * cached art, official set icon, owned/total and completion when measured.
     * Background image warmup runs outside the UI thread. */
    private void addSetProgressCard(JSONObject set,String source,int request){
        String code=set.optString("code","");
        String name=set.optString("name",code);
        if(code.isEmpty())return;
        try{
            String art=catalog.cachedRepresentativeSetArt(code,source);
            if(!art.isEmpty())set.put("artUrl",art);
        }catch(Exception ignored){}
        VaultSetValues.decorate(activity,set,source);
        ImageView tile=new ImageView(activity);
        tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final int width=activity.getResources().getDisplayMetrics().widthPixels-dp(36);
        tile.setImageBitmap(VaultSetProgressCard.render(activity,set,source,width,dp(161)));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(name+" · "+
            (set.has("total")?set.optInt("owned",0)+" of "+set.optInt("total",0):
                "completion pending") +" · open set details");
        tile.setOnClickListener(v->showSet(code,name,0));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(161));
        layout.bottomMargin=dp(9);
        content.addView(tile,layout);
        if(set.has("total")){
            int ownedCount=Math.max(0,set.optInt("owned",0));
            int allCount=Math.max(0,set.optInt("total",0));
            String released=set.optString("releaseDate","");
            String progress=ownedCount+" owned / "+allCount+" "+
                ("arena".equals(source)?"Arena names":"paper collector slots")+
                " · "+Math.max(0,allCount-ownedCount)+" missing";
            if(!released.isEmpty())progress+=" · Released "+released;
            TextView summary=VaultNativeStyle.label(activity,progress,
                12,VaultNativeStyle.SUBTEXT,false);
            VaultNativeStyle.margin(content,summary,3,activity);
        }
        // Resolve only cards visible in this page. No image fetch blocks the
        // overview or navigation, and later re-entry uses the on-disk cache.
        // Reuse existing cached artwork immediately. Missing images and set
        // symbols are populated asynchronously without blocking set progress.
        final String key=source+":"+code.toLowerCase(java.util.Locale.ROOT);
        if(!setArtInFlight.add(key))return;
        setArtWorkers.execute(()->{
            try{
                // Symbol download is independent of the local card catalog.
                VaultSetArtwork.prefetchSymbol(activity,code);
                ui.post(()->redrawSetProgressArt(tile,set,source,width,request));
                String art=catalog.resolveAndCacheSetArt(code,source);
                if(!art.isEmpty()){
                    VaultSetArtwork.prefetchArt(activity,art);
                    final String url=art;
                    ui.post(()->{
                        try{set.put("artUrl",url);}catch(Exception ignored){}
                        redrawSetProgressArt(tile,set,source,width,request);
                    });
                }
            }catch(Exception error){
                android.util.Log.w("ArcaneVault","Set artwork unavailable: "+code,error);
            }finally{
                ui.post(()->setArtInFlight.remove(key));
            }
        });
    }

    private void redrawSetProgressArt(ImageView tile,JSONObject set,String source,
            int width,int request){
        if(closed||request!=setRequestId)return;
        // Don't discard a completed image just because the list view has not
        // attached yet; the image view retains the bitmap when attached.
        if(tile.getParent()==null)return;
        tile.setImageBitmap(VaultSetProgressCard.render(
            activity,set,source,width,dp(161)));
    }

    /** Render only local data. No SQL is executed from the UI thread. */
    private void renderSetProgress(JSONObject result,int request,String source,
            String filter,int pageNumber,String order,boolean hide,boolean cached){
        if(closed||activity.isFinishing()||request!=setRequestId)return;
        JSONArray sets=result.optJSONArray("sets");
        if(sets==null)sets=new JSONArray();
        List<JSONObject> filtered=NativeSetProgressFilters.select(sets,hide,order);
        int allSets=sets.length();
        boolean missingOwned=filtered.isEmpty()&&allSets>0&&hide;
        content.removeAllViews();
        int totalCoverage=result.optInt("totalCards",-1);
        int verifiedCoverage=result.optInt("ownedCards",-1);
        if(totalCoverage>0&&verifiedCoverage>=0){
            double percent=100d*verifiedCoverage/totalCoverage;
            row("COLLECTION-WIDE SET COVERAGE",
                verifiedCoverage+" / "+totalCoverage+" "+
                    ("arena".equals(source)?"Arena card names":"paper collector slots")+
                    " · "+String.format(java.util.Locale.US,"%.1f%%",percent)+
                    " covered · "+Math.max(0,totalCoverage-verifiedCoverage)+" missing",
                null);
        }
        TextView hint=VaultNativeStyle.label(activity,
            "arena".equals(source)?
                "Arena ownership is tracked by card name, not verified physical edition.":
                "Physical progress uses confirmed printing identities.",
            12,VaultNativeStyle.SUBTEXT,false);
        VaultNativeStyle.margin(content,hint,10,activity);
        if(missingOwned)
            row("No sets with verified progress",
                "Hide empty sets is enabled. Unverified sets remain hidden; "+
                "turn it off to browse all sets.",null);
        if(filtered.isEmpty()&&!missingOwned)
            row("No sets match this view",
                "Clear the search, change the collection, or sync the set catalog.",null);
        java.util.Set<String> refreshing=catalog.dirtySetCodes(source);
        int start=pageNumber*40;
        int last=Math.min(filtered.size(),start+40);
        for(int i=start;i<last;i++){
            JSONObject set=filtered.get(i);
            String code=set.optString("code","");
            String name=set.optString("name","Unknown set");
            int owned=set.optInt("owned",0),total=set.optInt("total",0);
            addSetProgressCard(set,source,request);
            if(refreshing.contains(code.toLowerCase(java.util.Locale.ROOT))){
                TextView progress=VaultNativeStyle.label(activity,
                    "Updating "+name+" progress… · previous value shown",
                    11,VaultNativeStyle.GOLD,false);
                VaultNativeStyle.margin(content,progress,5,activity);
            }
        }
        if(last<filtered.size())
            button("Next sets ("+last+" / "+filtered.size()+")",
                ()->setProgress(filter,pageNumber+1));
        if(start>0)button("Previous sets",()->setProgress(filter,pageNumber-1));
        status.setText(filtered.size()+" shown / "+allSets+" sets · "+
            NativeSetProgressFilters.title(order)+
            (cached?" · saved snapshot":" · snapshot ready"));
    }

    void openSetFromCatalog(String code,String title,String source){
        if(closed)return;
        if("arena".equals(source)||"physical".equals(source))setSource=source;
        showSet(code,title,0);
    }

    private void showSet(String set,String title,int pageNumber){
        screenState="set-details";
        if(setOverviewFilters!=null)setOverviewFilters.setVisibility(View.GONE);
        if(setOverviewSearch!=null)setOverviewSearch.setVisibility(View.GONE);
        workspaceScroll.scrollTo(0,0);
        final int request=++setRequestId;
        final String source=setSource;
        final boolean hideUnowned=hideUnownedSetSlots;
        status.setText("Reading "+title+(source.equals("physical")?" printings":" Arena cards")+"…");
        // Always provide an escape route even if indexing or a particular
        // set's ownership query takes time. Never leave a blank detail pane.
        content.removeAllViews();
        button("← Back to sets",()->setProgress(setQuery,0));
        if("physical".equals(source)){
            row("OWNED SET VALUE",VaultSetValues.detail(activity,set),null);
            content.addView(VaultValueHistory.chart(activity,set),
                new LinearLayout.LayoutParams(-1,dp(164)));
        }
        row("Loading "+title+"…","Reading the local set index. Your collection is unchanged.",null);
        setDetailsWorker.execute(()->{
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
                    if("physical".equals(source)){
                        row("OWNED SET VALUE",VaultSetValues.detail(activity,set),null);
                        content.addView(VaultValueHistory.chart(activity,set),
                            new LinearLayout.LayoutParams(-1,dp(164)));
                    }
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
                            "Set completion counts distinct paper collector-number slots. "+
                            "Exact owned printing copies remain separately verified by "+
                            "Scryfall ID, never inferred from card name.":
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
                            ()->((MainActivity)activity).inspectSetPrinting(
                                p,title,set,source,qty));
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
        final boolean draft;
        boolean loaded;
        DeckCoverTarget(ImageView image,String commander){
            this(image,commander,false);
        }
        DeckCoverTarget(ImageView image,String commander,boolean draft){
            this.image=image;this.commander=commander;this.draft=draft;
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
        if(AiReviewMetadata.hasReview(deck)){
            boolean outdated=AiReviewMetadata.stale(deck);
            TextView reviewStatus=VaultNativeStyle.label(activity,
                outdated?"AI review · Outdated":"AI review · Saved",
                11,outdated?VaultNativeStyle.GOLD:VaultNativeStyle.SUBTEXT,false);
            reviewStatus.setPadding(0,dp(5),0,0);
            details.addView(reviewStatus);
        }
        entry.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        entry.setOnClickListener(v->showDeck(deck));
        VaultNativeStyle.margin(deckListPanel,entry,9,activity);
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
            if((target.draft?
                    android.os.SystemClock.uptimeMillis()<
                        draftArtRetryAfter.getOrDefault(target.commander,0L):
                    coverUnavailable.contains(target.commander)) ||
                    coverInFlight.contains(target.commander))continue;
            // Keep in-flight work equal to the two actual cover workers.
            // Otherwise an earlier visible group can monopolize a long queue
            // while later card-type sections are being scrolled into view.
            if(coverInFlight.size()>=2)break;
            String commander=target.commander;
            coverInFlight.add(commander);
            coverWorkers.execute(()->{
                Bitmap decoded=null;
                try {
                    // Saved deck entries can include Arena printing annotations
                    // such as "Esper Sentinel (MH2) 12". Look up the actual
                    // card identity without modifying the saved entry.
                    String cardIdentity=NativeArenaCardName.identity(commander);
                    String url=catalog.commanderCoverUrl(cardIdentity);
                    if(url.isEmpty()){
                        // Draft imports may use a front-face-only name for a
                        // double-faced card, while the local catalog stores
                        // the exact "Front // Back" identity.
                        try{
                            JSONObject exact=catalog.cardDetailsForReviewMention(cardIdentity);
                            JSONObject meta=exact.optJSONObject("meta");
                            if(meta!=null)url=meta.optString("image","");
                        }catch(Exception ignored){
                            // Keep the readable placeholder if metadata is absent.
                        }
                    }
                    if(!url.isEmpty()){
                        decoded=NativeArtworkQuality.load(artwork,url,
                            dp(98),dp(132),false);
                    }
                } catch(Exception ignored) {
                    // Offline or unknown commander: keep the deck-box fallback.
                }
                final Bitmap result=decoded;
                ui.post(()->{
                    coverInFlight.remove(commander);
                    if(result!=null){
                        coverBitmaps.put(commander,result);
                        coverUnavailable.remove(commander);
                        draftArtFailures.remove(commander);
                        draftArtRetryAfter.remove(commander);
                        // Apply the completed image immediately to live targets.
                        // A nested editor can be rebuilt without a scroll event,
                        // so don't depend on another visibility scan to repaint.
                        if(!closed&&pageGeneration==deckPageGeneration)
                            for(DeckCoverTarget candidate:deckCoverTargets)
                                if(commander.equals(candidate.commander)&&
                                        candidate.image.isAttachedToWindow()){
                                    candidate.image.setImageBitmap(result);
                                    candidate.loaded=true;
                                }
                    }else{
                        boolean draftTarget=false;
                        for(DeckCoverTarget candidate:deckCoverTargets)
                            if(candidate.draft&&commander.equals(candidate.commander)){
                                draftTarget=true;break;
                            }
                        if(draftTarget){
                            int failures=draftArtFailures.getOrDefault(commander,0)+1;
                            draftArtFailures.put(commander,failures);
                            // Back off transient offline, CDN rate limiting or
                            // indexing failures; never permanently blacklist a
                            // draft card while the editor remains open.
                            long delayMs=Math.min(60000L,6000L*failures);
                            draftArtRetryAfter.put(commander,
                                android.os.SystemClock.uptimeMillis()+delayMs);
                        }else coverUnavailable.add(commander);
                    }
                    if(!closed&&pageGeneration==deckPageGeneration)
                        loadVisibleDeckCovers();
                });
            });
        }
    }

    private void renderDeckList(){
        if(deckListPanel==null)return;
        deckListPanel.removeAllViews();
        deckCoverTargets.clear();
        ArrayList<JSONObject> matches=new ArrayList<>();
        String term=deckSearch.trim().toLowerCase(java.util.Locale.ROOT);
        for(int i=0;i<deckSnapshot.length()&&i<5000;i++){
            JSONObject deck=deckSnapshot.optJSONObject(i);if(deck==null)continue;
            String commander=deck.optString("commander","").trim();
            if(!deckCommanderFilter.isEmpty() &&
                    !deckCommanderFilter.equalsIgnoreCase(commander))continue;
            if(deckFavoritesOnly&&!deck.optBoolean("favorite",false))continue;
            String format=deck.optString("format","").trim();
            if(format.isEmpty())format="Unspecified";
            if(!deckFormat.isEmpty()&&!deckFormat.equalsIgnoreCase(format))continue;
            String folder=deck.optString("folder","Unfiled");
            if(folder.isEmpty())folder="Unfiled";
            if(!deckFolder.isEmpty()&&!deckFolder.equalsIgnoreCase(folder))continue;
            String haystack=(deck.optString("name","")+" "+commander+" "+folder+
                " "+deck.optString("format","")+" "+deck.optJSONArray("tags")).toLowerCase(java.util.Locale.ROOT);
            if(!term.isEmpty()&&!haystack.contains(term))continue;
            matches.add(deck);
        }
        Comparator<JSONObject> order=deckSort.equals("name")?
            Comparator.comparing(x->x.optString("name","").toLowerCase(java.util.Locale.ROOT)):
            deckSort.equals("commander")?
            Comparator.comparing(x->x.optString("commander","").toLowerCase(java.util.Locale.ROOT)):
            (a,b)->b.optString("updated","").compareTo(a.optString("updated",""));
        matches.sort(order.thenComparing(x->x.optString("id","")));
        status.setText(matches.size()+" matching decks · "+
            deckSnapshot.length()+" saved total");
        int limit=Math.min(matches.size(),300);
        for(int i=0;i<limit;i++){
            JSONObject deck=matches.get(i);
            JSONArray entries=deck.optJSONArray("entries");
            int total=deck.optString("commander","").trim().isEmpty()?0:1;
            if(entries!=null)for(int n=0;n<entries.length();n++){
                JSONObject card=entries.optJSONObject(n);
                if(card!=null)total+=Math.max(0,card.optInt("qty",0));
            }
            deckCoverRow(deck,total,deckPageGeneration);
        }
        if(matches.isEmpty())
            rowTo(deckListPanel,"No matching decks",
                deckCommanderFilter.isEmpty()?
                    "Try another search, format, folder or favorite filter.":
                    "No deck was created automatically. Clear the commander filter to see other decks.");
        else if(matches.size()>limit)
            rowTo(deckListPanel,"Showing first "+limit+" decks",
                "Narrow your search to find the others.");
        workspaceScroll.post(this::loadVisibleDeckCovers);
    }

    private void rowTo(LinearLayout parent,String title,String detail){
        TextView text=VaultNativeStyle.label(activity,title+"\n"+detail,13,
            VaultNativeStyle.SUBTEXT,false);
        VaultNativeStyle.margin(parent,text,9,activity);
    }

    private void deckLibrary(){
        screenState="deck-library";
        editingDeck=null;editorName=null;editorMainboard=null;
        final int pageGeneration=++deckPageGeneration;
        deckCoverTargets.clear();
        ui.removeCallbacks(deckSearchPending);
        status.setText("Reading saved deck library from the existing app…");
        decks.fetch(json->ui.post(()->{
            if(closed||activity.isFinishing()||pageGeneration!=deckPageGeneration)return;
            content.removeAllViews();
            try{
                deckSnapshot=new JSONArray(json);
                if(deckSnapshot.length()>5000)
                    throw new IllegalArgumentException("Deck library exceeds display safety limit");
                if(!deckCommanderFilter.isEmpty())
                    button("Show all saved decks",()->{
                        deckCommanderFilter="";deckLibrary();
                    });

                EditText search=new EditText(activity);
                VaultNativeStyle.search(activity,search);
                search.setSingleLine(true);
                search.setHint("Search deck, commander, format, or tags");
                search.setText(deckSearch);
                LinearLayout deckSearchRow=VaultNativeStyle.row(activity);
                deckSearchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
                Button clearDeckSearch=VaultNativeStyle.button(activity,"×",false);
                clearDeckSearch.setContentDescription("Clear deck library search");
                clearDeckSearch.setVisibility(deckSearch.isEmpty()?View.GONE:View.VISIBLE);
                LinearLayout.LayoutParams clearDeckParams=new LinearLayout.LayoutParams(dp(42),dp(48));
                clearDeckParams.leftMargin=dp(5);
                deckSearchRow.addView(clearDeckSearch,clearDeckParams);
                clearDeckSearch.setOnClickListener(v->search.setText(""));
                VaultNativeStyle.margin(content,deckSearchRow,7,activity);

                LinearLayout filters=VaultNativeStyle.row(activity);
                Spinner sort=new Spinner(activity);
                String[] names={"Recently updated","Deck name A-Z","Commander A-Z"};
                String[] values={"updated","name","commander"};
                ArrayAdapter<String> sortAdapter=new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item,names);
                sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sort.setAdapter(sortAdapter);
                sort.setSelection(Math.max(0,java.util.Arrays.asList(values).indexOf(deckSort)));
                filters.addView(sort,new LinearLayout.LayoutParams(0,dp(49),1));
                Spinner folders=new Spinner(activity);
                ArrayList<String> choices=new ArrayList<>();
                choices.add("All folders");
                for(int i=0;i<deckSnapshot.length();i++){
                    JSONObject item=deckSnapshot.optJSONObject(i);
                    if(item==null)continue;
                    String folder=item.optString("folder","Unfiled");
                    if(folder.isEmpty())folder="Unfiled";
                    if(!choices.contains(folder))choices.add(folder);
                }
                java.util.Collections.sort(choices.subList(1,choices.size()),
                    String.CASE_INSENSITIVE_ORDER);
                ArrayAdapter<String> folderAdapter=new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item,choices);
                folderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                folders.setAdapter(folderAdapter);
                int selectedFolder=deckFolder.isEmpty()?0:choices.indexOf(deckFolder);
                folders.setSelection(Math.max(0,selectedFolder));
                filters.addView(folders,new LinearLayout.LayoutParams(0,dp(49),1));
                VaultNativeStyle.margin(content,filters,5,activity);
                ArrayList<String> formats=new ArrayList<>();
                formats.add("All formats");
                for(int i=0;i<deckSnapshot.length();i++){
                    JSONObject item=deckSnapshot.optJSONObject(i);
                    if(item==null)continue;
                    String format=item.optString("format","").trim();
                    if(format.isEmpty())format="Unspecified";
                    if(!formats.contains(format))formats.add(format);
                }
                java.util.Collections.sort(formats.subList(1,formats.size()),
                    String.CASE_INSENSITIVE_ORDER);
                Spinner formatPicker=new Spinner(activity);
                ArrayAdapter<String> formatAdapter=new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item,formats);
                formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                formatPicker.setAdapter(formatAdapter);
                int selectedFormat=deckFormat.isEmpty()?0:formats.indexOf(deckFormat);
                formatPicker.setSelection(Math.max(0,selectedFormat));
                VaultNativeStyle.margin(content,formatPicker,5,activity);
                CheckBox favoritesOnly=new CheckBox(activity);
                favoritesOnly.setText("Favorite decks only");
                favoritesOnly.setTextColor(VaultNativeStyle.TEXT);
                favoritesOnly.setButtonTintList(android.content.res.ColorStateList.valueOf(VaultNativeStyle.PRIMARY));
                favoritesOnly.setChecked(deckFavoritesOnly);
                VaultNativeStyle.margin(content,favoritesOnly,4,activity);
                deckListPanel=VaultNativeStyle.column(activity);
                content.addView(deckListPanel);

                search.addTextChangedListener(new TextWatcher(){
                    public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                    public void onTextChanged(CharSequence x,int a,int b,int c){
                        deckSearch=x.toString();
                        clearDeckSearch.setVisibility(deckSearch.isEmpty()?View.GONE:View.VISIBLE);
                        ui.removeCallbacks(deckSearchPending);
                        ui.postDelayed(deckSearchPending,180);
                    }
                    public void afterTextChanged(Editable x){}
                });
                sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                    public void onNothingSelected(AdapterView<?> p){}
                    public void onItemSelected(AdapterView<?> p,View v,int idx,long id){
                        deckSort=values[idx];renderDeckList();
                    }
                });
                folders.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                    public void onNothingSelected(AdapterView<?> p){}
                    public void onItemSelected(AdapterView<?> p,View v,int idx,long id){
                        deckFolder=idx==0?"":choices.get(idx);renderDeckList();
                    }
                });
                favoritesOnly.setOnCheckedChangeListener((b,on)->{
                    deckFavoritesOnly=on;renderDeckList();
                });
                formatPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                    public void onNothingSelected(AdapterView<?> p){}
                    public void onItemSelected(AdapterView<?> p,View v,int idx,long id){
                        deckFormat=idx==0?"":formats.get(idx);renderDeckList();
                    }
                });
                renderDeckList();
                if(deckLibraryRestoreScroll>=0){
                    final int restored=deckLibraryRestoreScroll;
                    deckLibraryRestoreScroll=-1;
                    workspaceScroll.post(()->{
                        if(!closed&&"deck-library".equals(screenState))
                            workspaceScroll.scrollTo(0,restored);
                    });
                }
                button("Find decks closest to buildable",()->{
                    final int current=deckPageGeneration;
                    status.setText("Checking saved decks against recorded ownership…");
                    executor.execute(()->{
                        try{
                            JSONArray physical=new JSONArray(prefs.getString(
                                "vault_physicalCollectionV1","[]"));
                            JSONArray arena=new JSONArray(prefs.getString("vault_cards","[]"));
                            List<String> ranked=NativeDeckFit.rank(
                                deckSnapshot,physical,arena);
                            ui.post(()->{
                                if(closed||current!=deckPageGeneration)return;
                                status.setText("Deck coverage review ready");
                                new AlertDialog.Builder(activity)
                                    .setTitle("Closest to buildable")
                                    .setMessage(ranked.isEmpty()?
                                        "No readable saved decks found.":
                                        String.join("\n\n",ranked)+
                                        "\n\nCandidates are ranked by missing owned copies, "+
                                        "not by power or format legality.")
                                    .setPositiveButton("Close",null).show();
                            });
                        }catch(Exception error){
                            ui.post(()->{
                                if(!closed&&current==deckPageGeneration)
                                    status.setText("Deck review unavailable: "+
                                        error.getMessage());
                            });
                        }
                    });
                });
                button("Open legacy deck editing tools",legacy);
            }catch(Exception e){
                status.setText("Could not load deck library: "+e.getMessage());
                button("Open Legacy deck library",legacy);
            }
        }));
    }
    /** Keep the exact editor widgets alive while reviewing; no saves or mutation. */
    private void openAiReview(JSONObject deck,String unsavedBoard){
        if(aiClient==null){info("Open Options to configure ChatGPT first.");return;}
        if("ai-review".equals(screenState))return;
        aiReturnViews.clear();
        for(int i=0;i<content.getChildCount();i++)
            aiReturnViews.add(content.getChildAt(i));
        aiReturnState=screenState;
        aiReturnStatus=status.getText().toString();
        aiReturnScroll=workspaceScroll.getScrollY();
        content.removeAllViews();
        screenState="ai-review";
        status.setText("AI Review · no automatic deck changes");
        MobileAiReviewScreen review=new MobileAiReviewScreen(
            activity,aiClient,prefs,deck,unsavedBoard,this::returnFromAiReview,
            catalog,this::openAiCard,deckSaver);
        content.addView(review.view());
        workspaceScroll.post(()->workspaceScroll.scrollTo(0,0));
    }

    private void openAiCard(String name){
        if(closed||!"ai-review".equals(screenState)||aiCardInspector!=null)return;
        if(content.getChildCount()==0)return;
        aiReviewRetainedView=content.getChildAt(0);
        aiReviewRetainedScroll=workspaceScroll.getScrollY();
        content.removeAllViews();
        aiCardInspector=new NativeCatalogScreen(activity,catalog,artwork,prefs);
        aiCardInspector.useEmbeddedInspectorLayout();
        content.addView(aiCardInspector.view(),new LinearLayout.LayoutParams(
            -1,LinearLayout.LayoutParams.WRAP_CONTENT));
        aiCardInspector.inspectAiCard(name,this::returnFromAiCard);
        workspaceScroll.post(()->workspaceScroll.scrollTo(0,0));
    }

    private void returnFromAiCard(){
        if(aiCardInspector==null)return;
        aiCardInspector.close();
        aiCardInspector=null;
        content.removeAllViews();
        if(aiReviewRetainedView!=null)content.addView(aiReviewRetainedView);
        aiReviewRetainedView=null;
        workspaceScroll.post(()->workspaceScroll.scrollTo(0,aiReviewRetainedScroll));
    }

    private void returnFromAiReview(){
        if(aiCardInspector!=null){returnFromAiCard();return;}
        if(!"ai-review".equals(screenState))return;
        content.removeAllViews();
        for(View item:aiReturnViews)content.addView(item);
        aiReturnViews.clear();
        screenState=aiReturnState;
        status.setText(aiReturnStatus);
        int scroll=aiReturnScroll;
        workspaceScroll.post(()->workspaceScroll.scrollTo(0,scroll));
    }

    private void editDeck(JSONObject deck){
        screenState="deck-edit";editingDeck=deck;
        final int pageGeneration=++deckPageGeneration;
        deckCoverTargets.clear();
        // Stay in the same Decks workspace: the content area, rather than
        // another modal, owns the name, mainboard, search and save controls.
        content.removeAllViews();
        status.setText("Editing "+deck.optString("name","saved deck")+" · unsaved changes");
        button("← Back to deck",this::onBack);
        LinearLayout fields=VaultNativeStyle.column(activity);
        fields.setPadding(dp(16),dp(8),dp(16),dp(8));
        TextView commander=VaultNativeStyle.label(activity,
            "Commander: "+deck.optString("commander","")+" (unchanged)",13,
            VaultNativeStyle.SUBTEXT,false);
        fields.addView(commander);
        Button aiReview=VaultNativeStyle.button(activity,"✦ AI Review (current draft)",false);
        fields.addView(aiReview,new LinearLayout.LayoutParams(-1,dp(47)));
        aiReview.setOnClickListener(v->openAiReview(deck,
            editorMainboard==null?null:editorMainboard.getText().toString()));
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
        editorName=name;editorMainboard=lines;
        editorOriginalName=name.getText().toString();
        editorOriginalMainboard=lines.getText().toString();
        fields.addView(lines,new LinearLayout.LayoutParams(-1,dp(260)));

        // Optional, read-only insights always analyze the current unsaved
        // editor text. Never save or mutate inventory to build these charts.
        Button draftInsightsToggle=VaultNativeStyle.button(activity,
            "Deck Insights  ▾",false);
        fields.addView(draftInsightsToggle,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout draftInsightsPanel=VaultNativeStyle.column(activity);
        draftInsightsPanel.setVisibility(View.GONE);
        fields.addView(draftInsightsPanel);
        final boolean[] draftInsightsOpen={false};
        final int[] draftInsightRequests={0};
        // One automatic attempt per editor; Refresh Insights can retry after
        // connectivity returns. Only the local catalog's missing mana strings
        // are updated, never draft text or ownership.
        final boolean[] attemptedManaRepair={false};
        final Runnable updateDraftInsights=()->{
            if(closed||pageGeneration!=deckPageGeneration||
                    !draftInsightsOpen[0])return;
            final String snapshot=lines.getText().toString();
            final String draftCommander=deck.optString("commander","");
            final int request=++draftInsightRequests[0];
            draftInsightsPanel.removeAllViews();
            draftInsightsPanel.addView(VaultNativeStyle.label(activity,
                "Analyzing unsaved draft using the local card catalog…",12,
                VaultNativeStyle.SUBTEXT,false));
            executor.execute(()->{
                JSONObject result=null;
                String error=null;
                String repairNotice="";
                try{
                    JSONArray parsed=NativeDeckDraft.parseEntries(snapshot);
                    result=catalog.deckInsights(draftCommander,parsed);
                    // Cards indexed before the catalog's mana column was added
                    // have perfectly valid type/MV data, but blank mana strings.
                    // Recover only exact-name cards in this draft, in one small
                    // Scryfall batch, rather than redownloading the entire catalog.
                    if(result.optInt("manaCostCopies",0)==0 &&
                            result.optInt("missingCostCopies",0)>0 &&
                            !attemptedManaRepair[0]){
                        attemptedManaRepair[0]=true;
                        try{
                            int filled=catalog.fillMissingDeckManaCosts(
                                draftCommander,parsed);
                            result=catalog.deckInsights(draftCommander,parsed);
                            if(filled==0)repairNotice=
                                "Mana metadata still unavailable; use Refresh Insights "+
                                "after checking connectivity or catalog sync.";
                        }catch(Exception recovery){
                            repairNotice="Mana-cost recovery unavailable: "+
                                recovery.getMessage()+
                                ". Refresh Insights to retry.";
                        }
                    }
                }catch(Exception ex){error=ex.getMessage();}
                final JSONObject report=result;
                final String failure=error;
                final String notice=repairNotice;
                ui.post(()->{
                    if(closed||pageGeneration!=deckPageGeneration||
                            request!=draftInsightRequests[0]||
                            !draftInsightsOpen[0])return;
                    draftInsightsPanel.removeAllViews();
                    if(!snapshot.equals(lines.getText().toString())){
                        draftInsightsPanel.addView(VaultNativeStyle.label(activity,
                            "Draft changed · select Refresh Insights for current counts.",
                            12,VaultNativeStyle.GOLD,false));
                        return;
                    }
                    if(report==null)
                        draftInsightsPanel.addView(VaultNativeStyle.label(activity,
                            "Analysis unavailable: "+failure,12,
                            VaultNativeStyle.GOLD,false));
                    else{
                        renderDeckInsights(report,draftInsightsPanel);
                        if(!notice.isEmpty())
                            draftInsightsPanel.addView(VaultNativeStyle.label(activity,
                                notice,12,VaultNativeStyle.GOLD,false));
                    }
                });
            });
        };
        // Refresh stays outside the results container so clearing/repainting
        // charts never removes the action.
        Button insightRefresh=VaultNativeStyle.button(activity,
            "Refresh Insights",false);
        fields.addView(insightRefresh,new LinearLayout.LayoutParams(-1,dp(42)));
        insightRefresh.setVisibility(View.GONE);
        draftInsightsToggle.setOnClickListener(v->{
            draftInsightsOpen[0]=!draftInsightsOpen[0];
            draftInsightsToggle.setText(draftInsightsOpen[0]?
                "Deck Insights  ▴":"Deck Insights  ▾");
            draftInsightsPanel.setVisibility(draftInsightsOpen[0]?
                View.VISIBLE:View.GONE);
            insightRefresh.setVisibility(draftInsightsOpen[0]?
                View.VISIBLE:View.GONE);
            if(draftInsightsOpen[0])updateDraftInsights.run();
            else ++draftInsightRequests[0];
        });
        insightRefresh.setOnClickListener(v->{
            attemptedManaRepair[0]=false;
            updateDraftInsights.run();
        });

        // Optional visual editing over the SAME draft text and validated parser.
        // No separate card inventory, deck format or unsaved state is created.
        Button visualToggle=VaultNativeStyle.button(activity,
            "Visual mainboard  ▾",false);
        fields.addView(visualToggle,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout visualCards=VaultNativeStyle.column(activity);
        visualCards.setVisibility(View.GONE);
        fields.addView(visualCards);
        final boolean[] visualOpen={false};
        draftArtFailures.clear();
        draftArtRetryAfter.clear();
        // Presentation preference only. Never changes deck contents or draft text.
        final boolean[] visualArt={prefs.getBoolean("native_draft_art_rows_v1",false)};
        final boolean[] visualGrid={prefs.getBoolean("native_draft_grid_v1",false)};
        final String[] visualSnapshot={""};
        final Map<String,String> visualTypeCache=new HashMap<>();
        // UI-only state survives list/grid switches and unsaved quantity edits.
        final Set<String> collapsedDraftGroups=new HashSet<>();
        final Runnable redrawVisual=()->{
            if(closed||!visualOpen[0]||
                    pageGeneration!=deckPageGeneration)return;
            visualCards.removeAllViews();
            deckCoverTargets.clear();
            try{
                JSONArray parsed=NativeDeckDraft.parseEntries(lines.getText().toString());
                visualSnapshot[0]=lines.getText().toString();
                int copies=0;
                for(int i=0;i<parsed.length();i++)
                    copies+=parsed.optJSONObject(i).optInt("qty",0);
                TextView summary=VaultNativeStyle.label(activity,
                    copies+" mainboard cards · "+parsed.length()+
                    " names · unsaved draft",12,VaultNativeStyle.SUBTEXT,false);
                visualCards.addView(summary);
                LinearLayout viewModes=VaultNativeStyle.row(activity);
                Button listMode=VaultNativeStyle.button(activity,"List",!visualArt[0]&&!visualGrid[0]);
                Button artMode=VaultNativeStyle.button(activity,"Art + Details",visualArt[0]&&!visualGrid[0]);
                Button gridMode=VaultNativeStyle.button(activity,"Grid",visualGrid[0]);
                listMode.setAllCaps(false);
                artMode.setAllCaps(false);
                gridMode.setAllCaps(false);
                viewModes.addView(listMode,new LinearLayout.LayoutParams(0,dp(44),1));
                LinearLayout.LayoutParams artModeParams=new LinearLayout.LayoutParams(0,dp(44),1);
                artModeParams.leftMargin=dp(5);
                viewModes.addView(artMode,artModeParams);
                LinearLayout.LayoutParams gridModeParams=new LinearLayout.LayoutParams(0,dp(44),1);
                gridModeParams.leftMargin=dp(5);
                viewModes.addView(gridMode,gridModeParams);
                visualCards.addView(viewModes);
                if(visualGrid[0]||visualArt[0]){
                    Button retryImages=VaultNativeStyle.button(activity,
                        "Retry missing artwork",false);
                    retryImages.setAllCaps(false);
                    retryImages.setContentDescription(
                        "Retry card images that have not loaded in this deck");
                    visualCards.addView(retryImages,new LinearLayout.LayoutParams(-1,dp(42)));
                    retryImages.setOnClickListener(v->{
                        draftArtFailures.clear();
                        draftArtRetryAfter.clear();
                        coverUnavailable.clear();
                        workspaceScroll.post(this::loadVisibleDeckCovers);
                    });
                }
                listMode.setOnClickListener(v->{
                    if(!visualArt[0]&&!visualGrid[0])return;
                    visualArt[0]=false;visualGrid[0]=false;
                    prefs.edit().putBoolean("native_draft_art_rows_v1",false)
                        .putBoolean("native_draft_grid_v1",false).apply();
                    visualToggle.performClick();
                    visualToggle.performClick();
                });
                artMode.setOnClickListener(v->{
                    if(visualArt[0]&&!visualGrid[0])return;
                    visualArt[0]=true;visualGrid[0]=false;
                    prefs.edit().putBoolean("native_draft_art_rows_v1",true)
                        .putBoolean("native_draft_grid_v1",false).apply();
                    visualToggle.performClick();
                    visualToggle.performClick();
                });
                gridMode.setOnClickListener(v->{
                    if(visualGrid[0])return;
                    visualGrid[0]=true;visualArt[0]=false;
                    prefs.edit().putBoolean("native_draft_grid_v1",true)
                        .putBoolean("native_draft_art_rows_v1",false).apply();
                    visualToggle.performClick();
                    visualToggle.performClick();
                });
                if(parsed.length()==0)
                    visualCards.addView(VaultNativeStyle.label(activity,
                        "Add a card to see it here.",13,VaultNativeStyle.SUBTEXT,false));
                boolean missingTypes=false;
                for(int i=0;i<parsed.length();i++){
                    JSONObject item=parsed.optJSONObject(i);
                    if(item==null)continue;
                    String key=NativeArenaCardName.identity(
                        item.optString("name","")).toLowerCase(java.util.Locale.ROOT);
                    if(!visualTypeCache.containsKey(key)){missingTypes=true;break;}
                }
                if(missingTypes){
                    visualCards.addView(VaultNativeStyle.label(activity,
                        "Grouping cards using the local Scryfall catalog…",12,
                        VaultNativeStyle.SUBTEXT,false));
                    final String requestedDraft=visualSnapshot[0];
                    executor.execute(()->{
                        Map<String,String> types;
                        try{types=catalog.deckTypeLines(parsed);}
                        catch(Exception ignored){
                            // Missing or syncing metadata must not block draft editing.
                            types=new HashMap<>();
                            for(int n=0;n<parsed.length();n++){
                                JSONObject card=parsed.optJSONObject(n);
                                if(card==null)continue;
                                String key=NativeArenaCardName.identity(
                                    card.optString("name","")).toLowerCase(java.util.Locale.ROOT);
                                types.put(key,"");
                            }
                        }
                        final Map<String,String> resolvedTypes=types;
                        ui.post(()->{
                            if(closed||pageGeneration!=deckPageGeneration||
                                    !visualOpen[0])return;
                            visualTypeCache.putAll(resolvedTypes);
                            if(!requestedDraft.equals(lines.getText().toString())){
                                // Text changed during lookup. Keep the newer draft.
                                visualSnapshot[0]="";
                            }
                            visualToggle.performClick();
                            visualToggle.performClick();
                        });
                    });
                    return;
                }
                Map<String,java.util.ArrayList<JSONObject>> grouped=new java.util.LinkedHashMap<>();
                for(String section:NativeDeckTypeGroups.ORDER)
                    grouped.put(section,new java.util.ArrayList<>());
                for(int i=0;i<parsed.length();i++){
                    JSONObject item=parsed.optJSONObject(i);
                    if(item==null)continue;
                    String key=NativeArenaCardName.identity(
                        item.optString("name","")).toLowerCase(java.util.Locale.ROOT);
                    String section=NativeDeckTypeGroups.classify(visualTypeCache.get(key));
                    grouped.get(section).add(item);
                }
                // Compact composition uses only the current unsaved draft.
                // Never count ownership or rewrite card entries to summarize.
                StringBuilder composition=new StringBuilder();
                int populatedGroups=0;
                for(String type:NativeDeckTypeGroups.ORDER){
                    java.util.ArrayList<JSONObject> members=grouped.get(type);
                    if(members==null||members.isEmpty())continue;
                    int sectionTotal=0;
                    for(JSONObject member:members)sectionTotal+=member.optInt("qty",0);
                    if(composition.length()>0)composition.append("   ·   ");
                    composition.append(type).append(" ").append(sectionTotal);
                    populatedGroups++;
                }
                TextView compositionHeader=VaultNativeStyle.label(activity,
                    "DECK COMPOSITION · "+populatedGroups+" card types",
                    11,VaultNativeStyle.SUBTEXT,true);
                compositionHeader.setPadding(0,dp(9),0,dp(3));
                visualCards.addView(compositionHeader);
                TextView compositionDetail=VaultNativeStyle.label(activity,
                    composition.length()==0?"No mainboard cards":composition.toString(),
                    12,VaultNativeStyle.TEXT,false);
                visualCards.addView(compositionDetail);

                // Navigation is only a presentation aid: tapping a type
                // reveals its cards and scrolls there without changing the draft.
                final Map<String,Button> groupButtons=new HashMap<>();
                final Map<String,LinearLayout> groupBodies=new HashMap<>();
                LinearLayout bulkSectionActions=VaultNativeStyle.row(activity);
                Button collapseAll=VaultNativeStyle.button(activity,"Collapse All",false);
                Button expandAll=VaultNativeStyle.button(activity,"Expand All",false);
                collapseAll.setAllCaps(false);
                expandAll.setAllCaps(false);
                bulkSectionActions.addView(collapseAll,
                    new LinearLayout.LayoutParams(0,dp(42),1));
                LinearLayout.LayoutParams expandParams=
                    new LinearLayout.LayoutParams(0,dp(42),1);
                expandParams.leftMargin=dp(7);
                bulkSectionActions.addView(expandAll,expandParams);
                visualCards.addView(bulkSectionActions);
                collapseAll.setOnClickListener(v->{
                    for(String type:groupButtons.keySet()){
                        Button heading=groupButtons.get(type);
                        if(heading!=null&&!collapsedDraftGroups.contains(type))
                            heading.performClick();
                    }
                });
                expandAll.setOnClickListener(v->{
                    for(String type:groupButtons.keySet()){
                        Button heading=groupButtons.get(type);
                        if(heading!=null&&collapsedDraftGroups.contains(type))
                            heading.performClick();
                    }
                    workspaceScroll.post(this::loadVisibleDeckCovers);
                });
                HorizontalScrollView jumpScroll=VaultNativeStyle.horizontalScroll(activity);
                jumpScroll.setHorizontalScrollBarEnabled(false);
                LinearLayout jumpRow=VaultNativeStyle.row(activity);
                jumpRow.setPadding(0,dp(7),0,dp(4));
                jumpScroll.addView(jumpRow);
                visualCards.addView(jumpScroll);
                for(String section:NativeDeckTypeGroups.ORDER){
                    java.util.ArrayList<JSONObject> sectionCards=grouped.get(section);
                    if(sectionCards==null||sectionCards.isEmpty())continue;
                    int sectionCopies=0;
                    for(JSONObject item:sectionCards)sectionCopies+=item.optInt("qty",0);
                    final String group=section;
                    final int groupCount=sectionCopies;
                    final String title=section.toUpperCase(java.util.Locale.ROOT)+
                        " · "+groupCount+" cards";
                    LinearLayout sectionBody=VaultNativeStyle.column(activity);
                    Button groupHeading=VaultNativeStyle.button(activity,
                        title+(collapsedDraftGroups.contains(group)?"  ▸":"  ▾"),false);
                    groupHeading.setAllCaps(false);
                    groupHeading.setContentDescription(group+", "+groupCount+
                        " cards. Tap to "+(collapsedDraftGroups.contains(group)?"expand":"collapse"));
                    visualCards.addView(groupHeading,new LinearLayout.LayoutParams(-1,dp(48)));
                    visualCards.addView(sectionBody);
                    sectionBody.setVisibility(collapsedDraftGroups.contains(group)?
                        View.GONE:View.VISIBLE);
                    groupButtons.put(group,groupHeading);
                    groupBodies.put(group,sectionBody);
                    groupHeading.setOnClickListener(v->{
                        boolean collapse=!collapsedDraftGroups.contains(group);
                        if(collapse)collapsedDraftGroups.add(group);
                        else collapsedDraftGroups.remove(group);
                        sectionBody.setVisibility(collapse?View.GONE:View.VISIBLE);
                        groupHeading.setText(title+(collapse?"  ▸":"  ▾"));
                        groupHeading.setContentDescription(group+", "+groupCount+
                            " cards. Tap to "+(collapse?"expand":"collapse"));
                        if(!collapse)workspaceScroll.post(this::loadVisibleDeckCovers);
                    });
                    Button jump=VaultNativeStyle.button(activity,section,false);
                    jump.setAllCaps(false);
                    LinearLayout.LayoutParams jumpParams=new LinearLayout.LayoutParams(
                        -2,dp(38));
                    jumpParams.rightMargin=dp(6);
                    jumpRow.addView(jump,jumpParams);
                    jump.setContentDescription("Jump to "+group);
                    jump.setOnClickListener(v->{
                        if(collapsedDraftGroups.contains(group))
                            groupHeading.performClick();
                        workspaceScroll.post(()->{
                            if(closed||pageGeneration!=deckPageGeneration||
                                    !visualOpen[0])return;
                            int[] headingPosition=new int[2];
                            int[] scrollPosition=new int[2];
                            groupHeading.getLocationOnScreen(headingPosition);
                            workspaceScroll.getLocationOnScreen(scrollPosition);
                            int destination=workspaceScroll.getScrollY()+
                                headingPosition[1]-scrollPosition[1]-dp(6);
                            workspaceScroll.smoothScrollTo(0,Math.max(0,destination));
                        });
                    });
                    // Two art-forward columns on phones, three on wider screens.
                    int screenDp=Math.round(activity.getResources().getDisplayMetrics()
                        .widthPixels/activity.getResources().getDisplayMetrics().density);
                    int columns=screenDp>=600?3:2;
                    int tileWidth=Math.max(dp(112),
                        (activity.getResources().getDisplayMetrics().widthPixels-
                            dp(104)-dp(10)*(columns-1))/columns);
                    GridLayout cardGrid=null;
                    if(visualGrid[0]){
                        cardGrid=new GridLayout(activity);
                        cardGrid.setColumnCount(columns);
                        sectionBody.addView(cardGrid);
                    }
                    int gridIndex=0;
                    for(JSONObject item:sectionCards){
                    String cardName=item.optString("name","");
                    int quantity=item.optInt("qty",0);
                    LinearLayout cardRow=visualGrid[0]?
                        VaultNativeStyle.column(activity):VaultNativeStyle.row(activity);
                    if(visualGrid[0]){
                        cardRow.setPadding(dp(5),dp(5),dp(5),dp(7));
                        VaultNativeStyle.surface(activity,cardRow,false);
                    }
                    if(visualGrid[0]||visualArt[0]){
                        // Load only visible card art via the existing bounded,
                        // cache-backed deck cover worker. No full deck prefetch.
                        FrameLayout artFrame=new FrameLayout(activity);
                        artFrame.setBackground(VaultNativeStyle.rounded(activity,
                            VaultNativeStyle.ELEVATED,VaultNativeStyle.BORDER,8));
                        TextView placeholder=VaultNativeStyle.label(activity,"✦",22,
                            VaultNativeStyle.PRIMARY,true);
                        placeholder.setGravity(Gravity.CENTER);
                        artFrame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
                        ImageView artImage=new ImageView(activity);
                        artImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        artImage.setContentDescription("Card artwork for "+cardName);
                        artFrame.addView(artImage,new FrameLayout.LayoutParams(-1,-1));
                        LinearLayout.LayoutParams artSize=visualGrid[0]?
                            new LinearLayout.LayoutParams(-1,Math.round((tileWidth-dp(10))*1.36f)):
                            new LinearLayout.LayoutParams(dp(70),dp(95));
                        if(!visualGrid[0])artSize.rightMargin=dp(8);
                        cardRow.addView(artFrame,artSize);
                        if(visualGrid[0]){
                            TextView badge=VaultNativeStyle.label(activity,
                                "×"+quantity,12,Color.WHITE,true);
                            badge.setPadding(dp(6),dp(3),dp(6),dp(3));
                            badge.setBackgroundColor(Color.argb(220,16,19,28));
                            FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(
                                -2,-2,Gravity.TOP|Gravity.RIGHT);
                            artFrame.addView(badge,badgeParams);
                        }
                        artFrame.setOnClickListener(v->openDeckCard(cardName));
                        deckCoverTargets.add(new DeckCoverTarget(artImage,cardName,true));
                        // The editor can be rebuilt without a scroll event. An
                        // attachment/layout callback starts the visible request.
                        artImage.addOnAttachStateChangeListener(
                            new View.OnAttachStateChangeListener(){
                                public void onViewAttachedToWindow(View view){
                                    view.post(()->{
                                        if(!closed&&pageGeneration==deckPageGeneration)
                                            loadVisibleDeckCovers();
                                    });
                                }
                                public void onViewDetachedFromWindow(View view){}
                            });
                    }
                    Button preview=VaultNativeStyle.button(activity,
                        quantity+" × "+cardName,false);
                    preview.setAllCaps(false);
                    preview.setContentDescription("Inspect "+cardName+
                        ", "+quantity+" copies in unsaved draft");
                    if(visualGrid[0]){
                        preview.setMaxLines(2);
                        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        cardRow.addView(preview,new LinearLayout.LayoutParams(-1,dp(57)));
                    }else cardRow.addView(preview,new LinearLayout.LayoutParams(
                        0,visualArt[0]?dp(95):dp(46),1));
                    preview.setOnClickListener(v->openDeckCard(cardName));
                    Button minus=VaultNativeStyle.button(activity,"−",false);
                    minus.setContentDescription("Remove one "+cardName+" from draft");
                    LinearLayout quantityRow=visualGrid[0]?
                        VaultNativeStyle.row(activity):cardRow;
                    quantityRow.addView(minus,new LinearLayout.LayoutParams(
                        visualGrid[0]?0:dp(43),dp(46),visualGrid[0]?1:0));
                    Button plus=VaultNativeStyle.button(activity,"+",false);
                    plus.setContentDescription("Add one "+cardName+" to draft");
                    if(visualGrid[0]){
                        TextView count=VaultNativeStyle.label(activity,
                            "×"+quantity,12,VaultNativeStyle.TEXT,true);
                        count.setGravity(Gravity.CENTER);
                        quantityRow.addView(count,new LinearLayout.LayoutParams(0,dp(46),1));
                    }
                    quantityRow.addView(plus,new LinearLayout.LayoutParams(
                        visualGrid[0]?0:dp(43),dp(46),visualGrid[0]?1:0));
                    if(visualGrid[0])cardRow.addView(quantityRow);
                    minus.setOnClickListener(v->{
                        try{
                            JSONArray updated=NativeDeckDraft.parseEntries(lines.getText().toString());
                            for(int n=0;n<updated.length();n++){
                                JSONObject entry=updated.optJSONObject(n);
                                if(entry==null||!cardName.equalsIgnoreCase(entry.optString("name","")))continue;
                                int remaining=entry.optInt("qty",0)-1;
                                if(remaining>0)entry.put("qty",remaining);
                                else updated.remove(n);
                                break;
                            }
                            lines.setText(NativeDeckDraft.formatEntries(updated));
                            visualSnapshot[0]="";
                            visualToggle.performClick();
                            visualToggle.performClick();
                        }catch(Exception error){info("Draft not changed: "+error.getMessage());}
                    });
                    plus.setOnClickListener(v->{
                        try{
                            JSONArray updated=NativeDeckDraft.parseEntries(lines.getText().toString());
                            for(int n=0;n<updated.length();n++){
                                JSONObject entry=updated.optJSONObject(n);
                                if(entry==null||!cardName.equalsIgnoreCase(entry.optString("name","")))continue;
                                int qty=entry.optInt("qty",0);
                                if(qty>=999){info("Maximum 999 copies per entry.");return;}
                                entry.put("qty",qty+1);
                                break;
                            }
                            lines.setText(NativeDeckDraft.formatEntries(updated));
                            visualSnapshot[0]="";
                            visualToggle.performClick();
                            visualToggle.performClick();
                        }catch(Exception error){info("Draft not changed: "+error.getMessage());}
                    });
                    if(visualGrid[0]){
                        GridLayout.LayoutParams cell=new GridLayout.LayoutParams();
                        cell.width=tileWidth;
                        cell.height=-2;
                        cell.rowSpec=GridLayout.spec(gridIndex/columns);
                        cell.columnSpec=GridLayout.spec(gridIndex%columns);
                        cell.setMargins(dp(2),dp(3),dp(2),dp(4));
                        cardGrid.addView(cardRow,cell);
                        gridIndex++;
                    }else VaultNativeStyle.margin(sectionBody,cardRow,3,activity);
                    }
                }
                if(visualArt[0]||visualGrid[0])
                    workspaceScroll.post(this::loadVisibleDeckCovers);
            }catch(Exception error){
                visualCards.addView(VaultNativeStyle.label(activity,
                    "Fix draft text before viewing cards: "+error.getMessage(),
                    12,VaultNativeStyle.GOLD,false));
            }
        };
        visualToggle.setOnClickListener(v->{
            visualOpen[0]=!visualOpen[0];
            visualToggle.setText(visualOpen[0]?"Visual mainboard  ▴":"Visual mainboard  ▾");
            visualCards.setVisibility(visualOpen[0]?View.VISIBLE:View.GONE);
            if(visualOpen[0])redrawVisual.run();
        });
        lines.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){
                if(visualOpen[0]&&!text.toString().equals(visualSnapshot[0])){
                    visualCards.removeAllViews();
                    Button refresh=VaultNativeStyle.button(activity,
                        "Refresh visual mainboard",false);
                    visualCards.addView(refresh);
                    refresh.setOnClickListener(v->redrawVisual.run());
                }
            }
            public void afterTextChanged(Editable text){}
        });
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
        Button importList=VaultNativeStyle.button(activity,
            "Paste / Import Arena decklist",false);
        fields.addView(importList,new LinearLayout.LayoutParams(-1,dp(48)));
        importList.setOnClickListener(v->{
            EditText pasted=new EditText(activity);
            pasted.setTextColor(VaultNativeStyle.TEXT);
            pasted.setHint("Paste an Arena list with Deck / Commander headings, or numbered card names");
            pasted.setHintTextColor(VaultNativeStyle.SUBTEXT);
            pasted.setMinLines(7);
            pasted.setMaxLines(13);
            pasted.setGravity(Gravity.TOP);
            pasted.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(
                Context.CLIPBOARD_SERVICE);
            if(clipboard!=null&&clipboard.hasPrimaryClip() &&
                clipboard.getPrimaryClip()!=null &&
                clipboard.getPrimaryClip().getItemCount()>0){
                CharSequence text=clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity);
                if(text!=null&&text.length()<=64000)pasted.setText(text);
            }
            LinearLayout wrapper=VaultNativeStyle.column(activity);
            wrapper.setPadding(dp(14),dp(8),dp(14),dp(8));
            wrapper.addView(pasted);
            new AlertDialog.Builder(activity).setTitle("Import to unsaved mainboard")
                .setView(wrapper).setNegativeButton("Cancel",null)
                .setPositiveButton("Review", (dialog,which)->{
                    try{
                        NativeDeckTextExchange.Parsed parsed=NativeDeckTextExchange.parse(
                            pasted.getText().toString(),deck.optString("commander",""));
                        new AlertDialog.Builder(activity).setTitle("Replace draft mainboard?")
                            .setMessage(parsed.copies+" imported mainboard copies. "+
                                "Your saved deck remains unchanged until Review & Save. "+
                                (parsed.removedPrintingAnnotations?
                                "Arena set/collector annotations will be omitted; "+
                                "preferred printings are not inferred.":""))
                            .setNegativeButton("Keep draft",null)
                            .setPositiveButton("Use imported draft",(d,w)->{
                                if(pageGeneration==deckPageGeneration){
                                    lines.setText(parsed.entries);
                                    status.setText("Imported draft · not saved");
                                }
                            }).show();
                    }catch(Exception error){info("Import rejected: "+error.getMessage());}
                }).show();
        });
        // Smart Mana edits the UNSAVED draft on one tap, without altering
        // saved deck data, spells, existing nonbasics or either ownership pool.
        Button smartMana=VaultNativeStyle.button(activity,
            "Smart Mana — Optimize Now",true);
        fields.addView(smartMana,new LinearLayout.LayoutParams(-1,dp(48)));
        smartMana.setOnClickListener(v->{
            final String original=lines.getText().toString();
            final int current=deckPageGeneration;
            smartMana.setEnabled(false);
            status.setText("Smart Mana: checking owned shock and untapped lands…");
            executor.execute(()->{
                try{
                    JSONArray physical=new JSONArray(prefs.getString(
                        "vault_physicalCollectionV1","[]"));
                    JSONArray arenaCards=new JSONArray(prefs.getString(
                        "vault_cards","[]"));
                    NativeSmartMana.Plan plan=NativeSmartMana.analyze(
                        catalog,deck,original,physical,arenaCards);
                    String updated=NativeSmartMana.apply(original,plan);
                    ui.post(()->{
                        if(closed||current!=deckPageGeneration)return;
                        smartMana.setEnabled(true);
                        if(!original.equals(lines.getText().toString())){
                            status.setText("Draft changed; run Smart Mana again.");
                            return;
                        }
                        if(plan.swaps.isEmpty()){
                            status.setText(plan.summary());
                            return;
                        }
                        lines.setText(updated);
                        status.setText(plan.summary()+" · UNSAVED draft only. "+
                            "Review & Save remains separate.");
                    });
                }catch(Exception error){
                    ui.post(()->{
                        if(closed||current!=deckPageGeneration)return;
                        smartMana.setEnabled(true);
                        status.setText("Smart Mana made no changes: "+error.getMessage());
                    });
                }
            });
        });
        // Owned substitutions are staged only in the existing unsaved editor.
        // Every approval rechecks inventory, legality and the draft itself.
        Button planSubstitutions=VaultNativeStyle.button(activity,
            "Plan owned substitutions",false);
        fields.addView(planSubstitutions,new LinearLayout.LayoutParams(-1,dp(48)));
        final NativeDeckSubstitutions.Plan[] proposedPlan={null};
        final String[] plannedBoard={null};
        final String[] plannedFingerprint={null};
        planSubstitutions.setOnClickListener(v->{
            final String draft=lines.getText().toString();
            final int current=deckPageGeneration;
            proposedPlan[0]=null;plannedBoard[0]=null;plannedFingerprint[0]=null;
            planSubstitutions.setEnabled(false);
            status.setText("Reviewing owned substitution candidates…");
            executor.execute(()->{
                try{
                    JSONArray physical=new JSONArray(prefs.getString(
                        "vault_physicalCollectionV1","[]"));
                    JSONArray arenaCards=new JSONArray(prefs.getString("vault_cards","[]"));
                    NativeDeckSubstitutions.Plan plan=NativeDeckSubstitutions.analyze(
                        catalog,deck,draft,physical,arenaCards);
                    ui.post(()->{
                        if(closed||current!=deckPageGeneration)return;
                        planSubstitutions.setEnabled(true);
                        if(!draft.equals(lines.getText().toString())){
                            status.setText("Draft changed; generate a new substitution plan.");
                            return;
                        }
                        proposedPlan[0]=plan;
                        plannedBoard[0]=draft;
                        plannedFingerprint[0]=plan.fingerprint();
                        status.setText("Owned substitutions: "+plan.summary());
                        new AlertDialog.Builder(activity)
                            .setTitle("Review owned substitutions")
                            .setMessage(plan.details()+"\n\nSuggestions preserve recognized roles "+
                                "and local format/color filters, but cannot prove "+
                                "equivalent card power. No changes until approval.")
                            .setNegativeButton("Keep draft",null)
                            .setPositiveButton("Apply to unsaved draft",(d,w)->{
                                if(proposedPlan[0]!=plan||
                                    !draft.equals(lines.getText().toString())){
                                    info("Draft changed; plan again.");return;
                                }
                                planSubstitutions.setEnabled(false);
                                executor.execute(()->{
                                    try{
                                        JSONArray freshPhysical=new JSONArray(prefs.getString(
                                            "vault_physicalCollectionV1","[]"));
                                        JSONArray freshArena=new JSONArray(prefs.getString(
                                            "vault_cards","[]"));
                                        NativeDeckSubstitutions.Plan verified=
                                            NativeDeckSubstitutions.analyze(catalog,deck,
                                                draft,freshPhysical,freshArena);
                                        if(!verified.fingerprint().equals(plannedFingerprint[0]))
                                            throw new IllegalArgumentException(
                                                "Inventory or legality changed; generate a new plan.");
                                        String updated=NativeDeckSubstitutions.applyToText(
                                            draft,verified);
                                        ui.post(()->{
                                            if(closed||current!=deckPageGeneration)return;
                                            planSubstitutions.setEnabled(true);
                                            if(!draft.equals(lines.getText().toString())){
                                                info("Draft changed; no swaps applied.");return;
                                            }
                                            if(verified.swaps.isEmpty()){
                                                info("No eligible owned swaps to apply.");return;
                                            }
                                            lines.setText(updated);
                                            proposedPlan[0]=null;
                                            status.setText(verified.swaps.size()+
                                                " substitutions applied to UNSAVED draft. "+
                                                "Review & Save remains separate.");
                                        });
                                    }catch(Exception error){
                                        ui.post(()->{
                                            if(closed||current!=deckPageGeneration)return;
                                            planSubstitutions.setEnabled(true);
                                            info("Plan not applied: "+error.getMessage());
                                        });
                                    }
                                });
                            }).show();
                    });
                }catch(Exception error){
                    ui.post(()->{
                        if(closed||current!=deckPageGeneration)return;
                        planSubstitutions.setEnabled(true);
                        status.setText("Substitution plan unavailable: "+error.getMessage());
                    });
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
        discard.setOnClickListener(v->onBack());
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

    /** Keep the loaded Deck Details views alive while inspecting a card.
     * Returning restores the exact list and scroll without re-reading inventory,
     * rebuilding insights, or changing ownership or saved deck contents. */
    private void openDeckCard(String cardName){
        String name=cardName==null?"":cardName.trim();
        if(closed||(!"deck-details".equals(screenState)&&!"deck-edit".equals(screenState))||
                deckCardInspector!=null||name.isEmpty())return;
        deckCardReturnState=screenState;
        deckDetailsRetainedScroll=workspaceScroll.getScrollY();
        deckDetailsRetainedViews.clear();
        for(int i=0;i<content.getChildCount();i++)
            deckDetailsRetainedViews.add(content.getChildAt(i));
        content.removeAllViews();
        deckCardInspector=new NativeCatalogScreen(activity,catalog,artwork,prefs);
        deckCardInspector.useEmbeddedInspectorLayout();
        content.addView(deckCardInspector.view(),new LinearLayout.LayoutParams(
            -1,LinearLayout.LayoutParams.WRAP_CONTENT));
        deckCardInspector.inspectDeckCard(name,this::returnFromDeckCard);
        workspaceScroll.post(()->workspaceScroll.scrollTo(0,0));
    }

    private void returnFromDeckCard(){
        if(deckCardInspector==null)return;
        deckCardInspector.close();
        deckCardInspector=null;
        content.removeAllViews();
        for(View previous:deckDetailsRetainedViews)content.addView(previous);
        deckDetailsRetainedViews.clear();
        final int scroll=deckDetailsRetainedScroll;
        workspaceScroll.post(()->{
            if(!closed&&deckCardReturnState.equals(screenState))
                workspaceScroll.scrollTo(0,scroll);
        });
    }

    private void showDeck(JSONObject deck){
        if("deck-library".equals(screenState))
            deckLibraryRestoreScroll=workspaceScroll.getScrollY();
        ui.removeCallbacks(deckSearchPending);
        screenState="deck-details";
        editingDeck=null;editorName=null;editorMainboard=null;
        final int pageGeneration=++deckPageGeneration;
        deckCoverTargets.clear();
        status.setText("Reviewing "+deck.optString("name","saved deck")+"…");
        executor.execute(()->{
            try{
                Map<String,Integer> owned=new HashMap<>();
                // Never combine Arena and physical copies when evaluating
                // whether a saved deck is buildable in one inventory.
                String inventoryPool=NativeDeckFit.arenaFormat(
                    deck.optString("format",""))?"vault_cards":"vault_physicalCollectionV1";
                for(String key:new String[]{inventoryPool}){
                    JSONArray cards=new JSONArray(prefs.getString(key,"[]"));
                    if(cards.length()>150000)
                        throw new IllegalStateException("Inventory safety limit exceeded");
                    for(int i=0;i<cards.length();i++){
                        JSONObject card=cards.optJSONObject(i);if(card==null)continue;
                        String name=NativeArenaCardName.key(card.optString("name",""));
                        owned.put(name,owned.getOrDefault(name,0)+
                            Math.max(0,card.optInt("qty",0)));
                    }
                }
                JSONArray entries=deck.optJSONArray("entries");
                java.util.ArrayList<String[]> cards=new java.util.ArrayList<>();
                int missing=0, total=0;
                if(entries!=null)for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.optJSONObject(i);
                    if(entry==null)continue;
                    String name=entry.optString("name","");
                    int qty=Math.max(0,entry.optInt("qty",0));
                    int have=owned.getOrDefault(NativeArenaCardName.key(name),0);
                    int need=Math.max(0,qty-have);
                    missing+=need;total+=qty;
                    cards.add(new String[]{qty+" × "+name,
                        need>0?"Missing "+need+" · recorded copies "+have:
                            "Owned · recorded copies "+have,name});
                }
                NativeDeckFit.Result fit=NativeDeckFit.check(deck,
                    new JSONArray(prefs.getString("vault_physicalCollectionV1","[]")),
                    new JSONArray(prefs.getString("vault_cards","[]")));
                JSONObject insights=null;
                try{insights=catalog.deckInsights(deck.optString("commander",""),entries);}
                catch(Exception ignored){ /* Catalog may be syncing; show existing deck unchanged. */ }
                final JSONObject report=insights;
                int missingCount=fit.missingCopies(),totalCount=fit.required;
                ui.post(()->{
                    if(closed||activity.isFinishing()||pageGeneration!=deckPageGeneration)return;
                    content.removeAllViews();
                    status.setText(totalCount+" cards · "+missingCount+
                        " not covered by "+(fit.arena?"Arena":"physical")+" inventory");
                    button("← Back to Deck Library",this::deckLibrary);
                    TextView title=VaultNativeStyle.label(activity,
                        deck.optString("name","Untitled deck"),22,VaultNativeStyle.TEXT,true);
                    VaultNativeStyle.margin(content,title,8,activity);
                    String commanderName=deck.optString("commander","").trim();
                    row("COMMANDER",commanderName,
                        commanderName.isEmpty()?null:()->openDeckCard(commanderName));
                    if(AiReviewMetadata.hasReview(deck))
                        row("SAVED AI REVIEW",AiReviewMetadata.label(deck),null);
                    row("BUILDABILITY",fit.summary(),null);
                    if(!fit.missing.isEmpty())
                        row("MISSING OWNED COPIES",String.join(", ",
                            fit.missing.subList(0,Math.min(12,fit.missing.size()))),null);
                    TextView note=VaultNativeStyle.label(activity,
                        "Recorded ownership is informational. Unowned cards stay in the deck; "+
                        "no collection quantities or printings are changed.",12,
                        VaultNativeStyle.SUBTEXT,false);
                    VaultNativeStyle.margin(content,note,12,activity);
                    button("Edit this deck",()->editDeck(deck));
                    button("✦ AI Review",()->openAiReview(deck,null));
                    LinearLayout share=VaultNativeStyle.row(activity);
                    Button copyArena=VaultNativeStyle.button(activity,"Copy for Arena",false);
                    Button copyPlain=VaultNativeStyle.button(activity,"Copy plain list",false);
                    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);
                    p.rightMargin=dp(6);
                    share.addView(copyArena,p);
                    share.addView(copyPlain,new LinearLayout.LayoutParams(0,dp(46),1));
                    VaultNativeStyle.margin(content,share,8,activity);
                    copyArena.setOnClickListener(v->copyText("Arena deck",
                        NativeDeckTextExchange.export(deck,true),"Arena-format decklist"));
                    copyPlain.setOnClickListener(v->copyText("Plain decklist",
                        NativeDeckTextExchange.export(deck,false),"plain-text decklist"));
                    if(report!=null)renderDeckInsights(report);
                    else row("Deck insights","Local Scryfall metadata unavailable; counts and deck text remain visible.",null);
                    TextView mainboard=VaultNativeStyle.label(activity,
                        "MAINBOARD  ·  "+cards.size()+" card names",12,
                        VaultNativeStyle.GOLD,true);
                    VaultNativeStyle.margin(content,mainboard,10,activity);
                    for(String[] card:cards)
                        row(card[0],card[1],()->openDeckCard(card[2]));
                });
            }catch(Exception e){
                ui.post(()->{if(!closed&&pageGeneration==deckPageGeneration)info("Deck details unavailable: "+e.getMessage());});
            }
        });
    }

    private static final class ShortlistArtTarget {
        final ImageView view;
        final String url;
        final int width;
        final int height;
        boolean loaded;
        ShortlistArtTarget(ImageView view,String url,int width,int height){
            this.view=view;this.url=url;this.width=width;this.height=height;
        }
    }

    /** Only download normal-resolution card art when a visible Shortlist card
     * enters the ScrollView. This is intentionally bounded for 20k favorites. */
    private void loadVisibleShortlistArt(){
        if(closed||activity.isFinishing()||shortlistArtTargets.isEmpty())return;
        final int generation=deckPageGeneration;
        Rect visible=new Rect();
        for(ShortlistArtTarget target:shortlistArtTargets){
            if(target.loaded||!target.view.isAttachedToWindow()||
                    !target.view.getGlobalVisibleRect(visible)||visible.isEmpty())
                continue;
            Bitmap cached=shortlistBitmaps.get(target.url);
            if(cached!=null){
                target.view.setImageBitmap(cached);
                target.loaded=true;
                continue;
            }
            if(shortlistArtUnavailable.contains(target.url)||
                    shortlistArtInFlight.contains(target.url))continue;
            if(shortlistArtInFlight.size()>=6)break;
            String url=target.url;
            int width=target.width,height=target.height;
            shortlistArtInFlight.add(url);
            coverWorkers.execute(()->{
                Bitmap image=null;
                try{
                    image=NativeArtworkQuality.load(artwork,url,width,height,false);
                }catch(Exception ignored){
                    // Keep the readable card name and star while offline.
                }
                final Bitmap result=image;
                ui.post(()->{
                    shortlistArtInFlight.remove(url);
                    if(result!=null)shortlistBitmaps.put(url,result);
                    else shortlistArtUnavailable.add(url);
                    if(!closed&&generation==deckPageGeneration)
                        loadVisibleShortlistArt();
                });
            });
        }
    }

    private void openShortlistPreview(JSONObject card){
        shortlistRestoreScrollY=workspaceScroll.getScrollY();
        shortlistNeedsScrollRestore=true;
        screenState="shortlist-preview";
        final int generation=++deckPageGeneration;
        shortlistArtTargets.clear();
        content.removeAllViews();
        workspaceScroll.scrollTo(0,0);
        String name=card.optString("name","");
        String url=card.optString("image","");
        status.setText("Shortlist · "+name);
        button("← Back to Shortlist",this::shortlist);
        TextView title=VaultNativeStyle.label(activity,name,21,
            VaultNativeStyle.TEXT,true);
        VaultNativeStyle.margin(content,title,10,activity);
        FrameLayout frame=new FrameLayout(activity);
        frame.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.ELEVATED,VaultNativeStyle.BORDER,12));
        TextView placeholder=VaultNativeStyle.label(activity,
            NativeArtworkQuality.supported(url)?"Loading official card artwork…":
                "Card art unavailable in the local catalog",14,
            VaultNativeStyle.SUBTEXT,false);
        placeholder.setGravity(Gravity.CENTER);
        frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        ImageView large=new ImageView(activity);
        large.setScaleType(ImageView.ScaleType.FIT_CENTER);
        large.setContentDescription("Large, full Scryfall card image for "+name);
        large.setTag(url);
        frame.addView(large,new FrameLayout.LayoutParams(-1,-1));
        int height=Math.min(dp(480),
            (int)(activity.getResources().getDisplayMetrics().heightPixels*0.64f));
        VaultNativeStyle.margin(content,frame,10,activity);
        LinearLayout.LayoutParams frameParams=(LinearLayout.LayoutParams)frame.getLayoutParams();
        frameParams.height=height;
        frame.setLayoutParams(frameParams);
        String owned=card.optBoolean("known",false)?
            "Physical ×"+card.optInt("physical",0)+" · Arena ×"+
            card.optInt("arena",0):
            "Printing and ownership not verified in local catalog";
        row("COLLECTION",owned,null);
        Button remove=button("★ Remove from Shortlist",()->{
            ((MainActivity)activity).setNativeShortlist(name,false,(ok,message)->{
                if(closed||activity.isFinishing()||generation!=deckPageGeneration)return;
                if(ok)shortlist();
                else info(message);
            });
        });
        remove.setContentDescription("Remove "+name+" from My Shortlist");
        if(NativeArtworkQuality.supported(url)){
            coverWorkers.execute(()->{
                Bitmap bitmap=null;
                try{
                    bitmap=NativeArtworkQuality.load(artwork,url,
                        activity.getResources().getDisplayMetrics().widthPixels-dp(56),
                        height,true);
                }catch(Exception ignored){}
                final Bitmap result=bitmap;
                ui.post(()->{
                    if(closed||activity.isFinishing()||generation!=deckPageGeneration){
                        if(result!=null)result.recycle();
                        return;
                    }
                    if(result!=null){
                        placeholder.setVisibility(View.GONE);
                        large.setImageBitmap(result);
                    }else{
                        placeholder.setText("Full artwork unavailable offline");
                    }
                });
            });
        }
    }

    /** Native Shortlist reads/writes the existing vault_favorites array in
     * the authoritative WebView. No second bookmark store is introduced. */
    /** Local Scryfall facts only: missing names never turn into zero-cost cards. */
    private void renderDeckInsights(JSONObject report){
        renderDeckInsights(report,content);
    }

    /** Read-only chart for either a saved deck or the unsaved editing draft.
     * The local catalog is queried off-thread before this method is called. */
    private void renderDeckInsights(JSONObject report,LinearLayout target){
        LinearLayout insights=VaultNativeStyle.column(activity);
        insights.setPadding(dp(13),dp(12),dp(13),dp(12));
        VaultNativeStyle.surface(activity,insights,false);
        insights.addView(VaultNativeStyle.label(activity,"DECK INSIGHTS",12,
            VaultNativeStyle.PRIMARY,true));
        String coverage=report.optInt("known",0)+"/"+report.optInt("total",0)+
            " copies matched to local catalog";
        insights.addView(VaultNativeStyle.label(activity,coverage+
            (report.optInt("unknown",0)>0?" · "+report.optInt("unknown",0)+" unknown":""),
            12,VaultNativeStyle.SUBTEXT,false));
        insights.addView(VaultNativeStyle.label(activity,
            "Creatures "+report.optInt("creatures",0)+" · Lands "+report.optInt("lands",0)+
            " · Instants "+report.optInt("instants",0)+
            " · Sorceries "+report.optInt("sorceries",0),12,
            VaultNativeStyle.TEXT,false));
        insights.addView(VaultNativeStyle.label(activity,
            "Artifacts "+report.optInt("artifacts",0)+
            " · Enchantments "+report.optInt("enchantments",0)+
            " · Planeswalkers "+report.optInt("planeswalkers",0),12,
            VaultNativeStyle.TEXT,false));
        if(!report.isNull("avgMv"))
            insights.addView(VaultNativeStyle.label(activity,
                String.format(java.util.Locale.US,"Average nonland mana value: %.2f",
                    report.optDouble("avgMv",0)),12,VaultNativeStyle.GOLD,true));
        JSONArray curve=report.optJSONArray("curve");
        if(curve!=null){
            int max=1;
            for(int i=0;i<curve.length();i++)max=Math.max(max,curve.optInt(i,0));
            for(int i=0;i<Math.min(curve.length(),8);i++){
                LinearLayout bar=VaultNativeStyle.row(activity);
                TextView label=VaultNativeStyle.label(activity,
                    i==7?"7+":Integer.toString(i),11,VaultNativeStyle.SUBTEXT,false);
                label.setGravity(Gravity.CENTER_VERTICAL);
                bar.addView(label,new LinearLayout.LayoutParams(dp(34),dp(25)));
                android.widget.ProgressBar progress=new android.widget.ProgressBar(activity,
                    null,android.R.attr.progressBarStyleHorizontal);
                progress.setMax(max);
                progress.setProgress(curve.optInt(i,0));
                progress.setProgressTintList(android.content.res.ColorStateList.valueOf(VaultNativeStyle.PRIMARY));
                LinearLayout.LayoutParams width=new LinearLayout.LayoutParams(0,dp(12),1);
                width.gravity=Gravity.CENTER_VERTICAL;
                bar.addView(progress,width);
                TextView copies=VaultNativeStyle.label(activity,
                    Integer.toString(curve.optInt(i,0)),11,VaultNativeStyle.TEXT,false);
                copies.setGravity(Gravity.CENTER);
                bar.addView(copies,new LinearLayout.LayoutParams(dp(36),dp(25)));
                insights.addView(bar);
            }
        }
        JSONObject colors=report.optJSONObject("colorIdentity");
        if(colors!=null){
            TextView colorHeading=VaultNativeStyle.label(activity,
                "SPELL COLOR IDENTITY",12,VaultNativeStyle.GOLD,true);
            colorHeading.setPadding(0,dp(10),0,dp(3));
            insights.addView(colorHeading);
            int maxColor=1;
            for(String color:new String[]{"W","U","B","R","G","C"})
                maxColor=Math.max(maxColor,colors.optInt(color,0));
            for(String color:new String[]{"W","U","B","R","G","C"}){
                final int number=colors.optInt(color,0);
                LinearLayout colorRow=VaultNativeStyle.row(activity);
                String colorLabel=color.equals("W")?"White":color.equals("U")?"Blue":
                    color.equals("B")?"Black":color.equals("R")?"Red":
                    color.equals("G")?"Green":"Colorless";
                TextView label=VaultNativeStyle.label(activity,colorLabel,11,
                    VaultNativeStyle.SUBTEXT,false);
                label.setGravity(Gravity.CENTER_VERTICAL);
                colorRow.addView(label,new LinearLayout.LayoutParams(dp(78),dp(25)));
                android.widget.ProgressBar progress=new android.widget.ProgressBar(activity,
                    null,android.R.attr.progressBarStyleHorizontal);
                progress.setMax(maxColor);
                progress.setProgress(number);
                progress.setProgressTintList(android.content.res.ColorStateList.valueOf(
                    VaultNativeStyle.PRIMARY));
                LinearLayout.LayoutParams barWidth=new LinearLayout.LayoutParams(0,dp(12),1);
                barWidth.gravity=Gravity.CENTER_VERTICAL;
                colorRow.addView(progress,barWidth);
                TextView copies=VaultNativeStyle.label(activity,
                    Integer.toString(number),11,VaultNativeStyle.TEXT,false);
                copies.setGravity(Gravity.CENTER);
                colorRow.addView(copies,new LinearLayout.LayoutParams(dp(36),dp(25)));
                insights.addView(colorRow);
            }
            insights.addView(VaultNativeStyle.label(activity,
                "Nonland spells only · multicolor cards count once per color. "+
                "Not a mana-source or mana-symbol estimate.",11,
                VaultNativeStyle.SUBTEXT,false));
        }
        JSONObject pips=report.optJSONObject("coloredPipDemand");
        if(pips!=null){
            TextView pipHeading=VaultNativeStyle.label(activity,
                "COLORED CASTING-COST DEMAND",12,VaultNativeStyle.GOLD,true);
            pipHeading.setPadding(0,dp(10),0,dp(3));
            insights.addView(pipHeading);
            StringBuilder demand=new StringBuilder();
            String[] names={"White","Blue","Black","Red","Green"};
            String symbols="WUBRG";
            for(int ci=0;ci<5;ci++){
                if(demand.length()>0)demand.append("  ·  ");
                demand.append(names[ci]).append(" ");
                demand.append(String.format(java.util.Locale.US,"%.1f",
                    pips.optDouble(String.valueOf(symbols.charAt(ci)),0)));
            }
            insights.addView(VaultNativeStyle.label(activity,demand.toString(),
                12,VaultNativeStyle.TEXT,false));
            insights.addView(VaultNativeStyle.label(activity,
                report.optInt("manaCostCopies",0)+" nonland copies with costs · "+
                report.optInt("missingCostCopies",0)+" without cost metadata. "+
                "Approximate: hybrid/Phyrexian symbols discounted; not cast odds.",
                11,VaultNativeStyle.SUBTEXT,false));
        }
        JSONObject odds=report.optJSONObject("landOdds");
        if(odds!=null&&odds.length()>0){
            insights.addView(VaultNativeStyle.label(activity,
                "NATURAL LAND-DROP ESTIMATES",12,VaultNativeStyle.GOLD,true));
            StringBuilder estimate=new StringBuilder();
            for(int turn=2;turn<=4;turn++){
                if(estimate.length()>0)estimate.append("   ·   ");
                estimate.append("T").append(turn).append(" ≥").append(turn)
                    .append(" lands: ").append(String.format(java.util.Locale.US,
                        "%.0f%%",100*odds.optDouble("T"+turn,0)));
            }
            insights.addView(VaultNativeStyle.label(activity,
                estimate.toString(),12,VaultNativeStyle.TEXT,false));
            insights.addView(VaultNativeStyle.label(activity,
                "Known 99-card mainboard only · 7-card opener, one draw per turn "+
                "from turn 2; no mulligans, ramp or land timing modeled.",
                11,VaultNativeStyle.SUBTEXT,false));
        }else{
            insights.addView(VaultNativeStyle.label(activity,
                "Land-drop estimates require 99 known mainboard cards.",
                11,VaultNativeStyle.SUBTEXT,false));
        }
        VaultNativeStyle.margin(target,insights,10,activity);
    }

    private void shortlist(){
        screenState="shortlist";
        final int generation=++deckPageGeneration;
        deckCoverTargets.clear();
        shortlistArtTargets.clear();
        content.removeAllViews();
        workspaceScroll.scrollTo(0,0);
        status.setText("Reading saved card names…");
        ((MainActivity)activity).loadNativeShortlist(names->{
            if(closed||activity.isFinishing()||generation!=deckPageGeneration)return;
            content.removeAllViews();
            if(names==null){
                status.setText("Shortlist unavailable while legacy storage is loading");
                row("Existing favorites were not changed",
                    "Return once app data is ready, or use Legacy tools.",null);
                return;
            }
            if(names.length()>20000){
                status.setText("Shortlist exceeds safe display limit");return;
            }
            ArrayList<String> selected=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(int i=0;i<names.length();i++){
                String name=names.optString(i,"").trim();
                if(name.isEmpty()||name.length()>180)continue;
                if(seen.add(name.toLowerCase(java.util.Locale.ROOT)))selected.add(name);
            }
            status.setText(selected.size()+" bookmarked cards · no changes to inventory");
            EditText search=new EditText(activity);
            VaultNativeStyle.search(activity,search);
            search.setSingleLine(true);
            search.setHint("Search saved cards");
            search.setText(shortlistQuery);
            VaultNativeStyle.margin(content,search,8,activity);
            Button copy=button("Copy shortlist as decklist",()->{
                StringBuilder text=new StringBuilder();
                for(String name:selected)text.append("1 ").append(name).append('\n');
                copyText("Arcane Vault Shortlist",text.toString(),
                    selected.size()+" card names");
            });
            copy.setEnabled(!selected.isEmpty());
            shortlistPanel=VaultNativeStyle.column(activity);
            content.addView(shortlistPanel);
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence x,int a,int b,int c){}
                public void onTextChanged(CharSequence x,int a,int b,int c){
                    shortlistQuery=x.toString();shortlistPage=0;renderShortlist();
                }
                public void afterTextChanged(Editable x){}
            });
            executor.execute(()->{
                ArrayList<JSONObject> cards=new ArrayList<>();
                for(String name:selected){
                    try{cards.add(catalog.cardBrief(name));}
                    catch(Exception error){
                        JSONObject fallback=new JSONObject();
                        try {
                            fallback.put("name",name).put("known",false)
                                .put("physical",0).put("arena",0).put("type","");
                        } catch(Exception ignored){}
                        cards.add(fallback);
                    }
                }
                ui.post(()->{
                    if(closed||activity.isFinishing()||generation!=deckPageGeneration)return;
                    shortlistCards=cards;
                    renderShortlist();
                });
            });
        });
    }

    /** Image-first Shortlist gallery, matching Card Catalog's compact grid.
     * Search/pagination are bounded so long shortlists never construct thousands
     * of ImageViews or launch off-screen network fetches. */
    private void renderShortlist(){
        if(shortlistPanel==null)return;
        shortlistPanel.removeAllViews();
        shortlistArtTargets.clear();
        String term=shortlistQuery.trim().toLowerCase(java.util.Locale.ROOT);
        ArrayList<JSONObject> filtered=new ArrayList<>();
        for(JSONObject card:shortlistCards){
            if(card==null)continue;
            String name=card.optString("name","");
            if(name.toLowerCase(java.util.Locale.ROOT).contains(term))
                filtered.add(card);
        }
        if(filtered.isEmpty()){
            rowTo(shortlistPanel,shortlistCards.isEmpty()?
                "Nothing bookmarked yet":"No matching cards",
                "Use the star on Card Catalog details to save cards.");
            return;
        }
        int lastPage=(filtered.size()-1)/SHORTLIST_PAGE_SIZE;
        shortlistPage=Math.max(0,Math.min(shortlistPage,lastPage));
        int first=shortlistPage*SHORTLIST_PAGE_SIZE;
        int last=Math.min(filtered.size(),first+SHORTLIST_PAGE_SIZE);
        TextView pageLabel=VaultNativeStyle.label(activity,
            filtered.size()+" cards · showing "+(first+1)+"–"+last+
            " · tap artwork to preview",12,VaultNativeStyle.SUBTEXT,false);
        VaultNativeStyle.margin(shortlistPanel,pageLabel,6,activity);

        int screenWidth=activity.getResources().getDisplayMetrics().widthPixels;
        float density=activity.getResources().getDisplayMetrics().density;
        float widthDp=screenWidth/density;
        int columns=widthDp>=760?4:widthDp>=560?3:2;
        int tileWidth=Math.max(dp(116),
            (screenWidth-dp(42)-dp(8)*columns)/columns);
        int imageHeight=Math.round((tileWidth-dp(12))*1.40f);
        GridLayout gallery=new GridLayout(activity);
        gallery.setColumnCount(columns);
        for(int i=first;i<last;i++){
            JSONObject card=filtered.get(i);
            String name=card.optString("name","Unknown card");
            String url=card.optString("image","");
            LinearLayout tile=VaultNativeStyle.column(activity);
            tile.setPadding(dp(6),dp(6),dp(6),dp(8));
            VaultNativeStyle.surface(activity,tile,false);
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setContentDescription("Preview full card "+name);
            FrameLayout artFrame=new FrameLayout(activity);
            artFrame.setBackground(VaultNativeStyle.rounded(activity,
                VaultNativeStyle.ELEVATED,VaultNativeStyle.BORDER,9));
            TextView fallback=VaultNativeStyle.label(activity,
                NativeArtworkQuality.supported(url)?"✦":"Artwork unavailable",
                13,VaultNativeStyle.PRIMARY,true);
            fallback.setGravity(Gravity.CENTER);
            artFrame.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            if(NativeArtworkQuality.supported(url)){
                ImageView image=new ImageView(activity);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription("Full card art for "+name);
                artFrame.addView(image,new FrameLayout.LayoutParams(-1,-1));
                shortlistArtTargets.add(new ShortlistArtTarget(image,url,
                    tileWidth-dp(12),imageHeight));
            }
            tile.addView(artFrame,new LinearLayout.LayoutParams(-1,imageHeight));
            TextView heading=VaultNativeStyle.label(activity,name,13,
                VaultNativeStyle.TEXT,true);
            heading.setMaxLines(2);
            heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(heading);
            String counts=card.optBoolean("known",false)?
                "Physical ×"+card.optInt("physical",0)+" · Arena ×"+
                    card.optInt("arena",0):
                "Ownership unverified";
            TextView info=VaultNativeStyle.label(activity,counts,10,
                VaultNativeStyle.SUBTEXT,false);
            info.setMaxLines(2);
            tile.addView(info);
            Button remove=VaultNativeStyle.button(activity,"★ Saved · Remove",false);
            remove.setAllCaps(false);
            remove.setTextSize(11);
            remove.setContentDescription("Remove "+name+" from Shortlist");
            tile.addView(remove,new LinearLayout.LayoutParams(-1,dp(42)));
            remove.setOnClickListener(v->{
                remove.setEnabled(false);
                ((MainActivity)activity).setNativeShortlist(name,false,(ok,message)->{
                    if(!closed&&!activity.isFinishing()){
                        if(ok)shortlist();
                        else{remove.setEnabled(true);info(message);}
                    }
                });
            });
            tile.setOnClickListener(v->openShortlistPreview(card));
            GridLayout.LayoutParams placement=new GridLayout.LayoutParams();
            placement.width=tileWidth;
            placement.height=-2;
            placement.rowSpec=GridLayout.spec((i-first)/columns);
            placement.columnSpec=GridLayout.spec((i-first)%columns);
            placement.setMargins(dp(2),dp(2),dp(2),dp(4));
            gallery.addView(tile,placement);
        }
        shortlistPanel.addView(gallery);
        LinearLayout paging=VaultNativeStyle.row(activity);
        if(shortlistPage>0){
            Button previous=VaultNativeStyle.button(activity,"← Previous",false);
            paging.addView(previous,new LinearLayout.LayoutParams(0,dp(44),1));
            previous.setOnClickListener(v->{
                shortlistPage--;renderShortlist();
                workspaceScroll.smoothScrollTo(0,0);
            });
        }
        if(shortlistPage<lastPage){
            Button next=VaultNativeStyle.button(activity,"Next →",false);
            paging.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));
            next.setOnClickListener(v->{
                shortlistPage++;renderShortlist();
                workspaceScroll.smoothScrollTo(0,0);
            });
        }
        if(paging.getChildCount()>0)
            VaultNativeStyle.margin(shortlistPanel,paging,10,activity);
        workspaceScroll.post(()->{
            if(shortlistNeedsScrollRestore){
                shortlistNeedsScrollRestore=false;
                workspaceScroll.scrollTo(0,shortlistRestoreScrollY);
            }
            loadVisibleShortlistArt();
        });
    }

    private void dataSync(){
        screenState="sync";
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
            tab.setOnClickListener(v->{
                if(syncSection.equals(target))return;
                if(syncHistory.size()>=24)syncHistory.removeFirst();
                syncHistory.addLast(syncSection);
                syncSection=target;
                dataSync();
            });
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
        row("Native saved-deck and artwork safety copy",
            NativeDeckArtworkMirror.overview(activity),null);
        button("Export verified deck/artwork recovery copy",
            ()->((MainActivity)activity).exportNativeDeckArtworkMirror());
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
        row("Remembered peer checkpoints",
            NativePeerCheckpoint.overview(activity),null);
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
