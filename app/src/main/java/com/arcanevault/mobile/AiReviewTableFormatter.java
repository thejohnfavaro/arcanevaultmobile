package com.arcanevault.mobile;

import java.util.Locale;

/** Pure, lossless mobile layout of Markdown swap-table rows.
 * Card reference markers remain intact for catalog-backed clickable spans. */
final class AiReviewTableFormatter {
    private AiReviewTableFormatter(){}

    static boolean separator(String line){
        String s=line==null?"":line.trim();
        if(!s.startsWith("|"))return false;
        return s.matches("[|:\\-\\s]+") && s.contains("-");
    }
    static String[] cells(String row){
        String s=row==null?"":row.trim();
        if(s.startsWith("|"))s=s.substring(1);
        if(s.endsWith("|"))s=s.substring(0,s.length()-1);
        return s.split("\\|",-1);
    }
    static String label(String heading,int column){
        String key=heading==null?"":heading.replaceAll("[*_]","").trim().toLowerCase(Locale.ROOT);
        if(key.matches(".*(reason|why|rationale|benefit|explanation).*"))return "WHY";
        if(key.matches(".*(replacement|replace with|add|incoming).*"))return "ADD";
        if(key.matches(".*(cut|remove|outgoing|take out).*"))return "CUT";
        if(key.matches(".*(role|purpose|impact|priority|rank|notes?).*"))
            return key.toUpperCase(Locale.ROOT);
        return key.isEmpty()?"COLUMN "+(column+1):heading.trim().toUpperCase(Locale.ROOT);
    }
    static String row(String[] headers,String[] values,int index){
        StringBuilder b=new StringBuilder();
        b.append(index).append(". ");
        boolean wrote=false;
        for(int i=0;i<values.length;i++){
            String value=values[i].trim();
            if(value.isEmpty())continue;
            if(wrote)b.append("\n");
            String heading=headers!=null&&i<headers.length?headers[i]:"";
            b.append(label(heading,i)).append(": ").append(value);
            wrote=true;
        }
        return b.toString();
    }
}
