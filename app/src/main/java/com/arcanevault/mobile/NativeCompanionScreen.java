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
    private int visibleCount=PAGE_SIZE;
    private List<CardRow> physical=Collections.emptyList(),arena=Collections.emptyList();
    private int readGeneration;

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
    private int dp(float n){return VaultNativeStyle.dp(activity,n);}
    private LinearLayout column(){return VaultNativeStyle.column(activity);}
    private TextView text(String s,int size,int color,boolean bold){
        return VaultNativeStyle.label(activity,s,size,color,bold);
    }
    private Button button(String title,boolean selected){return VaultNativeStyle.button(activity,title,selected);}

    private String primaryScope(){
        if(scope.equals("physical")||scope.equals("arena")||scope.equals("sets"))
            return "collection";
        if(scope.equals("decks")||scope.equals("edh lab"))return "decks";
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
            addSecondaryTab("EDH Lab","edh lab");
        } else if(primary.equals("sync")){
            addSecondaryTab("Data & Sync","data & sync");
            Button legacy=button("Settings & legacy",false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.rightMargin=dp(7);
            secondaryTabs.addView(legacy,lp);
            legacy.setOnClickListener(v->{closeCatalog();openLegacy.run();});
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

    private void show(String next){
        closeCatalog();
        scope=next;
        query="";visibleCount=PAGE_SIZE;
        drawTabs();
        drawSecondaryTabs();
        stage.removeAllViews();
        if(scope.equals("sets")||scope.equals("decks")||scope.equals("shortlist")||scope.equals("data & sync")){
            workspace=NativeWorkspaceScreen.create(activity,prefs,catalog,artwork,openLegacy,deckLoader,
                deckSaver,scope.equals("data & sync")?"data":scope);
            stage.addView(workspace.view(),new FrameLayout.LayoutParams(-1,-1));
            subtitle.setText(scope.equals("sets")?"Set completion by owned edition":
                scope.equals("decks")?"Saved deck library":
                scope.equals("shortlist")?"Cards saved for later":"Backups, sync and recovery");
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
    private static long copies(List<CardRow> rows){
        long value=0;for(CardRow r:rows)value+=r.qty;return value;
    }
    private void renderHome(LinearLayout content){
        card(content,"Collection overview","Your vault",
            "Native Android companion • synced data stays on this device",VaultNativeStyle.GOLD);
        card(content,"Physical collection",String.format(Locale.US,"%,d cards",copies(physical)),
            String.format(Locale.US,"%,d printing and name entries",physical.size()),VaultNativeStyle.PRIMARY);
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
    private void refresh(){
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
