package com.arcanevault.mobile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Candidate extraction only. The local catalog verifies exact identities
 * before any review text becomes an interactive card link. No network calls. */
final class AiReviewCardMentions {
    private static final Pattern EMPHASIZED=Pattern.compile("\\*\\*([^*\\r\\n]{2,110})\\*\\*");
    private static final Pattern COUNT=Pattern.compile("^\\s*\\d{1,3}\\s+(?:x\\s+)?(.+)$");
    private AiReviewCardMentions(){}

    static final Pattern EXPLICIT = Pattern.compile(
        "\\[\\[card:([^\\]\\r\\n]{2,150})\\]\\]",
        Pattern.CASE_INSENSITIVE);

    /** Never infer links from ordinary prose or pre-populate deck words. */
    static List<String> candidates(String response,JSONArray entries,String commander) {
        Set<String> names=new LinkedHashSet<>();
        if(response!=null){
            Matcher matcher=EXPLICIT.matcher(response);
            while(matcher.find() && names.size()<150){
                String name=matcher.group(1).trim();
                if(!name.isEmpty())names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    static List<String> longestFirst(Iterable<String> validated){
        ArrayList<String> result=new ArrayList<>();
        for(String s:validated)if(s!=null&&!s.isBlank())result.add(s);
        result.sort((a,b)->{
            int cmp=Integer.compare(b.length(),a.length());
            return cmp!=0?cmp:a.compareToIgnoreCase(b);
        });
        return result;
    }
}
