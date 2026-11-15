package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated mobile deck-draft parser. Does not touch ownership or persistent state. */
final class NativeDeckDraft {
    private NativeDeckDraft(){}

    static JSONArray parseEntries(String source) throws Exception {
        if(source == null || source.length() > 50000)
            throw new IllegalArgumentException("Deck text is too large.");
        Map<String,Integer> quantities = new LinkedHashMap<>();
        Map<String,String> spelling = new LinkedHashMap<>();
        for(String raw : source.split("\\R")) {
            String line = raw.trim();
            if(line.isEmpty())continue;
            java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("^(\\d{1,4})\\s+(?:x\\s+)?(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(line);
            if(!match.matches())
                throw new IllegalArgumentException("Use '1 Card Name' on each line: " + line);
            int qty = Integer.parseInt(match.group(1));
            String name = match.group(2).trim();
            if(qty < 1 || qty > 999 || name.isEmpty() || name.length() > 180)
                throw new IllegalArgumentException("Invalid deck entry: " + line);
            String key = name.toLowerCase(java.util.Locale.ROOT);
            quantities.put(key, Math.addExact(quantities.getOrDefault(key,0),qty));
            spelling.putIfAbsent(key,name);
            if(quantities.size() > 500 || quantities.get(key) > 999)
                throw new IllegalArgumentException("Deck size or quantity limit exceeded.");
        }
        JSONArray output = new JSONArray();
        for(Map.Entry<String,Integer> e : quantities.entrySet())
            output.put(new JSONObject().put("name",spelling.get(e.getKey())).put("qty",e.getValue()));
        return output;
    }

    static String formatEntries(JSONArray entries) {
        StringBuilder text = new StringBuilder();
        if(entries != null)for(int i=0;i<entries.length();i++){
            JSONObject item=entries.optJSONObject(i);
            if(item==null)continue;
            if(text.length()>0)text.append('\n');
            text.append(item.optInt("qty",1)).append(' ').append(item.optString("name",""));
        }
        return text.toString();
    }

    static JSONObject revised(JSONObject original, String name, String entries) throws Exception {
        if(original==null || original.optString("id","").isEmpty())
            throw new IllegalArgumentException("Select an existing saved deck.");
        String cleanName = name == null ? "" : name.trim();
        if(cleanName.isEmpty() || cleanName.length() > 120)
            throw new IllegalArgumentException("Deck name must be 1-120 characters.");
        JSONArray parsed = parseEntries(entries);
        if(parsed.length()==0)throw new IllegalArgumentException("A saved deck needs at least one mainboard card.");
        JSONObject next=new JSONObject(original.toString());
        next.put("name",cleanName);
        next.put("entries",parsed);
        return next;
    }
}
