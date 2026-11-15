package com.arcanevault.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import com.caverock.androidsvg.SVG;
import java.io.ByteArrayInputStream;
import java.util.Locale;

/** Cache-only official representative art and set symbols for home screen.
 * Any network prefetch is explicit when the user configures a widget; periodic
 * launcher refreshes never issue network requests. */
final class VaultSetArtwork {
    private VaultSetArtwork(){}

    static String cropUrl(String original){
        if(original==null||!original.startsWith("https://cards.scryfall.io/")||
                !original.contains("/normal/"))return "";
        return original.replace("/normal/","/art_crop/");
    }
    static String setSymbolUrl(String code){
        if(code==null||!code.matches("[A-Za-z0-9]{2,12}"))return "";
        return "https://svgs.scryfall.io/sets/"+
            code.toLowerCase(Locale.ROOT)+".svg";
    }

    static Bitmap cachedArt(Context context,String original,int width,int height){
        if(original==null||!original.startsWith("https://cards.scryfall.io/"))
            return null;
        try{
            ArtworkCache cache=new ArtworkCache(context);
            String crop=cropUrl(original);
            byte[] bytes=crop.isEmpty()?null:cache.getCached(crop);
            if(bytes==null)bytes=cache.getCached(original);
            return NativeArtworkQuality.decode(bytes,Math.max(100,width),Math.max(100,height));
        }catch(Exception ignored){return null;}
    }

    static Bitmap cachedSymbol(Context context,String code){
        String url=setSymbolUrl(code);
        if(url.isEmpty())return null;
        try{
            byte[] bytes=new ArtworkCache(context).getCached(url);
            if(bytes==null||bytes.length<40||bytes.length>100000)return null;
            SVG official=SVG.getFromInputStream(new ByteArrayInputStream(bytes));
            Picture image=official.renderToPicture(112,112);
            if(image==null)return null;
            Bitmap bitmap=Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888);
            new Canvas(bitmap).drawPicture(image);
            return bitmap;
        }catch(Exception ignored){return null;}
    }

    static void prefetch(Context context,String url,String code){
        try{
            ArtworkCache artwork=new ArtworkCache(context);
            String crop=cropUrl(url);
            if(!crop.isEmpty())artwork.get(crop);
        }catch(Exception ignored){}
        try{
            String symbol=setSymbolUrl(code);
            if(!symbol.isEmpty())new ArtworkCache(context).get(symbol);
        }catch(Exception ignored){}
    }
}
