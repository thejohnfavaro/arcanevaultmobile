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
import android.widget.CheckBox;
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
    private final java.util.function.Consumer<String> openCommanderDecks;
    private final LinearLayout root,list,preview;
    private final ScrollView listScroll,previewScroll;
    private final TextView status;
    private final boolean wide;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService data=Executors.newSingleThreadExecutor();
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private int imageGeneration;
    private String query="",source="",owned="",colors="";
    private boolean filterOpen=false;
    private final LinearLayout filterPanel;
    private int page;
    private volatile boolean closed;

    NativeCommanderBrowserScreen(Activity activity,CatalogStore catalog,
            ArtworkCache artwork,Runnable openLegacy,
            java.util.function.Consumer<String> openCommanderDecks){
        this.activity=activity;this.catalog=catalog;this.artwork=artwork;
        this.openLegacy=openLegacy;
        this.openCommanderDecks=openCommanderDecks;
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
        // Filters remain collapsed on phones, so card previews keep their space.
        Button toggleFilters=VaultNativeStyle.button(activity,"Commander Filters ▾",false);
        addRow(root,toggleFilters);
        filterPanel=VaultNativeStyle.column(activity);
        filterPanel.setPadding(dp(10),dp(7),dp(10),dp(8));
        VaultNativeStyle.surface(activity,filterPanel,false);
        addRow(root,filterPanel);
        filterPanel.setVisibility(View.GONE);
        toggleFilters.setOnClickListener(v->{
            filterOpen=!filterOpen;
            filterPanel.setVisibility(filterOpen?View.VISIBLE:View.GONE);
            toggleFilters.setText(filterOpen?"Commander Filters ▴":"Commander Filters ▾");
        });
        addRow(filterPanel,VaultNativeStyle.label(activity,
            "OWNERSHIP",11,VaultNativeStyle.SUBTEXT,true));
        LinearLayout ownership=VaultNativeStyle.row(activity);
        String[] ownershipNames={"All","Owned","Physical","Arena"};
        String[] ownershipValues={"","any","physical","arena"};
        for(int i=0;i<ownershipNames.length;i++){
            final String value=ownershipValues[i];
            Button option=VaultNativeStyle.button(activity,ownershipNames[i],i==0);
            ownership.addView(option,new LinearLayout.LayoutParams(0,dp(40),1));
            option.setOnClickListener(v->{
                owned=value;page=0;load();
                for(int n=0;n<ownership.getChildCount();n++){
                    Button button=(Button)ownership.getChildAt(n);
                    button.setEnabled(!ownershipValues[n].equals(owned));
                }
            });
            option.setEnabled(i!=0);
        }
        addRow(filterPanel,ownership);
        addRow(filterPanel,VaultNativeStyle.label(activity,
            "EXACT COLOR IDENTITY",11,VaultNativeStyle.SUBTEXT,true));
        LinearLayout colorRow=VaultNativeStyle.row(activity);
        java.util.Map<String,Button> colorButtons=new java.util.LinkedHashMap<>();
        for(String symbol:new String[]{"W","U","B","R","G","C"}){
            Button choice=VaultNativeStyle.button(activity,symbol,false);
            colorButtons.put(symbol,choice);
            colorRow.addView(choice,new LinearLayout.LayoutParams(0,dp(40),1));
            choice.setContentDescription(("C".equals(symbol)?"Colorless":symbol)+
                " commander color identity");
            choice.setOnClickListener(v->{
                if("C".equals(symbol))colors="C".equals(colors)?"":"C";
                else{
                    String old="C".equals(colors)?"":colors;
                    StringBuilder next=new StringBuilder();
                    for(char c:"WUBRG".toCharArray())
                        if((String.valueOf(c).equals(symbol))!=(old.indexOf(c)>=0))
                            next.append(c);
                    colors=next.toString();
                }
                for(java.util.Map.Entry<String,Button> entry:colorButtons.entrySet()){
                    boolean selected=entry.getKey().equals("C")?
                        "C".equals(colors):colors.contains(entry.getKey());
                    entry.getValue().setAlpha(selected?1f:0.55f);
                }
                page=0;load();
            });
            choice.setAlpha(0.55f);
        }
        addRow(filterPanel,colorRow);
        Button clear=VaultNativeStyle.button(activity,"Clear commander filters",false);
        addRow(filterPanel,clear);
        clear.setOnClickListener(v->{
            colors="";owned="";page=0;
            for(Button button:colorButtons.values())button.setAlpha(0.55f);
            for(int n=0;n<ownership.getChildCount();n++){
                Button button=(Button)ownership.getChildAt(n);
                button.setEnabled(n!=0);
            }
            load();
        });
        LinearLayout workspace=new LinearLayout(activity);
        workspace.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        listScroll=VaultNativeStyle.scroll(activity);
        list=VaultNativeStyle.column(activity);
        listScroll.addView(list);
        previewScroll=VaultNativeStyle.scroll(activity);
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
        final String q=query,scope=source,ownership=owned,identity=colors;
        final int selectedPage=page;
        status.setText("Searching official commander candidates…");
        data.execute(()->{
            try{
                Map<String,String> args=new HashMap<>();
                args.put("q",q);args.put("source",scope);
                args.put("owned",ownership);args.put("colors",identity);
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
            LinearLayout row=VaultNativeStyle.row(activity);
            row.setPadding(dp(10),dp(8),dp(10),dp(8));
            VaultNativeStyle.surface(activity,row,false);
            String imageUrl=card.optString("image","");
            if(imageUrl.startsWith("https://cards.scryfall.io/")){
                ImageView thumbnail=new ImageView(activity);
                thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                thumbnail.setContentDescription("Commander artwork for "+
                    card.optString("name",""));
                LinearLayout.LayoutParams photo=new LinearLayout.LayoutParams(
                    dp(72),dp(101));
                photo.rightMargin=dp(11);
                row.addView(thumbnail,photo);
                // Only request artwork when a real official image URL exists.
                thumbnail.setTag(imageUrl);
                loadRowArt(thumbnail,imageUrl);
            }
            LinearLayout information=VaultNativeStyle.column(activity);
            TextView name=VaultNativeStyle.label(activity,
                card.optString("name","Unknown commander"),15,VaultNativeStyle.TEXT,true);
            information.addView(name);
            TextView desc=VaultNativeStyle.label(activity,
                card.optString("type","")+" · "+
                card.optString("colors","")+" · Physical ×"+
                card.optInt("physicalQty",0)+" · Arena ×"+card.optInt("arenaQty",0),
                12,VaultNativeStyle.SUBTEXT,false);
            information.addView(desc);
            row.addView(information,new LinearLayout.LayoutParams(0,-2,1));
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
        if(front.startsWith("https://cards.scryfall.io/")){
            addRow(preview,face);
            face.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(wide?300:350)));
        }
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
        Button savedDecks=VaultNativeStyle.button(activity,
            "View saved decks with this commander",true);
        savedDecks.setOnClickListener(v->openCommanderDecks.accept(name));
        addRow(preview,savedDecks);
        Button legacy=VaultNativeStyle.button(activity,
            "Copy commander name & open deck editor",false);
        legacy.setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText(
                "Commander",name));
            openLegacy.run();
        });
        addRow(preview,legacy);
        previewScroll.post(()->previewScroll.scrollTo(0,0));
    }
    private void loadRowArt(ImageView view,String url){
        images.execute(()->{
            try{
                byte[] bytes=artwork.get(url);
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=4;
                Bitmap thumb=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(thumb==null)return;
                ui.post(()->{
                    if(closed || activity.isFinishing() ||
                        !url.equals(view.getTag())) {
                        thumb.recycle();return;
                    }
                    view.setImageBitmap(thumb);
                });
            }catch(Exception ignored){ /* Text remains usable offline. */ }
        });
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
