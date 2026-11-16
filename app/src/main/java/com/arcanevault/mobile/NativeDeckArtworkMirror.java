package com.arcanevault.mobile;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Migration safety copy of the last VERIFIED native-visible deck/artwork data.
 * This is not yet an authoritative writable deck store. Never fall back to
 * stale mirrored decks after a failed live WebView read; the migration to a
 * single native authority requires handling every existing writer first.
 */
final class NativeDeckArtworkMirror {
    private static final int LIMIT=20_000_000;
    private NativeDeckArtworkMirror(){}

    private static File path(Context context){
        return new File(context.getFilesDir(),"native-deck-artwork-mirror-v1.json");
    }
    private static String hash(String value)throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();
        for(byte b:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    static void saveObserved(Context context,String decks,String artwork)throws Exception {
        JSONArray library=new JSONArray(decks);
        JSONObject images=new JSONObject(artwork);
        if(library.length()>150000)throw new IllegalArgumentException("Too many decks");
        String contents=new JSONObject().put("schema",1)
            .put("decks",library).put("preferredArtwork",images).toString();
        if(contents.length()>LIMIT)throw new IllegalArgumentException("Deck mirror too large");
        JSONObject envelope=new JSONObject().put("schema",1)
            .put("capturedUtc",System.currentTimeMillis())
            .put("content",new JSONObject(contents))
            .put("sha256",hash(contents));
        File target=path(context);
        AtomicFile atomic=new AtomicFile(target);
        FileOutputStream stream=null;
        try{
            stream=atomic.startWrite();
            stream.write(envelope.toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
            atomic.finishWrite(stream);stream=null;
        }finally{if(stream!=null)atomic.failWrite(stream);}
        JSONObject stored=read(context);
        if(stored.getJSONObject("content").getJSONArray("decks").length()!=library.length())
            throw new IllegalStateException("Native mirror verification failed");
    }
    static JSONObject read(Context context)throws Exception {
        File file=path(context);
        if(!file.isFile()||file.length()<20||file.length()>LIMIT)
            throw new IllegalStateException("No verified native deck mirror");
        JSONObject envelope=new JSONObject(new String(
            java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        if(envelope.getInt("schema")!=1)throw new IllegalStateException("Unsupported mirror");
        JSONObject content=envelope.getJSONObject("content");
        if(!hash(content.toString()).equals(envelope.getString("sha256")))
            throw new IllegalStateException("Native mirror hash mismatch");
        content.getJSONArray("decks");content.getJSONObject("preferredArtwork");
        return envelope;
    }
    static boolean existsVerified(Context context) {
        try{read(context);return true;}catch(Exception ignored){return false;}
    }
}
