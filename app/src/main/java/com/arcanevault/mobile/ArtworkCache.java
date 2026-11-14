package com.arcanevault.mobile;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import org.json.JSONObject;

// Small persistent, bounded, local on-demand Scryfall artwork cache.
final class ArtworkCache {
    private static final long LIMIT = 200L * 1024 * 1024;
    private final File directory;
    private final Object[] downloadLocks=new Object[32];
    private final Object cacheWrite=new Object();
    ArtworkCache(Context ctx){
        directory=new File(ctx.getFilesDir(),"artwork");directory.mkdirs();
        for(int i=0;i<downloadLocks.length;i++)downloadLocks[i]=new Object();
    }
    private URL permitted(String raw) throws Exception {
        URL u=new URL(raw);
        if(!u.getProtocol().equals("https") || !(u.getHost().equalsIgnoreCase("cards.scryfall.io") || u.getHost().equalsIgnoreCase("svgs.scryfall.io")))
            throw new IOException("Only official Scryfall image hosts are supported");
        return u;
    }
    private File file(String raw)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return new File(directory,hex.toString()+(raw.contains(".svg")?".svg":".img"));
    }
    byte[] get(String raw)throws Exception{
        // Synchronize by URL shard rather than serializing all card downloads.
        // A visible thumbnail can load while another page is being prefetched.
        URL url=permitted(raw);File destination=file(raw);
        Object gate=downloadLocks[(raw.hashCode()&Integer.MAX_VALUE)%downloadLocks.length];
        synchronized(gate){
            if(destination.isFile()){
                destination.setLastModified(System.currentTimeMillis());
                return read(destination);
            }
            HttpURLConnection conn=(HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(10000);conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent","ArcaneVaultAndroid/1.4 (local artwork cache)");
            try{
                if(conn.getResponseCode()!=200)
                    throw new IOException("Image HTTP "+conn.getResponseCode());
                String media=conn.getContentType();
                if(media==null||!(media.startsWith("image/jpeg")||
                    media.startsWith("image/png")||media.startsWith("image/webp")||
                    media.startsWith("image/svg+xml")))
                    throw new IOException("Unexpected image content type");
                byte[] bytes;
                try(InputStream in=conn.getInputStream();
                    ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] chunk=new byte[8192];int n;
                    while((n=in.read(chunk))!=-1){
                        if(out.size()+n>3_000_000)throw new IOException("Artwork too large");
                        out.write(chunk,0,n);
                    }
                    bytes=out.toByteArray();
                }
                synchronized(cacheWrite){
                    File[] files=directory.listFiles();
                    if(files!=null){
                        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
                        long total=0;for(File f:files)total+=f.length();
                        for(File f:files){
                            if(total+bytes.length<=LIMIT)break;
                            long size=f.length();if(f.delete())total-=size;
                        }
                    }
                    File temporary=new File(directory,destination.getName()+".partial");
                    try{
                        try(FileOutputStream output=new FileOutputStream(temporary)){
                            output.write(bytes);
                        }
                        try{
                            java.nio.file.Files.move(temporary.toPath(),
                                destination.toPath(),
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }catch(java.nio.file.AtomicMoveNotSupportedException ex){
                            java.nio.file.Files.move(temporary.toPath(),
                                destination.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }finally{
                        if(temporary.exists())temporary.delete();
                    }
                }
                return bytes;
            }finally{conn.disconnect();}
        }
    }
    private byte[] read(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}}
    JSONObject status() {
        synchronized(cacheWrite){
        File[] files = directory.listFiles();
        long bytes = 0;
        int count = 0;

        if (files != null) {
            for (File f : files) {
                count++;
                bytes += f.length();
            }
        }

        JSONObject result = new JSONObject();

        try {
            result.put("count", count);
            result.put("bytes", bytes);
            result.put("limitBytes", LIMIT);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(
                    "Failed to generate artwork cache status", e
            );
        }

        return result;
        }
    }
    JSONObject clear(){
        synchronized(cacheWrite){
            File[] files=directory.listFiles();
            if(files!=null)for(File f:files)f.delete();
            return status();
        }
    }
}
