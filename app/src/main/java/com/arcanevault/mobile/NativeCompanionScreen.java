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
    private final Handler ui=new Handler(Looper.getMainLooper());
    private NativeCatalogScreen nativeCatalog;
    private String scope="home", query="";
    private int visibleCount=PAGE_SIZE;
    private List<CardRow> physical=Collections.emptyList(),arena=Collections.emptyList();
    private int readGeneration;

    private static final class CardRow {
        final String name;final int qty;
        CardRow(String name,int qty){this.name=name;this.qty=qty;}
    }

    private NativeCompanionScreen(Activity activity,SharedPreferences prefs,
            CatalogStore catalog,ArtworkCache artwork,Runnable openLegacy) {
        this.activity=activity;
        this.prefs=prefs;this.catalog=catalog;this.artwork=artwork;this.openLegacy=openLegacy;
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

        HorizontalScrollView scroller=new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        tabs=VaultNativeStyle.row(activity);
        tabs.setPadding(dp(14),dp(4),dp(14),dp(12));
        scroller.addView(tabs);
        root.addView(scroller);
        stage=new FrameLayout(activity);
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        show("home");
        refresh();
    }

    static View create(Activity activity,SharedPreferences prefs,CatalogStore catalog,
            ArtworkCache artwork,Runnable openLegacy) {
        return new NativeCompanionScreen(activity,prefs,catalog,artwork,openLegacy).root;
    }
    private int dp(float n){return VaultNativeStyle.dp(activity,n);}
    private LinearLayout column(){return VaultNativeStyle.column(activity);}
    private TextView text(String s,int size,int color,boolean bold){
        return VaultNativeStyle.label(activity,s,size,color,bold);
    }
    private Button button(String title,boolean selected){return VaultNativeStyle.button(activity,title,selected);}

    private void drawTabs(){
        tabs.removeAllViews();
        for(String tab:new String[]{"Home","Catalog","Physical","Arena","Legacy tools"}){
            boolean active=scope.equals(tab.toLowerCase(Locale.ROOT));
            Button action=button(tab,active);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));
            lp.rightMargin=dp(8);
            tabs.addView(action,lp);
            action.setOnClickListener(v->{
                if(tab.equals("Legacy tools")){closeCatalog();openLegacy.run();return;}
                show(tab.toLowerCase(Locale.ROOT));
            });
        }
    }
    private void closeCatalog(){
        if(nativeCatalog!=null){nativeCatalog.close();nativeCatalog=null;}
    }
    private void show(String next){
        closeCatalog();
        scope=next;
        query="";visibleCount=PAGE_SIZE;
        drawTabs();
        stage.removeAllViews();
        if(scope.equals("catalog")){
            nativeCatalog=new NativeCatalogScreen(activity,catalog,artwork,prefs);
            stage.addView(nativeCatalog.view(),new FrameLayout.LayoutParams(-1,-1));
            subtitle.setText("Discover cards and printings");
            return;
        }
        subtitle.setText(scope.equals("home")?"Your collection. Anywhere.":
            scope.equals("physical")?"Paper collection":"Arena collection");
        ScrollView scroll=new ScrollView(activity);
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
        TextView note=text("Data & Sync, deck editing, and other tools are still available in Legacy tools while we finish their native screens.",
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
