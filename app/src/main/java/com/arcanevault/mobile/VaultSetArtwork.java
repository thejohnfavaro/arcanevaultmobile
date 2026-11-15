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

    /** Warm icon before cover art, so the set symbol appears promptly even
     * when a larger crop download is slow or unavailable. */
    static void prefetchSymbol(Context context,String code){
        String symbol=setSymbolUrl(code);
        if(symbol.isEmpty())return;
        try{new ArtworkCache(context).get(symbol);}
        catch(Exception error){
            android.util.Log.w("ArcaneVault","Set icon unavailable: "+code,error);
        }
    }
    static void prefetchArt(Context context,String url){
        if(url==null||!url.startsWith("https://cards.scryfall.io/"))return;
        ArtworkCache artwork=new ArtworkCache(context);
        String crop=cropUrl(url);
        boolean hasCrop=false;
        if(!crop.isEmpty())try{
            artwork.get(crop);
            hasCrop=true;
        }catch(Exception error){
            android.util.Log.w("ArcaneVault","Set art crop unavailable",error);
        }
        if(!hasCrop)try{
            // Not every printing supports the constructed art_crop URL.
            // The original normal image is a legitimate cached fallback.
            artwork.get(url);
        }catch(Exception error){
            android.util.Log.w("ArcaneVault","Set card art unavailable",error);
        }
    }
    static void prefetch(Context context,String url,String code){
        prefetchSymbol(context,code);
        prefetchArt(context,url);
    }
}
