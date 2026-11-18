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
    /** Only confirmed matching checkpoints qualify as remembered peers.
     * No credentials, transport URLs or implicit auto-sync state are stored. */
    static String overview(Context context) {
        File folder=new File(context.getFilesDir(),"peer-checkpoints");
        File[] files=folder.listFiles((dir,name)->name.matches(
            "[a-fA-F0-9-]{36}\\.json"));
        if(files==null||files.length==0)
            return "No matching Windows/Android peer checkpoint yet. "+
                "Complete a reviewed sync, then verify the matching baseline.";
        int verified=0;
        long newest=0;
        for(File file:files){
            try {
                String name=file.getName().substring(0,36);
                load(context,name);
                verified++;
                newest=Math.max(newest,file.lastModified());
            }catch(Exception ignored){}
        }
        if(verified==0)return "No verified peer checkpoints available.";
        String date=java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT)
            .format(new java.util.Date(newest));
        return verified+" confirmed peer checkpoint(s). Last verified file: "+date+
            ". Transfers still require a one-time link and explicit review.";
    }

    static String description(Context context,String peerDevice) {
        try {
            JSONObject snapshot=load(context,peerDevice);
            File file=location(context,peerDevice);
            String when=java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT)
                .format(new java.util.Date(file.lastModified()));
            String revision=snapshot.optString("revision","");
            String shortRevision=revision.length()>=12?revision.substring(0,12):"unknown";
            return "Verified checkpoint: "+when+"\nPeer: "+
                peerDevice.substring(0,Math.min(8,peerDevice.length()))+
                "…\nRevision: "+shortRevision+
                "\nThe checkpoint permits comparison only. Review and approve"+
                " changes before applying them.";
        }catch(Exception invalid){
            return "The saved peer checkpoint cannot be validated. "+
                "Choose a known shared baseline file instead.";
        }
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
        String actualDevice=NativePeerSync.verifiedPeerDevice(snapshot);
        if(!peerDevice.equalsIgnoreCase(actualDevice))
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
