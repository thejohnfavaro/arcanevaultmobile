package com.arcanevault.mobile.next;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** LAN-only transport for manually initiated, read-only peer reviews. */
public final class NativeWirelessPeerClient {
    private NativeWirelessPeerClient(){}
    private static final int MAX=NativePeerSync.MAX_BYTES;

    public static URL validate(String supplied)throws Exception {
        return validateTransport(supplied,false);
    }
    private static URL validateTransport(String supplied,boolean usb)throws Exception {
        String text=supplied==null?"":supplied.trim();
        if(text.length()>160)throw new IllegalArgumentException("Wireless URL is too long");
        URL url=new URL(text);
        if(!"http".equals(url.getProtocol())||url.getUserInfo()!=null||
            url.getQuery()!=null||url.getRef()!=null||
            url.getPort()<1||url.getPort()>65535||
            !url.getPath().matches("/arcane-peer/[a-f0-9]{48}"))
            throw new IllegalArgumentException("Expected a one-time Arcane Vault Wi-Fi link");
        String[] octets=url.getHost().split("\\.",-1);
        if(octets.length!=4)throw new IllegalArgumentException("Only private IPv4 addresses allowed");
        int[] ip=new int[4];
        for(int i=0;i<4;i++){
            if(!octets[i].matches("[0-9]{1,3}"))throw new IllegalArgumentException("Invalid IP");
            ip[i]=Integer.parseInt(octets[i]);
            if(ip[i]>255)throw new IllegalArgumentException("Invalid IP");
        }
        boolean loopback=ip[0]==127&&ip[1]==0&&ip[2]==0&&ip[3]==1;
        boolean privateLan=ip[0]==10 ||
            (ip[0]==172&&ip[1]>=16&&ip[1]<=31) ||
            (ip[0]==192&&ip[1]==168);
        if(usb?!loopback:!privateLan)
            throw new IllegalArgumentException(usb?
                "USB requires 127.0.0.1 loopback":"Link must use trusted private Wi-Fi");
        return url;
    }

    /** ADB reverse maps the device's loopback port to the Windows-only
     * localhost listener. Never accept a LAN URL for a USB launch. */
    public static URL validateUsb(String link)throws Exception {
        return validateTransport(link,true);
    }

    private static HttpURLConnection connection(URL url,String method)throws Exception{
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(6500);
        c.setReadTimeout(14000);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control","no-store");
        c.setRequestMethod(method);
        return c;
    }

    public static JSONObject receive(String link)throws Exception {
        return receiveFrom(validate(link));
    }
    public static JSONObject receiveUsb(String link)throws Exception {
        return receiveFrom(validateUsb(link));
    }
    private static JSONObject receiveFrom(URL url)throws Exception {
        HttpURLConnection c=connection(url,"GET");
        try{
            if(c.getResponseCode()!=200)
                throw new IllegalStateException("Windows transfer expired, used or rejected (HTTP "+c.getResponseCode()+")");
            if(c.getContentLengthLong()>MAX)
                throw new IllegalStateException("Peer snapshot exceeds 30 MiB");
            byte[] body=readBounded(c.getInputStream(),MAX);
            return new JSONObject(new String(body,StandardCharsets.UTF_8));
        }finally{c.disconnect();}
    }
    public static void send(String link,JSONObject peer)throws Exception {
        sendTo(validate(link),peer);
    }
    public static void sendUsb(String link,JSONObject peer)throws Exception {
        sendTo(validateUsb(link),peer);
    }
    private static void sendTo(URL url,JSONObject peer)throws Exception {
        byte[] bytes=peer.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length<20||bytes.length>MAX)
            throw new IllegalArgumentException("Peer snapshot must be within 30 MiB");
        HttpURLConnection c=connection(url,"POST");
        try{
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(bytes.length);
            c.setRequestProperty("Content-Type","application/json");
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            if(c.getResponseCode()!=200)
                throw new IllegalStateException("Windows declined peer snapshot (HTTP "+c.getResponseCode()+")");
            JSONObject receipt=new JSONObject(new String(readBounded(c.getInputStream(),4096),
                StandardCharsets.UTF_8));
            if(!receipt.optBoolean("received",false))
                throw new IllegalStateException("Windows did not confirm receipt");
        }finally{c.disconnect();}
    }
    private static byte[] readBounded(InputStream source,int max)throws Exception{
        try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] block=new byte[8192];int n;
            while((n=in.read(block))!=-1){
                if(out.size()+n>max)throw new IllegalArgumentException("Wireless payload too large");
                out.write(block,0,n);
            }
            return out.toByteArray();
        }
    }
}
