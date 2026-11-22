package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/** Windows-compatible, lossless saved-deck AI review metadata. */
final class AiReviewMetadata {
    private AiReviewMetadata(){}

    static String canonical(String raw) {
        String name=NativeArenaCardName.identity(raw==null?"":raw).trim();
        int slash=name.indexOf(" // ");
        if(slash>=0)name=name.substring(0,slash).trim();
        return Normalizer.normalize(name.replace('’',(char)39).replace('‘',(char)39),
            Normalizer.Form.NFKC);
    }

    static String fingerprint(JSONObject deck) throws Exception {
        if(deck==null)throw new IllegalArgumentException("Choose an existing saved deck.");
        JSONArray entries=deck.optJSONArray("entries");
        if(entries==null)throw new IllegalArgumentException("Deck entries are missing.");
        ArrayList<String[]> sorted=new ArrayList<>();
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.optJSONObject(i);
            if(entry==null)throw new IllegalArgumentException("Invalid deck entry.");
            String name=canonical(entry.optString("name","")).toUpperCase(Locale.ROOT);
            int count=entry.optInt("qty",0);
            if(name.isEmpty()||count<1||count>250)
                throw new IllegalArgumentException("Invalid deck entry.");
            sorted.add(new String[]{name,Integer.toString(count)});
        }
        sorted.sort(Comparator.comparing((String[] x)->x[0],String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(x->Integer.parseInt(x[1])));
        StringBuilder canonical=new StringBuilder()
            .append(deck.optString("format","").trim().toUpperCase(Locale.ROOT)).append('\n')
            .append(canonical(deck.optString("commander","")).toUpperCase(Locale.ROOT))
            .append('\n');
        for(int i=0;i<sorted.size();i++){
            if(i>0)canonical.append('\n');
            canonical.append(sorted.get(i)[1]).append(' ').append(sorted.get(i)[0]);
        }
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder output=new StringBuilder();
        for(byte b:digest)output.append(String.format(Locale.ROOT,"%02X",b&255));
        return output.toString();
    }

    static boolean hasReview(JSONObject deck) {
        JSONObject r=deck==null?null:deck.optJSONObject("aiReview");
        return r!=null&&!r.optString("markdown","").isEmpty();
    }
    static boolean stale(JSONObject deck) {
        if(!hasReview(deck))return false;
        try{return !fingerprint(deck).equalsIgnoreCase(
            deck.getJSONObject("aiReview").optString("deckFingerprint",""));}
        catch(Exception ex){return true;}
    }
    static String label(JSONObject deck) {
        if(!hasReview(deck))return "";
        JSONObject meta=deck.optJSONObject("aiReview");
        return (stale(deck)?"AI review · Outdated":"AI review · Saved")+
            " · "+meta.optString("mode","")+
            (meta.optString("savedUtc","").isEmpty()?"":" · "+meta.optString("savedUtc",""));
    }

    static JSONObject attach(JSONObject saved,String markdown,String model,String mode)
            throws Exception {
        if(saved==null||saved.optString("id","").isEmpty())
            throw new IllegalArgumentException("Save the deck before attaching a review.");
        if(markdown==null||markdown.isBlank()||markdown.length()>48000||markdown.indexOf('\0')>=0)
            throw new IllegalArgumentException("Complete AI review must be 1–48,000 characters.");
        if(model==null||model.isBlank()||model.length()>120||
            model.chars().anyMatch(Character::isISOControl)||
            !("health".equals(mode)||"owned".equals(mode)||"synergy".equals(mode)))
            throw new IllegalArgumentException("Invalid AI review metadata.");
        JSONObject changed=new JSONObject(saved.toString());
        JSONObject review=new JSONObject().put("schema",1).put("markdown",markdown)
            .put("model",model).put("mode",mode).put("savedUtc",Instant.now().toString())
            .put("deckFingerprint",fingerprint(saved));
        changed.put("aiReview",review);
        if(!reviewOnlyChange(saved,changed))
            throw new IllegalArgumentException("The review changed unrelated deck metadata.");
        return changed;
    }

    /** Only the aiReview property may change, never deck content or ownership. */
    static boolean reviewOnlyChange(JSONObject original,JSONObject changed) {
        if(original==null||changed==null)return false;
        try{
            JSONObject before=new JSONObject(original.toString());
            JSONObject after=new JSONObject(changed.toString());
            if(!before.optString("id","").equals(after.optString("id","")))return false;
            before.remove("aiReview");after.remove("aiReview");
            if(!com.arcanevault.mobile.NativePeerInventoryApply.canonicalEqual(before,after))
                return false;
            JSONObject metadata=changed.optJSONObject("aiReview");
            return metadata!=null&&metadata.optInt("schema")==1&&
                !metadata.optString("markdown","").isBlank()&&
                metadata.optString("markdown","").length()<=48000&&
                metadata.optString("model","").length()<=120&&
                java.util.Arrays.asList("health","owned","synergy").contains(metadata.optString("mode",""))&&
                fingerprint(changed).equalsIgnoreCase(metadata.optString("deckFingerprint",""));
        }catch(Exception ex){return false;}
    }
}
