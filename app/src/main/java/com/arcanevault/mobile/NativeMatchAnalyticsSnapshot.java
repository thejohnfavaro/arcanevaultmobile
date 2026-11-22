package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Read-only Arena results exchanged as optional metadata on an otherwise
 * unchanged peer inventory envelope. This never applies inventory changes.
 */
final class NativeMatchAnalyticsSnapshot {
    static final String PREF_KEY="vault_match_analytics_v1";
    static final int MAX_RECORDS=2000;
    private NativeMatchAnalyticsSnapshot() {}

    static JSONObject fromPeer(JSONObject peer)throws Exception {
        if(!"arcane-vault-peer-sync".equals(peer.optString("format")) ||
            peer.optInt("schema")!=1)
            throw new IllegalArgumentException("Not an Arcane Vault peer snapshot");
        JSONObject source=peer.optJSONObject("matchAnalytics");
        if(source==null)throw new IllegalArgumentException("Windows peer contains no match analytics");
        if(source.optInt("schema")!=1)
            throw new IllegalArgumentException("Unsupported match analytics schema");
        JSONArray input=source.optJSONArray("records");
        if(input==null||input.length()>MAX_RECORDS)
            throw new IllegalArgumentException("Invalid match analytics size");
        JSONArray clean=new JSONArray();
        for(int i=0;i<input.length();i++){
            JSONObject row=input.getJSONObject(i);
            String deck=row.optString("deckName").trim();
            String format=row.optString("format").trim();
            String result=row.optString("result");
            String time=row.optString("playedUtc");
            if(deck.isEmpty()||deck.length()>150||format.length()>90||
                !("win".equals(result)||"loss".equals(result))||
                time.length()>48)
                throw new IllegalArgumentException("Invalid confirmed match entry");
            Instant parsed=OffsetDateTime.parse(time).toInstant();
            String colors=normalizeOpponentColors(row.optString("opponentColors",""));
            clean.put(new JSONObject().put("playedUtc",parsed.toString())
                .put("deckName",deck).put("format",format).put("result",result)
                .put("opponentColors",colors));
        }
        return new JSONObject().put("schema",1).put("records",clean)
            .put("asOfUtc",source.optString("asOfUtc",""));
    }

