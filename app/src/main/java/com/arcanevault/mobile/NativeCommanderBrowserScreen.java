package com.arcanevault.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

/** Responsive native EDH commander browser. Local catalog/ownership are read
 * only; editing continues in the existing deck tools until native editing
 * receives its own transactional implementation. */
final class NativeCommanderBrowserScreen {
    private final Activity activity;
    private final CatalogStore catalog;
    private final ArtworkCache artwork;
    private final Runnable openLegacy;
    private final LinearLayout root,list,preview;
    private final ScrollView listScroll,previewScroll;
    private final TextView status;
    private final boolean wide;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService data=Executors.newSingleThreadExecutor();
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private int imageGeneration;
    private String query="",source="";
    private int page;
    private volatile boolean closed;

    NativeCommanderBrowserScreen(Activity activity,CatalogStore catalog,
            ArtworkCache artwork,Runnable openLegacy){
        this.activity=activity;this.catalog=catalog;this.artwork=artwork;
        this.openLegacy=openLegacy;
        wide=activity.getResources().getConfiguration().screenWidthDp>=700;
        root=VaultNativeStyle.column(activity);
        root.setPadding(dp(14),dp(8),dp(14),dp(12));
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        root.addView(VaultNativeStyle.label(activity,"EDH Lab · Commander Browser",
            23,VaultNativeStyle.TEXT,true));
        status=VaultNativeStyle.label(activity,"Discovering eligible commanders…",
            12,VaultNativeStyle.SUBTEXT,false);
        root.addView(status);
        EditText search=new EditText(activity);
        VaultNativeStyle.search(activity,search);
        search.setSingleLine(true);
        search.setHint("Search commander name or rules text");
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                query=s.toString();page=0;load();
            }
            public void afterTextChanged(Editable s){}
        });
        LinearLayout scopes=VaultNativeStyle.row(activity);
        Button all=VaultNativeStyle.button(activity,"All Commander Cards",true);
        Button arena=VaultNativeStyle.button(activity,"Arena Available",false);
        scopes.addView(all,new LinearLayout.LayoutParams(0,dp(43),1));
        scopes.addView(arena,new LinearLayout.LayoutParams(0,dp(43),1));
        all.setOnClickListener(v->{source="";page=0;load();
            all.setEnabled(false);arena.setEnabled(true);});
        arena.setOnClickListener(v->{source="arena";page=0;load();
            arena.setEnabled(false);all.setEnabled(true);});
        root.addView(scopes);
        LinearLayout workspace=new LinearLayout(activity);
        workspace.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        listScroll=new ScrollView(activity);
        list=VaultNativeStyle.column(activity);
        listScroll.addView(list);
        previewScroll=new ScrollView(activity);
        preview=VaultNativeStyle.column(activity);
        preview.setPadding(dp(12),dp(8),dp(12),dp(14));
        previewScroll.addView(preview);
        if(wide){
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);
            left.rightMargin=dp(9);
            workspace.addView(listScroll,left);
            workspace.addView(previewScroll,new LinearLayout.LayoutParams(0,-1,1));
        }else{
            workspace.addView(listScroll,new LinearLayout.LayoutParams(-1,-1));
            workspace.addView(previewScroll,new LinearLayout.LayoutParams(-1,-1));
            previewScroll.setVisibility(View.GONE);
        }
        preview.addView(VaultNativeStyle.label(activity,
            "Choose a commander to preview its card, Oracle text and ownership.",
            13,VaultNativeStyle.SUBTEXT,false));
        load();
    }

    View view(){return root;}
    void close(){closed=true;generation.incrementAndGet();imageGeneration++;
        data.shutdownNow();images.shutdownNow();}
    private int dp(float n){return VaultNativeStyle.dp(activity,n);}
    private void addRow(LinearLayout target,View view){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(8);target.addView(view,lp);
    }
    private void load(){
        final int request=generation.incrementAndGet();
        final String q=query,scope=source;
        final int selectedPage=page;
        status.setText("Searching official commander candidates…");
        data.execute(()->{
            try{
                Map<String,String> args=new HashMap<>();
                args.put("q",q);args.put("source",scope);
                args.put("page",Integer.toString(selectedPage));
                args.put("size","35");
                JSONObject results=catalog.commanders(args);
                ui.post(()->{
                    if(closed||activity.isFinishing()||request!=generation.get())return;
                    render(results,selectedPage);
                });
            }catch(Exception error){
                ui.post(()->{
                    if(!closed&&request==generation.get())
                        status.setText("Commander browser unavailable: "+error.getMessage());
                });
            }
        });
    }
    private void render(JSONObject result,int selectedPage){
        list.removeAllViews();
        JSONArray cards=result.optJSONArray("cards");
        int count=cards==null?0:cards.length(),total=result.optInt("total",0);
        status.setText(total+" eligible commander cards · page "+(selectedPage+1));
        if(total==0)addRow(list,VaultNativeStyle.label(activity,
            "No commanders found. Refresh Scryfall catalog metadata in Data & Sync.",
            13,VaultNativeStyle.SUBTEXT,false));
        for(int i=0;i<count;i++){
            JSONObject card=cards.optJSONObject(i);
            if(card==null)continue;
            LinearLayout row=VaultNativeStyle.column(activity);
            row.setPadding(dp(12),dp(9),dp(12),dp(9));
            VaultNativeStyle.surface(activity,row,false);
            TextView name=VaultNativeStyle.label(activity,
                card.optString("name","Unknown commander"),15,VaultNativeStyle.TEXT,true);
            row.addView(name);
            TextView desc=VaultNativeStyle.label(activity,
                card.optString("type","")+" · "+
                card.optString("colors","")+" · Physical ×"+
                card.optInt("physicalQty",0)+" · Arena ×"+card.optInt("arenaQty",0),
                12,VaultNativeStyle.SUBTEXT,false);
            row.addView(desc);
            row.setOnClickListener(v->showPreview(card));
            row.setContentDescription(card.optString("name","")+
                ". Tap to preview commander card.");
            addRow(list,row);
        }
        LinearLayout navigation=VaultNativeStyle.row(activity);
        Button previous=VaultNativeStyle.button(activity,"Previous",false);
        Button next=VaultNativeStyle.button(activity,"Next",false);
        previous.setEnabled(selectedPage>0);
        next.setEnabled((selectedPage+1)*35<total);
        navigation.addView(previous,new LinearLayout.LayoutParams(0,dp(46),1));
        navigation.addView(next,new LinearLayout.LayoutParams(0,dp(46),1));
        previous.setOnClickListener(v->{page--;load();listScroll.post(()->listScroll.scrollTo(0,0));});
        next.setOnClickListener(v->{page++;load();listScroll.post(()->listScroll.scrollTo(0,0));});
        addRow(list,navigation);
    }

    private void showPreview(JSONObject card){
        imageGeneration++;
        preview.removeAllViews();
        if(!wide){
            listScroll.setVisibility(View.GONE);
            previewScroll.setVisibility(View.VISIBLE);
            Button back=VaultNativeStyle.button(activity,"← Back to commanders",false);
            back.setOnClickListener(v->{
                imageGeneration++;
                previewScroll.setVisibility(View.GONE);
                listScroll.setVisibility(View.VISIBLE);
            });
            addRow(preview,back);
        }
        String name=card.optString("name","Unknown commander");
        addRow(preview,VaultNativeStyle.label(activity,name,21,VaultNativeStyle.TEXT,true));
        FrameLayout face=new FrameLayout(activity);
        ImageView image=new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        face.addView(image,new FrameLayout.LayoutParams(-1,-1));
        String front=card.optString("image",""),reverse=card.optString("imageBack","");
        loadArt(image,front,imageGeneration);
        if(front.startsWith("https://cards.scryfall.io/") &&
            reverse.startsWith("https://cards.scryfall.io/") && !front.equals(reverse)){
            final boolean[] showingReverse={false};
            Button flip=VaultNativeStyle.button(activity,"⟳",false);
            flip.setTextSize(19);
            flip.setContentDescription("Flip commander card to alternate face");
            FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(
                dp(44),dp(44),Gravity.BOTTOM|Gravity.RIGHT);
            position.setMargins(dp(5),dp(5),dp(5),dp(5));
            face.addView(flip,position);
            flip.setOnClickListener(v->{
                showingReverse[0]=!showingReverse[0];
                image.setImageDrawable(null);
                loadArt(image,showingReverse[0]?reverse:front,++imageGeneration);
                flip.setContentDescription("Show "+
                    (showingReverse[0]?"front":"alternate")+" commander card face");
            });
        }
        addRow(preview,face);
        face.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(wide?300:350)));
        addRow(preview,VaultNativeStyle.label(activity,
            card.optString("type",""),13,VaultNativeStyle.TEXT,true));
        addRow(preview,VaultNativeStyle.label(activity,
            "Mana value: "+card.optDouble("mv",0)+" · Rarity: "+
            card.optString("rarity",""),12,VaultNativeStyle.GOLD,true));
        addRow(preview,VaultNativeStyle.label(activity,
            "COLOR IDENTITY · "+(card.optString("colors","").isEmpty()?
                "COLORLESS":card.optString("colors","")),
            12,VaultNativeStyle.PRIMARY,true));
        addRow(preview,VaultNativeStyle.label(activity,
            "Physical ×"+card.optInt("physicalQty",0)+
            " · Arena ×"+card.optInt("arenaQty",0)+
            " (name-level inventory; not verified edition ownership)",
            12,VaultNativeStyle.SUBTEXT,false));
        addRow(preview,VaultNativeStyle.label(activity,"ORACLE TEXT",
            12,VaultNativeStyle.GOLD,true));
        TextView oracle=VaultNativeStyle.label(activity,
            card.optString("oracle",""),14,VaultNativeStyle.TEXT,false);
        oracle.setLineSpacing(dp(3),1f);
        addRow(preview,oracle);
        Button open=VaultNativeStyle.button(activity,
            "Copy commander name & open deck tools",true);
        open.setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText(
                "Commander",name));
            openLegacy.run();
        });
        addRow(preview,open);
        previewScroll.post(()->previewScroll.scrollTo(0,0));
    }
    private void loadArt(ImageView view,String url,int version){
        if(!url.startsWith("https://cards.scryfall.io/"))return;
        images.execute(()->{
            try{
                byte[] bytes=artwork.get(url);
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=2;
                Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(image==null)return;
                ui.post(()->{
                    if(closed||activity.isFinishing()||version!=imageGeneration||
                        view.getWindowToken()==null){image.recycle();return;}
                    view.setImageBitmap(image);
                });
            }catch(Exception ignored){ /* Commander metadata remains browsable offline. */ }
        });
    }
}
