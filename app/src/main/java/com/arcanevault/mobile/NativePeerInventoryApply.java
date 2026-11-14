package com.arcanevault.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Crash-recoverable physical/Arena peer writeback. Never alters deck/artwork storage. */
final class NativePeerInventoryApply {
    static final String PENDING="peer_sync_pending_recovery_v1";
    static final String LAST_BACKUP="peer_sync_last_inventory_recovery_v1";
    static final String PAPER="vault_physicalCollectionV1";
    static final String ARENA="vault_cards";

    private NativePeerInventoryApply(){}

    private static File folder(Context c){
        File dir=new File(c.getFilesDir(),"peer-recovery");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create recovery folder");
        return dir;
    }
    private static File backup(Context c,String filename){
        if(filename==null||!filename.matches("peer-[a-f0-9-]{36}\\.json"))
            throw new IllegalStateException("Invalid recovery filename");
        return new File(folder(c),filename);
    }
    private static void mirror(CatalogStore catalog,String arena,String paper)throws Exception {
        catalog.ownership(new JSONObject().put("cards",new JSONArray(arena)));
        catalog.physicalOwnership(new JSONObject().put("cards",new JSONArray(paper)));
    }

    /** Called during app startup before native catalog UI is displayed.
     * A pending commit is always restored rather than resumed blindly. */
    static void recoverIfPending(Context context,SharedPreferences prefs,CatalogStore catalog)throws Exception {
        String filename=prefs.getString(PENDING,null);
        if(filename==null)return;
        File file=backup(context,filename);
        JSONObject before=new JSONObject(
            new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        String oldArena=before.getJSONArray("arena").toString();
        String oldPhysical=before.getJSONArray("physical").toString();
        if(!prefs.edit().putString(ARENA,oldArena).putString(PAPER,oldPhysical).commit())
            throw new IllegalStateException("Cannot restore peer recovery inventories");
        mirror(catalog,oldArena,oldPhysical);
        if(!prefs.edit().remove(PENDING).commit())
            throw new IllegalStateException("Could not complete interrupted peer recovery");
    }

    static String apply(Context context,SharedPreferences prefs,CatalogStore catalog,
            JSONObject local,JSONObject candidate)throws Exception {
        JSONObject before=local.getJSONObject("data");
        JSONObject target=candidate.getJSONObject("candidate");
        // Until deck/WebView edits support an atomic journal with collection
        // and SQLite, refuse to apply ANY peer deck/artwork update on Android.
        if(!canonicalEqual(before.getJSONArray("deckLibrary"),target.getJSONArray("deckLibrary"))||
            !canonicalEqual(before.getJSONObject("preferredArtwork"),
                target.getJSONObject("preferredArtwork")))
            throw new IllegalStateException(
                "Peer changes to decks/artwork require Windows apply; Android inventory only.");
        String oldArena=prefs.getString(ARENA,"[]");
        String oldPhysical=prefs.getString(PAPER,"[]");
        if(!canonicalEqual(new JSONArray(oldArena),before.getJSONArray("cards")) ||
            !canonicalEqual(new JSONArray(oldPhysical),before.getJSONArray("physicalCards")))
            throw new IllegalStateException("Inventory changed since review; compare again.");
        String newArena=target.getJSONArray("cards").toString();
        String newPhysical=target.getJSONArray("physicalCards").toString();
        if(oldArena.equals(newArena)&&oldPhysical.equals(newPhysical))
            throw new IllegalStateException("No inventory differences to apply.");
        if(newArena.length()+newPhysical.length()>20_000_000)
            throw new IllegalArgumentException("Inventory exceeds Android write safety limit");
        JSONArray physicalRows=new JSONArray(newPhysical);
        JSONArray arenaRows=new JSONArray(newArena);
        if(physicalRows.length()>150000||arenaRows.length()>150000)
            throw new IllegalArgumentException("Too many inventory entries");

        String filename="peer-"+UUID.randomUUID().toString()+".json";
        File output=backup(context,filename);
        AtomicFile file=new AtomicFile(output);
        FileOutputStream stream=null;
        try{
            stream=file.startWrite();
            stream.write(new JSONObject().put("physical",new JSONArray(oldPhysical))
                .put("arena",new JSONArray(oldArena)).toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
            file.finishWrite(stream);stream=null;
        }finally{if(stream!=null)file.failWrite(stream);}
        // Commit the journal marker BEFORE writing either inventory.
        if(!prefs.edit().putString(PENDING,filename)
                .putString(LAST_BACKUP,filename).commit())
            throw new IllegalStateException("Recovery marker could not be persisted");
        try{
            if(!prefs.edit().putString(ARENA,newArena).putString(PAPER,newPhysical).commit())
                throw new IllegalStateException("Failed to commit inventory changes");
            mirror(catalog,newArena,newPhysical);
            if(!prefs.edit().remove(PENDING).commit())
                throw new IllegalStateException("Could not close recovery transaction");
        }catch(Exception failure){
            try{recoverIfPending(context,prefs,catalog);}
            catch(Exception rollback){
                throw new IllegalStateException("Peer apply failed and recovery is pending. "+
                    "Keep the app installed; restart to recover.",rollback);
            }
            throw failure;
        }
        return output.getAbsolutePath();
    }

    private static boolean canonicalEqual(Object a,Object b){
        // JSONArray/JSONObject serialization preserves record ordering, but
        // may normalize member ordering. These values are obtained from the
        // local export in the same Android process, so string comparisons are
        // sufficient for the optimistic check and fail closed if reordered.
        return a.toString().equals(b.toString());
    }
}
