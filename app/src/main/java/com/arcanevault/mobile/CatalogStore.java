package com.arcanevault.mobile;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/** On-device searchable Scryfall printing catalog. No network service beyond Scryfall. */
final class CatalogStore extends SQLiteOpenHelper {
    private final Context application;
    private final AtomicBoolean syncing=new AtomicBoolean(false);
    private volatile String phase="Not downloaded", error="", updatedAt=null;
    private volatile long imported=0;
    private volatile int total=0;
    // Serve progress from memory. A COUNT(*) here can block behind the large
    // SQLite bulk-import transaction and make the Android UI appear frozen.
    private volatile int indexedPrintings=0;
    private volatile long lastProgressAtMs=0L, downloadedBytes=0L, expectedBytes=0L;
    CatalogStore(Context context) {
        super(context,"arcane_catalog.db",null,3);
        application=context.getApplicationContext();
        SQLiteDatabase db=getWritableDatabase();
        total=count(db,"cards");
        indexedPrintings=count(db,"variants");
        try(Cursor c=db.rawQuery("SELECT value FROM state WHERE key='updated'",null)) {if(c.moveToFirst())updatedAt=c.getString(0);}
        phase=total>0?"Ready":"Not downloaded";
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE cards(name TEXT PRIMARY KEY COLLATE NOCASE,type TEXT NOT NULL,oracle TEXT NOT NULL,rarity TEXT NOT NULL,mv REAL NOT NULL,colors TEXT NOT NULL,image TEXT NOT NULL,uri TEXT NOT NULL,legalities TEXT NOT NULL,layout TEXT NOT NULL,setcode TEXT NOT NULL,arena INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE variants(print_id TEXT PRIMARY KEY,name TEXT NOT NULL COLLATE NOCASE,setcode TEXT NOT NULL,setname TEXT NOT NULL,collector TEXT NOT NULL,rarity TEXT NOT NULL,image TEXT NOT NULL,image_back TEXT NOT NULL,art_id TEXT NOT NULL,games TEXT NOT NULL,arena INTEGER NOT NULL,digital INTEGER NOT NULL,promo INTEGER NOT NULL,full_art INTEGER NOT NULL,border TEXT NOT NULL,release TEXT NOT NULL,url TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_variants_set ON variants(setcode,name)");
        db.execSQL("CREATE INDEX idx_variants_name ON variants(name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE ownership(name TEXT PRIMARY KEY COLLATE NOCASE,qty INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE physical_ownership(name TEXT PRIMARY KEY COLLATE NOCASE,qty INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE physical_printings(name TEXT NOT NULL COLLATE NOCASE,setcode TEXT NOT NULL COLLATE NOCASE,collector TEXT NOT NULL COLLATE NOCASE,print_id TEXT NOT NULL COLLATE NOCASE,qty INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2)db.execSQL("CREATE TABLE IF NOT EXISTS physical_ownership(name TEXT PRIMARY KEY COLLATE NOCASE,qty INTEGER NOT NULL)");
        if(oldVersion<3)db.execSQL("CREATE TABLE IF NOT EXISTS physical_printings(name TEXT NOT NULL COLLATE NOCASE,setcode TEXT NOT NULL COLLATE NOCASE,collector TEXT NOT NULL COLLATE NOCASE,print_id TEXT NOT NULL COLLATE NOCASE,qty INTEGER NOT NULL)");
    }
    private static final String SET_CACHE_PREF="arcane_set_progress_cache_v1";
    private android.content.SharedPreferences setCache(){
        return application.getSharedPreferences(SET_CACHE_PREF,Context.MODE_PRIVATE);
    }
    private static String inventoryFingerprint(JSONArray cards){
        // Order-independent identity signature: duplicate and quantity changes
        // are still tracked. Never use card-name counts as a printing identity.
        java.util.ArrayList<String> normalized=new java.util.ArrayList<>();
        for(int i=0;i<cards.length();i++){
            JSONObject item=cards.optJSONObject(i);
            if(item==null)continue;
            normalized.add(item.optString("name","").trim().toLowerCase(Locale.ROOT)+"|"+
                item.optString("set","").trim().toLowerCase(Locale.ROOT)+"|"+
                item.optString("number","").trim().toLowerCase(Locale.ROOT)+"|"+
                item.optString("scryfallId","").trim().toLowerCase(Locale.ROOT)+"|"+
                item.optInt("qty",0));
        }
        java.util.Collections.sort(normalized);
        try{
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            for(String row:normalized){
                digest.update(row.getBytes(StandardCharsets.UTF_8));
                digest.update((byte)10);
            }
            StringBuilder hex=new StringBuilder();
            for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));
            return hex.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private synchronized void invalidateSetProgress(String source){
        android.content.SharedPreferences prefs=setCache();
        android.content.SharedPreferences.Editor edit=prefs.edit();
        if("all".equals(source)){
            edit.remove("overview_physical").remove("overview_arena")
                .putLong("revision_physical",prefs.getLong("revision_physical",0)+1)
                .putLong("revision_arena",prefs.getLong("revision_arena",0)+1);
        }else{
            edit.remove("overview_"+source)
                .putLong("revision_"+source,prefs.getLong("revision_"+source,0)+1);
        }
        edit.commit();
    }
    /** Resolve physical owned printing identifiers to a stable set identity.
     * Used only after a real inventory change; never on screen navigation. */
    private java.util.Map<String,String> physicalSetSignatures(SQLiteDatabase db){
        java.util.TreeMap<String,java.util.ArrayList<String>> groups=new java.util.TreeMap<>();
        java.util.HashMap<String,String> lookup=new java.util.HashMap<>();
        try(Cursor c=db.rawQuery(
            "SELECT p.name,p.setcode,p.collector,p.print_id,p.qty,v.setcode "+
            "FROM physical_printings p LEFT JOIN variants v "+
            "ON p.print_id!='' AND v.print_id=p.print_id COLLATE NOCASE "+
            "WHERE p.qty>0",null)){
            while(c.moveToNext()){
                String raw=c.getString(1),resolved=c.getString(5);
                String code=(resolved==null?"":resolved).trim().toLowerCase(Locale.ROOT);
                if(code.isEmpty()&&raw!=null&&!raw.trim().isEmpty()){
                    String key=raw.trim().toLowerCase(Locale.ROOT);
                    if(!lookup.containsKey(key)){
                        String match="";
                        try(Cursor found=db.rawQuery(
                            "SELECT setcode FROM variants WHERE setcode=? COLLATE NOCASE "+
                            "OR setname=? COLLATE NOCASE LIMIT 1",
                            new String[]{raw,raw})){
                            if(found.moveToFirst())match=found.getString(0);
                        }
                        lookup.put(key,match==null?"":match.toLowerCase(Locale.ROOT));
                    }
                    code=lookup.get(key);
                }
                if(code.isEmpty())continue;
                String row=(c.getString(0)==null?"":c.getString(0).toLowerCase(Locale.ROOT))+"|"+
                    (c.getString(2)==null?"":c.getString(2).toLowerCase(Locale.ROOT))+"|"+
                    (c.getString(3)==null?"":c.getString(3).toLowerCase(Locale.ROOT))+"|"+
                    c.getInt(4);
                groups.computeIfAbsent(code,k->new java.util.ArrayList<>()).add(row);
            }
        }
        java.util.TreeMap<String,String> result=new java.util.TreeMap<>();
        for(java.util.Map.Entry<String,java.util.ArrayList<String>> item:groups.entrySet()){
            java.util.Collections.sort(item.getValue());
            result.put(item.getKey(),String.join("\\n",item.getValue()));
        }
        return result;
    }
    /** Preserve all unaffected tiles and flag only changed set codes. */
    private synchronized void invalidateChangedSets(String source,
            java.util.Set<String> changed){
        if(changed.isEmpty())return;
        android.content.SharedPreferences prefs=setCache();
        String existing=prefs.getString("overview_"+source,null);
        if(existing==null || changed.size()>80){
            invalidateSetProgress(source);
            return;
        }
        try{
            java.util.TreeSet<String> dirty=new java.util.TreeSet<>();
            JSONArray old=new JSONArray(prefs.getString("dirty_"+source,"[]"));
            for(int i=0;i<old.length();i++)dirty.add(old.optString(i));
            dirty.addAll(changed);
            JSONArray encoded=new JSONArray();
            for(String code:dirty)if(code.matches("[a-z0-9]{2,12}"))encoded.put(code);
            prefs.edit().putString("dirty_"+source,encoded.toString())
                .putLong("revision_"+source,prefs.getLong("revision_"+source,0)+1)
                .commit();
        }catch(Exception error){invalidateSetProgress(source);}
    }
    /** A stale set retains its last known values until its own scoped refresh. */
    java.util.Set<String> dirtySetCodes(String source){
        java.util.TreeSet<String> codes=new java.util.TreeSet<>();
        if(!"physical".equals(source)&&!"arena".equals(source))return codes;
        try{
            JSONArray list=new JSONArray(setCache().getString("dirty_"+source,"[]"));
            for(int i=0;i<list.length();i++){
                String code=list.optString(i,"");
                if(code.matches("[a-z0-9]{2,12}"))codes.add(code);
            }
        }catch(Exception ignored){}
        return codes;
    }
    /** Run on a worker only. Recalculate changed sets, never the entire overview. */
    boolean refreshDirtySetProgress(String source){
        java.util.Set<String> dirty=dirtySetCodes(source);
        if(dirty.isEmpty())return false;
        android.content.SharedPreferences prefs=setCache();
        long revision=prefs.getLong("revision_"+source,0);
        try{
            String json=prefs.getString("overview_"+source,null);
            if(json==null)return false;
            JSONObject snapshot=new JSONObject(json);
            JSONArray original=snapshot.optJSONArray("sets");
            if(original==null)return false;
            java.util.Map<String,JSONObject> refreshed=new java.util.HashMap<>();
            for(String code:dirty){
                JSONObject progress=indexedSetProgress(code,source);
                if(progress!=null)refreshed.put(code,progress);
            }
            JSONArray updated=new JSONArray();
            for(int i=0;i<original.length();i++){
                JSONObject row=original.optJSONObject(i);
                if(row==null)continue;
                String code=row.optString("code","").toLowerCase(Locale.ROOT);
                JSONObject progress=refreshed.get(code);
                if(progress!=null){
                    row.put("owned",progress.optInt("owned",0));
                    row.put("total",progress.optInt("total",0));
                    row.put("missing",progress.optInt("missing",0));
                    row.put("percentage",progress.optDouble("percentage",0));
                }
                updated.put(row);
            }
            snapshot.put("sets",updated);
            synchronized(this){
                if(prefs.getLong("revision_"+source,0)!=revision)return false;
                return prefs.edit().putString("overview_"+source,snapshot.toString())
                    .remove("dirty_"+source).commit();
            }
        }catch(Exception error){
            Log.w("ArcaneVault","Set-specific progress refresh unavailable",error);
            return false;
        }
    }

    private static JSONObject filterSetOverview(JSONObject snapshot,String query)throws Exception{
        String filter=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        JSONArray original=snapshot.optJSONArray("sets"),shown=new JSONArray();
        if(original!=null)for(int i=0;i<original.length();i++){
            JSONObject set=original.optJSONObject(i);
            if(set==null)continue;
            if(filter.isEmpty()||
                set.optString("code","").toLowerCase(Locale.ROOT).contains(filter)||
                set.optString("name","").toLowerCase(Locale.ROOT).contains(filter))
                shown.put(set);
        }
        return new JSONObject().put("sets",shown).put("count",shown.length())
            .put("source",snapshot.optString("source",""))
            .put("cached",true);
    }
    /** Memory/disk-backed snapshot read. No SQLite call or file lock. */
    JSONObject cachedSetOverview(String source,String query){
        if(!"physical".equals(source)&&!"arena".equals(source))return null;
        try{
            String json=setCache().getString("overview_"+source,null);
            return json==null?null:filterSetOverview(new JSONObject(json),query);
        }catch(Exception error){
            android.util.Log.w("ArcaneVault","Set snapshot unavailable",error);
            return null;
        }
    }
    private static int count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){return c.moveToFirst()?c.getInt(0):0;}}
    JSONObject status() throws Exception {
        boolean busy=syncing.get();
        long inactiveSeconds=busy&&lastProgressAtMs>0
            ?Math.max(0L,(android.os.SystemClock.elapsedRealtime()-lastProgressAtMs)/1000L):0L;
        int printings=indexedPrintings; // No SQLite access on the status HTTP worker.
        return new JSONObject().put("busy",busy).put("phase",phase)
            .put("imported",imported).put("total",total).put("printings",printings)
            .put("downloadedBytes",downloadedBytes).put("expectedBytes",expectedBytes)
            .put("idleSeconds",inactiveSeconds).put("stalled",busy&&inactiveSeconds>=180)
            .put("updatedAt",updatedAt==null?JSONObject.NULL:updatedAt)
            .put("error",error).put("source","Scryfall Default Cards (Android local database)")
            .put("hasPrintings",printings>0);
    }
    boolean start(){
        if(!syncing.compareAndSet(false,true))return false;
        imported=0;downloadedBytes=0;expectedBytes=0;
        lastProgressAtMs=android.os.SystemClock.elapsedRealtime();
        phase="Starting catalog sync";error="";
        new Thread(this::sync,"ArcaneCatalogSync").start();
        return true;
    }
    private static HttpURLConnection connect(String value)throws Exception{
        URL url=new URL(value);
        if(!"https".equals(url.getProtocol()))throw new IOException("Catalog download must use HTTPS");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(20000);c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent","ArcaneVaultAndroid/0.2 (local card collection manager)");
        c.setRequestProperty("Accept","application/json, */*;q=0.8");
        return c;
    }
    private void sync(){
        SQLiteDatabase db=null;
        try{
            // Even if opening SQLite fails (for example, storage is exhausted),
            // always reach finally and clear the busy flag so users can retry.
            db=getWritableDatabase();
            phase="Discovering Scryfall catalog";
            JSONObject manifest;
            HttpURLConnection c=connect("https://api.scryfall.com/bulk-data");
            try {if(c.getResponseCode()!=200)throw new IOException("Scryfall manifest HTTP "+c.getResponseCode());
                try(InputStream in=c.getInputStream()){manifest=new JSONObject(readSmall(in,2_000_000));}
            } finally {c.disconnect();}
            JSONArray datasets=manifest.getJSONArray("data");String url=null;
            for(int i=0;i<datasets.length();i++){
                JSONObject item=datasets.getJSONObject(i);
                if("default_cards".equals(item.optString("type"))){url=item.optString("jsonl_download_uri","");if(url.isEmpty())url=item.optString("download_uri","");break;}
            }
            if(url==null||url.isEmpty())throw new IOException("Scryfall bulk manifest is missing default_cards");
            URL download=new URL(url);
            if(!"https".equals(download.getProtocol())||!(download.getHost().equals("data.scryfall.io")||download.getHost().equals("data.scryfall.com")))throw new IOException("Unexpected Scryfall data host");
            phase="Downloading and indexing card printings";
            lastProgressAtMs=android.os.SystemClock.elapsedRealtime();
            // Keep active catalog available until the new dataset has been indexed successfully.
            db.execSQL("DROP TABLE IF EXISTS cards_stage");db.execSQL("DROP TABLE IF EXISTS variants_stage");
            db.execSQL("CREATE TABLE cards_stage AS SELECT * FROM cards WHERE 0");
            db.execSQL("CREATE UNIQUE INDEX cards_stage_unique ON cards_stage(name COLLATE NOCASE)");
            db.execSQL("CREATE TABLE variants_stage AS SELECT * FROM variants WHERE 0");
            db.execSQL("CREATE UNIQUE INDEX variants_stage_unique ON variants_stage(print_id)");
            HttpURLConnection remote=connect(url);
            db.beginTransaction();
            try {
                if(remote.getResponseCode()!=200)throw new IOException("Scryfall download HTTP "+remote.getResponseCode());
                // Track network progress separately from database writes. This
                // keeps the status endpoint informative while bytes are streaming.
                expectedBytes=Math.max(0L,remote.getContentLengthLong());
                try(InputStream tracked=new java.io.FilterInputStream(remote.getInputStream()){
                    @Override public int read(byte[] target,int offset,int length)throws IOException {
                        int n=super.read(target,offset,length);
                        if(n>0){downloadedBytes+=n;lastProgressAtMs=android.os.SystemClock.elapsedRealtime();}
                        return n;
                    }
                    @Override public int read()throws IOException {
                        int n=super.read();
                        if(n>=0){downloadedBytes++;lastProgressAtMs=android.os.SystemClock.elapsedRealtime();}
                        return n;
                    }
                };BufferedInputStream buffered=new BufferedInputStream(tracked,65536)){
                    buffered.mark(8);int a=buffered.read(),b=buffered.read();buffered.reset();
                    InputStream raw=(a==0x1f&&b==0x8b)?new GZIPInputStream(buffered,65536):buffered;
                    BufferedInputStream content=new BufferedInputStream(raw,65536);
                    content.mark(8192);int first;do{first=content.read();}while(first!=-1&&Character.isWhitespace(first));content.reset();
                    if(first=='['){try(JsonReader reader=new JsonReader(new InputStreamReader(content,StandardCharsets.UTF_8))){reader.beginArray();while(reader.hasNext())insert(db,(JSONObject)parseJson(reader));reader.endArray();}}
                    else {try(BufferedReader lines=new BufferedReader(new InputStreamReader(content,StandardCharsets.UTF_8),65536)){
                        String line;while((line=lines.readLine())!=null)if(!line.trim().isEmpty())insert(db,new JSONObject(line));
                    }}
                }
            }finally{if(db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();}remote.disconnect();}
            if(imported<1000)throw new IOException("Downloaded catalog unexpectedly contained too few cards: "+imported);
            phase="Finalizing local catalog";
            lastProgressAtMs=android.os.SystemClock.elapsedRealtime();
            db.beginTransaction();try {
                db.execSQL("DELETE FROM cards");db.execSQL("INSERT INTO cards SELECT * FROM cards_stage");
                db.execSQL("DELETE FROM variants");db.execSQL("INSERT INTO variants SELECT * FROM variants_stage");
                db.execSQL("INSERT OR REPLACE INTO state(key,value) VALUES('updated',?)",new Object[]{new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date())});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            invalidateSetProgress("all");
            total=count(db,"cards");
            indexedPrintings=count(db,"variants");
            lastProgressAtMs=android.os.SystemClock.elapsedRealtime();
            try(Cursor cur=db.rawQuery("SELECT value FROM state WHERE key='updated'",null)){if(cur.moveToFirst())updatedAt=cur.getString(0);}
            phase="Ready";
        }catch(Exception ex){error=ex.getMessage()==null?ex.toString():ex.getMessage();phase="Sync failed; previous catalog preserved";Log.e("ArcaneVault","Catalog synchronization failed",ex);}
        finally {
            // Cleanup failures must never leave the user seeing busy=true forever.
            try{if(db!=null&&db.inTransaction())db.endTransaction();}
            catch(Exception cleanup){Log.w("ArcaneVault","Catalog rollback cleanup failed",cleanup);}
            try{if(db!=null){db.execSQL("DROP TABLE IF EXISTS cards_stage");
                db.execSQL("DROP TABLE IF EXISTS variants_stage");
                db.execSQL("DROP INDEX IF EXISTS cards_stage_unique");
                db.execSQL("DROP INDEX IF EXISTS variants_stage_unique");}}
            catch(Exception cleanup){Log.w("ArcaneVault","Catalog staging cleanup failed",cleanup);}
            syncing.set(false);
            // A completed full catalog sync can resolve previously unknown
            // launcher sets immediately, without waiting for the next
            // periodic Android home-screen redraw.
            if("Ready".equals(phase))
                VaultWidgets.refreshSets(application);
        }
    }
    private static String readSmall(InputStream in,int max)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>max)throw new IOException("Manifest too large");}return out.toString("UTF-8");}
    private static Object parseJson(JsonReader reader)throws IOException {
        JsonToken token=reader.peek();switch(token){
            case BEGIN_OBJECT: JSONObject o=new JSONObject();reader.beginObject();while(reader.hasNext()){String key=reader.nextName();try{o.put(key,parseJson(reader));}catch(org.json.JSONException e){throw new IOException(e);}}reader.endObject();return o;
            case BEGIN_ARRAY: JSONArray arr=new JSONArray();reader.beginArray();while(reader.hasNext())arr.put(parseJson(reader));reader.endArray();return arr;
            case STRING:return reader.nextString();case NUMBER:{String n=reader.nextString();try{return Double.valueOf(n);}catch(Exception ex){return n;}}
            case BOOLEAN:return reader.nextBoolean();case NULL:reader.nextNull();return JSONObject.NULL;
            default:throw new IOException("Unexpected JSON token");
        }
    }
    private static String str(JSONObject o,String name){return o.optString(name,"");}
    private static String image(JSONObject o){JSONObject u=o.optJSONObject("image_uris");if(u!=null)return u.optString("normal","");JSONArray faces=o.optJSONArray("card_faces");if(faces!=null)for(int i=0;i<faces.length();i++){JSONObject f=faces.optJSONObject(i);if(f!=null&&f.optJSONObject("image_uris")!=null){String value=f.optJSONObject("image_uris").optString("normal","");if(!value.isEmpty())return value;}}return "";}
    private static String backImage(JSONObject o){JSONArray faces=o.optJSONArray("card_faces");if(faces!=null&&faces.length()>1){JSONObject f=faces.optJSONObject(1);if(f!=null&&f.optJSONObject("image_uris")!=null)return f.optJSONObject("image_uris").optString("normal","");}return "";}
    private static String oracle(JSONObject o){String text=str(o,"oracle_text");if(!text.isEmpty())return text;JSONArray faces=o.optJSONArray("card_faces");if(faces==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<faces.length();i++){JSONObject f=faces.optJSONObject(i);if(f!=null){if(b.length()>0)b.append("\n//\n");b.append(str(f,"oracle_text"));}}return b.toString();}
    private static String colors(JSONObject o){JSONArray a=o.optJSONArray("color_identity");if(a==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++)b.append(a.optString(i));return b.toString();}
    private static String games(JSONObject o){JSONArray a=o.optJSONArray("games");if(a==null)return "";StringJoiner result=new StringJoiner(",");for(int i=0;i<a.length();i++)result.add(a.optString(i));return result.toString();}
    private void insert(SQLiteDatabase db,JSONObject card){
        String name=str(card,"name"),id=str(card,"id");if(name.isEmpty()||name.length()>300||id.isEmpty())return;
        String game=games(card);boolean arena=Arrays.asList(game.split(",")).contains("arena");
        ContentValues v=new ContentValues();v.put("name",name);v.put("type",str(card,"type_line"));v.put("oracle",oracle(card));v.put("rarity",str(card,"rarity"));v.put("mv",card.optDouble("cmc",0));v.put("colors",colors(card));v.put("image",image(card));v.put("uri",str(card,"scryfall_uri"));v.put("legalities",card.optJSONObject("legalities")==null?"{}":card.optJSONObject("legalities").toString());v.put("layout",str(card,"layout"));v.put("setcode",str(card,"set"));v.put("arena",arena?1:0);
        // Prefer Arena-listed artwork when two printings have the same Oracle name.
        try(Cursor exists=db.rawQuery("SELECT arena FROM cards_stage WHERE name=?",new String[]{name})){
            if(!exists.moveToFirst()||(! (exists.getInt(0)==1&&!arena))){db.insertWithOnConflict("cards_stage",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
        }
        ContentValues p=new ContentValues();p.put("print_id",id);p.put("name",name);p.put("setcode",str(card,"set"));p.put("setname",str(card,"set_name"));p.put("collector",str(card,"collector_number"));p.put("rarity",str(card,"rarity"));p.put("image",image(card));p.put("image_back",backImage(card));p.put("art_id",str(card,"illustration_id"));p.put("games",game);p.put("arena",arena?1:0);p.put("digital",card.optBoolean("digital")?1:0);p.put("promo",card.optBoolean("promo")?1:0);p.put("full_art",card.optBoolean("full_art")?1:0);p.put("border",str(card,"border_color"));p.put("release",str(card,"released_at"));p.put("url",str(card,"scryfall_uri"));
        db.insertWithOnConflict("variants_stage",null,p,SQLiteDatabase.CONFLICT_REPLACE);imported++;
        if(imported%500==0)lastProgressAtMs=android.os.SystemClock.elapsedRealtime();
        if(imported%2000==0 && db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();}
    }
    // Last-resort recovery from the indexed ownership mirrors. This can recover
    // card names and quantities, but not distinct physical printing attributes.
    JSONObject recoverIndexedOwnership() throws Exception {
        JSONObject result=new JSONObject();
        SQLiteDatabase db=getReadableDatabase();
        JSONArray arena=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT name,qty FROM ownership WHERE qty>0 ORDER BY name COLLATE NOCASE",null)){
            while(c.moveToNext())arena.put(new JSONObject().put("name",c.getString(0)).put("qty",c.getInt(1))
                .put("sets",new JSONArray()).put("tags",new JSONArray()).put("uses",new JSONArray()));
        }
        result.put("arena",arena);
        // Reconstruct known printing identities BEFORE resorting to name-only
        // aggregate ownership. Name-only restoration previously lost set progress.
        Map<String,Integer> totals=new LinkedHashMap<>();
        Map<String,String> display=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT name,qty FROM physical_ownership WHERE qty>0 ORDER BY name COLLATE NOCASE",null)){
            while(c.moveToNext()){String name=c.getString(0),key=name.toLowerCase(Locale.ROOT);
                totals.put(key,c.getInt(1));display.put(key,name);}
        }
        Map<String,Integer> restored=new HashMap<>();
        JSONArray physical=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT name,setcode,collector,print_id,qty FROM physical_printings WHERE qty>0 ORDER BY name COLLATE NOCASE",null)){
            while(c.moveToNext()){
                String name=c.getString(0),key=name.toLowerCase(Locale.ROOT);
                int available=totals.getOrDefault(key,0)-restored.getOrDefault(key,0);
                if(available<=0)continue;
                int qty=Math.min(c.getInt(4),available);
                if(qty<=0)continue;
                physical.put(new JSONObject().put("name",name).put("qty",qty)
                    .put("set",c.getString(1)).put("number",c.getString(2))
                    .put("scryfallId",c.getString(3)).put("finish","nonfoil"));
                restored.put(key,restored.getOrDefault(key,0)+qty);
            }
        }
        for(Map.Entry<String,Integer> e:totals.entrySet()){
            int remainder=e.getValue()-restored.getOrDefault(e.getKey(),0);
            if(remainder>0)physical.put(new JSONObject().put("name",display.get(e.getKey()))
                .put("qty",remainder).put("set","").put("number","").put("finish","nonfoil"));
        }
        result.put("physical",physical);
        return result;
    }
    void ownership(JSONObject input)throws Exception{
        JSONArray cards=input.getJSONArray("cards");if(cards.length()>150000)throw new IllegalArgumentException("Too many ownership entries");
        String fingerprint=inventoryFingerprint(cards);
        // Repeated native-WebView sync projections should not rewrite the
        // entire ownership table or contend with Set Progress reads.
        if(fingerprint.equals(setCache().getString("fingerprint_arena","")))
            return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("ownership",null,null);
            for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c==null)continue;String name=c.optString("name","");int qty=c.optInt("qty",0);if(name.isEmpty()||name.length()>300||qty<=0)continue;ContentValues v=new ContentValues();v.put("name",name);v.put("qty",qty);db.insertWithOnConflict("ownership",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(!fingerprint.equals(setCache().getString("fingerprint_arena",""))){
            invalidateSetProgress("arena");
            setCache().edit().putString("fingerprint_arena",fingerprint).apply();
        }
    }
    // Isolated, persistent mirror for the local paper inventory. Never changes Arena quantities.
    synchronized void physicalOwnership(JSONObject input)throws Exception {
        JSONArray rows=input.getJSONArray("cards");
        if(rows.length()>150000)throw new IllegalArgumentException("Too many physical inventory entries");
        String fingerprint=inventoryFingerprint(rows);
        if(fingerprint.equals(setCache().getString("fingerprint_physical","")))
            return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            // An aggregate-only recovery snapshot may arrive after this database
            // already indexed the real printing identities. Preserve those identities
            // only when every incoming name and quantity matches the current mirror.
            Map<String,Integer> incoming=new LinkedHashMap<>();
            boolean hasIdentifiers=false;
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.optJSONObject(i);if(item==null)continue;
                String name=item.optString("name","").trim();int qty=item.optInt("qty",0);
                if(name.isEmpty()||qty<=0||qty>99999)continue;
                String key=name.toLowerCase(Locale.ROOT);
                incoming.put(key,incoming.getOrDefault(key,0)+qty);
                if(!item.optString("scryfallId","").trim().isEmpty()||
                    (!item.optString("set","").trim().isEmpty()&&!item.optString("number","").trim().isEmpty()))hasIdentifiers=true;
            }
            Map<String,Integer> prior=new LinkedHashMap<>();
            try(Cursor c=db.rawQuery("SELECT name,qty FROM physical_ownership",null)){
                while(c.moveToNext())prior.put(c.getString(0).toLowerCase(Locale.ROOT),c.getInt(1));
            }
            // Keep verified identities for unchanged card-name quantities even when
            // a Windows QR snapshot contains a mix of identified and name-only rows.
            // Never infer an identity from a card name and never carry identities
            // across a quantity change (that might represent another printing).
            java.util.Set<String> incomingIdentified=new java.util.HashSet<>();
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.optJSONObject(i);if(item==null)continue;
                String name=item.optString("name","").trim().toLowerCase(Locale.ROOT);
                if(!item.optString("scryfallId","").trim().isEmpty() ||
                   (!item.optString("set","").trim().isEmpty()&&!item.optString("number","").trim().isEmpty()))
                    incomingIdentified.add(name);
            }
            java.util.List<ContentValues> preserved=new java.util.ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT name,setcode,collector,print_id,qty FROM physical_printings WHERE qty>0",null)){
                while(c.moveToNext()){
                    String key=c.getString(0).toLowerCase(Locale.ROOT);
                    if(incomingIdentified.contains(key) || !incoming.containsKey(key) ||
                       !incoming.get(key).equals(prior.get(key)))continue;
                    ContentValues v=new ContentValues();
                    v.put("name",c.getString(0));v.put("setcode",c.getString(1));
                    v.put("collector",c.getString(2));v.put("print_id",c.getString(3));
                    v.put("qty",c.getInt(4));preserved.add(v);
                }
            }
            db.delete("physical_ownership",null,null);
            db.delete("physical_printings",null,null);
            for(ContentValues saved:preserved)db.insertOrThrow("physical_printings",null,saved);
            // Aggregate name duplicates before inserting. Android API 26's older SQLite
            // does not support the modern ON CONFLICT ... DO UPDATE syntax.
            Map<String,Integer> quantities=new LinkedHashMap<>();
            Map<String,String> displayNames=new HashMap<>();
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.optJSONObject(i);if(item==null)continue;
                String name=item.optString("name","").trim();int qty=item.optInt("qty",0);
                if(name.isEmpty()||name.length()>300||qty<=0||qty>99999)continue;
                String key=name.toLowerCase(Locale.ROOT);
                displayNames.putIfAbsent(key,name);
                quantities.put(key,quantities.getOrDefault(key,0)+qty);
                String set=item.optString("set","").trim();
                String collector=item.optString("number","").trim();
                String id=item.optString("scryfallId","").trim();
                // An unspecified printing must never create set-specific progress.
                if(!id.isEmpty() || (!set.isEmpty() && !collector.isEmpty())){
                    ContentValues ownedPrinting=new ContentValues();
                    ownedPrinting.put("name",name);
                    ownedPrinting.put("setcode",set);
                    ownedPrinting.put("collector",collector);
                    ownedPrinting.put("print_id",id);
                    ownedPrinting.put("qty",qty);
                    db.insertOrThrow("physical_printings",null,ownedPrinting);
                }
            }
            for(Map.Entry<String,Integer> entry:quantities.entrySet()){
                ContentValues record=new ContentValues();
                record.put("name",displayNames.get(entry.getKey()));
                record.put("qty",entry.getValue());
                db.insertWithOnConflict("physical_ownership",null,record,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        if(!fingerprint.equals(setCache().getString("fingerprint_physical",""))){
            invalidateSetProgress("physical");
            setCache().edit().putString("fingerprint_physical",fingerprint).apply();
        }
    }
    private static String printingCte(Map<String,String> p){
        if(!"physical".equals(get(p,"source")))
            return "confirmed AS (SELECT setcode,name,0 AS qty FROM variants WHERE 0)";
        // Printing identity, not imported display name, establishes ownership.
        // A Scryfall ID is authoritative even when two sources spell split/adventure names differently.
        // If that ID is absent from the catalog, use an exact set+collector identity instead.
        // Set exports may contain the set's full name rather than its short code.
        return "confirmed AS (SELECT v.setcode,v.name,SUM(p.qty) qty FROM physical_printings p JOIN variants v ON "+
            "(p.print_id!='' AND v.print_id=p.print_id COLLATE NOCASE) OR "+
            "(p.setcode!='' AND p.collector!='' AND v.collector=p.collector COLLATE NOCASE "+
            "AND (v.setcode=p.setcode COLLATE NOCASE OR v.setname=p.setcode COLLATE NOCASE) "+
            "AND (p.print_id='' OR NOT EXISTS(SELECT 1 FROM variants vi WHERE vi.print_id=p.print_id COLLATE NOCASE))) "+
            "GROUP BY v.setcode,v.name)";
    }
    private static String ownershipTable(Map<String,String> p){
        return "physical".equals(get(p,"source"))?"physical_ownership":"ownership";
    }
    private static String get(Map<String,String> p,String name){return p.getOrDefault(name,"").trim();}
    /** Search literal rules text; never interpret typed % or _ as SQL wildcards. */
    private static String[] oracleTerms(Map<String,String> p) {
        String raw=get(p,"oracle");
        if(raw.isEmpty())return new String[0];
        if(raw.length()>120)throw new IllegalArgumentException(
            "Keyword search is limited to 120 characters");
        String[] terms=raw.split(",",-1);
        if(terms.length>8)throw new IllegalArgumentException(
            "Search at most eight mechanic phrases");
        for(int i=0;i<terms.length;i++){
            terms[i]=terms[i].trim();
            if(terms[i].isEmpty())throw new IllegalArgumentException(
                "Each comma-separated mechanic must be nonempty");
        }
        return terms;
    }
    private static String escapeOracle(String phrase) {
        return phrase.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
    }
    private static void addOracleTerms(StringBuilder query,List<String> args,
            Map<String,String> p) {
        String[] terms=oracleTerms(p);
        if(terms.length==0)return;
        String mode=get(p,"oracleMatch");
        if(!mode.isEmpty()&&!mode.equals("all")&&!mode.equals("any"))
            throw new IllegalArgumentException("Invalid mechanic match mode");
        String connector=mode.equals("any")?" OR ":" AND ";
        query.append(" AND (");
        for(int i=0;i<terms.length;i++){
            if(i>0)query.append(connector);
            query.append("c.oracle LIKE ? ESCAPE '\\'");
            args.add("%"+escapeOracle(terms[i])+"%");
        }
        query.append(")");
    }
    /** Offline, read-only named card summary for Shortlist. Counts are
     * independently recorded physical and Arena copies, never merged or
     * credited to an unverified printing. */
    JSONObject cardBrief(String cardName)throws Exception {
        String name=cardName==null?"":cardName.trim();
        if(name.isEmpty()||name.length()>180)
            throw new IllegalArgumentException("Invalid card name");
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery(
                "SELECT c.type,c.image,COALESCE(po.qty,0),COALESCE(ao.qty,0) "+
                "FROM cards c LEFT JOIN physical_ownership po ON po.name=c.name "+
                "LEFT JOIN ownership ao ON ao.name=c.name "+
                "WHERE c.name=? COLLATE NOCASE LIMIT 1",
                new String[]{name})){
            if(c.moveToFirst())
                return new JSONObject().put("name",name).put("type",c.getString(0))
                    .put("image",c.getString(1)).put("physical",c.getInt(2))
                    .put("arena",c.getInt(3)).put("known",true);
        }
        return new JSONObject().put("name",name).put("known",false)
            .put("physical",0).put("arena",0).put("type","")
            .put("image","");
    }

    /** Small, read-only Windows-style overview from the same local catalog.
     * Unresolved card names remain UNKNOWN (not zero-cost spells), and the
     * mana curve excludes lands. Quantities, not names, are counted. */
    JSONObject deckInsights(String commander,JSONArray entries)throws Exception {
        if(entries==null || entries.length()>500)
            throw new IllegalArgumentException("Too many deck entries to analyze");
        SQLiteDatabase db=getReadableDatabase();
        int total=0,known=0,unknown=0,lands=0,creatures=0,
            instants=0,sorceries=0,artifacts=0,enchantments=0,
            planeswalkers=0,other=0,nonland=0;
        double costSum=0;
        int[] curve=new int[8];
        JSONArray cards=new JSONArray();
        for(int i=-1;i<entries.length();i++){
            JSONObject row=i<0?null:entries.optJSONObject(i);
            String name=i<0?(commander==null?"":commander.trim()):
                row==null?"":row.optString("name","").trim();
            int qty=i<0?(name.isEmpty()?0:1):
                row==null?0:Math.max(0,Math.min(999,row.optInt("qty",0)));
            if(name.isEmpty()||qty==0)continue;
            total+=qty;
            if(total>10000)throw new IllegalArgumentException("Deck size exceeds analysis limit");
            try(Cursor card=db.rawQuery(
                    "SELECT type,mv FROM cards WHERE name=? COLLATE NOCASE LIMIT 1",
                    new String[]{name})){
                if(!card.moveToFirst()){
                    unknown+=qty;
                    cards.put(name);
                    continue;
                }
                known+=qty;
                String type=card.getString(0);
                if(type==null)type="";
                String normalized=type.toLowerCase(java.util.Locale.ROOT);
                double mv=card.getDouble(1);
                if(normalized.contains("land"))lands+=qty;
                else{
                    nonland+=qty;
                    if(Double.isFinite(mv)&&mv>=0){
                        curve[Math.min(7,(int)Math.floor(mv))]+=qty;
                        costSum+=mv*qty;
                    }
                    if(normalized.contains("creature"))creatures+=qty;
                    else if(normalized.contains("instant"))instants+=qty;
                    else if(normalized.contains("sorcery"))sorceries+=qty;
                    else if(normalized.contains("artifact"))artifacts+=qty;
                    else if(normalized.contains("enchantment"))enchantments+=qty;
                    else if(normalized.contains("planeswalker"))planeswalkers+=qty;
                    else other+=qty;
                }
            }
        }
        JSONArray bars=new JSONArray();
        for(int number:curve)bars.put(number);
        return new JSONObject().put("total",total).put("known",known)
            .put("unknown",unknown).put("lands",lands)
            .put("creatures",creatures).put("instants",instants)
            .put("sorceries",sorceries).put("artifacts",artifacts)
            .put("enchantments",enchantments).put("planeswalkers",planeswalkers)
            .put("other",other).put("curve",bars).put("unknownNames",cards)
            .put("avgMv",nonland>0?costSum/nonland:JSONObject.NULL);
    }

    /** Resolve cover art for a saved deck's exact commander name.
     * This is a read-only illustration lookup, NOT an ownership or printing
     * assertion. Do not use a partial name match (a different commander could
     * otherwise appear on the wrong deck box). */
    String commanderCoverUrl(String commanderName) throws Exception {
        if(commanderName==null)return "";
        String name=commanderName.trim();
        if(name.isEmpty()||name.length()>250)return "";
        SQLiteDatabase db=getReadableDatabase();
        for(String sql:new String[]{
                "SELECT image FROM cards WHERE name=? COLLATE NOCASE LIMIT 1",
                "SELECT image FROM variants WHERE name=? COLLATE NOCASE "+
                    "AND image<>'' ORDER BY digital ASC, release DESC LIMIT 1"}) {
            try(Cursor cursor=db.rawQuery(sql,new String[]{name})) {
                if(!cursor.moveToFirst())continue;
                String url=cursor.getString(0);
                if(url!=null&&url.startsWith("https://cards.scryfall.io/"))
                    return url;
            }
        }
        return "";
    }

    JSONObject search(Map<String,String> p)throws Exception{
        String scope=get(p,"scope"),color=get(p,"colors"),role=get(p,"role"),sort=get(p,"sort"),arena=get(p,"arena");
        // Legacy web callers omit source: keep Arena semantics for compatibility.
        // Native callers explicitly select Arena, physical or combined ownership.
        String source=get(p,"source");
        String quantity="physical".equals(source) ? "COALESCE(po.qty,0)" :
            "both".equals(source) ? "(COALESCE(ao.qty,0)+COALESCE(po.qty,0))" : "COALESCE(ao.qty,0)";
        StringBuilder where=new StringBuilder(" WHERE 1=1 ");List<String> args=new ArrayList<>();
        if("arena".equals(arena))where.append(" AND c.arena=1");else if("paper".equals(arena))where.append(" AND c.arena=0");
        if("unowned".equals(scope))where.append(" AND ").append(quantity).append("=0");else if("owned".equals(scope))where.append(" AND ").append(quantity).append(">0");
        if(!get(p,"q").isEmpty()){where.append(" AND (c.name LIKE ? OR c.oracle LIKE ? OR c.type LIKE ? OR c.setcode LIKE ?)");String q="%"+get(p,"q").substring(0,Math.min(160,get(p,"q").length()))+"%";for(int i=0;i<4;i++)args.add(q);}
        if(!get(p,"set").isEmpty()){
            String requestedSet=get(p,"set");
            if(!requestedSet.matches("[a-zA-Z0-9]{1,12}"))
                throw new IllegalArgumentException("Set code must be 1–12 letters or digits");
            // Cards retain one representative set code. For exact printing
            // searches use the confirmed-printings mode instead.
            where.append(" AND EXISTS (SELECT 1 FROM variants vs WHERE vs.name=c.name "+
                "AND vs.setcode=? COLLATE NOCASE)");
            args.add(requestedSet);
        }
        if(!get(p,"type").isEmpty()){where.append(" AND c.type LIKE ?");args.add("%"+get(p,"type")+"%");}
        addOracleTerms(where,args,p);
        if(!get(p,"rarity").isEmpty()){where.append(" AND c.rarity=?");args.add(get(p,"rarity"));}
        String mvMin=get(p,"mvMin"),mvMax=get(p,"mvMax");
        double min=mvMin.isEmpty()?0:Double.parseDouble(mvMin);
        double max=mvMax.isEmpty()?1000:Double.parseDouble(mvMax);
        if(!Double.isFinite(min)||!Double.isFinite(max)||
           min<0||max>1000||min>max)
            throw new IllegalArgumentException("Mana value must be 0–1000 with minimum ≤ maximum");
        if(!mvMin.isEmpty()){where.append(" AND c.mv>=?");args.add(Double.toString(min));}
        if(!mvMax.isEmpty()){where.append(" AND c.mv<=?");args.add(Double.toString(max));}

        // Exact color identity choice(s): OR across selections, never fuzzy subsets.
        // SQL canonicalization handles different letter order in imported records.
        List<String> identities=new ArrayList<>();
        for(String candidate:color.split(",")){
            String token=candidate.trim();
            if(token.isEmpty())continue;
            if("C".equals(token)){if(!identities.contains(""))identities.add("");continue;}
            if(token.length()>5||!token.matches("[WUBRG]+"))continue;
            StringBuilder canonical=new StringBuilder();
            for(char ch:"WUBRG".toCharArray())if(token.indexOf(ch)>=0)canonical.append(ch);
            if(canonical.length()!=token.length())continue;
            if(!identities.contains(canonical.toString()))identities.add(canonical.toString());
            if(identities.size()>=32)break;
        }
        if(!identities.isEmpty()){
            String canonicalColors="(CASE WHEN instr(c.colors,'W')>0 THEN 'W' ELSE '' END"+
                " || CASE WHEN instr(c.colors,'U')>0 THEN 'U' ELSE '' END"+
                " || CASE WHEN instr(c.colors,'B')>0 THEN 'B' ELSE '' END"+
                " || CASE WHEN instr(c.colors,'R')>0 THEN 'R' ELSE '' END"+
                " || CASE WHEN instr(c.colors,'G')>0 THEN 'G' ELSE '' END)";
            where.append(" AND ").append(canonicalColors).append(" IN (");
            for(int i=0;i<identities.size();i++){if(i>0)where.append(',');where.append('?');args.add(identities.get(i));}
            where.append(')');
        }
        Map<String,String> hints=new HashMap<>();hints.put("Ramp","%add %mana%");hints.put("Removal","%destroy target%");hints.put("Board Wipe","%destroy all%");hints.put("Discard","%discard%");hints.put("Counterspell","%counter target%");hints.put("Card Draw","%draw %card%");hints.put("Card Selection","%scry%");hints.put("Lifegain","%gain %life%");hints.put("Tutor","%search your library%");hints.put("Stax","%can't cast%");hints.put("Haste","%haste%");hints.put("Protection","%hexproof%");hints.put("Recursion","%from your graveyard%");hints.put("Sacrifice","%sacrifice%");hints.put("Tokens","%create %token%");hints.put("Graveyard Hate","%exile %graveyard%");hints.put("Finisher","%loses the game%");hints.put("Equipment","%Equipment%");
        if(hints.containsKey(role)){where.append("Equipment".equals(role)?" AND c.type LIKE ?":" AND c.oracle LIKE ?");args.add(hints.get(role));}
        String from=" FROM cards c LEFT JOIN ownership ao ON ao.name=c.name"+
            " LEFT JOIN physical_ownership po ON po.name=c.name"+where;
        SQLiteDatabase db=getReadableDatabase();int found;
        try(Cursor r=db.rawQuery("SELECT COUNT(*)"+from,args.toArray(new String[0]))){r.moveToFirst();found=r.getInt(0);}
        String order="c.name COLLATE NOCASE";if("qty".equals(sort))order=quantity+" DESC, c.name COLLATE NOCASE";if("mv".equals(sort))order="c.mv ASC,c.name COLLATE NOCASE";if("rarity".equals(sort))order="CASE c.rarity WHEN 'mythic' THEN 4 WHEN 'rare' THEN 3 WHEN 'uncommon' THEN 2 ELSE 1 END DESC,c.name COLLATE NOCASE";
        int page=clamp(get(p,"page"),0,100000,0),size=clamp(get(p,"size"),1,250,48);
        List<String> qargs=new ArrayList<>(args);qargs.add(Integer.toString(size));qargs.add(Integer.toString(page*size));JSONArray rows=new JSONArray();
        try(Cursor r=db.rawQuery("SELECT c.name,c.type,c.oracle,c.rarity,c.mv,c.colors,c.image,c.uri,c.legalities,c.layout,c.setcode,"+quantity+",c.arena,COALESCE(ao.qty,0),COALESCE(po.qty,0),"+
            "COALESCE((SELECT v.image_back FROM variants v WHERE v.name=c.name "+
            "AND v.image=c.image AND v.image_back!='' LIMIT 1),'')"+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",qargs.toArray(new String[0]))){
            while(r.moveToNext()){
                JSONObject meta=new JSONObject().put("type",r.getString(1)).put("oracle",r.getString(2)).put("rarity",r.getString(3)).put("mv",r.getDouble(4)).put("image",r.getString(6)).put("url",r.getString(7)).put("legalities",new JSONObject(r.getString(8))).put("layout",r.getString(9)).put("set_",r.getString(10)).put("arena",r.getInt(12)>0);
                JSONArray ci=new JSONArray();for(char ch:r.getString(5).toCharArray())ci.put(String.valueOf(ch));meta.put("colors",ci);
                meta.put("imageBack",r.getString(15));
                rows.put(new JSONObject().put("name",r.getString(0)).put("qty",r.getInt(11))
                    .put("arenaQty",r.getInt(13)).put("physicalQty",r.getInt(14)).put("sets",new JSONArray().put(r.getString(10))).put("tags",new JSONArray()).put("typeHint","").put("meta",meta));
            }
        }
        return new JSONObject().put("total",found).put("page",page).put("size",size)
            .put("source",source.isEmpty()?"arena":source).put("cards",rows);
    }
    /**
     * Only confirmed physical editions. Name-only ownership is deliberately
     * excluded. An unknown/contradictory ID is never converted into ownership.
     * Set+collector fallback requires exactly one matching catalog printing.
     * This is a read-only projection of physical_printings, not another ledger.
     */
    /** Native commander discovery uses the actual local Scryfall catalog,
     * never a second deck inventory or inferred commander legality. */
    JSONObject commanders(Map<String,String> p)throws Exception {
        String term=get(p,"q"),source=get(p,"source");
        if(term.length()>120)term=term.substring(0,120);
        int page=clamp(get(p,"page"),0,100000,0),size=clamp(get(p,"size"),1,80,40);
        String owned=get(p,"owned");
        String colors=get(p,"colors");
        if(!colors.isEmpty()&&!colors.equals("C")&&!colors.matches("[WUBRG]{1,5}"))
            throw new IllegalArgumentException("Unsupported commander color identity");
        StringBuilder canonical=new StringBuilder();
        for(char c:"WUBRG".toCharArray())if(colors.indexOf(c)>=0)canonical.append(c);
        if(!colors.equals("C")&&canonical.length()!=colors.length())
            throw new IllegalArgumentException("Duplicate commander color symbols");
        String colorExpression="(CASE WHEN instr(COALESCE(c.colors,''),'W')>0 THEN 'W' ELSE '' END "+
            "|| CASE WHEN instr(COALESCE(c.colors,''),'U')>0 THEN 'U' ELSE '' END "+
            "|| CASE WHEN instr(COALESCE(c.colors,''),'B')>0 THEN 'B' ELSE '' END "+
            "|| CASE WHEN instr(COALESCE(c.colors,''),'R')>0 THEN 'R' ELSE '' END "+
            "|| CASE WHEN instr(COALESCE(c.colors,''),'G')>0 THEN 'G' ELSE '' END)";
        StringBuilder from=new StringBuilder(" FROM cards c LEFT JOIN ownership ao ON ao.name=c.name "+
            "LEFT JOIN physical_ownership po ON po.name=c.name "+
            "WHERE (c.type LIKE '%Legendary%Creature%' OR "+
            "c.oracle LIKE '%can be your commander%') "+
            "AND (?='' OR c.name LIKE ? OR c.oracle LIKE ?) "+
            ("arena".equals(source)?"AND c.arena=1 ":""));
        String q="%"+term+"%";
        java.util.ArrayList<String> args=new java.util.ArrayList<>(Arrays.asList(term,q,q));
        if("physical".equals(owned))from.append(" AND COALESCE(po.qty,0)>0");
        else if("arena".equals(owned))from.append(" AND COALESCE(ao.qty,0)>0");
        else if("any".equals(owned))from.append(" AND (COALESCE(po.qty,0)+COALESCE(ao.qty,0))>0");
        if(!colors.isEmpty()){
            from.append(" AND ").append(colorExpression).append("=?");
            args.add("C".equals(colors)?"":canonical.toString());
        }
        SQLiteDatabase db=getReadableDatabase();int count=0;
        try(Cursor c=db.rawQuery("SELECT COUNT(*)"+from,args.toArray(new String[0]))){
            if(c.moveToFirst())count=c.getInt(0);
        }
        java.util.ArrayList<String> paged=new java.util.ArrayList<>(args);
        paged.add(Integer.toString(size));paged.add(Integer.toString(page*size));
        JSONArray rows=new JSONArray();
        String sql="SELECT c.name,c.type,c.oracle,c.colors,c.image,c.mv,c.rarity,"+
            "COALESCE(ao.qty,0),COALESCE(po.qty,0),"+
            "COALESCE((SELECT v.image_back FROM variants v WHERE v.name=c.name "+
            "AND v.image=c.image AND v.image_back!='' LIMIT 1),'')"+
            from+" ORDER BY c.name COLLATE NOCASE LIMIT ? OFFSET ?";
        try(Cursor c=db.rawQuery(sql,paged.toArray(new String[0]))){
            while(c.moveToNext()){
                rows.put(new JSONObject().put("name",c.getString(0))
                    .put("type",c.getString(1)).put("oracle",c.getString(2))
                    .put("colors",c.getString(3)).put("image",c.getString(4))
                    .put("mv",c.getDouble(5)).put("rarity",c.getString(6))
                    .put("arenaQty",c.getInt(7)).put("physicalQty",c.getInt(8))
                    .put("imageBack",c.getString(9)));
            }
        }
        return new JSONObject().put("total",count).put("page",page)
            .put("size",size).put("cards",rows);
    }

    JSONObject ownedPhysicalPrintings(Map<String,String> p)throws Exception{
        String term=get(p,"q");
        if(term.length()>160)term=term.substring(0,160);
        int page=clamp(get(p,"page"),0,100000,0);
        int size=clamp(get(p,"size"),1,100,40);
        String confirmed=
            "WITH matches AS ("+
            " SELECT v.print_id id,p.qty qty FROM physical_printings p "+
            " JOIN variants v ON p.print_id!='' AND v.print_id=p.print_id COLLATE NOCASE "+
            " WHERE p.qty>0 "+
            " AND (p.setcode='' OR p.setcode=v.setcode COLLATE NOCASE OR p.setcode=v.setname COLLATE NOCASE)"+
            " AND (p.collector='' OR p.collector=v.collector COLLATE NOCASE)"+
            " UNION ALL "+
            " SELECT v.print_id id,p.qty qty FROM physical_printings p "+
            " JOIN variants v ON p.print_id='' AND p.setcode!='' AND p.collector!='' "+
            " AND (p.setcode=v.setcode COLLATE NOCASE OR p.setcode=v.setname COLLATE NOCASE) "+
            " AND p.collector=v.collector COLLATE NOCASE "+
            " WHERE p.qty>0 AND NOT EXISTS (SELECT 1 FROM variants other "+
            " WHERE other.print_id!=v.print_id AND "+
            " (other.setcode=p.setcode COLLATE NOCASE OR other.setname=p.setcode COLLATE NOCASE) "+
            " AND other.collector=p.collector COLLATE NOCASE)"+
            " ), confirmed AS (SELECT id,SUM(qty) qty FROM matches GROUP BY id) ";
        StringBuilder from=new StringBuilder(" FROM confirmed o JOIN variants v ON v.print_id=o.id "+
            " LEFT JOIN cards c ON c.name=v.name WHERE o.qty>0 "+
            " AND (?='' OR v.name LIKE ? OR v.setcode LIKE ? OR v.setname LIKE ? OR v.collector LIKE ?)");
        String pattern="%"+term+"%";
        ArrayList<String> filter=new ArrayList<>(Arrays.asList(term,pattern,pattern,pattern,pattern));
        String chosenSet=get(p,"set");
        if(!chosenSet.isEmpty()){
            if(!chosenSet.matches("[a-zA-Z0-9]{1,12}"))
                throw new IllegalArgumentException("Set code must be 1–12 letters or digits");
            from.append(" AND v.setcode=? COLLATE NOCASE");
            filter.add(chosenSet);
        }
        if(!get(p,"rarity").isEmpty()){
            from.append(" AND v.rarity=?");
            filter.add(get(p,"rarity"));
        }
        if(!get(p,"type").isEmpty()){
            from.append(" AND c.type LIKE ?");
            filter.add("%"+get(p,"type")+"%");
        }
        addOracleTerms(from,filter,p);

        String mvMin=get(p,"mvMin"),mvMax=get(p,"mvMax");
        double min=mvMin.isEmpty()?0:Double.parseDouble(mvMin);
        double max=mvMax.isEmpty()?1000:Double.parseDouble(mvMax);
        if(!Double.isFinite(min)||!Double.isFinite(max)||
            min<0||max>1000||min>max)
            throw new IllegalArgumentException("Mana value must be 0–1000 with minimum ≤ maximum");
        if(!mvMin.isEmpty()){from.append(" AND c.mv>=?");filter.add(Double.toString(min));}
        if(!mvMax.isEmpty()){from.append(" AND c.mv<=?");filter.add(Double.toString(max));}
        String exactColors=get(p,"colors");
        if(!exactColors.isEmpty()){
            if(!exactColors.equals("C")&&!exactColors.matches("[WUBRG]{1,5}"))
                throw new IllegalArgumentException("Invalid color identity");
            StringBuilder normalized=new StringBuilder();
            for(char symbol:"WUBRG".toCharArray())
                if(exactColors.indexOf(symbol)>=0)normalized.append(symbol);
            if(!exactColors.equals("C")&&normalized.length()!=exactColors.length())
                throw new IllegalArgumentException("Invalid color identity");
            String canonical="(CASE WHEN instr(COALESCE(c.colors,''),'W')>0 THEN 'W' ELSE '' END"+
                " || CASE WHEN instr(COALESCE(c.colors,''),'U')>0 THEN 'U' ELSE '' END"+
                " || CASE WHEN instr(COALESCE(c.colors,''),'B')>0 THEN 'B' ELSE '' END"+
                " || CASE WHEN instr(COALESCE(c.colors,''),'R')>0 THEN 'R' ELSE '' END"+
                " || CASE WHEN instr(COALESCE(c.colors,''),'G')>0 THEN 'G' ELSE '' END)";
            from.append(" AND ").append(canonical).append("=?");
            filter.add("C".equals(exactColors)?"":normalized.toString());
        }
        String role=get(p,"role");
        Map<String,String> hints=new HashMap<>();
        hints.put("Ramp","%add %mana%");hints.put("Removal","%destroy target%");
        hints.put("Board Wipe","%destroy all%");hints.put("Discard","%discard%");
        hints.put("Counterspell","%counter target%");hints.put("Card Draw","%draw %card%");
        hints.put("Card Selection","%scry%");hints.put("Lifegain","%gain %life%");
        hints.put("Tutor","%search your library%");hints.put("Stax","%can't cast%");
        hints.put("Haste","%haste%");hints.put("Protection","%hexproof%");
        hints.put("Recursion","%from your graveyard%");hints.put("Sacrifice","%sacrifice%");
        hints.put("Tokens","%create %token%");hints.put("Graveyard Hate","%exile %graveyard%");
        hints.put("Finisher","%loses the game%");hints.put("Equipment","%Equipment%");
        if(hints.containsKey(role)){
            from.append("Equipment".equals(role)?" AND c.type LIKE ?":" AND c.oracle LIKE ?");
            filter.add(hints.get(role));
        }
        SQLiteDatabase db=getReadableDatabase();
        int found;
        try(Cursor c=db.rawQuery(confirmed+"SELECT COUNT(*)"+from,filter.toArray(new String[0]))){
            found=c.moveToFirst()?c.getInt(0):0;
        }
        ArrayList<String> args=new ArrayList<>(filter);
        args.add(Integer.toString(size));args.add(Integer.toString(page*size));
        JSONArray rows=new JSONArray();
        String sort=get(p,"sort");
        String order="v.name COLLATE NOCASE,v.release DESC,v.collector";
        if("qty".equals(sort))order="o.qty DESC,v.name COLLATE NOCASE,v.collector";
        if("rarity".equals(sort))order="CASE v.rarity WHEN 'mythic' THEN 4 WHEN 'rare' THEN 3 "+
            "WHEN 'uncommon' THEN 2 ELSE 1 END DESC,v.name COLLATE NOCASE";
        if("mv".equals(sort))order="COALESCE(c.mv,0) ASC,v.name COLLATE NOCASE";
        String query=confirmed+"SELECT v.name,v.print_id,v.setcode,v.setname,v.collector,v.rarity,"+
            "v.image,v.release,o.qty,COALESCE(c.type,''),COALESCE(c.oracle,''),"+
            "COALESCE(c.arena,0),v.image_back"+from+
            " ORDER BY "+order+" LIMIT ? OFFSET ?";
        try(Cursor c=db.rawQuery(query,args.toArray(new String[0]))){
            while(c.moveToNext()){
                JSONObject meta=new JSONObject()
                    .put("type",c.getString(9)).put("oracle",c.getString(10))
                    .put("rarity",c.getString(5)).put("image",c.getString(6))
                    .put("set_",c.getString(2)).put("arena",c.getInt(11)>0);
                JSONObject printing=new JSONObject()
                    .put("id",c.getString(1)).put("set",c.getString(2))
                    .put("setName",c.getString(3)).put("collectorNumber",c.getString(4))
                    .put("rarity",c.getString(5)).put("image",c.getString(6))
                    .put("released",c.getString(7)).put("imageBack",c.getString(12))
                    .put("confirmedPhysicalCopies",c.getInt(8));
                rows.put(new JSONObject().put("name",c.getString(0))
                    .put("qty",c.getInt(8)).put("physicalQty",c.getInt(8))
                    .put("arenaQty",0).put("meta",meta).put("printing",printing));
            }
        }
        return new JSONObject().put("total",found).put("page",page)
            .put("size",size).put("source","ownedPhysicalPrintings").put("cards",rows);
    }

    JSONObject variants(Map<String,String> p)throws Exception{
        String name=get(p,"name"),scope=get(p,"scope");if(name.isEmpty()||name.length()>300)throw new IllegalArgumentException("Invalid name");
        JSONArray rows=new JSONArray();
        try(Cursor r=getReadableDatabase().rawQuery("SELECT print_id,setcode,setname,collector,rarity,image,image_back,art_id,games,arena,digital,promo,full_art,border,release,url FROM variants WHERE name=? AND (?!='arena' OR arena=1) ORDER BY arena DESC,release DESC LIMIT 400",new String[]{name,scope})){
            while(r.moveToNext()){
                JSONArray games=new JSONArray();for(String game:r.getString(8).split(","))if(!game.isEmpty())games.put(game);
                rows.put(new JSONObject().put("id",r.getString(0)).put("set",r.getString(1)).put("setName",r.getString(2)).put("collectorNumber",r.getString(3)).put("rarity",r.getString(4)).put("image",r.getString(5)).put("imageBack",r.getString(6)).put("illustrationId",r.getString(7)).put("games",games).put("arena",r.getInt(9)>0).put("digital",r.getInt(10)>0).put("promo",r.getInt(11)>0).put("fullArt",r.getInt(12)>0).put("border",r.getString(13)).put("released",r.getString(14)).put("url",r.getString(15)));
            }
        }
        return new JSONObject().put("name",name).put("scope",scope).put("count",rows.length()).put("variants",rows).put("source","Scryfall printings (not all Arena cosmetics)");
    }
    private int matchedPrintingCount(Map<String,String> p){
        if(!"physical".equals(get(p,"source")))return 0;
        try(Cursor c=getReadableDatabase().rawQuery("WITH "+printingCte(p)+" SELECT COUNT(*) FROM confirmed",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    /** Scoped read-only printing browser for native Set Progress. */
    /** One-set page: restrict printing reconciliation to returned printing IDs.
     * Never compute an all-catalog ownership CTE before drawing a set page. */
    JSONObject setPrintings(Map<String,String> p)throws Exception{
        String set=get(p,"set");
        if(!set.matches("[A-Za-z0-9]{2,12}"))
            throw new IllegalArgumentException("Invalid set code");
        int page=clamp(get(p,"page"),0,100000,0);
        int size=clamp(get(p,"size"),1,80,48);
        SQLiteDatabase db=getReadableDatabase();
        int total=0;
        try(Cursor c=db.rawQuery(
            "SELECT COUNT(*) FROM variants WHERE setcode=? COLLATE NOCASE",
            new String[]{set})){
            if(c.moveToFirst())total=c.getInt(0);
        }
        JSONArray rows=new JSONArray();
        String sql="SELECT v.name,v.print_id,v.setcode,v.setname,v.collector,"+
            "v.rarity,v.image FROM variants v "+
            "WHERE v.setcode=? COLLATE NOCASE "+
            "ORDER BY v.name COLLATE NOCASE,v.collector "+
            "LIMIT ? OFFSET ?";
        try(Cursor c=db.rawQuery(sql,new String[]{
            set,Integer.toString(size),Integer.toString(page*size)})){
            while(c.moveToNext()){
                String id=c.getString(1),code=c.getString(2),name=c.getString(3),
                    collector=c.getString(4);
                int qty=0;
                // ID is authoritative. If absent from the ownership ledger,
                // fall back only to the exact set+collector identity of rows
                // lacking a Scryfall ID. Never match a card name across sets.
                try(Cursor owned=db.rawQuery(
                    "SELECT COALESCE(SUM(qty),0) FROM physical_printings "+
                    "WHERE qty>0 AND print_id=? COLLATE NOCASE",
                    new String[]{id==null?"":id})){
                    if(owned.moveToFirst())qty=Math.max(0,owned.getInt(0));
                }
                if(qty==0){
                    try(Cursor fallback=db.rawQuery(
                        "SELECT COALESCE(SUM(qty),0) FROM physical_printings "+
                        "WHERE qty>0 AND print_id='' AND collector=? COLLATE NOCASE "+
                        "AND (setcode=? COLLATE NOCASE OR setcode=? COLLATE NOCASE)",
                        new String[]{collector==null?"":collector,
                            code==null?"":code,name==null?"":name})){
                        if(fallback.moveToFirst())qty=Math.max(0,fallback.getInt(0));
                    }
                }
                rows.put(new JSONObject().put("name",c.getString(0))
                    .put("id",id).put("set",code).put("setName",name)
                    .put("collectorNumber",collector)
                    .put("rarity",c.getString(5)).put("image",c.getString(6))
                    .put("confirmedPhysicalCopies",qty));
            }
        }
        return new JSONObject().put("total",total).put("page",page)
            .put("printings",rows);
    }

    /** Arena set coverage is card-name ownership, never inferred physical printing ownership. */
    private static String setProgressCte(Map<String,String> p) {
        if(!"arena".equals(get(p,"source")))return printingCte(p);
        return "confirmed AS (SELECT d.setcode,d.name,COALESCE(o.qty,0) qty "+
            "FROM (SELECT DISTINCT setcode,name FROM variants WHERE arena=1) d "+
            "LEFT JOIN ownership o ON o.name=d.name COLLATE NOCASE)";
    }

    /** Fast picker metadata from the actual local variants index. Unlike sets(),
     * this does NOT join every printing against every ownership row. */
    JSONArray indexedSetNames()throws Exception{
        JSONArray rows=new JSONArray();
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery(
                "SELECT setcode,MAX(setname) FROM variants "+
                "WHERE setcode<>'' GROUP BY setcode "+
                "ORDER BY MAX(setname) COLLATE NOCASE",null)){
            while(c.moveToNext()){
                String code=c.getString(0),name=c.getString(1);
                if(code==null||!code.matches("[A-Za-z0-9]{2,12}"))continue;
                rows.put(new JSONObject().put("code",code.toLowerCase(Locale.ROOT))
                    .put("name",name==null?code:name).put("indexed",true));
            }
        }
        return rows;
    }

    /** One-set-only progress, keyed by EXACT verified printing for physical.
     * Returns null when this set is not in the indexed catalog — never 0%. */
    JSONObject indexedSetProgress(String setCode,String source)throws Exception{
        if(setCode==null||!setCode.matches("[A-Za-z0-9]{2,12}")||
                (!"physical".equals(source)&&!"arena".equals(source)))
            throw new IllegalArgumentException("Invalid indexed set selection");
        SQLiteDatabase db=getReadableDatabase();
        String code=setCode.toLowerCase(Locale.ROOT);
        boolean arena="arena".equals(source);
        int total=0,owned=0;
        String name=null;
        try(Cursor c=db.rawQuery(
                "SELECT MAX(setname),COUNT(DISTINCT name) FROM variants "+
                "WHERE setcode=? COLLATE NOCASE"+(arena?" AND arena=1":""),
                new String[]{code})){
            if(c.moveToFirst()){name=c.getString(0);total=c.getInt(1);}
        }
        if(total<=0)return null;
        if(arena){
            try(Cursor c=db.rawQuery(
                    "SELECT COUNT(DISTINCT v.name) FROM variants v "+
                    "JOIN ownership o ON o.name=v.name COLLATE NOCASE AND o.qty>0 "+
                    "WHERE v.setcode=? COLLATE NOCASE AND v.arena=1",
                    new String[]{code})){
                if(c.moveToFirst())owned=c.getInt(0);
            }
        }else{
            // Same confirmed identity rules as printingCte(), scoped by set
            // first so widget updates do not evaluate every set on the phone.
            try(Cursor c=db.rawQuery(
                    "SELECT COUNT(DISTINCT v.name) FROM variants v "+
                    "JOIN physical_printings p ON p.qty>0 AND ("+
                    "(p.print_id!='' AND v.print_id=p.print_id COLLATE NOCASE) OR "+
                    "(p.setcode!='' AND p.collector!='' AND "+
                    "v.collector=p.collector COLLATE NOCASE AND "+
                    "(v.setcode=p.setcode COLLATE NOCASE OR "+
                    "v.setname=p.setcode COLLATE NOCASE) AND "+
                    "(p.print_id='' OR NOT EXISTS ("+
                    "SELECT 1 FROM variants vi WHERE "+
                    "vi.print_id=p.print_id COLLATE NOCASE)))) "+
                    "WHERE v.setcode=? COLLATE NOCASE",
                    new String[]{code})){
                if(c.moveToFirst())owned=c.getInt(0);
            }
        }
        owned=Math.max(0,Math.min(owned,total));
        return new JSONObject().put("code",code)
            .put("name",name==null?code:name).put("total",total)
            .put("owned",owned).put("missing",total-owned)
            .put("percentage",Math.round(1000.0*owned/total)/10.0)
            .put("indexed",true);
    }

    /** Representative official image for set tiles, never the set logo.
     * A set widget uses this only as an art background. */
    String representativeSetArt(String setCode,boolean arena)throws Exception{
        if(setCode==null||!setCode.matches("[A-Za-z0-9]{2,12}"))
            return "";
        String eligible=arena?"arena=1":
            "digital=0 AND instr(','||lower(replace(games,' ',''))||',',',paper,')>0";
        String sql="SELECT image FROM variants WHERE setcode=? COLLATE NOCASE "+
            "AND "+eligible+" AND image LIKE 'https://cards.scryfall.io/%' "+
            "ORDER BY CASE WHEN collector GLOB '[0-9]*' THEN 0 ELSE 1 END, "+
            "CAST(collector AS INTEGER),collector COLLATE NOCASE LIMIT 1";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{setCode})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /**
     * Lightweight Set Progress overview. Counts distinct catalog card names per set,
     * then resolves owned exact printing matches in one separate grouped query.
     * Never multiplies every variant by the entire inventory in a global CTE.
     * Unmatched ownership stays unknown, not credited to an arbitrary edition.
     */
    JSONObject indexedSetOverview(String source,String query)throws Exception{
        JSONObject saved=cachedSetOverview(source,query);
        if(saved!=null)return saved;
        long revision=setCache().getLong("revision_"+source,0);
        if(!"physical".equals(source)&&!"arena".equals(source))
            throw new IllegalArgumentException("Invalid set progress source");
        String filter=query==null?"":query.trim();
        if(filter.length()>120)throw new IllegalArgumentException("Set search is too long");
        SQLiteDatabase db=getReadableDatabase();
        JSONArray rows=new JSONArray();
        java.util.HashMap<String,Integer> ownedBySet=new java.util.HashMap<>();
        String ownedSql;
        if("arena".equals(source)){
            ownedSql="SELECT v.setcode,COUNT(DISTINCT v.name) FROM variants v "+
                "JOIN ownership o ON o.name=v.name COLLATE NOCASE AND o.qty>0 "+
                "WHERE v.arena=1 GROUP BY v.setcode";
        }else{
            // Split the lookup by identity: SQLite can use the print_id primary
            // key instead of evaluating a broad OR across all variants.
            ownedSql="SELECT m.setcode,COUNT(DISTINCT m.name) FROM ("+
                "SELECT v.setcode,v.name FROM physical_printings p "+
                "JOIN variants v ON v.print_id=p.print_id COLLATE NOCASE "+
                "WHERE p.qty>0 AND p.print_id!='' "+
                "UNION "+
                "SELECT v.setcode,v.name FROM physical_printings p "+
                "JOIN variants v ON v.setcode=p.setcode COLLATE NOCASE "+
                "AND v.collector=p.collector COLLATE NOCASE "+
                "WHERE p.qty>0 AND p.setcode!='' AND p.collector!='' "+
                "AND (p.print_id='' OR NOT EXISTS "+
                "(SELECT 1 FROM variants vi WHERE vi.print_id=p.print_id COLLATE NOCASE)) "+
                "UNION "+
                "SELECT v.setcode,v.name FROM physical_printings p "+
                "JOIN variants v ON v.setname=p.setcode COLLATE NOCASE "+
                "AND v.collector=p.collector COLLATE NOCASE "+
                "WHERE p.qty>0 AND p.setcode!='' AND p.collector!='' "+
                "AND (p.print_id='' OR NOT EXISTS "+
                "(SELECT 1 FROM variants vi WHERE vi.print_id=p.print_id COLLATE NOCASE))"+
                ") m GROUP BY m.setcode";

        }
        try(Cursor c=db.rawQuery(ownedSql,null)){
            while(c.moveToNext())ownedBySet.put(c.getString(0),c.getInt(1));
        }
        String sql="SELECT setcode,MAX(setname),COUNT(DISTINCT name),"+
            "MIN(CASE WHEN length(release)=10 AND release GLOB '????-??-??' "+
            "THEN release END) FROM variants "+
            ("arena".equals(source)?"WHERE arena=1 ":"")+
            "GROUP BY setcode ORDER BY MAX(setname) COLLATE NOCASE";
        int totalCards=0,ownedCards=0;
        try(Cursor c=db.rawQuery(sql,null)){
            while(c.moveToNext()){
                String code=c.getString(0),name=c.getString(1);
                if(code==null||code.isEmpty())continue;
                // Store the complete set snapshot. Search is done in memory.
                int total=c.getInt(2);
                if(total<=0)continue;
                int owned=Math.min(total,Math.max(0,ownedBySet.getOrDefault(code,0)));
                totalCards+=total;ownedCards+=owned;
                rows.put(new JSONObject().put("code",code)
                    .put("name",name==null?code:name)
                    .put("total",total).put("owned",owned)
                    .put("missing",total-owned)
                    .put("percentage",Math.round(1000.0*owned/total)/10.0)
                    .put("releaseDate",c.isNull(3)?"":c.getString(3)));
            }
        }
        JSONObject snapshot=new JSONObject().put("sets",rows)
            .put("count",rows.length()).put("totalCards",totalCards)
            .put("ownedCards",ownedCards).put("source",source);
        // The dataset itself is persisted, not only the last page/filter.
        // Avoid retaining an outdated result if the inventory changed
        // during reconciliation: the snapshot is valid for current index.
        // Never publish an old calculation after a catalog or ownership
        // update invalidated the snapshot while the SQLite query was running.
        if(setCache().getLong("revision_"+source,0)==revision){
            if(!setCache().edit().putString("overview_"+source,snapshot.toString()).commit())
                android.util.Log.w("ArcaneVault","Unable to persist Set Progress cache");
        }
        return filterSetOverview(snapshot,filter);
    }

    JSONObject sets(Map<String,String> p)throws Exception{
        String arena=get(p,"arena"),query=get(p,"q");JSONArray rows=new JSONArray();int t=0,owned=0;
        String source=setProgressCte(p);
        String sql="WITH distinct_cards AS (SELECT DISTINCT setcode,name FROM variants WHERE (?!='arena' OR arena=1)), "+source+", per_set AS (SELECT d.setcode,COUNT(*) total,SUM(CASE WHEN COALESCE(o.qty,0)>0 THEN 1 ELSE 0 END) owned FROM distinct_cards d LEFT JOIN confirmed o ON o.name=d.name AND o.setcode=d.setcode GROUP BY d.setcode),names AS (SELECT setcode,MAX(setname) setname,MIN(CASE WHEN length(release)=10 AND release GLOB '????-??-??' THEN release END) released FROM variants GROUP BY setcode) SELECT p.setcode,n.setname,p.total,p.owned,n.released FROM per_set p JOIN names n ON n.setcode=p.setcode WHERE (?='' OR p.setcode LIKE ? OR n.setname LIKE ?) ORDER BY n.setname COLLATE NOCASE";
        try(Cursor r=getReadableDatabase().rawQuery(sql,new String[]{arena,query,"%"+query+"%","%"+query+"%"})){
            while(r.moveToNext()){int all=r.getInt(2),have=r.getInt(3);t+=all;owned+=have;rows.put(new JSONObject().put("code",r.getString(0)).put("name",r.getString(1)).put("total",all).put("owned",have).put("missing",all-have).put("percentage",all==0?0:Math.round(1000.0*have/all)/10.0).put("releaseDate",r.isNull(4)?"":r.getString(4)));}
        }
        return new JSONObject().put("sets",rows).put("count",rows.length()).put("totalCards",t).put("ownedCards",owned)
            .put("physicalNames",count(getReadableDatabase(),"physical_ownership"))
            .put("identifiedPrintings",count(getReadableDatabase(),"physical_printings"))
            .put("matchedCards",matchedPrintingCount(p))
            .put("source","physical".equals(get(p,"source"))?"Confirmed physical printings":"Arena printing identity unavailable").put("note","Only confirmed printings are counted; name-only ownership does not imply a specific set.");
    }
    JSONObject setCards(Map<String,String> p)throws Exception{
        String code=get(p,"set").toLowerCase(Locale.US),arena=get(p,"arena"),scope=get(p,"ownership");if(!code.matches("[a-z0-9]{2,8}"))throw new IllegalArgumentException("Invalid set code");
        int page=clamp(get(p,"page"),0,10000,0),size=clamp(get(p,"size"),1,100,48);
        String confirmed=setProgressCte(p);
        String filter="";if("owned".equals(scope))filter=" AND COALESCE(o.qty,0)>0";else if("missing".equals(scope))filter=" AND COALESCE(o.qty,0)=0";
        String sql="WITH setcards AS (SELECT DISTINCT name FROM variants WHERE setcode=? AND (?!='arena' OR arena=1)), "+confirmed+" SELECT sc.name,COALESCE(o.qty,0),COALESCE(v.image,c.image,''),COALESCE(c.type,''),COALESCE(c.rarity,''),COALESCE(c.uri,'') FROM setcards sc LEFT JOIN confirmed o ON o.name=sc.name AND o.setcode=? LEFT JOIN cards c ON c.name=sc.name LEFT JOIN variants v ON v.print_id=(SELECT print_id FROM variants WHERE setcode=? AND name=sc.name AND (?!='arena' OR arena=1) ORDER BY release DESC LIMIT 1) WHERE 1=1"+filter+" ORDER BY sc.name COLLATE NOCASE LIMIT ? OFFSET ?";
        JSONArray rows=new JSONArray();try(Cursor r=getReadableDatabase().rawQuery(sql,new String[]{code,arena,code,code,arena,""+size,""+(page*size)})){
            while(r.moveToNext())rows.put(new JSONObject().put("name",r.getString(0)).put("qty",r.getInt(1)).put("image",r.getString(2)).put("type",r.getString(3)).put("rarity",r.getString(4)).put("url",r.getString(5)));
        }
        return new JSONObject().put("set",code).put("page",page).put("size",size).put("cards",rows).put("hasMore",rows.length()==size);
    }
    private static int clamp(String s,int min,int max,int fallback){try{return Math.max(min,Math.min(max,Integer.parseInt(s)));}catch(Exception e){return fallback;}}
}
