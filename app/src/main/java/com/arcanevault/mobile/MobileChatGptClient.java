package com.arcanevault.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Android-local ChatGPT plan connection. No API key, account sharing or sync. */
final class MobileChatGptClient {
    interface Result { void done(String message, boolean ok); }
    interface Review { void done(String answer, String model, String error); }
    private static final String AUTH="https://auth.openai.com";
    private static final String RESOURCE="https://api.openai.com/v1";
    private static final String SCOPE="chatgpt.tokens.use.direct";
    private static final String ALIAS="ArcaneVaultChatGptLocalV1";
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private String clientId,subject,access,refresh,idToken,hostId;
    private long expiration;
    MobileChatGptClient(Activity activity){this.activity=activity;load();}

    private volatile boolean connectionVerified;
    private volatile String lastConnectionIssue="";
    synchronized boolean connected(){return refresh!=null && !refresh.isEmpty();}
    synchronized String status(){
        if(!connected())return lastConnectionIssue.isEmpty()?
            "Not connected · Options → ChatGPT":lastConnectionIssue;
        if(connectionVerified)return "ChatGPT plan authorization verified on this Android device";
        return "Saved ChatGPT authorization · not yet verified on this device";
    }
    /** Public network diagnostics: no tokens, prompts, deck contents, or collection data. */
    void checkNetwork(Result callback){
        worker.execute(()->{
            StringBuilder report=new StringBuilder();
            boolean healthy=true;
            String[][] targets={
                {"auth.openai.com","https://auth.openai.com/.well-known/openid-configuration"},
                {"api.openai.com","https://api.openai.com/v1/models"}
            };
            for(String[] target:targets){
                String host=target[0];
                try{
                    InetAddress[] records=InetAddress.getAllByName(host);
                    if(records.length==0)throw new UnknownHostException(host);
                    report.append(host).append(": DNS OK; ");
                    HttpURLConnection conn=null;
                    try{
                        conn=(HttpURLConnection)new URL(target[1]).openConnection();
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setInstanceFollowRedirects(false);
                        conn.setRequestProperty("Accept","application/json");
                        int code=conn.getResponseCode();
                        // 401/403/404 are valid reachability responses: no auth is sent.
                        report.append("HTTPS reachable (HTTP ").append(code).append(")");
                    }finally{
                        if(conn!=null)conn.disconnect();
                    }
                }catch(UnknownHostException ex){
                    healthy=false;
                    report.append(host).append(": DNS lookup failed");
                }catch(javax.net.ssl.SSLException ex){
                    healthy=false;
                    report.append("TLS failure (").append(ex.getClass().getSimpleName()).append(")");
                }catch(java.net.SocketTimeoutException ex){
                    healthy=false;
                    report.append("Connection timed out");
                }catch(Exception ex){
                    healthy=false;
                    report.append("Connection failed (").append(ex.getClass().getSimpleName()).append(")");
                }
                report.append('\n');
            }
            report.append(healthy?
                "Both hosts are reachable from Arcane Vault. Network access alone does not confirm OAuth or model permissions.":
                "A browser may use different DNS/network handling than the app. Compare the failing host in Chrome; check Private DNS or VPN only if Chrome also fails.");
            boolean passed=healthy;
            String message=report.toString();
            ui.post(()->callback.done(message,passed));
        });
    }

    static String connectionError(Throwable ex){
        Throwable cursor=ex;
        while(cursor!=null){
            if(cursor instanceof UnknownHostException){
                return "Network DNS could not resolve an OpenAI host. Switch between Wi-Fi and mobile data; if one works, check Private DNS, VPN, router or ad-blocking DNS settings. Your ChatGPT account and saved decks have not been changed.";
            }
            cursor=cursor.getCause();
        }
        return ex.getMessage()==null?"Unknown connection error":ex.getMessage();
    }

