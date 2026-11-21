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

    static List<String> candidates(String response,JSONArray entries,String commander) {
        Set<String> names=new LinkedHashSet<>();
        if(commander!=null&&!commander.isBlank())add(names,commander);
        if(entries!=null)for(int i=0;i<entries.length();i++){
            JSONObject card=entries.optJSONObject(i);
            if(card!=null)add(names,card.optString("name",""));
        }
        if(response!=null){
            Matcher m=EMPHASIZED.matcher(response);
            int count=0;
            while(m.find()&&count++<180)add(names,m.group(1));
            for(String line:response.split("\\R")){
                if(names.size()>250)break;
                String trimmed=line.trim();
                if(!trimmed.startsWith("|"))continue;
                String[] cells=trimmed.replaceAll("^\\||\\|$","").split("\\|");
                for(int i=0;i<Math.min(2,cells.length);i++)add(names,cells[i]);
            }
        }
        return new ArrayList<>(names);
    }

    private static void add(Set<String> set,String value){
        if(value==null)return;
        String s=value.trim().replace("**","");
        s=s.replaceAll("^[\\s•*\\-]+","");
        Matcher c=COUNT.matcher(s);
        if(c.matches())s=c.group(1).trim();
        s=s.replaceAll("(?i)^(?:add|cut|swap|replace|remove|include)\\s*:\\s*","");
        s=s.replaceAll("^[\"']|[\"'.,;:]$","");
        s=NativeArenaCardName.identity(s).trim();
        if(s.length()>=2&&s.length()<=110&&!s.contains("\n"))
            set.add(s);
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
