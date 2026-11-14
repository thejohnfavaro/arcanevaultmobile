package com.arcanevault.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
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
    private final ArtworkCache artwork;
    private final LinearLayout layout;
    private final LinearLayout results;
    private final TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger request = new AtomicInteger();
    private final EditText search;
    private String source = "all", owned = "all", query = "";
    private int page = 0;
    private volatile boolean disposed;

    NativeCatalogScreen(Activity activity, CatalogStore catalog, ArtworkCache artwork) {
        this.activity = activity;
        this.catalog = catalog;
        this.artwork = artwork;
        layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14),dp(8),dp(14),dp(12));
        TextView title = text("Card Catalog",24,true);
        layout.addView(title);
        status = text("Searching on-device Scryfall catalog…",12,false);
        status.setTextColor(MUTED);
        layout.addView(status);
        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search card name, type or Oracle text");
        layout.addView(search);
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
            filters.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
            b.setOnClickListener(v -> {
                source = item.equals("All cards") ? "all" : item.equals("Arena") ? "arena" : "paper";
                page = 0; schedule();
            });
        }
        layout.addView(filters);
        LinearLayout ownership = new LinearLayout(activity);
        ownership.setOrientation(LinearLayout.HORIZONTAL);
        for (String item : new String[]{"Any Arena qty", "Arena owned", "Arena missing"}) {
            Button b = button(item);
            ownership.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));
            b.setOnClickListener(v -> {
                owned = item.equals("Arena owned") ? "owned" : item.equals("Arena missing") ? "unowned" : "all";
                page = 0; schedule();
            });
        }
        layout.addView(ownership);
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
    }
    private int dp(float n) { return (int)(activity.getResources().getDisplayMetrics().density*n+.5f); }
    private TextView text(String value,int size,boolean bold) {
        TextView t=new TextView(activity);
        t.setText(value); t.setTextSize(size); t.setTextColor(TEXT);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(dp(8),dp(10),dp(8),dp(10));
        return t;
    }
    private Button button(String value) {
        Button b=new Button(activity);b.setAllCaps(false);b.setText(value);b.setTextSize(11);
        b.setPadding(0,0,0,0); return b;
    }

    private void schedule() {
        final int id=request.incrementAndGet();
        final String q=query, src=source, scope=owned;
        final int selectedPage=page;
        status.setText("Searching…");
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
        status.setText(total+" matching cards • page "+(selectedPage+1)
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
            LinearLayout entry=new LinearLayout(activity);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setPadding(dp(12),dp(8),dp(12),dp(8));
            entry.setBackgroundColor(PANEL);
            entry.addView(text(name+"  ·  "+rarity,16,true));
            TextView sub=text(type+"  •  "+colors,12,false);
            sub.setTextColor(MUTED);
            entry.addView(sub);
            String imageUrl=meta==null?"":meta.optString("image","");
            entry.setOnClickListener(v -> showDetails(name,type,oracle,rarity,imageUrl));
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
        next.setEnabled((selectedPage+1)*40<total);
        back.setOnClickListener(v -> { page=Math.max(0,page-1); schedule(); });
        next.setOnClickListener(v -> { page++; schedule(); });
        results.addView(pager);
    }
    private void showDetails(String name,String type,String oracle,String rarity,String imageUrl) {
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(4),dp(16),dp(8));
        ImageView art=new ImageView(activity);
        art.setAdjustViewBounds(true);
        art.setMaxHeight(dp(420));
        art.setContentDescription("Official card artwork for "+name);
        body.addView(art,new LinearLayout.LayoutParams(-1,dp(320)));
        TextView description=text(type+"\n\n"+oracle+"\n\nRarity: "+rarity,14,false);
        body.addView(description);
        ScrollView scroll=new ScrollView(activity);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(name).setView(scroll)
            .setPositiveButton("Close",null).create();
        dialog.show();
        if(!imageUrl.startsWith("https://cards.scryfall.io/"))return;
        worker.execute(() -> {
            try {
                byte[] image=artwork.get(imageUrl);
                Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length);
                if(bitmap==null)return;
                ui.post(() -> {
                    if(disposed || !dialog.isShowing()){ bitmap.recycle(); return; }
                    art.setImageBitmap(bitmap);
                });
            } catch(Exception ignored) {
                // Oracle text remains readable when artwork is offline.
            }
        });
    }

}
