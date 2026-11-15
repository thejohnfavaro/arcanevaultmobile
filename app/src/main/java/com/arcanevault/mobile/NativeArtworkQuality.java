package com.arcanevault.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Shared native card-image policy. Decode to the pixel dimensions required
 * by the view, rather than using the old forced 1/4-sized bitmap everywhere.
 * Full previews prefer Scryfall's large JPEG while tiles keep normal images.
 * Neither preference changes the on-disk card catalog or ownership records. */
final class NativeArtworkQuality {
    private static final String OFFICIAL="https://cards.scryfall.io/";
    private NativeArtworkQuality(){}

    static boolean supported(String url){
        return url!=null && url.startsWith(OFFICIAL);
    }

    static String largeUrl(String original){
        if(!supported(original))return "";
        // The normal/large path variants are Scryfall's own image endpoints.
        // Do not rewrite external URLs, other variants, or arbitrary query text.
        if(!original.contains("/normal/")||!original.endsWith(".jpg"))
            return original;
        return original.replace("/normal/","/large/");
    }

    static Bitmap decode(byte[] bytes,int targetWidth,int targetHeight){
        if(bytes==null||bytes.length==0)return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
        // Avoid accidental huge bitmap allocations, but never force a view to
        // stretch a 122px image across a 400px screen.
        if(bounds.outWidth>8192||bounds.outHeight>8192)return null;
        int desiredWidth=Math.max(80,Math.min(targetWidth,2048));
        int desiredHeight=Math.max(112,Math.min(targetHeight,2048));
        int sample=1;
        while(sample<16 && bounds.outWidth/(sample*2)>=desiredWidth &&
                bounds.outHeight/(sample*2)>=desiredHeight)
            sample*=2;
        BitmapFactory.Options decode=new BitmapFactory.Options();
        decode.inSampleSize=sample;
        decode.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,decode);
    }

    static Bitmap load(ArtworkCache cache,String url,int targetWidth,
            int targetHeight,boolean fullPreview)throws Exception {
        if(!supported(url))return null;
        if(fullPreview){
            String large=largeUrl(url);
            if(!large.equals(url)){
                try{
                    Bitmap bitmap=decode(cache.get(large),targetWidth,targetHeight);
                    if(bitmap!=null)return bitmap;
                }catch(java.io.IOException ignored){
                    // When offline, the cached normal edition is still useful.
                }
            }
        }
        return decode(cache.get(url),targetWidth,targetHeight);
    }
}
