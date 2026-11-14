package com.arcanevault.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import com.arcanevault.mobile.next.NativePrintingOwnership;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
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
    private final TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkWorker = Executors.newFixedThreadPool(2);
    private final AtomicInteger request = new AtomicInteger();
    private final EditText search;
    private String source = "all", owned = "all", query = "";
    private int page = 0;
    private volatile boolean disposed;

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
        LinearLayout filters = new LinearLayout(activity);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        for (String item : new String[]{"All cards", "Arena", "Not on Arena"}) {
            Button b = button(item);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.rightMargin=dp(7);filters.addView(b,lp);
            b.setOnClickListener(v -> {
                source = item.equals("All cards") ? "all" : item.equals("Arena") ? "arena" : "paper";
                page = 0; schedule();
            });
        }
        HorizontalScrollView sourceScroll=new HorizontalScrollView(activity);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.addView(filters);
        layout.addView(sourceScroll);
        LinearLayout ownership = new LinearLayout(activity);
        ownership.setOrientation(LinearLayout.HORIZONTAL);
        for (String item : new String[]{"Any Arena qty", "Arena owned", "Arena missing"}) {
            Button b = button(item);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.rightMargin=dp(7);ownership.addView(b,lp);
            b.setOnClickListener(v -> {
                owned = item.equals("Arena owned") ? "owned" : item.equals("Arena missing") ? "unowned" : "all";
                page = 0; schedule();
            });
        }
        HorizontalScrollView ownedScroll=new HorizontalScrollView(activity);
        ownedScroll.setHorizontalScrollBarEnabled(false);
        ownedScroll.addView(ownership);
        LinearLayout.LayoutParams ownedParams=new LinearLayout.LayoutParams(-1,-2);
        ownedParams.topMargin=dp(6);ownedParams.bottomMargin=dp(14);
        layout.addView(ownedScroll,ownedParams);
        ScrollView scroll = new ScrollView(activity);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        schedule();
    }

    View view() { return layout; }
    void close() {
        disposed = true;
        request.incrementAndGet();
        worker.shutdownNow();
        artworkWorker.shutdownNow();
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

    private void schedule() {
        final int id=request.incrementAndGet();
        final String q=query, src=source, scope=owned;
        final int selectedPage=page;
        status.setText("Searching your catalog…");
        worker.execute(() -> {
            try {
                Map<String,String> options=new HashMap<>();
                options.put("q",q);options.put("scope",scope);
                options.put("arena","arena".equals(src) ? "arena" : "paper".equals(src) ? "paper" : "");
                options.put("page",Integer.toString(selectedPage));
                options.put("size","40");
                JSONObject response=catalog.search(options);
                if(disposed || id!=request.get())return;
                ui.post(() -> {
                    if(disposed || id!=request.get() || activity.isFinishing())return;
                    render(response,selectedPage,src);
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

    private void render(JSONObject response,int selectedPage,String src) {
        results.removeAllViews();
        int total=response.optInt("total",0);
        JSONArray rows=response.optJSONArray("cards");
        int count=rows==null?0:rows.length();
        status.setText(total+" cards found • page "+(selectedPage+1)
                +("paper".equals(src)?" • non-Arena catalog":"arena".equals(src)?" • Arena catalog":""));
        if(total==0){
            results.addView(text("No cards found. If your catalog is empty, sync Scryfall data in Legacy tools.",14,false));
            return;
        }
        for(int i=0;i<count;i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
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
            ImageView thumb=new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumb.setContentDescription("Card artwork for "+name);
            thumb.setBackground(VaultNativeStyle.rounded(activity,VaultNativeStyle.ELEVATED,
                VaultNativeStyle.BORDER,8));
            LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(66),dp(93));
            imageParams.rightMargin=dp(14);entry.addView(thumb,imageParams);
            thumb.setImageDrawable(null);
            loadThumbnail(thumb,imageUrl);
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
            int arenaQty=row.optInt("qty",0);
            String rarityLabel=rarity.isEmpty()?"Card":rarity.substring(0,1).toUpperCase(java.util.Locale.ROOT)+rarity.substring(1);
            TextView metadata=text(rarityLabel+
                (arenaQty>0?"   •   Arena ×"+arenaQty:""),12,true);
            metadata.setTextColor(arenaQty>0?VaultNativeStyle.PRIMARY:VaultNativeStyle.GOLD);
            detail.addView(metadata);
            entry.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
            entry.setContentDescription(name+", "+type+", "+rarityLabel+
                (arenaQty>0?", Arena copies "+arenaQty:"")+". Tap to inspect");
            entry.setOnClickListener(v -> showDetails(name,type,oracle,rarity,imageUrl,arenaQty));
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
        back.setOnClickListener(v -> { page=Math.max(0,page-1); schedule(); });
        next.setOnClickListener(v -> { page++; schedule(); });
        results.addView(pager);
    }
    /** Artwork is loaded off-thread, using the existing guarded Scryfall cache. */
    private void loadThumbnail(ImageView target,String imageUrl) {
        if(!imageUrl.startsWith("https://cards.scryfall.io/"))return;
        artworkWorker.execute(() -> {
            try {
                byte[] encoded=artwork.get(imageUrl);
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=4;
                Bitmap bitmap=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed || activity.isFinishing() || target.getWindowToken()==null) {
                        bitmap.recycle();return;
                    }
                    target.setImageBitmap(bitmap);
                });
            } catch(Exception ignored) {
                // Offline users retain searchable card names and metadata.
            }
        });
    }

    private void showDetails(String name,String type,String oracle,String rarity,String imageUrl,int arenaQty) {
        LinearLayout body=VaultNativeStyle.column(activity);
        body.setPadding(dp(16),dp(12),dp(16),dp(20));
        body.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        TextView category=text("CARD INSPECTOR",11,true);
        category.setTextColor(VaultNativeStyle.PRIMARY);
        body.addView(category);

        ImageView art=new ImageView(activity);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setAdjustViewBounds(true);
        art.setContentDescription("Official card artwork for "+name);
        art.setBackground(VaultNativeStyle.rounded(activity,
            VaultNativeStyle.SURFACE,VaultNativeStyle.BORDER,12));
        LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,dp(335));
        imageParams.topMargin=dp(10);imageParams.bottomMargin=dp(12);
        body.addView(art,imageParams);
        TextView typeLabel=text(type,13,false);
        typeLabel.setTextColor(VaultNativeStyle.SUBTEXT);
        body.addView(typeLabel);
        LinearLayout oraclePanel=VaultNativeStyle.column(activity);
        oraclePanel.setPadding(dp(13),dp(10),dp(13),dp(12));
        VaultNativeStyle.surface(activity,oraclePanel,false);
        TextView oracleTitle=text("ORACLE TEXT",11,true);
        oracleTitle.setTextColor(VaultNativeStyle.GOLD);
        oraclePanel.addView(oracleTitle);
        TextView oracleText=text(oracle.isEmpty()?"No rules text":oracle,14,false);
        oracleText.setLineSpacing(dp(3),1f);
        oraclePanel.addView(oracleText);
        VaultNativeStyle.margin(body,oraclePanel,12,activity);
        TextView rarityText=text("Rarity: "+rarity+
            "    •    Arena copies: "+arenaQty,13,true);
        rarityText.setTextColor(VaultNativeStyle.PRIMARY);
        VaultNativeStyle.margin(body,rarityText,12,activity);
        Button browsePrintings=VaultNativeStyle.button(activity,
            "Browse printings & physical copies",true);
        body.addView(browsePrintings,new LinearLayout.LayoutParams(-1,dp(50)));
        browsePrintings.setOnClickListener(v->loadPrintings(name));

        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(name)
            .setView(scroll).setPositiveButton("Close",null).create();
        dialog.show();
        if(!imageUrl.startsWith("https://cards.scryfall.io/"))return;
        artworkWorker.execute(() -> {
            try {
                byte[] image=artwork.get(imageUrl);
                Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed||!dialog.isShowing()){bitmap.recycle();return;}
                    art.setImageBitmap(bitmap);
                });
            }catch(Exception ignored){
                // Metadata and Oracle text remain fully accessible while offline.
            }
        });
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
        ScrollView scroll=new ScrollView(activity);
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
        ScrollView scroll=new ScrollView(activity);
        scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(name)
            .setView(scroll).setPositiveButton("Close",null).show();
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
