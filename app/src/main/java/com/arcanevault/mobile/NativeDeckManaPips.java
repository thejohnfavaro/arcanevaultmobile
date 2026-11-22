package com.arcanevault.mobile;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only colored casting-cost pressure, matching Windows EDH Lab's
 * EdhLabColoredPips policy. Not mana production or a castability simulator.
 * Generic/X/colorless are omitted, hybrid splits and Phyrexian or 2/color
 * alternatives are discounted. First face only for split costs.
 */
final class NativeDeckManaPips {
    private static final String COLORS="WUBRG";
    private static final Pattern SYMBOL=Pattern.compile("\\{([^{}]{1,15})\\}");

    private NativeDeckManaPips(){}

    static double[] count(String manaCost){
        double[] result=new double[5];
        if(manaCost==null||manaCost.trim().isEmpty()||manaCost.length()>512)
            return result;
        String cost=manaCost.split(" // ",2)[0];
        Matcher matches=SYMBOL.matcher(cost);
        while(matches.find()){
            String[] parts=matches.group(1).toUpperCase(Locale.ROOT).split("/");
            boolean[] included=new boolean[5];
            int colors=0;
            for(String part:parts){
                if(part.length()!=1)continue;
                int index=COLORS.indexOf(part.charAt(0));
                if(index>=0&&!included[index]){
                    included[index]=true;
                    colors++;
                }
            }
            if(colors==0)continue;
            double mass=parts.length>1&&colors==1?0.5:1.0;
            for(int i=0;i<included.length;i++)
                if(included[i])result[i]+=mass/colors;
        }
        return result;
    }
}
