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
import com.arcanevault.mobile.next.InventoryClearPlan;
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
    private static final int FILE_PICK = 21, FILE_SAVE = 22, PEER_PICK = 23, PEER_3WAY_PICK = 24, PEER_BASE_PICK = 25, PEER_WIRELESS_BASE_PICK = 26;
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
    private boolean nativeHomeVisible = false;
    private byte[] pendingExport;
    private Uri pendingPeerComparison;
    private boolean pendingPeerApply;
    private JSONObject pendingWirelessIncoming;
    // Legacy WebView remains the sole owner of saved decks and artwork preferences.
    // Native peer exports queue until that background origin has finished loading.
    private boolean peerDataPageReady;
    private final List<PeerDataCallback> pendingPeerData = new ArrayList<>();
    private HttpServerLite server;
    private ExecutorService workers;
    private CatalogStore catalog;
    private ArtworkCache artwork;
    // All live Scryfall card collection requests share one conservative limiter,
    // including manually refreshed cards and full owned-card metadata syncs.
    private final Object scryfallRequestGate = new Object();
    private long scryfallLastRequestAt = 0L;
    private long scryfallCooldownUntil = 0L;
    private static final long SCRYFALL_MIN_GAP_MS = 700L;


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,16,23));
        getWindow().setNavigationBarColor(Color.rgb(13,16,23));
        durable = getSharedPreferences(COLLECTION_PREFS, MODE_PRIVATE);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(13,16,23));
        NativeSystemInsets.apply(this,browser);
        setContentView(browser);
        WebSettings webSettings = browser.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if(android.os.Build.VERSION.SDK_INT>=26)webSettings.setSafeBrowsingEnabled(true);
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
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                peerDataPageReady=false;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(appUrl==null || !appUrl.equals(url))return;
                peerDataPageReady=true;
                if(pendingPeerData.isEmpty())return;
                List<PeerDataCallback> ready=new ArrayList<>(pendingPeerData);
                pendingPeerData.clear();
                for(PeerDataCallback callback:ready)readPeerData(callback);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (RECOVERY_PATH.equals(url.getPath()) && "127.0.0.1".equals(url.getHost())
                        && "http".equals(url.getScheme())) {
                    String page = "<!doctype html><meta charset=utf-8><script>(function(){var d={};"
                            + "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);"
                            + "if(k.startsWith('vault_')||k.startsWith('arcaneVault.')||k==='arcaneVault.collectionHistory.v1')"
                            + "d[k]=localStorage.getItem(k)}}catch(e){}"
                            + "ArcaneRecovery.capture(JSON.stringify(d))})()</script>";
                    return new WebResourceResponse("text/html","UTF-8",
                            new ByteArrayInputStream(page.getBytes(StandardCharsets.UTF_8)));
                }
                return null;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && RECOVERY_PATH.equals(request.getUrl().getPath()))
                    runOnUiThread(() -> finishRecoveryOrigin(null));
                else if(request.isForMainFrame() && appUrl!=null &&
                        appUrl.equals(request.getUrl().toString())) {
                    peerDataPageReady=false;
                    if(!pendingPeerData.isEmpty()) {
                        pendingPeerData.clear();
                        toast("Could not load saved decks/artwork. Snapshot was not created.");
                    }
                }
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
        // Recover interrupted peer inventory commits before opening native UI.
        // Do not continue if restoring the backed-up SQLite mirrors fails.
        try {
            NativePeerInventoryApply.recoverIfPending(this,durable,catalog);
        } catch(Exception recoveryError) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Arcane Vault recovery required")
                .setMessage("An interrupted peer update could not be recovered. "+
                    "The previous collection backup remains on this device. "+
                    "Do not reinstall or clear app storage.\n\n"+
                    recoveryError.getMessage())
                .setCancelable(false).setPositiveButton("Close app",(d,w)->finish()).show();
            return;
        }
        artwork = new ArtworkCache(getApplicationContext());
        try {
            try { server = new HttpServerLite(APP_PORT); }
            catch (IOException inUse) {
                // Another local service may briefly own the port. Native inventories
                // remain safe even if a temporary alternative port is required.
                server = new HttpServerLite(0);
                android.util.Log.w("ArcaneVault","Stable local port unavailable; continuing with native durable inventories",inUse);
            }
            workers.execute(server);
            appUrl = "http://127.0.0.1:" + server.getPort() + "/";
            startLegacyRecovery();
        } catch(IOException e) {toast("Unable to start local app service: " + e.getMessage());}
    }

    /** Native-first shell. Preserve legacy WebView and origin recovery. */
    private void showNativeHome() {
        nativeHomeVisible = true;
        View nativeRoot=NativeCompanionScreen.create(this, durable, catalog, artwork,
            this::showLegacyTools, callback -> {
                // Read the WebView's authoritative saved-deck library. The native
                // screen is a projection and never writes a duplicate database.
                browser.evaluateJavascript("(function(){try{return localStorage.getItem('vault_libraryV3')||'[]'}catch(e){return '[]'}})()",
                    encoded -> {
                        try {
                            Object value=new org.json.JSONTokener(encoded).nextValue();
                            callback.accept(value instanceof String?(String)value:"[]");
                        }catch(Exception error){callback.accept("[]");}
                    });
            });
        NativeSystemInsets.apply(this,nativeRoot);
        setContentView(nativeRoot);
    }

    /** Fetch saved decks/artwork from the authoritative background WebView.
     * The native Data & Sync screen must work after a cold start: it never
     * needs users to open Legacy tools first or creates a duplicate store.
     */
    private interface PeerDataCallback { void use(String decks,String art); }
    private void withPeerData(PeerDataCallback callback) {
        if(appUrl==null || browser==null) {
            toast("Collection source is unavailable. Snapshot was not created.");
            return;
        }
        if(peerDataPageReady && appUrl.equals(browser.getUrl())) {
            readPeerData(callback);
            return;
        }
        pendingPeerData.add(callback);
        // During normal startup finishLegacyRecovery already loads this URL.
        // Only request a background load if we're not currently navigating
        // to the canonical app origin. Never replace an in-progress recovery.
        String current=browser.getUrl();
        if(nativeHomeVisible && (current==null || !current.startsWith(appUrl))) {
            browser.loadUrl(appUrl);
        }
    }
    private void readPeerData(PeerDataCallback callback) {
        if(!peerDataPageReady || !appUrl.equals(browser.getUrl())) {
            pendingPeerData.add(callback);
            return;
        }
        browser.evaluateJavascript(
            "(function(){try{let d=localStorage.getItem('vault_libraryV3');"+
            "let a=localStorage.getItem('vault_preferredArtworkV1');"+
            "return JSON.stringify({decks:JSON.parse(d||'[]'),art:JSON.parse(a||'{}')})"+
            "}catch(e){return 'SYNC_ERROR'}})()", result -> {
                try {
                    if(!peerDataPageReady || !appUrl.equals(browser.getUrl()))
                        throw new IllegalStateException("Collection source navigated during export");
                    Object decoded=new org.json.JSONTokener(result).nextValue();
                    if(!(decoded instanceof String) || "SYNC_ERROR".equals(decoded))
                        throw new IllegalStateException("Stored deck data is not readable");
                    JSONObject envelope=new JSONObject((String)decoded);
                    callback.use(envelope.getJSONArray("decks").toString(),
                        envelope.getJSONObject("art").toString());
                } catch(Exception error) {
                    toast("Peer sync cannot read decks/artwork: "+error.getMessage());
                }
            });
    }
    private String peerId() {
        String id=durable.getString("peer_sync_device_id_v1",null);
        if(id!=null)return id;
        id=java.util.UUID.randomUUID().toString();
        if(!durable.edit().putString("peer_sync_device_id_v1",id).commit())
            throw new IllegalStateException("Could not save device identity");
        return id;
    }
    /** One-use LAN peer link entry: never sync by guessing an unverified URL. */
    /** Reuse the established in-app Google Code Scanner without camera permissions. */
    void scanWirelessPeerQr() {
        GmsBarcodeScannerOptions options=new GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
        GmsBarcodeScanning.getClient(this,options).startScan()
            .addOnSuccessListener(barcode->{
                String url=barcode.getRawValue();
                try{com.arcanevault.mobile.next.NativeWirelessPeerClient.validate(url);}
                catch(Exception error){
                    toast("Not a valid Arcane Vault Wi-Fi QR: "+error.getMessage());return;
                }
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Windows ↔ Android QR sync")
                    .setMessage("What did you select on Windows? The transfer will only be reviewed; "+
                        "it will not modify either collection. Use trusted home Wi-Fi.")
                    .setNegativeButton("Cancel",null)
                    .setNeutralButton("Receive from PC",(d,w)->wirelessPeerReview(false,url))
                    .setPositiveButton("Send to PC",(d,w)->wirelessPeerReview(true,url)).show();
            })
            .addOnFailureListener(error->toast(
                "QR scanner unavailable. Use the one-time link instead: "+error.getMessage()));
    }

    /** Scan a Windows one-use QR and prepare an opt-in inventory merge. */
    void scanWirelessPeerQrForApply() {
        GmsBarcodeScannerOptions options=new GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
        GmsBarcodeScanning.getClient(this,options).startScan()
            .addOnSuccessListener(barcode->{
                String link=barcode.getRawValue();
                try{com.arcanevault.mobile.next.NativeWirelessPeerClient.validate(link);}
                catch(Exception ex){toast("Invalid Windows peer QR: "+ex.getMessage());return;}
                wirelessPeerReview(false,link,true);
            })
            .addOnFailureListener(error->toast("QR scanner unavailable: "+error.getMessage()));
    }

    void wirelessPeerReview(boolean send) { wirelessPeerReview(send,null,false); }
    void wirelessPeerReview(boolean send,String scannedLink) {
        wirelessPeerReview(send,scannedLink,false);
    }
    private void wirelessPeerReview(boolean send,String scannedLink,boolean prepareApply) {
        android.widget.EditText address=new android.widget.EditText(this);
        address.setSingleLine(true);
        address.setTextSize(13);
        address.setHint("http://192.168.x.x:port/arcane-peer/token");
        if(scannedLink!=null)address.setText(scannedLink);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new android.app.AlertDialog.Builder(this)
            .setTitle(send?"Send Android snapshot to Windows":"Receive Windows snapshot")
            .setMessage("Start the matching Wi-Fi offer in Windows Data & Sync, then paste its one-time link. "+
                "Use a trusted home Wi-Fi network: the current LAN transfer is HTTP and unencrypted. "+
                (prepareApply?"After downloading you will select a common baseline and approve "+
                "any inventory write. It is not automatic sync.":"Only a read-only comparison will be performed."))
            .setView(address)
            .setNegativeButton("Cancel",null)
            .setPositiveButton(send?"Send for review":prepareApply?"Download & review":"Compare",(dialog,which)->{
                String link=address.getText().toString().trim();
                try{com.arcanevault.mobile.next.NativeWirelessPeerClient.validate(link);}
                catch(Exception error){toast("Invalid wireless link: "+error.getMessage());return;}
                withPeerData((decks,art)->{
                    try{
                        JSONObject local=com.arcanevault.mobile.next.NativePeerSync
                            .export(durable,peerId(),decks,art);
                        workers.execute(()->{
                            try{
                                if(send) {
                                    com.arcanevault.mobile.next.NativeWirelessPeerClient.send(link,local);
                                    runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                                        .setTitle("Snapshot sent for Windows review")
                                        .setMessage("Windows received the Android snapshot. "+
                                            "No collection or deck was changed on either device.")
                                        .setPositiveButton("OK",null).show());
                                } else {
                                    JSONObject remote=com.arcanevault.mobile.next.NativeWirelessPeerClient
                                        .receive(link);
                                    if(prepareApply) {
                                        // No write occurs during Wi-Fi transfer.
                                        // Store latest snapshot only until user selects
                                        // an earlier baseline with Android's file picker.
                                        runOnUiThread(()->chooseWirelessApplyBaseline(remote));
                                        return;
                                    }
                                    JSONObject preview=com.arcanevault.mobile.next.NativePeerSync
                                        .preview(local,remote);
                                    org.json.JSONArray changes=preview.getJSONArray("changes");
                                    StringBuilder message=new StringBuilder()
                                        .append("Differences: ").append(changes.length())
                                        .append("\nPotential conflicts: ")
                                        .append(preview.getInt("conflicts"))
                                        .append("\nMatching records: ")
                                        .append(preview.getInt("equal"))
                                        .append("\n\nRead-only wireless review. Nothing was applied.");
                                    for(int i=0;i<Math.min(12,changes.length());i++){
                                        JSONObject diff=changes.getJSONObject(i);
                                        message.append("\n").append(diff.optString("action"))
                                            .append(" · ").append(diff.optString("group"))
                                            .append(" · ").append(diff.optString("key"));
                                    }
                                    if(changes.length()>12)message.append("\n… and ")
                                        .append(changes.length()-12).append(" more");
                                    boolean same=changes.length()==0 &&
                                        !local.optString("deviceId").equalsIgnoreCase(
                                            remote.optString("deviceId"));
                                    runOnUiThread(()->{
                                        android.app.AlertDialog.Builder baselineDialog=
                                            new android.app.AlertDialog.Builder(this)
                                            .setTitle("Windows peer snapshot")
                                            .setMessage(message.toString())
                                            .setPositiveButton("Close",null);
                                        if(same)baselineDialog.setNeutralButton(
                                            "Remember shared baseline",
                                            (d,w)->rememberMatchingWirelessBaseline(remote));
                                        baselineDialog.show();
                                    });
                                }
                            }catch(Exception error){runOnUiThread(()->toast(
                                "Wi-Fi peer review failed: "+error.getMessage()));}
                        });
                    }catch(Exception error){toast("Cannot export local peer data: "+error.getMessage());}
                });
            }).show();
    }

    /** A checkpoint is permitted only when a newly refreshed phone snapshot
     * matches every recognized record in the received peer snapshot. */
    private void rememberMatchingWirelessBaseline(JSONObject received) {
        withPeerData((decks,art)->{
            try {
                JSONObject current=com.arcanevault.mobile.next.NativePeerSync
                    .export(durable,peerId(),decks,art);
                workers.execute(()->{
                    try {
                        String path=NativePeerCheckpoint.saveIdentical(this,current,received);
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Matching baseline remembered")
                            .setMessage("The identical peer snapshot was saved for later "+
                                "three-way comparisons. Reconfirm on Windows to "+
                                "establish a checkpoint there too. This is local "+
                                "confirmation, not cryptographic pairing.\n\n"+path)
                            .setPositiveButton("OK",null).show());
                    } catch(Exception ex) {
                        runOnUiThread(()->toast("Baseline not saved: "+ex.getMessage()));
                    }
                });
            } catch(Exception ex) {
                toast("Could not verify current records: "+ex.getMessage());
            }
        });
    }

    private void chooseWirelessApplyBaseline(JSONObject incoming) {
        pendingWirelessIncoming=incoming;
        if(NativePeerCheckpoint.exists(this,incoming.optString("deviceId",""))) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Use remembered peer baseline?")
                .setMessage("An earlier identical peer checkpoint is stored locally. "+
                    "Use it to prepare this inventory update? You will still review "+
                    "the three-way result and approve any writes.")
                .setNegativeButton("Choose file",(d,w)->selectWirelessBaselineFile())
                .setNeutralButton("Cancel",(d,w)->pendingWirelessIncoming=null)
                .setPositiveButton("Use saved checkpoint",(d,w)->{
                    JSONObject received=pendingWirelessIncoming;
                    pendingWirelessIncoming=null;
                    if(received!=null)
                        applyPeerInventorySnapshot(()->received,
                            ()->NativePeerCheckpoint.load(this,received.getString("deviceId")));
                }).show();
            return;
        }
        selectWirelessBaselineFile();
    }
    private void selectWirelessBaselineFile() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Wireless snapshot received")
            .setMessage("Choose the previously shared, unchanged PC/Android "+
                "baseline snapshot. Without a genuine common baseline we cannot "+
                "safely infer which device edited which cards. The Wi-Fi "+
                "transfer has NOT changed your collection.")
            .setNegativeButton("Cancel",(d,w)->pendingWirelessIncoming=null)
            .setPositiveButton("Select common baseline",(d,w)->{
                Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("application/json");
                startActivityForResult(picker,PEER_WIRELESS_BASE_PICK);
            }).show();
    }

    /** Save a durable peer-format snapshot without opening the Android file picker.
     * This is a local reference/recovery snapshot, NOT a confirmed common
     * Windows/Android baseline. Retain every previous snapshot.
     */
    void createSyncSnapshot() {
        withPeerData((decks,art)->{
            try {
                JSONObject peer=com.arcanevault.mobile.next.NativePeerSync
                    .export(durable,peerId(),decks,art);
                byte[] bytes=peer.toString().getBytes(StandardCharsets.UTF_8);
                if(bytes.length<20 ||
                    bytes.length>com.arcanevault.mobile.next.NativePeerSync.MAX_BYTES)
                    throw new IllegalArgumentException("Peer snapshot is empty or too large");
                workers.execute(()->{
                    try {
                        java.io.File folder=new java.io.File(getFilesDir(),"sync-snapshots");
                        if(!folder.isDirectory()&&!folder.mkdirs())
                            throw new IOException("Cannot create sync snapshot directory");
                        String stamp=java.time.format.DateTimeFormatter
                            .ofPattern("yyyyMMdd-HHmmss-SSS",java.util.Locale.US)
                            .withZone(java.time.ZoneOffset.UTC)
                            .format(java.time.Instant.now());
                        java.io.File file=new java.io.File(folder,"ArcaneVault-Android-"+
                            stamp+"-"+java.util.UUID.randomUUID().toString().substring(0,8)+".json");
                        // AtomicFile writes a staged file and then replaces it
                        // atomically; the random unique filename prevents replacing
                        // any previously saved snapshot.
                        android.util.AtomicFile atomic=new android.util.AtomicFile(file);
                        java.io.FileOutputStream output=null;
                        try {
                            output=atomic.startWrite();
                            output.write(bytes);
                            atomic.finishWrite(output);
                        }catch(Exception error){
                            if(output!=null)atomic.failWrite(output);
                            throw error;
                        }
                        byte[] saved=java.nio.file.Files.readAllBytes(file.toPath());
                        if(!java.security.MessageDigest.isEqual(
                            java.security.MessageDigest.getInstance("SHA-256").digest(bytes),
                            java.security.MessageDigest.getInstance("SHA-256").digest(saved)))
                            throw new IOException("Saved sync snapshot did not verify");
                        JSONObject check=new JSONObject(new String(saved,StandardCharsets.UTF_8));
                        if(!com.arcanevault.mobile.next.NativePeerSync.FORMAT.equals(
                                check.optString("format")) ||
                            !peer.getString("deviceId").equals(check.optString("deviceId")) ||
                            !peer.getString("revision").equals(check.optString("revision")))
                            throw new IOException("Saved sync snapshot identity mismatch");
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Sync snapshot saved")
                            .setMessage("Your Android collection, decks and artwork " +
                                "preferences were saved locally with no file picker. " +
                                "The live data was not changed. Older snapshots were kept.\\n\\n" +
                                file.getAbsolutePath() + "\\n\\n" +
                                "This is a local reference snapshot, not proof that " +
                                "Windows has the same common sync baseline.")
                            .setPositiveButton("OK",null).show());
                    }catch(Exception error){
                        runOnUiThread(()->toast("Snapshot could not be saved: "+
                            error.getMessage()));
                    }
                });
            }catch(Exception error){
                toast("Snapshot could not be created: "+error.getMessage());
            }
        });
    }

    void exportPeerSync() {
        withPeerData((decks,art)->{
            try{
                JSONObject envelope=com.arcanevault.mobile.next.NativePeerSync
                    .export(durable,peerId(),decks,art);
                byte[] bytes=envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(bytes.length>com.arcanevault.mobile.next.NativePeerSync.MAX_BYTES)
                    throw new IllegalArgumentException("Peer snapshot too large");
                pendingExport=bytes;
                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType("application/json");
                save.putExtra(Intent.EXTRA_TITLE,"ArcaneVault-PeerSync-"+
                    java.time.LocalDate.now()+".json");
                startActivityForResult(save,FILE_SAVE);
            }catch(Exception error){pendingExport=null;toast("Peer export failed: "+error.getMessage());}
        });
    }
    void previewPeerSync() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("application/json");
        startActivityForResult(pick,PEER_PICK);
    }
    void refreshAfterPeerInventory() {
        // Existing Legacy tools cache inventory in JavaScript memory; reload
        // their current origin after a native commit so they cannot continue
        // displaying an obsolete pre-sync snapshot.
        if(browser!=null && appUrl!=null && browser.getUrl()!=null &&
            browser.getUrl().startsWith(appUrl))browser.reload();
    }

    void reviewAndApplyPeerInventory() {
        pendingPeerApply=true;
        selectLatestPeerForThreeWay();
    }
    void previewThreeWayPeerSync() {
        pendingPeerApply=false;
        selectLatestPeerForThreeWay();
    }
    private void selectLatestPeerForThreeWay() {
        Intent latest=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        latest.addCategory(Intent.CATEGORY_OPENABLE);
        latest.setType("application/json");
        startActivityForResult(latest,PEER_3WAY_PICK);
    }
    private void readPeerBaseline(Uri latest) {
        if(latest==null)return;
        // When a Windows peer has already been confirmed identical, reuse its
        // durable checkpoint without making users hunt for the JSON file.
        // This is only a source for the same reviewed three-way merge; it does
        // not authorize unattended writes or skip the approval dialog.
        if(pendingPeerApply){
            workers.execute(()->{
                try{
                    JSONObject remote=loadPeerFile(latest);
                    String peerDevice=remote.getString("deviceId");
                    if(NativePeerCheckpoint.exists(this,peerDevice)){
                        JSONObject previous=NativePeerCheckpoint.load(this,peerDevice);
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Use remembered Windows baseline?")
                            .setMessage("Arcane Vault found the previously confirmed "+
                                "matching snapshot for this Windows device. Compare "+
                                "the two versions and review only the differences? "+
                                "No changes will be applied without approval.")
                            .setNegativeButton("Choose file",(d,w)->selectManualPeerBaseline(latest))
                            .setNeutralButton("Cancel",(d,w)->pendingPeerComparison=null)
                            .setPositiveButton("Compare changes",(d,w)->{
                                pendingPeerComparison=null;
                                applyPeerInventorySnapshot(()->remote,()->previous);
                            }).show());
                        return;
                    }
                    runOnUiThread(()->selectManualPeerBaseline(latest));
                }catch(Exception error){
                    runOnUiThread(()->toast("Could not inspect Windows snapshot: "+
                        error.getMessage()));
                }
            });
            return;
        }
        selectManualPeerBaseline(latest);
    }
    private void selectManualPeerBaseline(Uri latest) {
        pendingPeerComparison=latest;
        new android.app.AlertDialog.Builder(this)
            .setTitle("Select your shared baseline")
            .setMessage("Choose the older snapshot that BOTH devices last agreed upon. "+
                "This is a read-only three-way comparison; the baseline is not authenticated yet.")
            .setNegativeButton("Cancel",(d,w)->pendingPeerComparison=null)
            .setPositiveButton("Choose baseline",(d,w)->{
                Intent baseline=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                baseline.addCategory(Intent.CATEGORY_OPENABLE);
                baseline.setType("application/json");
                startActivityForResult(baseline,PEER_BASE_PICK);
            }).show();
    }
    private JSONObject loadPeerFile(Uri uri)throws Exception {
        try(java.io.InputStream input=getContentResolver().openInputStream(uri)){
            if(input==null)throw new IOException("Peer file unavailable");
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] chunk=new byte[8192];int n;
            while((n=input.read(chunk))!=-1){
                if(bytes.size()+n>com.arcanevault.mobile.next.NativePeerSync.MAX_BYTES)
                    throw new IOException("Peer file exceeds 30 MiB");
                bytes.write(chunk,0,n);
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }
    }
    private interface PeerSnapshotLoader { JSONObject load()throws Exception; }
    private void applyThreeWayInventoryUris(Uri latest,Uri baseline) {
        if(latest==null||baseline==null||latest.equals(baseline)){
            toast("Choose distinct peer and previously shared baseline files.");return;
        }
        applyPeerInventorySnapshot(()->loadPeerFile(latest),baseline);
    }
    private void applyPeerInventorySnapshot(PeerSnapshotLoader incoming,Uri baseline) {
        if(baseline==null)return;
        applyPeerInventorySnapshot(incoming,()->loadPeerFile(baseline));
    }
    /** Persist the entire pre-write workspace, including authoritative WebView
     * decks and artwork. Never overwrite the previous recovery files. */
    private String saveFullPeerRecovery(JSONObject snapshot)throws Exception {
        byte[] source=snapshot.toString().getBytes(StandardCharsets.UTF_8);
        if(source.length<20 ||
                source.length>com.arcanevault.mobile.next.NativePeerSync.MAX_BYTES)
            throw new IOException("Full sync backup invalid or too large");
        java.io.File folder=new java.io.File(getFilesDir(),"peer-full-recovery");
        if(!folder.isDirectory()&&!folder.mkdirs())
            throw new IOException("Cannot create full sync recovery directory");
        java.io.File dest=new java.io.File(folder,
            "pre-peer-"+java.util.UUID.randomUUID().toString()+".json");
        android.util.AtomicFile atomic=new android.util.AtomicFile(dest);
        java.io.FileOutputStream output=null;
        try {
            output=atomic.startWrite();
            output.write(source);
            output.getFD().sync();
            atomic.finishWrite(output);
            output=null;
        } finally {
            if(output!=null)atomic.failWrite(output);
        }
        byte[] saved=java.nio.file.Files.readAllBytes(dest.toPath());
        if(!java.security.MessageDigest.isEqual(
                java.security.MessageDigest.getInstance("SHA-256").digest(source),
                java.security.MessageDigest.getInstance("SHA-256").digest(saved)))
            throw new IOException("Full sync backup verification failed");
        JSONObject check=new JSONObject(new String(saved,StandardCharsets.UTF_8));
        if(!NativePeerInventoryApply.canonicalEqual(
                snapshot.getJSONObject("data"),check.getJSONObject("data")))
            throw new IOException("Full sync backup contents changed");
        return dest.getAbsolutePath();
    }

    /** Update the authoritative WebView localStorage without creating a
     * second deck database. Callback reports read-after-write verification.
     * The full native snapshot remains available for manual recovery. */
    private void writePeerWebData(JSONObject expected,JSONObject target,
            java.util.function.Consumer<String> done) {
        if(browser==null || !peerDataPageReady || appUrl==null ||
                !appUrl.equals(browser.getUrl())) {
            done.accept("Saved deck origin is not loaded");
            return;
        }
        String js="(function(){try{"+
            "const keys=['vault_libraryV3','vault_preferredArtworkV1'];"+
            "const old=keys.map(k=>localStorage.getItem(k));"+
            "const before=["+
                JSONObject.quote(expected.optJSONArray("deckLibrary").toString())+","+
                JSONObject.quote(expected.optJSONObject("preferredArtwork").toString())+
            "];"+
            "const next=["+
                JSONObject.quote(target.optJSONArray("deckLibrary").toString())+","+
                JSONObject.quote(target.optJSONObject("preferredArtwork").toString())+
            "];"+
            "const norm=(s)=>JSON.stringify(JSON.parse(s));"+
            "if(keys.some((k,i)=>norm(old[i]|| (i===0?'[]':'{}'))!==norm(before[i])))"+
                "return JSON.stringify({ok:false,error:'Decks or artwork changed since review'});"+
            "try{keys.forEach((k,i)=>localStorage.setItem(k,next[i]));"+
                "if(keys.some((k,i)=>localStorage.getItem(k)!==next[i]))"+
                    "throw new Error('WebView readback mismatch');"+
                "return JSON.stringify({ok:true});"+
            "}catch(e){keys.forEach((k,i)=>{try{"+
                "if(old[i]===null)localStorage.removeItem(k);"+
                "else localStorage.setItem(k,old[i]);}catch(ignored){}});"+
                "return JSON.stringify({ok:false,error:String(e)});}"+
            "}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()";
        browser.evaluateJavascript(js,result->{
            try {
                Object decoded=new org.json.JSONTokener(result).nextValue();
                JSONObject status=new JSONObject((String)decoded);
                done.accept(status.optBoolean("ok")?null:
                    status.optString("error","WebView write not verified"));
            } catch(Exception ex) {
                done.accept("WebView write verification failed: "+ex.getMessage());
            }
        });
    }

    private void applyPeerInventorySnapshot(PeerSnapshotLoader incoming,
            PeerSnapshotLoader baseline) {
        withPeerData((decks,art)->{
            try {
                JSONObject local=com.arcanevault.mobile.next.NativePeerSync
                    .export(durable,peerId(),decks,art);
                workers.execute(()->{
                    try {
                        JSONObject remote=incoming.load();
                        JSONObject original=baseline.load();
                        JSONObject proposal=com.arcanevault.mobile.next.NativePeerSync
                            .prepareMerge(local,remote,original);
                        JSONObject before=local.getJSONObject("data");
                        JSONObject target=proposal.getJSONObject("candidate");
                        boolean decksChanged=!NativePeerInventoryApply.canonicalEqual(
                            before.getJSONArray("deckLibrary"),target.getJSONArray("deckLibrary"));
                        boolean artworkChanged=!NativePeerInventoryApply.canonicalEqual(
                            before.getJSONObject("preferredArtwork"),
                            target.getJSONObject("preferredArtwork"));
                        JSONObject delta=com.arcanevault.mobile.next.NativePeerSync
                            .inventoryDelta(local,proposal);
                        int inventoryCount=delta.getInt("changedEntries");
                        if(inventoryCount==0&&!decksChanged&&!artworkChanged) {
                            runOnUiThread(()->toast("All peer records already match."));
                            return;
                        }
                        org.json.JSONArray changes=delta.getJSONArray("changes");
                        StringBuilder preview=new StringBuilder()
                            .append("Inventory entries: ").append(inventoryCount)
                            .append("\\nCopies to add: +").append(delta.getLong("addedCopies"))
                            .append("\\nCopies to remove: -").append(delta.getLong("removedCopies"))
                            .append("\\nSaved decks: ").append(decksChanged?"Update":"Unchanged")
                            .append("\\nPreferred artwork: ").append(artworkChanged?"Update":"Unchanged")
                            .append("\\nLocal-only edits preserved: ")
                            .append(proposal.optInt("localChanges",0)).append("\\n\\n");
                        for(int i=0;i<Math.min(10,changes.length());i++){
                            JSONObject entry=changes.getJSONObject(i);
                            long quantity=entry.getLong("delta");
                            preview.append(quantity>0?"+":"").append(quantity)
                                .append("  ").append(entry.optString("name","Unknown")).append("\\n");
                        }
                        preview.append("\\nThis is a reviewed, opt-in merge. A full verified "+
                            "Android snapshot is saved BEFORE ANY write. Inventory uses "+
                            "its existing crash-recovery journal. Deck/artwork updates "+
                            "use WebView storage with readback verification. If the app "+
                            "stops between writes, restore the saved pre-sync snapshot. "+
                            "Windows is not changed.");
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Apply reviewed peer changes?")
                            .setMessage(preview.toString())
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Save backup & apply",(dialog,which)->{
                                // Re-read the live WebView state at approval time.
                                withPeerData((freshDecks,freshArt)->{
                                    try {
                                        JSONObject live=com.arcanevault.mobile.next.NativePeerSync
                                            .export(durable,peerId(),freshDecks,freshArt);
                                        if(!NativePeerInventoryApply.canonicalEqual(
                                                live.getJSONObject("data"),
                                                local.getJSONObject("data")))
                                            throw new IllegalStateException(
                                                "Android data changed since review; compare again");
                                        workers.execute(()->{
                                            String recovery=null;
                                            try {
                                                String saved=saveFullPeerRecovery(live);
                                                boolean inventoryNeeded=inventoryCount>0;
                                                if(inventoryNeeded)
                                                    NativePeerInventoryApply.applyInventoryPart(
                                                        this,durable,catalog,live,proposal);
                                                String finalBackup=saved;
                                                runOnUiThread(()->{
                                                    java.util.function.Consumer<String> report=error->{
                                                        if(error==null){
                                                            refreshAfterPeerInventory();
                                                            new android.app.AlertDialog.Builder(this)
                                                                .setTitle("Android peer changes applied")
                                                                .setMessage("Reviewed inventory/deck/artwork changes "+
                                                                    "have been written and verified.\\n\\n"+
                                                                    "Pre-sync full backup: "+finalBackup+
                                                                    "\\n\\nConfirm a new matching baseline "+
                                                                    "on both devices before future merges.")
                                                                .setPositiveButton("OK",null).show();
                                                        } else {
                                                            new android.app.AlertDialog.Builder(this)
                                                                .setTitle("Peer apply needs recovery")
                                                                .setMessage("Inventory may already have changed. "+
                                                                    "Deck/artwork write was not verified: "+error+
                                                                    "\\n\\nFull pre-sync backup: "+finalBackup+
                                                                    "\\nDo not sync again until restored or inspected.")
                                                                .setPositiveButton("OK",null).show();
                                                        }
                                                    };
                                                    if(decksChanged||artworkChanged)
                                                        writePeerWebData(before,target,report);
                                                    else report.accept(null);
                                                });
                                            } catch(Exception failure) {
                                                String path=recovery;
                                                runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                                                    .setTitle("Peer changes not fully applied")
                                                    .setMessage(failure.getMessage()+
                                                        "\\n\\nCheck native recovery before retrying."+
                                                        (path==null?"":"\\nBackup: "+path))
                                                    .setPositiveButton("OK",null).show());
                                            }
                                        });
                                    } catch(Exception failure) {
                                        toast("Sync review expired: "+failure.getMessage());
                                    }
                                });
                            }).show());
                    } catch(Exception error) {
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Peer merge rejected")
                            .setMessage(error.getMessage()+
                                "\\n\\nNo inventory, deck or artwork data was changed.")
                            .setPositiveButton("OK",null).show());
                    }
                });
            } catch(Exception error) {
                toast("Cannot read Android sync snapshot: "+error.getMessage());
            }
        });
    }

    private void previewThreeWayPeerUris(Uri latest,Uri baseline) {
        if(latest==null||baseline==null)return;
        if(latest.equals(baseline)){toast("Peer and baseline must be different files");return;}
        withPeerData((decks,art)->{
            try{
                JSONObject local=com.arcanevault.mobile.next.NativePeerSync
                    .export(durable,peerId(),decks,art);
                workers.execute(()->{
                    try {
                        JSONObject incoming=loadPeerFile(latest);
                        JSONObject ancestor=loadPeerFile(baseline);
                        JSONObject plan=com.arcanevault.mobile.next.NativePeerSync
                            .compareWithBaseline(local,incoming,ancestor);
                        org.json.JSONArray records=plan.getJSONArray("records");
                        StringBuilder message=new StringBuilder()
                            .append("Incoming-only changes: ").append(plan.getInt("incomingOnly"))
                            .append("\nAndroid-only changes: ").append(plan.getInt("localOnly"))
                            .append("\nConcurrent conflicts: ").append(plan.getInt("conflicts"))
                            .append("\n\nNo data changed. The selected baseline has not been authenticated as a paired checkpoint.");
                        for(int i=0;i<Math.min(16,records.length());i++){
                            JSONObject row=records.getJSONObject(i);
                            message.append("\n").append(row.getString("decision"))
                                .append(" · ").append(row.getString("group"))
                                .append(" · ").append(row.getString("key"));
                        }
                        if(records.length()>16)message.append("\n… and ")
                            .append(records.length()-16).append(" more");
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Three-way peer comparison")
                            .setMessage(message.toString())
                            .setPositiveButton("Close",null).show());
                    }catch(Exception error){runOnUiThread(()->toast(
                        "Three-way comparison rejected: "+error.getMessage()));}
                });
            }catch(Exception error){toast("Could not read local peer data: "+error.getMessage());}
        });
    }

    private void previewPeerUri(Uri uri) {
        if(uri==null)return;
        withPeerData((decks,art)->{
            try {
                JSONObject local=com.arcanevault.mobile.next.NativePeerSync
                    .export(durable,peerId(),decks,art);
                workers.execute(()->{
                    try(java.io.InputStream input=getContentResolver().openInputStream(uri)){
                        if(input==null)throw new IOException("Peer file unavailable");
                        java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
                        byte[] chunk=new byte[8192];int n;
                        while((n=input.read(chunk))!=-1){
                            if(buffer.size()+n>com.arcanevault.mobile.next.NativePeerSync.MAX_BYTES)
                                throw new IOException("Peer file exceeds 30 MiB limit");
                            buffer.write(chunk,0,n);
                        }
                        JSONObject remote=new JSONObject(buffer.toString("UTF-8"));
                        JSONObject result=com.arcanevault.mobile.next.NativePeerSync
                            .preview(local,remote);
                        org.json.JSONArray changes=result.getJSONArray("changes");
                        StringBuilder message=new StringBuilder()
                            .append(changes.length()).append(" record differences · ")
                            .append(result.getInt("conflicts")).append(" existing/deleted record conflicts")
                            .append("\n").append(result.getInt("equal")).append(" matching records")
                            .append("\n\nThis is a dry-run preview. No collection or deck has been changed. ")
                            .append("Without a shared sync baseline, we do not auto-apply conflicts.");
                        for(int i=0;i<Math.min(changes.length(),10);i++){
                            JSONObject row=changes.getJSONObject(i);
                            message.append("\n").append(row.getString("action"))
                                .append(" · ").append(row.getString("group")).append(": ")
                                .append(row.getString("key"));
                        }
                        if(changes.length()>10)message.append("\n… and ")
                            .append(changes.length()-10).append(" more");
                        runOnUiThread(()->new android.app.AlertDialog.Builder(this)
                            .setTitle("Windows / Android peer comparison")
                            .setMessage(message.toString()).setPositiveButton("Close",null).show());
                    }catch(Exception error){runOnUiThread(()->toast(
                        "Could not preview peer file: "+error.getMessage()));}
                });
            }catch(Exception error){toast("Peer preview unavailable: "+error.getMessage());}
        });
    }

    /** Preferred artwork uses the existing legacy library key; no duplicate map. */
    void savePreferredArtwork(String name,String printingId,String imageUrl){
        if(name==null||name.isEmpty()||name.length()>200||imageUrl==null||
            !imageUrl.startsWith("https://cards.scryfall.io/"))return;
        String key=JSONObject.quote(name.toLowerCase(java.util.Locale.ROOT));
        String id=JSONObject.quote(printingId==null?"":printingId);
        String image=JSONObject.quote(imageUrl);
        String script="(function(){try{let p=JSON.parse(localStorage.getItem('vault_preferredArtworkV1')||'{}');"+
            "p["+key+"]={image:"+image+",printingId:"+id+"};"+
            "localStorage.setItem('vault_preferredArtworkV1',JSON.stringify(p));return 'saved'}"+
            "catch(e){return 'error'}})()";
        browser.evaluateJavascript(script,result->{
            if("\"saved\"".equals(result))Toast.makeText(this,"Preferred artwork saved",Toast.LENGTH_SHORT).show();
            else Toast.makeText(this,"Artwork preference could not be saved",Toast.LENGTH_LONG).show();
        });
    }

    private void showLegacyTools() {
        nativeHomeVisible = false;
        setContentView(browser);
        if (appUrl != null) browser.loadUrl(appUrl);
    }

    // Enumerate WebView's previous localhost origins. Old builds used an ephemeral
    // port as the origin, so their localStorage is still on disk but invisible.
    private void startLegacyRecovery() {
        WebStorage.getInstance().getOrigins(origins -> runOnUiThread(() -> {
            recoveryOrigins.clear();
            if (origins != null) for (Object originKey : origins.keySet()) {
                String origin = String.valueOf(originKey);
                if (origin.matches("http://127\\.0\\.0\\.1:[0-9]{2,5}")
                        && !origin.equals("http://127.0.0.1:" + APP_PORT))
                    recoveryOrigins.add(origin);
            }
            // Scan older data-rich origins first. Never add quantities from
            // different snapshots together: they may contain the same cards.
            recoveryOrigins.sort((a,b)->Long.compare(
                    origins.get(b) instanceof WebStorage.Origin ? ((WebStorage.Origin)origins.get(b)).getUsage() : 0L,
                    origins.get(a) instanceof WebStorage.Origin ? ((WebStorage.Origin)origins.get(a)).getUsage() : 0L));
            // Scan every app-local origin, including older versions. No old data is
            // deleted until a verified copy exists in the stable storage location.
            continueLegacyRecovery();
        }));
    }
    private void continueLegacyRecovery() {
        if (recoveryIndex >= recoveryOrigins.size()) {
            finishLegacyRecovery();
            return;
        }
        int expectedIndex=recoveryIndex;
        browser.loadUrl(recoveryOrigins.get(recoveryIndex) + RECOVERY_PATH);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if(recoveryIndex==expectedIndex) finishRecoveryOrigin(null);
        },3000);
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
            if(current==null){
                if(durable.edit().putString(key,candidate).commit()) imported++;
            }
        }
        // The catalog database contains ownership mirrors independent of WebView.
        // Recover aggregate name/quantity records only if no complete inventory
        // survived; never replace successfully migrated printing-specific data.
        int indexedRecovered=0;
        try{
            JSONObject indexed=catalog.recoverIndexedOwnership();
            for(String kind:new String[]{"arena","physical"}){
                String key="arena".equals(kind)?"vault_cards":"vault_physicalCollectionV1";
                String current=durable.getString(key,null);
                if(current!=null)continue;
                org.json.JSONArray rows=indexed.optJSONArray(kind);
                if(rows!=null&&rows.length()>0&&durable.edit().putString(key,rows.toString()).commit())
                    indexedRecovered++;
            }
        }catch(Exception ex){android.util.Log.w("ArcaneVault","Indexed ownership recovery unavailable",ex);}
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
            // A conflicting preference/deck/library snapshot must not be erased
            // automatically. Only proven duplicate origins qualify for cleanup.
            if(compatible)for(java.util.Iterator<String> it=data.keys();it.hasNext();){
                String key=it.next();
                if(key.equals("vault_cards")||key.equals("vault_physicalCollectionV1"))continue;
                if(!data.optString(key,"").equals(recoveredSettings.optString(key,""))){compatible=false;legacyConflicts++;break;}
            }
            if(compatible)matchedRecoveryOrigins.add(entry.getKey());
        }
        android.util.Log.i("ArcaneVault","Legacy origins scanned="+legacyScanned+
                ", collection types recovered="+imported+", conflicting snapshots="+legacyConflicts);
        durable.edit().putString("recoveryReport","Checked "+legacyScanned+" older storage locations; restored "+imported+" full collection inventories and "+indexedRecovered+" aggregate inventory fallbacks. "+legacyConflicts+" older differences preserved for safety.").commit();
        browser.loadUrl(appUrl);
        if(recoveredSettings.length()==0) cleanVerifiedLegacyOrigins();
        showNativeHome();
    }
    private void cleanVerifiedLegacyOrigins(){
        if (durable.getString("migrationSettings",null)!=null) return;
        int removed=matchedRecoveryOrigins.size();
        for(String origin:matchedRecoveryOrigins) WebStorage.getInstance().deleteOrigin(origin);
        matchedRecoveryOrigins.clear();
        String prior=durable.getString("recoveryReport","Storage ready.");
        durable.edit().putString("recoveryReport",prior+" Removed "+removed+" confirmed redundant origins.").commit();
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
                // A premature EOF can otherwise produce an opaque JSON parse error,
                // and must never be treated as a complete collection snapshot.
                if (declared >= 0 && bytes.length != declared)
                    throw new IOException("Incomplete Windows transfer: received " + bytes.length +
                            " of " + declared + " bytes. Retry the same QR/link while it is valid.");
                if (bytes.length == 0)
                    throw new IOException("Windows returned an empty collection transfer.");
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
        @JavascriptInterface public synchronized String readCollection(String key) {
            return validCollectionKey(key)?durable.getString("vault_"+key,null):null;
        }
        @JavascriptInterface public synchronized boolean writeCollection(String key,String json) {
            if(!validCollectionKey(key)||json==null||json.length()>6_000_000 || collectionSize(json)<0)return false;
            // Native SharedPreferences is the authoritative single copy for
            // inventories. Use commit() so a failed write is not reported as saved.
            return durable.edit().putString("vault_"+key,json).commit();
        }
        private boolean matchCollectionSnapshot(String expectedArena, String expectedPhysical) {
            return java.util.Objects.equals(durable.getString("vault_cards", null), expectedArena) &&
                   java.util.Objects.equals(durable.getString("vault_physicalCollectionV1", null), expectedPhysical);
        }
        private boolean validScope(String scope) {
            return "physical".equals(scope) || "arena".equals(scope) || "both".equals(scope);
        }
        // Atomic native inventory reset; explicit empty [] is an important
        // tombstone. Legacy and SQLite recovery must not recreate cleared data
        // after app restart. The most recent clear retains its prior native
        // arrays until another clear (or explicit successful undo).
        @JavascriptInterface public synchronized String clearInventories(
                String scope, String expectedArena, String expectedPhysical) {
            if(!validScope(scope))return "invalid-scope";
            if(!matchCollectionSnapshot(expectedArena, expectedPhysical))return "stale-collection";
            String arena=durable.getString("vault_cards",null);
            String physical=durable.getString("vault_physicalCollectionV1",null);
            if((arena!=null&&collectionSize(arena)<0) ||
               (physical!=null&&collectionSize(physical)<0))return "invalid-inventory";
            InventoryClearPlan.Plan plan=InventoryClearPlan.create(scope,arena,physical);
            boolean resetArena=plan.clearArena;
            boolean resetPhysical=plan.clearPhysical;
            if(plan.alreadyEmpty)return "already-empty";
            SharedPreferences.Editor edit=durable.edit()
                .putString("vault_lastInventoryClearScope",scope)
                .putString("vault_lastInventoryClearDate",
                    java.time.Instant.now().toString());
            if(resetArena){
                edit.putString("vault_lastInventoryClearArena",arena);
                edit.putString("vault_cards","[]");
            }else edit.remove("vault_lastInventoryClearArena");
            if(resetPhysical){
                edit.putString("vault_lastInventoryClearPhysical",physical);
                edit.putString("vault_physicalCollectionV1","[]");
            }else edit.remove("vault_lastInventoryClearPhysical");
            return edit.commit()?"ok":"storage-error";
        }
        @JavascriptInterface public synchronized String undoLastInventoryClear(
                String expectedArena, String expectedPhysical) {
            if(!matchCollectionSnapshot(expectedArena,expectedPhysical))return "stale-collection";
            String scope=durable.getString("vault_lastInventoryClearScope",null);
            if(!validScope(scope))return "no-clear-to-undo";
            boolean resetArena="arena".equals(scope)||"both".equals(scope);
            boolean resetPhysical="physical".equals(scope)||"both".equals(scope);
            if(!InventoryClearPlan.canUndo(scope,
                    durable.getString("vault_cards",null),
                    durable.getString("vault_physicalCollectionV1",null)))
                return "inventory-changed-since-clear";
            String arena=durable.getString("vault_lastInventoryClearArena",null);
            String physical=durable.getString("vault_lastInventoryClearPhysical",null);
            if((resetArena && arena!=null && collectionSize(arena)<0) ||
               (resetPhysical && physical!=null && collectionSize(physical)<0))
                return "invalid-recovery";
            SharedPreferences.Editor edit=durable.edit();
            if(resetArena){
                if(arena==null)edit.remove("vault_cards");
                else edit.putString("vault_cards",arena);
            }
            if(resetPhysical){
                if(physical==null)edit.remove("vault_physicalCollectionV1");
                else edit.putString("vault_physicalCollectionV1",physical);
            }
            edit.remove("vault_lastInventoryClearScope")
                .remove("vault_lastInventoryClearDate")
                .remove("vault_lastInventoryClearArena")
                .remove("vault_lastInventoryClearPhysical");
            return edit.commit()?"ok":"storage-error";
        }
        @JavascriptInterface public String readRecoveryReport() {
            return durable.getString("recoveryReport","Storage protection active. Collections are saved in native app storage.");
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
        } else if(request==PEER_3WAY_PICK){
            if(result==RESULT_OK && data!=null)readPeerBaseline(data.getData());
        } else if(request==PEER_WIRELESS_BASE_PICK){
            JSONObject received=pendingWirelessIncoming;
            pendingWirelessIncoming=null;
            if(result==RESULT_OK && data!=null && received!=null)
                applyPeerInventorySnapshot(()->received,data.getData());
        } else if(request==PEER_BASE_PICK){
            Uri latest=pendingPeerComparison;
            pendingPeerComparison=null;
            boolean apply=pendingPeerApply;
            pendingPeerApply=false;
            if(result==RESULT_OK && data!=null){
                if(apply)applyThreeWayInventoryUris(latest,data.getData());
                else previewThreeWayPeerUris(latest,data.getData());
            }
        } else if(request==PEER_PICK){
            if(result==RESULT_OK && data!=null)previewPeerUri(data.getData());
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
        if (nativeHomeVisible) {
            super.onBackPressed();
            return;
        }
        // Preserve the old modal-close behavior inside the legacy tools.
        browser.evaluateJavascript("(function(){let d=document.querySelector('dialog[open]');if(d){d.close();return 'closed'}return 'none'})()", result -> {
            if ("\"none\"".equals(result)) showNativeHome();
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
                else if(path.equals("/api/app/releases") && request[0].equals("GET")){
                    String host=hdr.getOrDefault("host","");
                    String origin=hdr.getOrDefault("origin","");
                    String local="http://127.0.0.1:"+getPort();
                    if(!host.equals("127.0.0.1:"+getPort()) || (!origin.isEmpty()&&!origin.equals(local))){
                        respondJson(out,403,new JSONObject().put("error","Local app only").toString());return;
                    }
                    String repo=queryParams(request[1]).getOrDefault("repo","");
                    if(repo.length()>150 || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || repo.contains("..")){
                        respondJson(out,400,new JSONObject().put("error","Invalid repository owner/name").toString());return;
                    }
                    HttpURLConnection conn=null;
                    try{
                        conn=(HttpURLConnection)new URL("https://api.github.com/repos/"+repo+"/releases?per_page=50").openConnection();
                        conn.setConnectTimeout(12000);conn.setReadTimeout(20000);
                        conn.setRequestProperty("User-Agent","ArcaneVault-Android-Updater/1.0");
                        conn.setRequestProperty("Accept","application/vnd.github+json");
                        int code=conn.getResponseCode();
                        if(code!=200){respondJson(out,502,new JSONObject().put("error","GitHub returned HTTP "+code).toString());return;}
                        byte[] data=readBytes(conn.getInputStream(),2_000_000);
                        String json=new String(data,StandardCharsets.UTF_8);
                        if(!json.trim().startsWith("[")){respondJson(out,502,new JSONObject().put("error","Unexpected GitHub release response").toString());return;}
                        respondJson(out,200,json);
                    }catch(Exception ex){
                        respondJson(out,502,new JSONObject().put("error","Native GitHub release check failed: "+(ex.getMessage()==null?"network unavailable":ex.getMessage())).toString());
                    }finally{if(conn!=null)conn.disconnect();}
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
            // Validate batch size and shape before any upstream call. Never
            // repeatedly retry an upstream 429 (Scryfall's stated policy).
            try {
                JSONObject batch = new JSONObject(new String(payload,StandardCharsets.UTF_8));
                org.json.JSONArray ids=batch.optJSONArray("identifiers");
                if(ids==null || ids.length()<1 || ids.length()>75){
                    respondJson(out,400,"{\"error\":\"Expected 1–75 card identifiers.\"}");return;
                }
                for(int i=0;i<ids.length();i++){
                    JSONObject id=ids.optJSONObject(i);
                    if(id==null || (!id.has("name")&&!id.has("id")&&!id.has("set"))){
                        respondJson(out,400,"{\"error\":\"Invalid Scryfall identifier.\"}");return;
                    }
                }
            }catch(Exception invalid){
                respondJson(out,400,"{\"error\":\"Invalid card collection request.\"}");return;
            }
            synchronized(scryfallRequestGate){
                HttpURLConnection conn=null;
                try{
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now<scryfallCooldownUntil){
                        long remaining=Math.max(1,(scryfallCooldownUntil-now+999)/1000);
                        sendScryfallError(out,429,"Scryfall rate limit is active. Wait before resuming.",remaining);
                        return;
                    }
                    long wait=SCRYFALL_MIN_GAP_MS-(now-scryfallLastRequestAt);
                    if(scryfallLastRequestAt>0&&wait>0){
                        try{Thread.sleep(wait);}catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();throw new IOException("Interrupted while pacing Scryfall requests");
                        }
                    }
                    scryfallLastRequestAt=android.os.SystemClock.elapsedRealtime();
                    conn=(HttpURLConnection)new URL("https://api.scryfall.com/cards/collection").openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(12000);conn.setReadTimeout(25000);
                    conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setRequestProperty("User-Agent","ArcaneVaultAndroid/2.0 (personal collection manager)");
                    try(OutputStream stream=conn.getOutputStream()){stream.write(payload);}
                    int status=conn.getResponseCode();
                    if(status==429 || status==403 || status==400){
                        long seconds=status==429?120:300;
                        String retry=conn.getHeaderField("Retry-After");
                        if(retry!=null){
                            try{seconds=Math.max(60,Math.min(1800,Long.parseLong(retry.trim())));}
                            catch(NumberFormatException ignored){
                                try{
                                    java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",java.util.Locale.US);
                                    format.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
                                    java.util.Date date=format.parse(retry);
                                    if(date!=null)seconds=Math.max(60,Math.min(1800,(date.getTime()-System.currentTimeMillis()+999)/1000));
                                }catch(Exception ignoredDate){}
                            }
                        }
                        scryfallCooldownUntil=android.os.SystemClock.elapsedRealtime()+seconds*1000;
                        sendScryfallError(out,status,"Scryfall temporarily blocked live card lookups (HTTP "+status+"). Resume after the cooldown; existing collections and metadata are preserved.",seconds);
                        return;
                    }
                    InputStream upstream=status>=400?conn.getErrorStream():conn.getInputStream();
                    byte[] result=upstream==null?"{}".getBytes(StandardCharsets.UTF_8):readBytes(upstream,3_000_000);
                    // Do not convert ordinary Scryfall errors to 502, which
                    // would make non-transient errors look retryable.
                    respond(out,status,"application/json",result);
                }catch(Exception error){
                    String msg="Scryfall network request failed: "+(error.getMessage()==null?"check connectivity":error.getMessage());
                    sendScryfallError(out,502,msg,0);
                }finally{if(conn!=null)conn.disconnect();}
            }
        }
        private void sendScryfallError(OutputStream out,int code,String message,long cooldownSeconds)throws IOException{
            JSONObject data=new JSONObject();
            try{data.put("error",message);if(cooldownSeconds>0)data.put("retryAfterSeconds",cooldownSeconds);}
            catch(org.json.JSONException exception){throw new IOException(exception);}
            respondJson(out,code,data.toString());
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
