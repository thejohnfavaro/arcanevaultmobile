package com.arcanevault.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Native-first phone shell; original physical and Arena inventories remain authoritative. */
final class NativeCompanionScreen {
    private static final int PAGE_SIZE=60;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final CatalogStore catalog;
    private final ArtworkCache artwork;
    private final Runnable openLegacy;
    private final LinearLayout root;
    private final FrameLayout stage;
    private final TextView subtitle;
    private final LinearLayout tabs;
    private final HorizontalScrollView secondaryScroll;
    private final LinearLayout secondaryTabs;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private NativeCatalogScreen nativeCatalog;
    private NativeCommanderBrowserScreen commanderBrowser;
    private NativeWorkspaceScreen workspace;
    private final NativeWorkspaceScreen.DeckLoader deckLoader;
    private final NativeWorkspaceScreen.DeckSaver deckSaver;
    private String scope="home", query="";
    private final ArrayDeque<String> navigationHistory=new ArrayDeque<>();
    private boolean restoringHistory=false;
    private boolean closed=false;
    private int visibleCount=PAGE_SIZE;
    private List<CardRow> physical=Collections.emptyList(),arena=Collections.emptyList();
    private int readGeneration;
    private final java.util.concurrent.ExecutorService auditWorker=
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private int auditGeneration;
    private String pendingSetFromWidget="";

    private static final class CardRow {
        final String name;final int qty;
        CardRow(String name,int qty){this.name=name;this.qty=qty;}
    }

