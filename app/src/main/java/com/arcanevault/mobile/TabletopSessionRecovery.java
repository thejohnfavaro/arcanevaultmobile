package com.arcanevault.mobile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Offline two-generation tabletop recovery envelope. Both generations belong
 * only to the tabletop preference file; never to decks or peer sync.
 */
final class TabletopSessionRecovery {
    private static final int SCHEMA=1;
    private TabletopSessionRecovery(){}

    static String checkpoint(String sessionJson){
        TabletopSession.decode(sessionJson); // verify before making a checkpoint
        try{
            byte[] data=sessionJson.getBytes(StandardCharsets.UTF_8);
            StringBuilder sha=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(data))
                sha.append(String.format(Locale.ROOT,"%02x",b&255));
            return new JSONObject().put("schema",SCHEMA).put("session",sessionJson)
                .put("sha256",sha.toString()).toString();
        }catch(Exception error){
            throw new IllegalArgumentException("Unable to create tabletop recovery checkpoint",error);
        }
    }

    static TabletopSession restore(String checkpoint){
        try{
            JSONObject envelope=new JSONObject(checkpoint);
            if(envelope.getInt("schema")!=SCHEMA)
                throw new IllegalArgumentException("Unsupported tabletop recovery version");
            String original=envelope.getString("session");
            if(original.length()>250000)
                throw new IllegalArgumentException("Tabletop recovery exceeds safe size");
            String expected=envelope.getString("sha256");
            String trusted=checkpoint(original);
            String actual=new JSONObject(trusted).getString("sha256");
            if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("Tabletop recovery integrity mismatch");
            return TabletopSession.decode(original);
        }catch(Exception error){
            throw new IllegalArgumentException("The tabletop recovery checkpoint is invalid",error);
        }
    }
}
