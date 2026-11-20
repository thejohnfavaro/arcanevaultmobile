package com.arcanevault.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.widget.TextView;
import com.caverock.androidsvg.SVG;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-line official MTG card symbols inside Android Oracle text.
 * Unknown tokens remain readable, verbatim; no fabricated mana icons.
 * Images come only from the fixed official Scryfall symbol host and are
 * downloaded through the existing artwork cache off the UI thread.
 */
final class NativeOracleSymbols {
    private static final Pattern PIP=Pattern.compile("\\{([^{}]{1,12})\\}");
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final Map<String,Bitmap> CACHE=new HashMap<>();
    // Only render documented MTG symbol notation. Do not fetch arbitrary
    // brace-delimited words from imported rules text.
    private static boolean supported(String key) {
        if(key.matches("[0-9]{1,2}") && Integer.parseInt(key)<=20)return true;
        if(key.equals("100")||key.equals("1000000"))return true;
        if(key.matches("[WUBRGCXYZTQESP]")||key.equals("CHAOS")||
            key.equals("PW")||key.equals("A")||key.equals("HW")||key.equals("HR"))return true;
        if(key.matches("[WUBRG]/P")||key.matches("[2C]/[WUBRG]"))return true;
        String pairs="W/U,W/B,U/B,U/R,B/R,B/G,R/G,R/W,G/W,G/U";
        if(pairs.contains(key) && java.util.Arrays.asList(pairs.split(",")).contains(key))
            return true;
        if(key.endsWith("/P")){
            String pair=key.substring(0,key.length()-2);
            return java.util.Arrays.asList(pairs.split(",")).contains(pair);
        }
        return false;
    }
    private NativeOracleSymbols(){}

    static TextView render(Activity activity,ArtworkCache artwork,String oracle,int sizeSp) {
        TextView label=VaultNativeStyle.label(activity,oracle,sizeSp,VaultNativeStyle.TEXT,false);
        if(oracle==null||!PIP.matcher(oracle).find())return label;
        // Keep the original readable text until official symbols are available.
        EXEC.execute(()->{
            try{
                Matcher scan=PIP.matcher(oracle);
                Map<String,Bitmap> local=new HashMap<>();
                while(scan.find()){
                    String key=scan.group(1).toUpperCase(Locale.ROOT);
                    if(!supported(key)||local.containsKey(key))continue;
                    Bitmap bitmap;
                    synchronized(CACHE){bitmap=CACHE.get(key);}
                    if(bitmap==null){
                        byte[] bytes=artwork.get("https://svgs.scryfall.io/card-symbols/"+key.replace("/","")+".svg");
                        if(bytes==null||bytes.length<40||bytes.length>100000)continue;
                        SVG svg=SVG.getFromInputStream(new ByteArrayInputStream(bytes));
                        Picture picture=svg.renderToPicture(96,96);
                        if(picture==null)continue;
                        bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
                        new Canvas(bitmap).drawPicture(picture);
                        synchronized(CACHE){CACHE.put(key,bitmap);}
                    }
                    local.put(key,bitmap);
                }
                if(local.isEmpty())return;
                activity.runOnUiThread(()->{
                    // Avoid overwriting text on a recycled or detached inspector.
                    if(!label.isAttachedToWindow()||!oracle.equals(label.getText().toString()))return;
                    SpannableStringBuilder styled=new SpannableStringBuilder(oracle);
                    Matcher replace=PIP.matcher(oracle);
                    int icon=Math.round(label.getTextSize()*1.15f);
                    while(replace.find()){
                        Bitmap source=local.get(replace.group(1).toUpperCase(Locale.ROOT));
                        if(source==null)continue;
                        BitmapDrawable drawable=new BitmapDrawable(activity.getResources(),source);
                        drawable.setBounds(0,0,icon,icon);
                        styled.setSpan(new ImageSpan(drawable,ImageSpan.ALIGN_BOTTOM),
                            replace.start(),replace.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    label.setText(styled,TextView.BufferType.SPANNABLE);
                });
            }catch(Exception ignored){
                // Official glyphs are optional: keep Oracle text legible offline.
            }
        });
        return label;
    }
}
