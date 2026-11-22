package com.arcanevault.mobile;

import java.util.Locale;

/**
 * Conservative local Oracle-text signals, based on Windows EDH Lab's
 * EdhLabDeckBlueprint categories. These are broad hints, not verified card
 * functionality, legality, synergy, or a deck power score.
 */
final class NativeDeckHealth {
    static final String[] KEYS={"Land","Ramp","Card Draw","Removal","Board Wipe","Protection"};
    static final String[] LABELS={"Lands","Ramp","Draw","Removal","Wipes","Protection"};
    static final int[] TARGETS={36,10,10,8,2,2};
    static final String[] FOCUS={"Balanced","Faster starts","Card advantage",
        "Interaction & protection","Commander synergy"};
    private NativeDeckHealth(){}

    static boolean[] roles(String typeLine,String oracle){
        String type=(typeLine==null?"":typeLine).toLowerCase(Locale.ROOT);
        String text=(oracle==null?"":oracle).toLowerCase(Locale.ROOT);
        boolean[] roles=new boolean[6];
        roles[0]=type.contains("land");
        if(roles[0])return roles;
        roles[1]=text.contains("add {")||text.contains("add one mana")||
            text.contains("add two mana")||text.contains("add three mana")||
            text.contains("search your library for a basic land")||
            text.contains("put a land card from your hand onto the battlefield")||
            text.contains("additional land");
        roles[2]=text.contains("draw a card")||text.contains("draw two cards")||
            text.contains("draw three cards")||text.contains("draw x cards")||
            text.contains("investigate")||text.contains("search your library for a card");
        roles[3]=text.contains("destroy target")||text.contains("exile target")||
            text.contains("deals damage to target")||text.contains("return target creature")||
            text.contains("counter target spell");
        roles[4]=text.contains("destroy all creatures")||
            text.contains("exile all creatures")||
            text.contains("destroy all nonland permanents")||
            text.contains("all creatures get -");
        roles[5]=text.contains("hexproof")||text.contains("indestructible")||
            text.contains("phase out")||text.contains("protection from")||
            text.contains("ward {");
        return roles;
    }

    static int[] priority(int focus){
        switch(focus){
            case 1:return new int[]{1,0,2,3,4,5};
            case 2:return new int[]{2,1,0,3,5,4};
            case 3:return new int[]{3,5,4,2,1,0};
            case 4:return new int[]{2,1,3,5,0,4};
            default:return new int[]{0,1,2,3,4,5};
        }
    }

    static String state(int count,int target,int mainboard,int unknown){
        if(unknown>0)return "Provisional";
        if(mainboard<60)return "Draft building";
        return count>=target?"At starting target":"Below suggested target";
    }
}