    /** A saved token is not proof the linked account can perform AI requests.
     * Check the same model-list endpoint used by the review workflow. Never
     * transmit a deck, collection or personal information for diagnostics. */
    void verifyConnection(Result callback){
        if(!connected()){
            ui.post(()->callback.done("Arcane Vault is not authorized. Use Continue with ChatGPT in Options, even if the ChatGPT app is signed in.",false));
            return;
        }
        worker.execute(()->{
            try{
                String bearer=currentAccess();
                JSONObject models=bearerRequest(RESOURCE+"/models",bearer,null);
                JSONArray listed=models.optJSONArray("models");
                if(listed==null)throw new IOException("Model list was not returned");
                int usable=0;
                for(int i=0;i<listed.length();i++){
                    JSONObject item=listed.optJSONObject(i);
                    if(item!=null&&"list".equals(item.optString("visibility"))&&
                        !item.optString("slug","").isEmpty())usable++;
                }
                if(usable==0)throw new IOException("No eligible ChatGPT models were returned for this connection");
                connectionVerified=true;
                lastConnectionIssue="";
                final int availableModels=usable;
                ui.post(()->callback.done("ChatGPT connection verified. "+availableModels+
                    " eligible model"+(availableModels==1?"":"s")+" available for deck reviews.",true));
            }catch(Exception ex){
                connectionVerified=false;
                String message=connectionError(ex);
                lastConnectionIssue="ChatGPT connection not verified: "+message;
                ui.post(()->callback.done(lastConnectionIssue+
                    ". If authorization is no longer valid, disconnect and authorize Arcane Vault again.",false));
            }
        });
    }

