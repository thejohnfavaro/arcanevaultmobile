package com.arcanevault.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.webkit.WebStorage;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Toast;
import android.view.View;
import android.graphics.Color;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;
import org.json.JSONException;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.json.JSONObject;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int FILE_PICK = 21, FILE_SAVE = 22;
    private static final int APP_PORT = 18765;
    private static final String COLLECTION_PREFS = "arcane_vault_collections_v1";
    private static final String RECOVERY_PATH = "/__arcane_recover__";
    private WebView browser;
    private SharedPreferences durable;
    private final List<String> recoveryOrigins = new ArrayList<>();
    private final List<String> matchedRecoveryOrigins = new ArrayList<>();
    private int recoveryIndex = 0, legacyScanned = 0, legacyConflicts = 0;
    private final Map<String,String> recoveryBest = new HashMap<>();
    private final Map<String,Integer> recoveryBestSize = new HashMap<>();
    private final Map<String,JSONObject> legacySnapshots = new HashMap<>();
    private JSONObject recoveredSettings = new JSONObject();
    private String appUrl;
    private ValueCallback<Uri[]> pickerCallback;
    private byte[] pendingExport;
    private HttpServerLite server;
    private ExecutorService workers;
    private CatalogStore catalog;
    private ArtworkCache artwork;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,16,23));
        getWindow().setNavigationBarColor(Color.rgb(13,16,23));
        durable = getSharedPreferences(COLLECTION_PREFS, MODE_PRIVATE);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(13,16,23));
        setContentView(browser);
        WebSettings webSettings = browser.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(true);
        browser.addJavascriptInterface(new AndroidBridge(), "ArcaneAndroid");
        browser.addJavascriptInterface(new RecoveryBridge(), "ArcaneRecovery");
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params){
                if(pickerCallback != null) pickerCallback.onReceiveValue(null);
                pickerCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(intent, FILE_PICK);
                } catch(Exception ex) { pickerCallback = null; callback.onReceiveValue(null); toast("Cannot open document picker"); }
                return true;
            }
        });
        browser.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (RECOVERY_PATH.equals(url.getPath()) && "127.0.0.1".equals(url.getHost())
                        && "http".equals(url.getScheme())) {
                    String page = "<!doctype html><meta charset=utf-8><script>(function(){var d={};"
                            + "for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);"
                            + "if(k==='vault_cards'||k==='vault_physicalCollectionV1')d[k]=localStorage.getItem(k)}"
                            + "ArcaneRecovery.capture(JSON.stringify(d))})()</script>";
                    return new WebResourceResponse("text/html","UTF-8",
                            new ByteArrayInputStream(page.getBytes(StandardCharsets.UTF_8)));
                }
                return null;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && RECOVERY_PATH.equals(request.getUrl().getPath()))
                    runOnUiThread(() -> finishRecoveryOrigin(null));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                Uri u = request.getUrl();
                if("127.0.0.1".equals(u.getHost()) && u.getPort() == server.getPort()) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch(Exception e) {toast("Unable to open link");}
                return true;
            }
        });
        workers = Executors.newCachedThreadPool();
        catalog = new CatalogStore(getApplicationContext());
        artwork = new ArtworkCache(getApplicationContext());
        try {
            server = new HttpServerLite(APP_PORT);
            workers.execute(server);
            appUrl = "http://127.0.0.1:" + server.getPort() + "/";
            startLegacyRecovery();
        } catch(IOException e) {toast("Unable to start local app service: " + e.getMessage());}
    }

    // Enumerate WebView's previous localhost origins. Old builds used an ephemeral
    // port as the origin, so their localStorage is still on disk but invisible.
    private void startLegacyRecovery() {
        WebStorage.getInstance().getOrigins(origins -> runOnUiThread(() -> {
            recoveryOrigins.clear();
            if (origins != null) for (String origin : origins.keySet()) {
                if (origin.matches("http://127\\.0\\.0\\.1:[0-9]{2,5}")
                        && !origin.equals("http://127.0.0.1:" + APP_PORT))
                    recoveryOrigins.add(origin);
            }
            // Scan older data-rich origins first. Never add quantities from
            // different snapshots together: they may contain the same cards.
            recoveryOrigins.sort((a,b)->Long.compare(
                    origins.get(b)==null?0:origins.get(b).getUsage(),
                    origins.get(a)==null?0:origins.get(a).getUsage()));
            if (recoveryOrigins.size() > 64) recoveryOrigins.subList(64,recoveryOrigins.size()).clear();
            continueLegacyRecovery();
        }));
    }
    private void continueLegacyRecovery() {
        if (recoveryIndex >= recoveryOrigins.size()) {
            finishLegacyRecovery();
            return;
        }
        browser.loadUrl(recoveryOrigins.get(recoveryIndex) + RECOVERY_PATH);
    }
    private final class RecoveryBridge {
        @JavascriptInterface public void capture(String json) {
            runOnUiThread(() -> finishRecoveryOrigin(json));
        }
    }
    private static int collectionSize(String json) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            if (arr.length() > 150000) return -1;
            int valid=0;
            for(int i=0;i<arr.length();i++){
                JSONObject card=arr.optJSONObject(i);
                if(card!=null && !card.optString("name","").trim().isEmpty() && card.optInt("qty",0)>0)valid++;
            }
            return valid;
        }catch(Exception ignored){return -1;}
    }
    private void finishRecoveryOrigin(String json) {
        if (recoveryIndex >= recoveryOrigins.size()) return;
        String origin = recoveryOrigins.get(recoveryIndex);
        recoveryIndex++;
        legacyScanned++;
        if(json!=null)try{
            JSONObject old = new JSONObject(json);
            legacySnapshots.put(origin,old);
            for(String key:new String[]{"vault_cards","vault_physicalCollectionV1"}){
                String payload=old.optString(key,null);
                if(payload==null)continue;
                int size=collectionSize(payload);
                if(size>recoveryBestSize.getOrDefault(key,-1)){
                    recoveryBestSize.put(key,size);
                    recoveryBest.put(key,payload);
                }
            }
            // Keep all non-collection settings from one origin in the
            // browser's stable origin, without copying redundant collections.
            for(java.util.Iterator<String> it=old.keys();it.hasNext();){
                String key=it.next();
                if(key.equals("vault_cards")||key.equals("vault_physicalCollectionV1"))continue;
                if(!recoveredSettings.has(key))recoveredSettings.put(key,old.optString(key,""));
            }
        }catch(Exception ex){android.util.Log.w("ArcaneVault","Legacy origin read failed: "+origin,ex);}
        continueLegacyRecovery();
    }
    private void finishLegacyRecovery() {
        int imported=0;
        for(String key:new String[]{"vault_cards","vault_physicalCollectionV1"}){
            String candidate=recoveryBest.get(key);
            if(candidate==null||collectionSize(candidate)<=0)continue;
            String current=durable.getString(key,null);
            if(current==null||collectionSize(current)<=0){
                if(durable.edit().putString(key,candidate).commit()) imported++;
            }
        }
        // Preserve settings in the stable origin before running application JS.
        if(recoveredSettings.length()>0)
            durable.edit().putString("migrationSettings",recoveredSettings.toString()).commit();
        for(Map.Entry<String,JSONObject> entry:legacySnapshots.entrySet()){
            JSONObject data=entry.getValue();
            boolean compatible=true;
            for(String key:new String[]{"vault_cards","vault_physicalCollectionV1"}){
                String source=data.optString(key,null);
                if(source==null||collectionSize(source)<=0)continue;
                String canonical=durable.getString(key,null);
                if(canonical==null||!canonical.equals(source)){compatible=false;legacyConflicts++;break;}
            }
            // An old origin may also contain non-collection settings; cleanup is
            // deferred until the new WebView has confirmed their restoration.
            if(compatible)matchedRecoveryOrigins.add(entry.getKey());
        }
        android.util.Log.i("ArcaneVault","Legacy origins scanned="+legacyScanned+
                ", collection types recovered="+imported+", conflicting snapshots="+legacyConflicts);
        browser.loadUrl(appUrl);
    }
    private void cleanVerifiedLegacyOrigins(){
        if (durable.getString("migrationSettings",null)!=null) return;
        for(String origin:matchedRecoveryOrigins) WebStorage.getInstance().deleteOrigin(origin);
        matchedRecoveryOrigins.clear();
    }

    private void toast(String message) {runOnUiThread(()-> Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    // The Windows QR is an expiring, single-use URL on the local network.
    // Only numeric RFC1918 IPv4 addresses with the exact Arcane Vault path are accepted.
    private static URL validatedWindowsSyncUrl(String raw) throws Exception {
        URL url = new URL(raw == null ? "" : raw.trim());
        if (!"http".equalsIgnoreCase(url.getProtocol()) || url.getAuthority().contains("@") ||
                url.getQuery() != null || url.getRef() != null) throw new IOException("Not a Windows Arcane Vault QR link.");
        String host = url.getHost();
        if (!host.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")) throw new IOException("The QR link must use a local IPv4 address.");
        String[] octets = host.split("\\.");
        int a = Integer.parseInt(octets[0]), b = Integer.parseInt(octets[1]);
        for (String item : octets) if (Integer.parseInt(item) > 255) throw new IOException("Invalid local IP address.");
        boolean privateLan = (a == 127 && b == 0 && "127.0.0.1".equals(host)) || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
        if (!privateLan || url.getPort() < 1 || url.getPort() > 65535 ||
                !url.getPath().matches("/arcane-vault-sync/[a-f0-9]{48}")) throw new IOException("Not a Windows Arcane Vault LAN or USB transfer link.");
        return url;
    }
    private void webSyncCallback(String name, String argument) {
        String javascript = "window." + name + "(" + JSONObject.quote(argument) + ")";
        runOnUiThread(() -> { if(browser != null) browser.evaluateJavascript(javascript, null); });
    }
    private void downloadWindowsSync(String link) {
        workers.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = validatedWindowsSyncUrl(link);
                conn = (HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() != 200) throw new IOException("Windows rejected the link (expired, already used, or firewall blocked). HTTP " + conn.getResponseCode());
                int max = 8 * 1024 * 1024;
                int declared = conn.getContentLength();
                if (declared > max) throw new IOException("Transfer exceeds 8 MB.");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) >= 0) {
                        if (out.size() + count > max) throw new IOException("Transfer exceeds 8 MB.");
                        out.write(buffer, 0, count);
                    }
                }
                byte[] bytes = out.toByteArray();
                JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (!"arcane-vault-collection-sync".equals(data.optString("format")) || data.optInt("schema") != 1)
                    throw new IOException("Windows returned an unsupported collection snapshot.");
                webSyncCallback("arcaneVaultSyncData", Base64.encodeToString(bytes, Base64.NO_WRAP));
            } catch (Exception ex) { webSyncCallback("arcaneVaultSyncError", ex.getMessage() == null ? "Transfer failed" : ex.getMessage()); }
            finally { if (conn != null) conn.disconnect(); }
        });
    }
    private class AndroidBridge {
        private boolean validCollectionKey(String key) {
            return "cards".equals(key) || "physicalCollectionV1".equals(key);
        }
        @JavascriptInterface public String readCollection(String key) {
            return validCollectionKey(key)?durable.getString("vault_"+key,null):null;
        }
        @JavascriptInterface public boolean writeCollection(String key,String json) {
            if(!validCollectionKey(key)||json==null||json.length()>6_000_000 || collectionSize(json)<0)return false;
            // Native SharedPreferences is the authoritative single copy for
            // inventories. Use commit() so a failed write is not reported as saved.
            return durable.edit().putString("vault_"+key,json).commit();
        }
        @JavascriptInterface public String readLegacySettings() {
            return durable.getString("migrationSettings",null);
        }
        @JavascriptInterface public void finishLegacySettings() {
            // Browser verifies every key before acknowledging the migration.
            if(durable.edit().remove("migrationSettings").commit())
                runOnUiThread(() -> cleanVerifiedLegacyOrigins());
        }
        @JavascriptInterface public void scanWindowsQr() {
            runOnUiThread(() -> {
                GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
                GmsBarcodeScanning.getClient(MainActivity.this, options).startScan()
                        .addOnSuccessListener(barcode -> {
                            String value = barcode.getRawValue();
                            if (value != null) webSyncCallback("arcaneVaultScannedQr", value);
                            else webSyncCallback("arcaneVaultSyncError", "QR code was empty.");
                        })
                        .addOnFailureListener(err -> webSyncCallback("arcaneVaultSyncError",
                                "Camera QR scanner unavailable. Paste the Windows transfer link instead. " + err.getMessage()));
            });
        }
        @JavascriptInterface public void fetchWindowsSync(String link) { downloadWindowsSync(link); }
        @JavascriptInterface public void exportFile(String filename, String mime, String base64) {
            try {
                byte[] data = Base64.decode(base64,Base64.DEFAULT);
                if(data.length > 20*1024*1024) {toast("Export exceeds 20 MB");return;}
                runOnUiThread(()->{
                    pendingExport = data;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime == null || mime.isEmpty()?"application/octet-stream":mime);
                    i.putExtra(Intent.EXTRA_TITLE, filename.replaceAll("[/\\\\]","_"));
                    try {startActivityForResult(i, FILE_SAVE);} catch(Exception e){pendingExport=null;toast("Unable to open save dialog");}
                });
            } catch(Exception e) {toast("Could not prepare export");}
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==FILE_PICK){
            Uri chosen = result==RESULT_OK && data!=null?data.getData():null;
            if(pickerCallback!=null)pickerCallback.onReceiveValue(chosen==null?null:new Uri[]{chosen});
            pickerCallback=null;
        } else if(request==FILE_SAVE){
            if(result==RESULT_OK && data!=null && data.getData()!=null && pendingExport!=null){
                try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
                    if(stream!=null){stream.write(pendingExport);toast("Saved successfully");}
                }catch(IOException e){toast("Could not save: "+e.getMessage());}
            }
            pendingExport=null;
        }
    }
    @Override public void onBackPressed(){
        // First close modal, then navigate to Cards instead of exiting abruptly.
        browser.evaluateJavascript("(function(){let d=document.querySelector('dialog[open]');if(d){d.close();return 'closed'}return 'none'})()",result->{
            if("\"none\"".equals(result)) super.onBackPressed();
        });
    }
    @Override protected void onDestroy(){
        if(server!=null)server.close();
        if(workers!=null)workers.shutdownNow();
        if(catalog!=null)catalog.close();
        if(browser!=null){browser.removeJavascriptInterface("ArcaneAndroid");browser.destroy();}
        super.onDestroy();
    }

    private class HttpServerLite implements Runnable {
        private final ServerSocket listener;
        private volatile boolean open=true;
        private final byte[] page;
        HttpServerLite(int port) throws IOException {
            listener=new ServerSocket(port,12,InetAddress.getByName("127.0.0.1"));
            try(InputStream stream=getAssets().open("index.html")){page=readBytes(stream,5_000_000);}
        }
        int getPort(){return listener.getLocalPort();}
        void close(){open=false;try{listener.close();}catch(IOException ignored){}}
        @Override public void run(){
            while(open)try {Socket client=listener.accept();workers.execute(()->serve(client));}catch(IOException e){if(open)android.util.Log.w("ArcaneVault","Local server interrupted",e);}
        }
        private void serve(Socket socket) {
            try(socket){socket.setSoTimeout(20_000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
                String first=readLine(in);if(first==null)return;
                String[] request=first.split(" ");if(request.length<2){respond(out,400,"application/json","{}".getBytes(StandardCharsets.UTF_8));return;}
                String path=request[1].split("\\?")[0];
                Map<String,String> hdr=new HashMap<>();String line;
                while((line=readLine(in))!=null&&!line.isEmpty()){
                    int sep=line.indexOf(':');if(sep>0)hdr.put(line.substring(0,sep).toLowerCase(),line.substring(sep+1).trim());
                }
                int length=Integer.parseInt(hdr.getOrDefault("content-length","0"));
                if(length<0 || length>3_000_000){respondJson(out,413,"{\"error\":\"Request too large\"}");return;}
                byte[] body=new byte[length];int pos=0;while(pos<length){int count=in.read(body,pos,length-pos);if(count<0)throw new IOException("Incomplete body");pos+=count;}
                if((path.equals("/")||path.equals("/index.html")) && request[0].equals("GET"))respond(out,200,"text/html; charset=utf-8",page);
                else if(path.equals("/ArcaneVaultIcon.png")){
                    try(InputStream asset=getAssets().open("icon.png")){respond(out,200,"image/png",readBytes(asset,1_000_000));}
                }
                else if(path.equals("/api/art/status") && request[0].equals("GET")) respondJson(out,200,artwork.status().toString());
                else if(path.equals("/api/art/clear") && request[0].equals("POST")) respondJson(out,200,artwork.clear().toString());
                else if(path.equals("/api/art") && request[0].equals("GET")) {
                    String url=queryParams(request[1]).getOrDefault("url", "");
                    String type=url.contains(".svg")?"image/svg+xml":url.contains(".png")?"image/png":url.contains(".webp")?"image/webp":"image/jpeg";
                    try {respond(out,200,type,artwork.get(url));}
                    catch(Exception ex){respondJson(out,502,new JSONObject().put("error","Artwork not cached and could not be fetched").toString());}
                }
                else if(path.equals("/api/catalog/status")) respondJson(out,200,catalog.status().toString());
                else if(path.equals("/api/catalog/sync") && request[0].equals("POST")){
                    if(catalog.start())respondJson(out,200,"{\"ok\":true}");
                    else respondJson(out,409,"{\"error\":\"Catalog synchronization already in progress\"}");
                }
                else if(path.equals("/api/catalog/ownership") && request[0].equals("POST")){
                    catalog.ownership(new JSONObject(new String(body,StandardCharsets.UTF_8)));
                    respondJson(out,200,"{\"ok\":true}");
                }
                else if(path.equals("/api/catalog/physical-ownership") && request[0].equals("POST")){
                    catalog.physicalOwnership(new JSONObject(new String(body,StandardCharsets.UTF_8)));
                    respondJson(out,200,"{\"ok\":true}");
                }
                else if(path.startsWith("/api/catalog/") && request[0].equals("GET")){
                    Map<String,String> params=queryParams(request[1]);
                    JSONObject result;
                    switch(path){
                        case "/api/catalog/search":result=catalog.search(params);break;
                        case "/api/catalog/variants":result=catalog.variants(params);break;
                        case "/api/catalog/sets":result=catalog.sets(params);break;
                        case "/api/catalog/set-cards":result=catalog.setCards(params);break;
                        default:result=new JSONObject().put("error","Unknown catalog endpoint");
                    }
                    respondJson(out,200,result.toString());
                }
                else if(path.equals("/api/scryfall/collection") && request[0].equals("POST")) proxyScryfall(out,body);
                else if(path.startsWith("/api/")) respondJson(out,501,"{\"error\":\"This desktop-only service is not available in Arcane Vault Android 0.1. Use Owned-only browsing or the Windows edition.\"}");
                else respondJson(out,404,"{\"error\":\"Not found\"}");
            } catch(Exception e){android.util.Log.w("ArcaneVault","Local request failed",e);
                try {String safe=e.getMessage()==null?"Unknown catalog error":e.getMessage();
                    respondJson(socket.getOutputStream(),500,new JSONObject().put("error",safe).toString());}catch(Exception ignored){}
            }
        }
        private Map<String,String> queryParams(String target)throws Exception{
            Map<String,String> parsed=new HashMap<>();int question=target.indexOf('?');if(question<0)return parsed;
            for(String pair:target.substring(question+1).split("&")){
                String[] kv=pair.split("=",2);
                parsed.put(java.net.URLDecoder.decode(kv[0],"UTF-8"),kv.length==2?java.net.URLDecoder.decode(kv[1],"UTF-8"):"");
            }
            return parsed;
        }
        private void proxyScryfall(OutputStream out,byte[] payload)throws IOException{
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL("https://api.scryfall.com/cards/collection").openConnection();
                conn.setRequestMethod("POST");conn.setConnectTimeout(12000);conn.setReadTimeout(20000);
                conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
                conn.setRequestProperty("Accept","application/json");conn.setRequestProperty("User-Agent","ArcaneVaultAndroid/0.1 (personal collection manager)");
                conn.getOutputStream().write(payload);
                int status=conn.getResponseCode();
                InputStream content=status>=400?conn.getErrorStream():conn.getInputStream();
                byte[] result=content==null?"{}".getBytes(StandardCharsets.UTF_8):readBytes(content,3_000_000);
                respond(out,status,"application/json",result);
            } catch(Exception e){
                String message="{\"error\":\"Scryfall could not be reached. Check the network connection and retry.\"}";
                respondJson(out,502,message);
            } finally {if(conn!=null)conn.disconnect();}
        }
        private void respondJson(OutputStream o,int code,String s)throws IOException{respond(o,code,"application/json",s.getBytes(StandardCharsets.UTF_8));}
        private void respond(OutputStream out,int code,String type,byte[] data)throws IOException{
            String reason=code==200?"OK":code==501?"Not Implemented":code==404?"Not Found":code==502?"Bad Gateway":"Error";
            String headers="HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
        }
        private String readLine(InputStream in)throws IOException{
            java.io.ByteArrayOutputStream line=new java.io.ByteArrayOutputStream();int b;
            while((b=in.read())!=-1){if(b=='\n')break;if(b!='\r')line.write(b);if(line.size()>8192)throw new IOException("Header too long");}
            if(b==-1&&line.size()==0)return null;
            return line.toString("UTF-8");
        }
        private byte[] readBytes(InputStream in,int max)throws IOException{
            java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
            while((n=in.read(chunk))!=-1){buf.write(chunk,0,n);if(buf.size()>max)throw new IOException("Response too large");}
            return buf.toByteArray();
        }
    }
}