    private NativeCompanionScreen(Activity activity,SharedPreferences prefs,
            CatalogStore catalog,ArtworkCache artwork,Runnable openLegacy,
            NativeWorkspaceScreen.DeckLoader deckLoader,
            NativeWorkspaceScreen.DeckSaver deckSaver) {
        this.activity=activity;
        this.prefs=prefs;this.catalog=catalog;this.artwork=artwork;this.openLegacy=openLegacy;
        this.deckLoader=deckLoader;
        this.deckSaver=deckSaver;
        root=VaultNativeStyle.column(activity);
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        root.setPadding(0,dp(14),0,0);

        LinearLayout brand=VaultNativeStyle.row(activity);
        brand.setPadding(dp(20),dp(6),dp(20),dp(10));
        TextView mark=VaultNativeStyle.label(activity,"◆",26,VaultNativeStyle.GOLD,true);
        brand.addView(mark);
        LinearLayout titles=VaultNativeStyle.column(activity);
        titles.setPadding(dp(12),0,0,0);
        titles.addView(VaultNativeStyle.label(activity,"ARCANE VAULT",22,VaultNativeStyle.TEXT,true));
        subtitle=VaultNativeStyle.label(activity,"Your collection. Anywhere.",12,VaultNativeStyle.SUBTEXT,false);
        titles.addView(subtitle);
        brand.addView(titles);
        root.addView(brand);

        // The main workspaces are always reachable at the bottom of the screen.
        // Related destinations live in a compact contextual row, not a
        // nine-item scrolling bar that consumes room needed for card results.
        secondaryScroll=VaultNativeStyle.horizontalScroll(activity);
        secondaryScroll.setHorizontalScrollBarEnabled(false);
        secondaryTabs=VaultNativeStyle.row(activity);
        secondaryTabs.setPadding(dp(14),dp(2),dp(14),dp(7));
        secondaryScroll.addView(secondaryTabs);
        root.addView(secondaryScroll,new LinearLayout.LayoutParams(-1,dp(51)));

        stage=new FrameLayout(activity);
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));

        tabs=VaultNativeStyle.row(activity);
        tabs.setPadding(dp(5),dp(6),dp(5),dp(7));
        tabs.setBackgroundColor(VaultNativeStyle.SURFACE);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(66)));
        show("home");
        refresh();
    }

    static View create(Activity activity,SharedPreferences prefs,CatalogStore catalog,
            ArtworkCache artwork,Runnable openLegacy,NativeWorkspaceScreen.DeckLoader deckLoader,
            NativeWorkspaceScreen.DeckSaver deckSaver) {
        return new NativeCompanionScreen(activity,prefs,catalog,artwork,openLegacy,deckLoader,deckSaver).root;
    }
    /** Owned by MainActivity while its native view is visible.
     * Keeping the controller lets Android system Back pop the correct page. */
    static NativeCompanionScreen createController(Activity activity,SharedPreferences prefs,
            CatalogStore catalog,ArtworkCache artwork,Runnable openLegacy,
            NativeWorkspaceScreen.DeckLoader deckLoader,
            NativeWorkspaceScreen.DeckSaver deckSaver) {
        return new NativeCompanionScreen(activity,prefs,catalog,artwork,openLegacy,
            deckLoader,deckSaver);
    }
    View view(){return root;}
    void close(){
        if(closed)return;
        closed=true;
        readGeneration++;
        auditGeneration++;
        auditWorker.shutdownNow();
        closeCatalog();
        navigationHistory.clear();
    }

    boolean onBack(){
        if(closed)return false;
        if(nativeCatalog!=null&&nativeCatalog.onBack())return true;
        if(commanderBrowser!=null&&commanderBrowser.onBack())return true;
        if(workspace!=null&&workspace.onBack())return true;
        if(!navigationHistory.isEmpty()){
            String previous=navigationHistory.removeLast();
            restoringHistory=true;
            try{show(previous);}
            finally{restoringHistory=false;}
            return true;
        }
        if(!"home".equals(scope)){
            restoringHistory=true;
            try{show("home");}
            finally{restoringHistory=false;}
            return true;
        }
        return false; // Only the native Home root is allowed to exit.
    }

    private void remember(String current){
        if(navigationHistory.size()>=32)navigationHistory.removeFirst();
        navigationHistory.addLast(current);
    }

    private int dp(float n){return VaultNativeStyle.dp(activity,n);}
    private LinearLayout column(){return VaultNativeStyle.column(activity);}
    private TextView text(String s,int size,int color,boolean bold){
        return VaultNativeStyle.label(activity,s,size,color,bold);
    }
    private Button button(String title,boolean selected){return VaultNativeStyle.button(activity,title,selected);}

    private String primaryScope(){
        if(scope.equals("physical")||scope.equals("arena")||scope.equals("sets"))
            return "collection";
        if(scope.equals("decks")||scope.equals("edh lab")||scope.equals("analytics"))return "decks";
        if(scope.equals("shortlist"))return "catalog";
        if(scope.equals("data & sync"))return "sync";
        return scope;
    }

    private void drawTabs(){
        tabs.removeAllViews();
        final String active=primaryScope();
        final String[] destinations={"home","catalog","collection","decks","sync"};
        final String[] labels={"Home","Catalog","Collection","Decks","Sync"};
        final String[] symbols={"⌂","▤","▣","♜","⇄"};
        for(int i=0;i<destinations.length;i++){
            final String destination=destinations[i];
            boolean selected=destination.equals(active);
            LinearLayout item=VaultNativeStyle.column(activity);
            item.setGravity(Gravity.CENTER);
            item.setFocusable(true);
            item.setClickable(true);
            item.setContentDescription(labels[i]+" workspace"+
                (selected?", selected":""));
            item.setBackground(VaultNativeStyle.rounded(activity,
                selected?VaultNativeStyle.ELEVATED:VaultNativeStyle.SURFACE,
                selected?VaultNativeStyle.PRIMARY:Color.TRANSPARENT,10));
            TextView symbol=text(symbols[i],19,
                selected?VaultNativeStyle.PRIMARY:VaultNativeStyle.SUBTEXT,true);
            symbol.setGravity(Gravity.CENTER);
            item.addView(symbol,new LinearLayout.LayoutParams(-1,dp(24)));
            TextView label=text(labels[i],10,
                selected?VaultNativeStyle.TEXT:VaultNativeStyle.SUBTEXT,selected);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.addView(label,new LinearLayout.LayoutParams(-1,dp(18)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(51),1);
            lp.setMargins(dp(2),0,dp(2),0);
            tabs.addView(item,lp);
            item.setOnClickListener(v->show(
                destination.equals("collection")?"physical":
                destination.equals("sync")?"data & sync":destination));
        }
    }

    private void drawSecondaryTabs(){
        secondaryTabs.removeAllViews();
        String primary=primaryScope();
        if(primary.equals("catalog")){
            addSecondaryTab("Card Catalog","catalog");
            addSecondaryTab("My Shortlist","shortlist");
        } else if(primary.equals("collection")){
            for(String sub:new String[]{"Physical", "Arena", "Sets"}){
                String target=sub.toLowerCase(Locale.ROOT);
                addSecondaryTab(sub,target);
            }
        } else if(primary.equals("decks")){
            addSecondaryTab("Deck Library","decks");
            addSecondaryTab("Deck Analytics","analytics");
            addSecondaryTab("EDH Lab","edh lab");
        } else if(primary.equals("sync")){
            // The old WebView UI is retired. Its hidden storage bridge
            // remains available for existing decks/artwork until migrated.
            addSecondaryTab("Data & Sync","data & sync");
        }
        secondaryScroll.setVisibility(secondaryTabs.getChildCount()==0?View.GONE:View.VISIBLE);
    }

    private void addSecondaryTab(String label,String target){
        Button choice=button(label,scope.equals(target));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
        lp.rightMargin=dp(7);
        secondaryTabs.addView(choice,lp);
        choice.setContentDescription(label+" section");
        choice.setOnClickListener(v->show(target));
    }

    private void closeCatalog(){
        if(nativeCatalog!=null){nativeCatalog.close();nativeCatalog=null;}
        if(commanderBrowser!=null){commanderBrowser.close();commanderBrowser=null;}
        if(workspace!=null){workspace.close();workspace=null;}
    }
    /** Navigate within the same native workspace; the selected commander is
     * used only as a read-only saved-deck filter, never a deck mutation. */
    private void showDecksForCommander(String commanderName){
        if(commanderName==null||commanderName.trim().isEmpty())return;
        if(!"decks".equals(scope))remember(scope);
        closeCatalog();
        scope="decks";query="";visibleCount=PAGE_SIZE;
        drawTabs();
        drawSecondaryTabs();
        stage.removeAllViews();
        workspace=NativeWorkspaceScreen.create(activity,prefs,catalog,artwork,openLegacy,
            deckLoader,deckSaver,"decks",commanderName);
        stage.addView(workspace.view(),new FrameLayout.LayoutParams(-1,-1));
        subtitle.setText("Saved decks for "+commanderName);
    }

    /** Widget taps enter an existing native workspace. No data is modified:
     * the set code and source only select the current read-only checklist. */
    void openWidgetDestination(String destination,String set,String source){
        if(closed)return;
        if("physical".equals(destination)){
            show("physical");
            return;
        }
        if(!"set".equals(destination) || set==null ||
                !set.matches("[A-Za-z0-9]{2,12}"))return;
        String requested="arena".equals(source)?"arena":"physical";
        prefs.edit().putString("native_set_source_v1",requested).apply();
        pendingSetFromWidget=set;
        try{show("sets");}
        finally{pendingSetFromWidget="";}
    }

    /** Set card rows enter the existing full Catalog inspector. Returning
     * restores the set detail instead of discarding the navigation context. */
    void inspectSetPrinting(JSONObject printing,String setName,String setCode,
            String source,int quantity){
        if(closed)return;
        show("catalog");
        if(nativeCatalog==null)return;
        nativeCatalog.inspectSetPrinting(printing,setName,source,quantity,()->{
            if(closed)return;
            // This returns through an existing navigation edge, not a new
            // visit to Catalog. Do not create a Back-button loop.
            restoringHistory=true;
            try{
                show("sets");
                if(workspace!=null)
                    workspace.openSetFromCatalog(setCode,setName,source);
            }finally{restoringHistory=false;}
        });
    }

    private void show(String next){
        if(closed)return;
        if(workspace!=null && workspace.confirmLeave(()->show(next)))return;
        if(!next.equals(scope)&&!restoringHistory)remember(scope);
        closeCatalog();
        scope=next;
        query="";visibleCount=PAGE_SIZE;
        drawTabs();
        drawSecondaryTabs();
        stage.removeAllViews();
        if(scope.equals("sets")||scope.equals("decks")||scope.equals("shortlist")||scope.equals("data & sync")){
            workspace=NativeWorkspaceScreen.create(activity,prefs,catalog,artwork,openLegacy,deckLoader,
                deckSaver,scope.equals("data & sync")?"data":scope,
                scope.equals("sets")?pendingSetFromWidget:"");
            stage.addView(workspace.view(),new FrameLayout.LayoutParams(-1,-1));
            subtitle.setText(scope.equals("sets")?"Set completion by owned edition":
                scope.equals("decks")?"Saved deck library":
                scope.equals("shortlist")?"Cards saved for later":"Backups, sync and recovery");
            return;
        }
        if(scope.equals("analytics")){
            stage.addView(NativeMatchAnalyticsScreen.create(activity,prefs),
                new FrameLayout.LayoutParams(-1,-1));
            subtitle.setText("Verified Arena results from Windows sync");
            return;
        }
        if(scope.equals("edh lab")){
            commanderBrowser=new NativeCommanderBrowserScreen(
                activity,catalog,artwork,openLegacy,this::showDecksForCommander);
            stage.addView(commanderBrowser.view(),new FrameLayout.LayoutParams(-1,-1));
            subtitle.setText("Commander discovery and preview");
            return;
        }
        if(scope.equals("catalog")){
            nativeCatalog=new NativeCatalogScreen(activity, catalog, artwork, prefs);
            stage.addView(nativeCatalog.view(),new FrameLayout.LayoutParams(-1,-1));
            subtitle.setText("Discover cards and printings");
            return;
        }
        subtitle.setText(scope.equals("home")?"Your collection. Anywhere.":
            scope.equals("physical")?"Paper collection":"Arena collection");
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        scroll.setFillViewport(true);
        LinearLayout content=column();
        content.setPadding(dp(18),dp(12),dp(18),dp(26));
        scroll.addView(content);
        stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(scope.equals("home"))renderHome(content);
        else renderInventory(content,scope.equals("physical")?physical:arena);
    }

    private void card(LinearLayout target,String eyebrow,String headline,String sub,int accent){
        LinearLayout block=column();
        block.setPadding(dp(18),dp(16),dp(18),dp(18));
        VaultNativeStyle.surface(activity,block,false);
        TextView over=text(eyebrow.toUpperCase(Locale.ROOT),11,accent,true);
        block.addView(over);
        TextView name=text(headline,23,VaultNativeStyle.TEXT,true);
        name.setPadding(0,dp(8),0,dp(5));
        block.addView(name);
        block.addView(text(sub,13,VaultNativeStyle.SUBTEXT,false));
        VaultNativeStyle.margin(target,block,12,activity);
    }
    /** In-app read-only projection of the exact saved Scryfall widget valuation.
     * Currency and selected market are display preferences, never price writes. */
    private void renderCollectionValue(LinearLayout parent){
        android.content.SharedPreferences quotes=VaultWidgets.prefs(activity);
        LinearLayout pane=column();
        pane.setPadding(dp(16),dp(13),dp(16),dp(15));
        VaultNativeStyle.surface(activity,pane,false);
        pane.addView(text("PHYSICAL COLLECTION VALUE",12,VaultNativeStyle.GOLD,true));
        TextView total=text("—",27,VaultNativeStyle.TEXT,true);
        total.setPadding(0,dp(9),0,dp(5));
        pane.addView(total);
        TextView detail=text("",12,VaultNativeStyle.SUBTEXT,false);
        pane.addView(detail);
        TextView origin=text("",11,VaultNativeStyle.SUBTEXT,false);
        origin.setPadding(0,dp(5),0,dp(7));
        pane.addView(origin);
        LinearLayout choices=VaultNativeStyle.row(activity);
        android.widget.Spinner market=new android.widget.Spinner(activity);
        String[] marketKeys={"scryfall","tcgplayer"};
        String[] marketNames={"Scryfall reference","TCGplayer via Scryfall"};
        android.widget.ArrayAdapter<String> markets=new android.widget.ArrayAdapter<>(
            activity,android.R.layout.simple_spinner_item,marketNames);
        markets.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        market.setAdapter(markets);
        market.setSelection("tcgplayer".equals(quotes.getString("value_market_global","scryfall"))?1:0);
        choices.addView(market,new LinearLayout.LayoutParams(0,dp(48),2));
        android.widget.Spinner currency=new android.widget.Spinner(activity);
        String[] codes={"USD","EUR","GBP","CAD","AUD","JPY"};
        android.widget.ArrayAdapter<String> currencies=new android.widget.ArrayAdapter<>(
            activity,android.R.layout.simple_spinner_item,codes);
        currencies.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currency.setAdapter(currencies);
        String savedCode=quotes.getString("value_currency_global","USD");
        int selectedCode=java.util.Arrays.asList(codes).indexOf(savedCode);
        currency.setSelection(Math.max(0,selectedCode));
        choices.addView(currency,new LinearLayout.LayoutParams(0,dp(48),1));
        pane.addView(choices);
        Runnable update=()->{
            String marketKey=marketKeys[market.getSelectedItemPosition()];
            String code=codes[currency.getSelectedItemPosition()];
            quotes.edit().putString("value_market_global",marketKey)
                .putString("value_currency_global",code).apply();
            int priced=quotes.getInt("priced_rows",0);
            int all=quotes.getInt("total_rows",0);
            boolean valid=quotes.getBoolean("value_valid",false);
            Double rate=VaultWidgetExchangeRates.rate(activity,code);
            if(valid&&priced>0&&rate!=null){
                java.util.Locale locale="EUR".equals(code)?java.util.Locale.GERMANY:
                    "GBP".equals(code)?java.util.Locale.UK:
                    "CAD".equals(code)?java.util.Locale.CANADA:
                    "AUD".equals(code)?new java.util.Locale("en","AU"):
                    "JPY".equals(code)?java.util.Locale.JAPAN:java.util.Locale.US;
                java.text.NumberFormat amount=java.text.NumberFormat.getCurrencyInstance(locale);
                amount.setCurrency(java.util.Currency.getInstance(code));
                total.setText(amount.format((quotes.getLong("value_cents",0)/100.0)*rate));
            }else total.setText("—");
            String message=!valid?"Open Data & Sync or refresh Scryfall prices to load value":
                all==0?"No inventoried physical printings":
                !quotes.getBoolean("quotes_present",false)?"No cached Scryfall prices":
                priced==0?"Owned printings have no matching price quotes":
                rate==null?"Exchange rate unavailable · reconnect to refresh":
                    priced+" of "+all+" printing entries priced · unpriced excluded";
            detail.setText(message);
            String updated=quotes.getString("quote_updated","");
            if(updated.length()>10)updated=updated.substring(0,10);
            origin.setText(("tcgplayer".equals(marketKey)?"TCGplayer market via Scryfall":
                "Scryfall market reference")+" · "+code+
                (updated.isEmpty()?" · cached quotes":" · quotes "+updated)+
                ("USD".equals(code)?"":" · FX "+VaultWidgetExchangeRates.date(activity)));
            if(rate==null)VaultWidgetExchangeRates.refreshIfNeeded(activity);
        };
        market.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){
                update.run();
            }
        });
        currency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){
                update.run();
            }
        });
        update.run();
        View history=VaultValueHistory.chart(activity,null);
        pane.addView(history,new LinearLayout.LayoutParams(-1,dp(164)));
        VaultNativeStyle.margin(parent,pane,12,activity);
    }

    /** Native mobile collection analytics; all money originates from saved
     * exact-printing Scryfall snapshots, never guessed card-name prices. */
    private void renderCollectionInsights(LinearLayout target){
        LinearLayout pane=column();
        pane.setPadding(dp(16),dp(14),dp(16),dp(16));
        VaultNativeStyle.surface(activity,pane,false);
        pane.addView(text("COLLECTION INTELLIGENCE",12,VaultNativeStyle.GOLD,true));
        JSONObject snapshot=VaultCollectionInsights.read(activity);
        int priced=snapshot.optInt("pricedRows",0);
        int total=snapshot.optInt("totalRows",0);
        String coverage=total>0?
            String.format(Locale.US,"%.1f%%",100.0*priced/total):"Not priced";
        pane.addView(text("Price coverage: "+coverage+" · "+priced+"/"+total+
            " physical printing entries",12,VaultNativeStyle.SUBTEXT,false));
        pane.addView(text("Scryfall quote estimates; adding cards is not a market gain.",
            11,VaultNativeStyle.SUBTEXT,false));
        android.widget.LinearLayout.LayoutParams topSpace=
            new android.widget.LinearLayout.LayoutParams(-1,dp(10));
        String[][] lists={
            {"MOST VALUABLE OWNED PRINTINGS",
                String.join("\n",VaultCollectionInsights.topCards(activity))},
            {"TOP SETS BY OWNED VALUE",
                String.join("\n",VaultCollectionInsights.topSets(activity))},
            {"MISSING PRICE QUOTES",
                String.join("\n",VaultCollectionInsights.unpriced(activity))}
        };
        for(String[] group:lists){
            View spacing=new View(activity);
            pane.addView(spacing,topSpace);
            pane.addView(text(group[0],11,VaultNativeStyle.GOLD,true));
            pane.addView(text(group[1].isEmpty()?"No verified results yet.":group[1],
                12,VaultNativeStyle.TEXT,false));
        }
        View separation=new View(activity);
        pane.addView(separation,new LinearLayout.LayoutParams(-1,dp(11)));
        pane.addView(text("DATA INTEGRITY",11,VaultNativeStyle.GOLD,true));
        TextView health=text("Checking stored inventory without modifying it…",
            12,VaultNativeStyle.SUBTEXT,false);
        pane.addView(health);
        final int audit=++auditGeneration;
        auditWorker.execute(()->{
            String result;
            try{result=VaultCollectionAudit.inspect(prefs).summary();}
            catch(Exception error){result="Audit unavailable: "+error.getMessage();}
            final String display=result;
            ui.post(()->{
                if(!closed&&audit==auditGeneration)health.setText(display);
            });
        });
        VaultNativeStyle.margin(target,pane,12,activity);
    }

    private static long copies(List<CardRow> rows){
        long value=0;for(CardRow r:rows)value+=r.qty;return value;
    }
    private void renderHome(LinearLayout content){
        card(content,"Collection overview","Your vault",
            "Native Android companion • synced data stays on this device",VaultNativeStyle.GOLD);
        card(content,"Physical collection",String.format(Locale.US,"%,d cards",copies(physical)),
            String.format(Locale.US,"%,d printing and name entries",physical.size()),VaultNativeStyle.PRIMARY);
        renderCollectionValue(content);
        renderCollectionInsights(content);
        card(content,"Arena collection",String.format(Locale.US,"%,d cards",copies(arena)),
            String.format(Locale.US,"%,d entries",arena.size()),VaultNativeStyle.GOLD);
        TextView tools=text("QUICK ACTIONS",12,VaultNativeStyle.SUBTEXT,true);
        tools.setPadding(0,dp(14),0,dp(8));
        content.addView(tools);
        Button catalogButton=button("Explore Card Catalog",true);
        VaultNativeStyle.margin(content,catalogButton,10,activity);
        catalogButton.setOnClickListener(v->show("catalog"));
        Button update=button("Check for app updates",false);
        VaultNativeStyle.margin(content,update,10,activity);
        update.setOnClickListener(v->new NativeAppUpdater(activity).check());
        Button refresh=button("Refresh collection totals",false);
        VaultNativeStyle.margin(content,refresh,10,activity);
        refresh.setOnClickListener(v->refresh());
        TextView note=text("Your vault, card catalog, collection, decks and sync each have a dedicated workspace. Advanced settings remain under Sync.",
                12,VaultNativeStyle.SUBTEXT,false);
        note.setPadding(0,dp(12),0,0);
        content.addView(note);
    }
    private void renderInventory(LinearLayout content,List<CardRow> rows){
        boolean paper=scope.equals("physical");
        card(content,paper?"Physical inventory":"Arena inventory",
            String.format(Locale.US,"%,d copies",copies(rows)),
            String.format(Locale.US,"%,d entries • read-only",rows.size()),
            paper?VaultNativeStyle.PRIMARY:VaultNativeStyle.GOLD);
        if(paper){
            renderCollectionValue(content);
            renderCollectionInsights(content);
        }
        EditText search=new EditText(activity);
        search.setHint("Search your cards");
        VaultNativeStyle.search(activity,search);
        VaultNativeStyle.margin(content,search,12,activity);
        LinearLayout result=column();
        content.addView(result);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                query=s.toString();visibleCount=PAGE_SIZE;drawRows(result,rows);
            }
            public void afterTextChanged(Editable s){}
        });
        drawRows(result,rows);
    }
    private void drawRows(LinearLayout target,List<CardRow> rows){
        target.removeAllViews();
        int matched=0,shown=0;
        String filter=query.trim().toLowerCase(Locale.ROOT);
        for(CardRow row:rows){
            if(!row.name.toLowerCase(Locale.ROOT).contains(filter))continue;
            matched++;
            if(shown>=visibleCount)continue;
            shown++;
            LinearLayout entry=VaultNativeStyle.row(activity);
            entry.setPadding(dp(14),dp(12),dp(14),dp(12));
            VaultNativeStyle.surface(activity,entry,false);
            TextView name=text(row.name,15,VaultNativeStyle.TEXT,true);
            name.setMaxLines(2);
            entry.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView count=text("×"+row.qty,14,VaultNativeStyle.PRIMARY,true);
            count.setPadding(dp(10),0,0,0);
            entry.addView(count);
            VaultNativeStyle.margin(target,entry,7,activity);
        }
        String label=String.format(Locale.US,"%,d matching entries",matched);
        TextView footer=text(label+(matched>visibleCount?" • "+visibleCount+" shown":""),12,
                VaultNativeStyle.SUBTEXT,false);
        footer.setPadding(0,dp(8),0,dp(10));target.addView(footer);
        if(matched>visibleCount){
            Button more=button("Load more cards",false);
            target.addView(more);
            more.setOnClickListener(v->{visibleCount+=PAGE_SIZE;drawRows(target,rows);});
        }
    }
    private static List<CardRow> load(SharedPreferences prefs,String key)throws Exception{
        String source=prefs.getString(key,"[]");
        JSONArray list=new JSONArray(source==null?"[]":source);
        if(list.length()>150000)throw new IllegalArgumentException("Inventory exceeds safety limit");
        ArrayList<CardRow> output=new ArrayList<>(list.length());
        for(int i=0;i<list.length();i++){
            JSONObject item=list.optJSONObject(i);if(item==null)continue;
            String name=item.optString("name","").trim();int qty=item.optInt("qty",0);
            if(!name.isEmpty()&&qty>0)output.add(new CardRow(name,qty));
        }
        output.sort(Comparator.comparing(row->row.name,String.CASE_INSENSITIVE_ORDER));
        return output;
    }
    void refresh(){
        int ticket=++readGeneration;
        subtitle.setText("Refreshing local collection…");
        new Thread(()->{
            try{
                List<CardRow> paper=load(prefs,"vault_physicalCollectionV1");
                List<CardRow> digital=load(prefs,"vault_cards");
                activity.runOnUiThread(()->{
                    if(ticket!=readGeneration||activity.isFinishing())return;
                    physical=paper;arena=digital;show(scope);
                });
            }catch(Exception error){
                activity.runOnUiThread(()->{
                    if(ticket!=readGeneration)return;
                    subtitle.setText("Inventory unavailable • open Legacy tools to restore");
                });
            }
        },"arcane-native-inventory-summary").start();
    }
}
