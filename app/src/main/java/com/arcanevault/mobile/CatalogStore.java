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
    private final AtomicBoolean syncing=new AtomicBoolean(false);
    private volatile String phase="Not downloaded", error="", updatedAt=null;
    private volatile long imported=0;
    private volatile int total=0;
    CatalogStore(Context context) {
        super(context,"arcane_catalog.db",null,3);
        SQLiteDatabase db=getWritableDatabase();
        total=count(db,"cards");
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
    private static int count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){return c.moveToFirst()?c.getInt(0):0;}}
    JSONObject status() throws Exception {int printings=count(getReadableDatabase(),"variants");return new JSONObject().put("busy",syncing.get()).put("phase",phase).put("imported",imported).put("total",total).put("printings",printings).put("updatedAt",updatedAt==null?JSONObject.NULL:updatedAt).put("error",error).put("source","Scryfall Default Cards (Android local database)").put("hasPrintings",printings>0);}
    boolean start(){if(!syncing.compareAndSet(false,true))return false;imported=0;error="";new Thread(this::sync,"ArcaneCatalogSync").start();return true;}
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
        SQLiteDatabase db=getWritableDatabase();
        try{
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
                // Detect gzip from bytes rather than trusting extension/content-encoding.
                try(InputStream network=remote.getInputStream();BufferedInputStream buffered=new BufferedInputStream(network,65536)){
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
            db.beginTransaction();try {
                db.execSQL("DELETE FROM cards");db.execSQL("INSERT INTO cards SELECT * FROM cards_stage");
                db.execSQL("DELETE FROM variants");db.execSQL("INSERT INTO variants SELECT * FROM variants_stage");
                db.execSQL("INSERT OR REPLACE INTO state(key,value) VALUES('updated',?)",new Object[]{new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date())});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            total=count(db,"cards");
            try(Cursor cur=db.rawQuery("SELECT value FROM state WHERE key='updated'",null)){if(cur.moveToFirst())updatedAt=cur.getString(0);}
            phase="Ready";
        }catch(Exception ex){error=ex.getMessage()==null?ex.toString():ex.getMessage();phase="Sync failed; previous catalog preserved";Log.e("ArcaneVault","Catalog synchronization failed",ex);}
        finally {if(db.inTransaction())db.endTransaction();try{db.execSQL("DROP TABLE IF EXISTS cards_stage");db.execSQL("DROP TABLE IF EXISTS variants_stage");db.execSQL("DROP INDEX IF EXISTS cards_stage_unique");db.execSQL("DROP INDEX IF EXISTS variants_stage_unique");}catch(Exception ignored){}syncing.set(false);}
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
        if(imported%2000==0 && db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();}
    }
    // Last-resort recovery from the indexed ownership mirrors. This can recover
    // card names and quantities, but not distinct physical printing attributes.
    JSONObject recoverIndexedOwnership() throws Exception {
        JSONObject result=new JSONObject();
        SQLiteDatabase db=getReadableDatabase();
        for(String kind:new String[]{"arena","physical"}){
            String table="physical".equals(kind)?"physical_ownership":"ownership";
            JSONArray rows=new JSONArray();
            try(Cursor cursor=db.rawQuery("SELECT name,qty FROM "+table+" WHERE qty>0 ORDER BY name COLLATE NOCASE",null)){
                while(cursor.moveToNext()){
                    JSONObject card=new JSONObject().put("name",cursor.getString(0)).put("qty",cursor.getInt(1));
                    if("arena".equals(kind)){
                        card.put("sets",new JSONArray()).put("tags",new JSONArray()).put("uses",new JSONArray());
                    }else{
                        card.put("set","").put("number","").put("finish","nonfoil");
                    }
                    rows.put(card);
                }
            }
            result.put(kind,rows);
        }
        return result;
    }
    void ownership(JSONObject input)throws Exception{
        JSONArray cards=input.getJSONArray("cards");if(cards.length()>150000)throw new IllegalArgumentException("Too many ownership entries");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("ownership",null,null);
            for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c==null)continue;String name=c.optString("name","");int qty=c.optInt("qty",0);if(name.isEmpty()||name.length()>300||qty<=0)continue;ContentValues v=new ContentValues();v.put("name",name);v.put("qty",qty);db.insertWithOnConflict("ownership",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    // Isolated, persistent mirror for the local paper inventory. Never changes Arena quantities.
    synchronized void physicalOwnership(JSONObject input)throws Exception {
        JSONArray rows=input.getJSONArray("cards");
        if(rows.length()>150000)throw new IllegalArgumentException("Too many physical inventory entries");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("physical_ownership",null,null);
            db.delete("physical_printings",null,null);
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
    JSONObject search(Map<String,String> p)throws Exception{
        String scope=get(p,"scope"),color=get(p,"colors"),role=get(p,"role"),sort=get(p,"sort"),arena=get(p,"arena");
        StringBuilder where=new StringBuilder(" WHERE 1=1 ");List<String> args=new ArrayList<>();
        if("arena".equals(arena))where.append(" AND c.arena=1");else if("paper".equals(arena))where.append(" AND c.arena=0");
        if("unowned".equals(scope))where.append(" AND COALESCE(o.qty,0)=0");else if("owned".equals(scope))where.append(" AND COALESCE(o.qty,0)>0");
        if(!get(p,"q").isEmpty()){where.append(" AND (c.name LIKE ? OR c.oracle LIKE ? OR c.type LIKE ? OR c.setcode LIKE ?)");String q="%"+get(p,"q").substring(0,Math.min(160,get(p,"q").length()))+"%";for(int i=0;i<4;i++)args.add(q);}
        if(!get(p,"type").isEmpty()){where.append(" AND c.type LIKE ?");args.add("%"+get(p,"type")+"%");}
        if(!get(p,"rarity").isEmpty()){where.append(" AND c.rarity=?");args.add(get(p,"rarity"));}
        if(!color.isEmpty())switch(color){case "C":where.append(" AND c.colors=''");break;case "B+":where.append(" AND c.colors LIKE '%B%'");break;case "WU":where.append(" AND c.colors NOT GLOB '*[BRG]*'");break;default:where.append(" AND c.colors=?");args.add(color);}
        Map<String,String> hints=new HashMap<>();hints.put("Ramp","%add %mana%");hints.put("Removal","%destroy target%");hints.put("Board Wipe","%destroy all%");hints.put("Discard","%discard%");hints.put("Counterspell","%counter target%");hints.put("Card Draw","%draw %card%");hints.put("Card Selection","%scry%");hints.put("Lifegain","%gain %life%");hints.put("Tutor","%search your library%");hints.put("Stax","%can't cast%");hints.put("Haste","%haste%");hints.put("Protection","%hexproof%");hints.put("Recursion","%from your graveyard%");hints.put("Sacrifice","%sacrifice%");hints.put("Tokens","%create %token%");hints.put("Graveyard Hate","%exile %graveyard%");hints.put("Finisher","%loses the game%");hints.put("Equipment","%Equipment%");
        if(hints.containsKey(role)){where.append("Equipment".equals(role)?" AND c.type LIKE ?":" AND c.oracle LIKE ?");args.add(hints.get(role));}
        String from=" FROM cards c LEFT JOIN ownership o ON o.name=c.name"+where;
        SQLiteDatabase db=getReadableDatabase();int found;
        try(Cursor r=db.rawQuery("SELECT COUNT(*)"+from,args.toArray(new String[0]))){r.moveToFirst();found=r.getInt(0);}
        String order="c.name COLLATE NOCASE";if("qty".equals(sort))order="COALESCE(o.qty,0) DESC, c.name COLLATE NOCASE";if("mv".equals(sort))order="c.mv ASC,c.name COLLATE NOCASE";if("rarity".equals(sort))order="CASE c.rarity WHEN 'mythic' THEN 4 WHEN 'rare' THEN 3 WHEN 'uncommon' THEN 2 ELSE 1 END DESC,c.name COLLATE NOCASE";
        int page=clamp(get(p,"page"),0,100000,0),size=clamp(get(p,"size"),1,250,48);
        List<String> qargs=new ArrayList<>(args);qargs.add(Integer.toString(size));qargs.add(Integer.toString(page*size));JSONArray rows=new JSONArray();
        try(Cursor r=db.rawQuery("SELECT c.name,c.type,c.oracle,c.rarity,c.mv,c.colors,c.image,c.uri,c.legalities,c.layout,c.setcode,COALESCE(o.qty,0),c.arena"+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",qargs.toArray(new String[0]))){
            while(r.moveToNext()){
                JSONObject meta=new JSONObject().put("type",r.getString(1)).put("oracle",r.getString(2)).put("rarity",r.getString(3)).put("mv",r.getDouble(4)).put("image",r.getString(6)).put("url",r.getString(7)).put("legalities",new JSONObject(r.getString(8))).put("layout",r.getString(9)).put("set_",r.getString(10)).put("arena",r.getInt(12)>0);
                JSONArray ci=new JSONArray();for(char ch:r.getString(5).toCharArray())ci.put(String.valueOf(ch));meta.put("colors",ci);
                rows.put(new JSONObject().put("name",r.getString(0)).put("qty",r.getInt(11)).put("sets",new JSONArray().put(r.getString(10))).put("tags",new JSONArray()).put("typeHint","").put("meta",meta));
            }
        }
        return new JSONObject().put("total",found).put("page",page).put("size",size).put("cards",rows);
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
    JSONObject sets(Map<String,String> p)throws Exception{
        String arena=get(p,"arena"),query=get(p,"q");JSONArray rows=new JSONArray();int t=0,owned=0;
        String source=printingCte(p);
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
        String confirmed=printingCte(p);
        String filter="";if("owned".equals(scope))filter=" AND COALESCE(o.qty,0)>0";else if("missing".equals(scope))filter=" AND COALESCE(o.qty,0)=0";
        String sql="WITH setcards AS (SELECT DISTINCT name FROM variants WHERE setcode=? AND (?!='arena' OR arena=1)), "+confirmed+" SELECT sc.name,COALESCE(o.qty,0),COALESCE(v.image,c.image,''),COALESCE(c.type,''),COALESCE(c.rarity,''),COALESCE(c.uri,'') FROM setcards sc LEFT JOIN confirmed o ON o.name=sc.name AND o.setcode=? LEFT JOIN cards c ON c.name=sc.name LEFT JOIN variants v ON v.print_id=(SELECT print_id FROM variants WHERE setcode=? AND name=sc.name AND (?!='arena' OR arena=1) ORDER BY release DESC LIMIT 1) WHERE 1=1"+filter+" ORDER BY sc.name COLLATE NOCASE LIMIT ? OFFSET ?";
        JSONArray rows=new JSONArray();try(Cursor r=getReadableDatabase().rawQuery(sql,new String[]{code,arena,code,code,arena,""+size,""+(page*size)})){
            while(r.moveToNext())rows.put(new JSONObject().put("name",r.getString(0)).put("qty",r.getInt(1)).put("image",r.getString(2)).put("type",r.getString(3)).put("rarity",r.getString(4)).put("url",r.getString(5)));
        }
        return new JSONObject().put("set",code).put("page",page).put("size",size).put("cards",rows).put("hasMore",rows.length()==size);
    }
    private static int clamp(String s,int min,int max,int fallback){try{return Math.max(min,Math.min(max,Integer.parseInt(s)));}catch(Exception e){return fallback;}}
}