    private static String b64(byte[] bytes){return Base64.encodeToString(bytes,
        Base64.NO_PADDING|Base64.NO_WRAP|Base64.URL_SAFE);}
    private static byte[] un64(String value){return Base64.decode(value,Base64.NO_WRAP|Base64.URL_SAFE);}
    private static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return b64(bytes);}
    private static String sha(String s) throws Exception {
        return b64(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII)));
    }
    private static String param(String s){try{return java.net.URLEncoder.encode(s,"UTF-8");}
        catch(Exception ex){throw new IllegalStateException(ex);}}
    private static String form(Map<String,String> values){
        StringBuilder s=new StringBuilder();
        for(Map.Entry<String,String> e:values.entrySet()){
            if(s.length()>0)s.append('&');
            s.append(param(e.getKey())).append('=').append(param(e.getValue()));
        }
        return s.toString();
    }
    private static Map<String,String> parseQuery(String query)throws Exception{
        Map<String,String> out=new HashMap<>();
        for(String part:query.split("&")){
            int equal=part.indexOf('=');
            if(equal>0)out.put(java.net.URLDecoder.decode(part.substring(0,equal),"UTF-8"),
                java.net.URLDecoder.decode(part.substring(equal+1),"UTF-8"));
        }
        return out;
    }
    private static JSONObject request(String url,String encoded)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(45000);
        c.setRequestProperty("Accept","application/json");
        if(encoded!=null){
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8");
            try(OutputStream out=c.getOutputStream()){out.write(encoded.getBytes(StandardCharsets.UTF_8));}
        }
        try{
            int code=c.getResponseCode();
            if(code<200||code>=300){
                // Never surface raw OAuth responses: they can contain account
                // information or authorization material. Preserve only safe,
                // structured error identifiers and the HTTP status.
                String errorCode="";
                try(InputStream err=c.getErrorStream()){
                    if(err!=null){
                        JSONObject details=new JSONObject(new String(readBounded(err,16384),
                            StandardCharsets.UTF_8));
                        JSONObject nested=details.optJSONObject("error");
                        if(nested!=null)errorCode=nested.optString("code","");
                        else errorCode=details.optString("error","");
                    }
                }catch(Exception ignored){}
                if(!errorCode.matches("[A-Za-z0-9_.-]{1,80}"))errorCode="";
                throw new IOException("ChatGPT authorization service HTTP "+code+
                    (errorCode.isEmpty()?"":" ("+errorCode+")"));
            }
            try(InputStream in=c.getInputStream()){
                return new JSONObject(new String(readBounded(in,1024*1024),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
    private static byte[] readBounded(InputStream in,int max)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){
            if(out.size()+count>max)throw new IOException("Response is too large.");
            out.write(buffer,0,count);
        }
        return out.toByteArray();
    }

    private File store(){return new File(activity.getNoBackupFilesDir(),"chatgpt-account.enc");}
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    private synchronized void save()throws Exception{
        if(refresh==null||clientId==null||subject==null)
            throw new IllegalStateException("Incomplete ChatGPT authorization.");
        JSONObject session=new JSONObject()
            .put("client",clientId).put("subject",subject).put("access",access)
            .put("refresh",refresh).put("id",idToken).put("host",hostId).put("expires",expiration);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();
        byte[] ciphertext=cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8));
        File path=store();File temp=new File(path.getParentFile(),path.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){
            out.write(iv.length);out.write(iv);out.write(ciphertext);out.getFD().sync();
        }
        if(!temp.renameTo(path)){
            Files.move(temp.toPath(),path.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private synchronized void load(){
        try{
            File path=store();if(!path.isFile())return;
            try(FileInputStream in=new FileInputStream(path)){
                int ivSize=in.read();if(ivSize!=12)throw new IOException("Bad IV");
                byte[] iv=new byte[ivSize];if(in.read(iv)!=ivSize)throw new IOException("Truncated IV");
                byte[] encrypted=readBounded(in,64*1024);
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
                JSONObject p=new JSONObject(new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8));
                clientId=p.optString("client",null);subject=p.optString("subject",null);
                access=p.optString("access",null);refresh=p.optString("refresh",null);
                idToken=p.optString("id",null);hostId=p.optString("host",null);
                expiration=p.optLong("expires",0L);
                if(clientId==null||refresh==null||subject==null)
                    throw new IOException("Incomplete saved session");
            }
        }catch(Exception ex){clientId=null;subject=null;access=null;refresh=null;idToken=null;expiration=0;}
    }
    private synchronized void clear(){
        access=null;refresh=null;subject=null;idToken=null;expiration=0;
        connectionVerified=false;lastConnectionIssue="";
        store().delete(); // Never put OAuth material into collection SharedPreferences.
    }

    private static String verify(String jwt,String client,String expectedNonce)throws Exception{
        String[] parts=jwt.split("\\.");
        if(parts.length!=3)throw new SecurityException("Invalid OpenID token");
        JSONObject head=new JSONObject(new String(un64(parts[0]),StandardCharsets.UTF_8));
        JSONObject claims=new JSONObject(new String(un64(parts[1]),StandardCharsets.UTF_8));
        if(!"RS256".equals(head.optString("alg")))throw new SecurityException("Unexpected signature algorithm");
        JSONObject jwks=request(AUTH+"/.well-known/jwks.json",null);
        JSONArray keys=jwks.getJSONArray("keys");JSONObject key=null;
        for(int i=0;i<keys.length();i++){
            JSONObject k=keys.getJSONObject(i);
            if(head.optString("kid").equals(k.optString("kid"))&&"RSA".equals(k.optString("kty"))){key=k;break;}
        }
        if(key==null)throw new SecurityException("Unknown OpenID signing key");
        java.math.BigInteger n=new java.math.BigInteger(1,un64(key.getString("n")));
        java.math.BigInteger e=new java.math.BigInteger(1,un64(key.getString("e")));
        PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e));
        Signature sign=Signature.getInstance("SHA256withRSA");
        sign.initVerify(pub);sign.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        if(!sign.verify(un64(parts[2])))throw new SecurityException("Invalid OpenID signature");
        boolean audience=client.equals(claims.optString("aud"));
        JSONArray audiences=claims.optJSONArray("aud");
        if(audiences!=null)for(int i=0;i<audiences.length();i++)
            if(client.equals(audiences.optString(i)))audience=true;
        if(!AUTH.equals(claims.optString("iss"))||!audience||
            !expectedNonce.equals(claims.optString("nonce"))||
            claims.optLong("exp",0)<Instant.now().getEpochSecond())
            throw new SecurityException("OpenID issuer, audience, nonce or expiry mismatch");
        return claims.getString("sub");
    }

    void connect(Result callback){
        if(connected()){ui.post(()->callback.done("Already connected",true));return;}
        // Do not open authorization in a WebView or handle any browser cookies.
        String state=random(),nonce=random(),verifier=random();
        worker.execute(()->{
            String stage="starting local callback";
            try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
                socket.setSoTimeout(180000);
                String redirect="http://127.0.0.1:"+socket.getLocalPort()+"/auth/callback";
                if(hostId==null||hostId.isEmpty())hostId="urn:uuid:"+UUID.randomUUID();
                String incomingClient=clientId==null?"dynamic_agent_client":clientId;
                Map<String,String> args=new LinkedHashMap<>();
                args.put("client_id",incomingClient);args.put("ext_agent_host_id",hostId);
                args.put("response_type","code");args.put("redirect_uri",redirect);
                args.put("scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct");
                args.put("resource",RESOURCE);args.put("state",state);args.put("nonce",nonce);
                args.put("code_challenge_method","S256");args.put("code_challenge",sha(verifier));
                if(clientId==null)args.put("agent_name_hint","Arcane Vault Android");
                String authorize=AUTH+"/api/accounts/authorize?"+form(args);
                stage="opening browser";
                ui.post(()->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(authorize))));
                stage="waiting for browser callback";
                Map<String,String> callbackData=null;
                for(int attempt=0;attempt<5;attempt++){
                    try(Socket connection=socket.accept()){
                        connection.setSoTimeout(10000);
                        BufferedReader in=new BufferedReader(new InputStreamReader(
                            connection.getInputStream(),StandardCharsets.US_ASCII));
                        String first=in.readLine();
                        String[] tokens=first==null?new String[0]:first.split(" ");
                        boolean path=tokens.length==3&&tokens[0].equals("GET")&&
                            tokens[1].startsWith("/auth/callback?");
                        Map<String,String> values=path?parseQuery(tokens[1].substring(tokens[1].indexOf('?')+1)):
                            new HashMap<>();
                        boolean valid=path&&state.equals(values.get("state"));
                        byte[] body=(valid?"Authorization received. Return to Arcane Vault.":
                            "Authorization not accepted.").getBytes(StandardCharsets.UTF_8);
                        OutputStream out=connection.getOutputStream();
                        out.write(("HTTP/1.1 "+(valid?"200 OK":"400 Bad Request")+
                            "\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\n"+
                            "Content-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(body);out.flush();
                        if(valid){callbackData=values;break;}
                    }
                }
                if(callbackData==null)throw new SecurityException("No valid local OAuth callback");
                if(callbackData.containsKey("error")){
                    String reason=callbackData.get("error");
                    if(!reason.matches("[A-Za-z0-9_.-]{1,80}"))reason="authorization_error";
                    throw new SecurityException("OAuth callback returned "+reason);
                }
                String code=callbackData.get("code");
                if(code==null||code.length()<8)throw new SecurityException("Missing code");
                String issued=clientId;
                if(issued==null)issued=callbackData.get("client_id");
                else if(callbackData.containsKey("client_id")&&!issued.equals(callbackData.get("client_id")))
                    throw new SecurityException("OAuth client mismatch");
                if(issued==null||issued.equals("dynamic_agent_client"))
                    throw new SecurityException("OpenAI did not issue a client registration");
                Map<String,String> request=new LinkedHashMap<>();
                request.put("grant_type","authorization_code");request.put("client_id",issued);
                request.put("code",code);request.put("code_verifier",verifier);
                request.put("redirect_uri",redirect);request.put("resource",RESOURCE);
                stage="exchanging authorization code";
                JSONObject tokens=request(AUTH+"/api/accounts/oauth/token",form(request));
                stage="verifying signed identity";
                String user=verify(tokens.getString("id_token"),issued,nonce);
                if(subject!=null&&!subject.equals(user))throw new SecurityException("Account mismatch");
                stage="checking granted ChatGPT plan permission";
                if(!Arrays.asList(tokens.optString("scope","").split(" ")).contains(SCOPE))
                    throw new SecurityException("ChatGPT plan permission was not granted");
                String refreshValue=tokens.optString("refresh_token","");
                if(refreshValue.isEmpty())throw new SecurityException("No renewable access was granted");
                stage="saving encrypted authorization";
                synchronized(this){
                    clientId=issued;subject=user;access=tokens.getString("access_token");
                    refresh=refreshValue;idToken=tokens.getString("id_token");
                    expiration=Instant.now().getEpochSecond()+tokens.getLong("expires_in");
                    save();
                }
                connectionVerified=false;
                lastConnectionIssue="";
                ui.post(()->callback.done("ChatGPT authorization saved. Use Check connection to confirm plan access on this device.",true));
            }catch(Exception ex){
                String reason=ex instanceof SocketTimeoutException?
                    "Browser authorization did not return to Arcane Vault within three minutes":
                    connectionError(ex);
                lastConnectionIssue="ChatGPT authorization failed while "+stage+": "+reason;
                String result=lastConnectionIssue;
                ui.post(()->callback.done(result,false));
            }
        });
    }

    private synchronized String currentAccess()throws Exception {
        if(access!=null&&expiration>Instant.now().getEpochSecond()+120)return access;
        if(refresh==null||clientId==null)throw new SecurityException("Connect ChatGPT from Options first");
        Map<String,String> values=new LinkedHashMap<>();
        values.put("grant_type","refresh_token");values.put("client_id",clientId);
        values.put("refresh_token",refresh);values.put("resource",RESOURCE);
        JSONObject p;
        try{p=request(AUTH+"/api/accounts/oauth/token",form(values));}
        catch(IOException ex){throw new IOException("ChatGPT renewal failed: "+ex.getMessage(),ex);}
        if(p.has("scope")&&!Arrays.asList(p.optString("scope","").split(" ")).contains(SCOPE)){
            clear();throw new SecurityException("Plan use permission was withdrawn");
        }
        String rotated=p.optString("refresh_token","");
        if(rotated.isEmpty())throw new SecurityException("OAuth token rotation missing");
        access=p.getString("access_token");refresh=rotated;
        expiration=Instant.now().getEpochSecond()+p.getLong("expires_in");
        save();
        return access;
    }

    void review(String prompt,Review callback){
        if(prompt==null||prompt.isEmpty()){ui.post(()->callback.done("","","Deck is empty"));return;}
        worker.execute(()->{
            String model="";
            try{
                String bearer=currentAccess();
                JSONObject models=bearerRequest(RESOURCE+"/models",bearer,null);
                JSONArray list=models.getJSONArray("models");
                for(int i=0;i<list.length();i++){
                    JSONObject item=list.getJSONObject(i);
                    if("list".equals(item.optString("visibility"))){
                        model=item.optString("slug");if(!model.isEmpty())break;
                    }
                }
                if(model.isEmpty())throw new IOException("No models available for this plan");
                JSONObject body=new JSONObject().put("model",model)
                    .put("input",new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)))
                    .put("store",false).put("stream",true);
                HttpURLConnection c=(HttpURLConnection)new URL(RESOURCE+"/responses").openConnection();
                c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(180000);
                c.setRequestProperty("Authorization","Bearer "+bearer);
                c.setRequestProperty("Content-Type","application/json");
                c.setDoOutput(true);
                try(OutputStream out=c.getOutputStream()){
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                StringBuilder answer=new StringBuilder();boolean complete=false;
                try{
                    if(c.getResponseCode()!=200)throw new IOException("ChatGPT review HTTP "+c.getResponseCode());
                    try(BufferedReader in=new BufferedReader(new InputStreamReader(
                        c.getInputStream(),StandardCharsets.UTF_8))){
                        String line;
                        while((line=in.readLine())!=null){
                            if(!line.startsWith("data: "))continue;
                            String data=line.substring(6);
                            if(data.equals("[DONE]"))break;
                            JSONObject event=new JSONObject(data);
                            String type=event.optString("type");
                            if(type.equals("response.output_text.delta")){
                                answer.append(event.optString("delta"));
                                if(answer.length()>250000)throw new IOException("Review too long");
                            }else if(type.equals("response.completed"))complete=true;
                            else if(type.equals("response.failed")||type.equals("response.incomplete"))
                                throw new IOException("Review did not complete");
                        }
                    }
                }finally{c.disconnect();}
                if(!complete)throw new IOException("AI response ended before completion");
                connectionVerified=true;
                lastConnectionIssue="";
                String finalModel=model;
                ui.post(()->callback.done(answer.toString(),finalModel,null));
            }catch(Exception ex){
                String error=ex.getMessage();
                ui.post(()->callback.done("","",error));
            }
        });
    }
    private static JSONObject bearerRequest(String url,String bearer,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(45000);
        c.setRequestProperty("Authorization","Bearer "+bearer);
        if(body!=null){
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
        }
        try{
            if(c.getResponseCode()!=200)throw new IOException("ChatGPT returned HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream()){
                return new JSONObject(new String(readBounded(in,2*1024*1024),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
    void disconnect(Result callback){
        worker.execute(()->{
            boolean revoked=false;
            try{
                String activeRefresh,activeClient;
                synchronized(this){activeRefresh=refresh;activeClient=clientId;}
                if(activeRefresh!=null&&activeClient!=null){
                    String endpoint=request(AUTH+"/.well-known/openid-configuration",null)
                        .optString("revocation_endpoint","");
                    if(endpoint.startsWith(AUTH+"/")){
                        Map<String,String> args=new HashMap<>();
                        args.put("token",activeRefresh);args.put("token_type_hint","refresh_token");
                        args.put("client_id",activeClient);
                        request(endpoint,form(args));revoked=true;
                    }
                }else revoked=true;
            }catch(Exception ignored){revoked=false;}
            synchronized(this){clear();}
            boolean confirmed=revoked;
            ui.post(()->callback.done(confirmed?"Disconnected from ChatGPT.":
                "Disconnected locally; remote revocation not confirmed. Remove access in ChatGPT settings if needed.",true));
        });
    }
    void close(){worker.shutdownNow();}
}
