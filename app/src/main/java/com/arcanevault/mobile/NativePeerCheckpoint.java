package com.arcanevault.mobile;

import android.content.Context;
import android.util.AtomicFile;
import com.arcanevault.mobile.next.NativePeerSync;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Locally remembered, user-confirmed matching record snapshot. This is
 * NOT authenticated pairing and cannot authorize automatic LAN writes.
 */
final class NativePeerCheckpoint {
    private NativePeerCheckpoint(){}
    private static File location(Context context,String device)throws Exception {
        UUID id=UUID.fromString(device);
        if(id.equals(new UUID(0,0)))throw new IllegalArgumentException("Empty peer ID");
        File folder=new File(context.getFilesDir(),"peer-checkpoints");
        if(!folder.exists()&&!folder.mkdirs())
            throw new IllegalStateException("Cannot create peer checkpoint directory");
        return new File(folder,id.toString()+".json");
    }
    static boolean exists(Context context,String peerDevice) {
        try{return location(context,peerDevice).isFile();}
        catch(Exception e){return false;}
    }
    static JSONObject load(Context context,String peerDevice)throws Exception {
        File file=location(context,peerDevice);
        if(!file.isFile()||file.length()<20||file.length()>NativePeerSync.MAX_BYTES)
            throw new IllegalStateException("Peer checkpoint missing or too large");
        JSONObject snapshot=new JSONObject(
            new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        if(!peerDevice.equalsIgnoreCase(snapshot.getString("deviceId")))
            throw new IllegalStateException("Peer checkpoint identity mismatch");
        return snapshot;
    }
    static String saveIdentical(Context context,JSONObject local,
            JSONObject received)throws Exception {
        String peer=received.getString("deviceId");
        if(peer.equalsIgnoreCase(local.getString("deviceId")))
            throw new IllegalArgumentException("Cannot checkpoint this same device");
        JSONObject preview=NativePeerSync.preview(local,received);
        if(preview.getJSONArray("changes").length()!=0)
            throw new IllegalStateException("Peer inventories/decks/artwork are different");
        byte[] bytes=received.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length<20||bytes.length>NativePeerSync.MAX_BYTES)
            throw new IllegalArgumentException("Baseline exceeds 30 MiB");
        File target=location(context,peer);
        if(target.exists()){
            File archive=new File(target.getParentFile(),
                target.getName()+".history-"+System.currentTimeMillis()+".json");
            java.nio.file.Files.copy(target.toPath(),archive.toPath());
        }
        AtomicFile atomic=new AtomicFile(target);
        FileOutputStream stream=null;
        try {
            stream=atomic.startWrite();
            stream.write(bytes);
            stream.getFD().sync();
            atomic.finishWrite(stream);stream=null;
        } finally {
            if(stream!=null)atomic.failWrite(stream);
        }
        // Confirm readable persisted data before telling the user it succeeded.
        JSONObject stored=load(context,peer);
        if(NativePeerSync.preview(local,stored).getJSONArray("changes").length()!=0)
            throw new IllegalStateException("Stored baseline failed verification");
        return target.getAbsolutePath();
    }
}
