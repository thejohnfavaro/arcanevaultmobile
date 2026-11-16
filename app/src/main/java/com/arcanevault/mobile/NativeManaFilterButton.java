package com.arcanevault.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.caverock.androidsvg.SVG;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared official Scryfall mana-symbol filter control for native catalog and
 * commander search. Never draws approximations: offline users see the
 * literal {W}/{U}/{B}/{R}/{G}/{C} token until a cached official SVG is present.
 */
final class NativeManaFilterButton extends FrameLayout {
    private static final ExecutorService WORKERS=Executors.newFixedThreadPool(2);
    private static final Map<String,Bitmap> ICON_CACHE=new ConcurrentHashMap<>();
    private static final String ALLOWED="WUBRGC";

    private final Activity activity;
    private final ImageView image;
    private final TextView fallback;
    private final String symbol;

    NativeManaFilterButton(Activity activity,ArtworkCache artwork,String code) {
        super(activity);
        String normalized=code==null?"":code.trim().toUpperCase(Locale.ROOT);
        if(normalized.length()!=1 || ALLOWED.indexOf(normalized.charAt(0))<0)
            throw new IllegalArgumentException("Unsupported mana symbol");
        this.activity=activity;
        this.symbol=normalized;
        int side=VaultNativeStyle.dp(activity,40);
        setMinimumWidth(side);
        setMinimumHeight(side);
        setFocusable(true);
        setClickable(true);
        setContentDescription(colorName(normalized)+" color identity filter");

        fallback=VaultNativeStyle.label(activity,"{"+normalized+"}",13,
            VaultNativeStyle.TEXT,true);
        fallback.setGravity(Gravity.CENTER);
        addView(fallback,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));

        image=new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int icon=VaultNativeStyle.dp(activity,29);
        addView(image,new FrameLayout.LayoutParams(icon,icon,Gravity.CENTER));
        image.setVisibility(GONE);
        setSelectedMana(false);
        loadOfficial(artwork);
    }

    static String colorName(String code) {
        switch(code) {
            case "W":return "White";
            case "U":return "Blue";
            case "B":return "Black";
            case "R":return "Red";
            case "G":return "Green";
            case "C":return "Colorless";
            default:throw new IllegalArgumentException("Unsupported mana symbol");
        }
    }

    void setSelectedMana(boolean selected) {
        setSelected(selected);
        setAlpha(selected?1f:0.76f);
        setBackground(VaultNativeStyle.rounded(activity,
            selected?VaultNativeStyle.ELEVATED:VaultNativeStyle.SURFACE,
            selected?VaultNativeStyle.PRIMARY:VaultNativeStyle.BORDER,11));
        setContentDescription(colorName(symbol)+" color identity "+
            (selected?"selected":"not selected")+", tap to toggle");
    }

    /** Same official icon, but semantically a read-only detail glyph rather
     * than an active card-filter button. */
    void setReadOnlyDisplay(){
        setSelected(false);
        setClickable(false);
        setFocusable(false);
        setAlpha(1f);
        setBackgroundColor(Color.TRANSPARENT);
        setContentDescription(colorName(symbol)+" mana symbol");
    }

    private void showBitmap(Bitmap bitmap) {
        if(bitmap==null || bitmap.isRecycled())return;
        image.setImageDrawable(new BitmapDrawable(getResources(),bitmap));
        image.setVisibility(VISIBLE);
        fallback.setVisibility(GONE);
    }

    private void loadOfficial(ArtworkCache artwork) {
        Bitmap cached=ICON_CACHE.get(symbol);
        if(cached!=null) {showBitmap(cached);return;}
        // Fixed, known-good official Scryfall card-symbol endpoints. Symbol
        // content cannot be influenced by card text, imports, or user queries.
        final String url="https://svgs.scryfall.io/card-symbols/"+symbol+".svg";
        WORKERS.execute(()->{
            try{
                byte[] bytes=artwork.get(url);
                if(bytes.length<40 || bytes.length>100000)return;
                SVG official=SVG.getFromInputStream(new ByteArrayInputStream(bytes));
                Picture picture=official.renderToPicture(96,96);
                if(picture==null)return;
                Bitmap bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
                new Canvas(bitmap).drawPicture(picture);
                Bitmap previous=ICON_CACHE.putIfAbsent(symbol,bitmap);
                if(previous!=null){bitmap.recycle();bitmap=previous;}
                Bitmap ready=bitmap;
                post(()->showBitmap(ready));
            }catch(Exception ignored){
                // Keep the readable official {token} fallback while offline.
            }
        });
    }
}
