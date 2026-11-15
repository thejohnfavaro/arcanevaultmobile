package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loss-aware, read-only decklist interchange. Import only changes an unsaved
 * editor draft; authoritative saved decks still require explicit review and
 * the existing compare-and-swap recovery-backed save. */
final class NativeDeckTextExchange {
    private NativeDeckTextExchange(){}
    private static final Pattern COUNTED=Pattern.compile("^(\\d{1,4})\\s+(?:x\\s+)?(.+)$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern ARENA_PRINTING=Pattern.compile(
        "^(.+?)\\s+\\(([A-Za-z0-9]{2,8})\\)\\s+([0-9]{1,6}[A-Za-z]?)$");

    static final class Parsed {
        final String entries;
        final int copies;
        final boolean removedPrintingAnnotations;
        Parsed(String entries,int copies,boolean removedPrintingAnnotations){
            this.entries=entries;this.copies=copies;
            this.removedPrintingAnnotations=removedPrintingAnnotations;
        }
    }

    static Parsed parse(String raw,String existingCommander)throws Exception {
        if(raw==null||raw.isBlank()||raw.length()>64000)
            throw new IllegalArgumentException("Paste a decklist of at most 64,000 characters.");
        int section=0,rows=0,side=0,commanderRows=0;
        boolean stripped=false;
        StringBuilder main=new StringBuilder();
        String commander=existingCommander==null?"":existingCommander.trim();
        for(String original:raw.replace("\r\n","\n").replace('\r','\n').split("\n")){
            String line=original.trim().replace("\uFEFF","");
            if(line.isEmpty())continue;
            if(++rows>550)throw new IllegalArgumentException("Decklist exceeds 550 lines.");
            String lower=line.toLowerCase(Locale.ROOT);
            if(lower.equals("deck")||lower.equals("mainboard")||
                    lower.equals("main deck")){section=0;continue;}
            if(lower.equals("commander")||lower.equals("commanders")){section=1;continue;}
            if(lower.equals("sideboard")||lower.equals("side")||
                    lower.equals("companion")||lower.equals("maybeboard")||
                    lower.equals("considering")||lower.equals("wishboard"))
                throw new IllegalArgumentException(
                    "Sideboard/companion sections cannot be imported without losing data.");
            Matcher count=COUNTED.matcher(line);
            if(!count.matches())throw new IllegalArgumentException(
                "Expected '1 Card Name' on each line: "+line);
            int qty=Integer.parseInt(count.group(1));
            String name=count.group(2).trim();
            Matcher annotated=ARENA_PRINTING.matcher(name);
            if(annotated.matches()){name=annotated.group(1).trim();stripped=true;}
            if(name.isEmpty()||name.length()>180||qty<1||qty>999)
                throw new IllegalArgumentException("Invalid deck row: "+line);
            if(section==1){
                commanderRows++;
                if(commanderRows>1||qty!=1||commander.isEmpty()||
                    !commander.equalsIgnoreCase(name))
                    throw new IllegalArgumentException(
                        "Imported commander must match this deck's commander. "+
                        "Choose a matching deck before importing.");
            }else{
                if(main.length()>0)main.append('\n');
                main.append(qty).append(' ').append(name);
            }
        }
        JSONArray normalized=NativeDeckDraft.parseEntries(main.toString());
        if(normalized.length()==0)
            throw new IllegalArgumentException("The imported mainboard is empty.");
        int copies=0;
        for(int i=0;i<normalized.length();i++)
            copies+=normalized.getJSONObject(i).getInt("qty");
        return new Parsed(NativeDeckDraft.formatEntries(normalized),copies,stripped);
    }

    static String export(JSONObject deck,boolean arena){
        StringBuilder out=new StringBuilder();
        String commander=deck.optString("commander","").trim();
        if(!commander.isEmpty()){
            out.append("Commander\n1 ").append(commander).append("\n\n");
        }
        out.append(arena?"Deck\n":"Mainboard\n");
        out.append(NativeDeckDraft.formatEntries(deck.optJSONArray("entries")));
        return out.toString().trim()+"\n";
    }
}