    static String normalizeOpponentColors(String raw){
        if(raw==null)return "";
        String value=raw.trim().toUpperCase(Locale.ROOT);
        if(value.equals("C"))return "C";
        if(value.length()>16)return "";
        StringBuilder normalized=new StringBuilder();
        for(char color:"WUBRG".toCharArray())
            if(value.indexOf(color)>=0)normalized.append(color);
        for(int i=0;i<value.length();i++)
            if("WUBRG ,/".indexOf(value.charAt(i))<0)return "";
        return normalized.toString();
    }
    static final class ColorResult {
        final String identity;
        int wins,losses;
        ColorResult(String name){identity=name;}
        int matches(){return wins+losses;}
        int percent(){return matches()==0?0:(int)Math.round(100d*wins/matches());}
    }
    static ArrayList<ColorResult> byOpponentColors(JSONObject snapshot,
            String deck,String format,int days,Instant now)throws Exception {
        JSONArray all=snapshot.getJSONArray("records");
        Instant cutoff=days>0?now.minusSeconds(days*86400L):Instant.EPOCH;
        LinkedHashMap<String,ColorResult> grouped=new LinkedHashMap<>();
        for(int i=0;i<all.length();i++){
            Row row=new Row(all.getJSONObject(i));
            if(row.time.isAfter(now)||row.time.isBefore(cutoff))continue;
            if(deck!=null&&!deck.isEmpty()&&!row.deck.equalsIgnoreCase(deck))continue;
            String queue=row.format.isEmpty()?"Unknown format":row.format;
            if(format!=null&&!format.isEmpty()&&!queue.equalsIgnoreCase(format))continue;
            String key=row.opponentColors.isEmpty()?"Unknown":row.opponentColors;
            ColorResult result=grouped.get(key);
            if(result==null){result=new ColorResult(key);grouped.put(key,result);}
            if(row.result.equals("win"))result.wins++;else result.losses++;
        }
        ArrayList<ColorResult> sorted=new ArrayList<>(grouped.values());
        sorted.sort((a,b)->{
            if(a.identity.equals("Unknown"))return 1;
            if(b.identity.equals("Unknown"))return -1;
            int n=Integer.compare(b.matches(),a.matches());
            return n!=0?n:a.identity.compareTo(b.identity);
        });
        return sorted;
    }
    static final class Row {
        final String deck,format,result,opponentColors;
        final Instant time;
        Row(JSONObject o)throws Exception {
            deck=o.getString("deckName");
            format=o.optString("format");
            result=o.getString("result");
            opponentColors=normalizeOpponentColors(o.optString("opponentColors",""));
            time=Instant.parse(o.getString("playedUtc"));
        }
    }
    static final class Weekly {
        final String week; int wins,losses;
        Weekly(String w){week=w;}
    }
    static final class Report {
        int wins,losses,winStreak,lossStreak,bestWins,worstLosses,latestWins;
        final ArrayList<Row> newest=new ArrayList<>();
        final ArrayList<Weekly> weeks=new ArrayList<>();
        final ArrayList<Integer> rollingRates=new ArrayList<>();
        int matches(){return wins+losses;}
        int percent(){return matches()==0?0:(int)Math.round(100d*wins/matches());}
    }
    static Report calculate(JSONObject snapshot,String deck,String format,int days,Instant now)throws Exception {
        JSONArray arr=snapshot.getJSONArray("records");
        ArrayList<Row> included=new ArrayList<>();
        Instant cutoff=days>0?now.minusSeconds(86400L*days):Instant.EPOCH;
        for(int i=0;i<arr.length();i++){
            Row r=new Row(arr.getJSONObject(i));
            if(r.time.isAfter(now)||r.time.isBefore(cutoff))continue;
            if(deck!=null&&!deck.isEmpty()&&!r.deck.equalsIgnoreCase(deck))continue;
            String queue=r.format.isEmpty()?"Unknown format":r.format;
            if(format!=null&&!format.isEmpty()&&!queue.equalsIgnoreCase(format))continue;
            included.add(r);
        }
        included.sort(Comparator.comparing(x->x.time));
        Report out=new Report();
        LinkedHashMap<String,Weekly> periods=new LinkedHashMap<>();
        ArrayList<Integer> lastTen=new ArrayList<>();
        for(Row r:included){
            boolean won="win".equals(r.result);
            if(won){
                out.wins++;out.winStreak++;out.lossStreak=0;
                out.bestWins=Math.max(out.bestWins,out.winStreak);
            }else{
                out.losses++;out.lossStreak++;out.winStreak=0;
                out.worstLosses=Math.max(out.worstLosses,out.lossStreak);
            }
            lastTen.add(won?1:0);
            if(lastTen.size()>10)lastTen.remove(0);
            int hits=0;for(int value:lastTen)hits+=value;
            out.rollingRates.add((int)Math.round(100d*hits/lastTen.size()));
            LocalDate day=r.time.atZone(ZoneOffset.UTC).toLocalDate();
            String week=day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            Weekly stat=periods.get(week);
            if(stat==null){stat=new Weekly(week);periods.put(week,stat);}
            if(won)stat.wins++;else stat.losses++;
        }
        for(int i=included.size()-1;i>=0&&out.newest.size()<20;i--)
            out.newest.add(included.get(i));
        ArrayList<Weekly> ordered=new ArrayList<>(periods.values());
        ordered.sort((a,b)->b.week.compareTo(a.week));
        out.weeks.addAll(ordered.subList(0,Math.min(12,ordered.size())));
        for(int i=Math.max(0,included.size()-10);i<included.size();i++)
            if("win".equals(included.get(i).result))out.latestWins++;
        return out;
    }
}
