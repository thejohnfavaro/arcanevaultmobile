package com.arcanevault.mobile;

import java.util.Random;

/**
 * Offline tabletop randomizers. These are display-only utilities: no changes
 * to game session, life, poison, commander damage, decks, or collections.
 */
final class TabletopRandomizer {
    private TabletopRandomizer(){}

    static int chooseStartingPlayer(int players,Random random){
        if(players<2||players>4||random==null)
            throw new IllegalArgumentException("Select a 2–4 player game");
        return random.nextInt(players);
    }

    static String flipCoin(Random random){
        if(random==null)throw new IllegalArgumentException("Missing random source");
        return random.nextBoolean()?"Heads":"Tails";
    }

    static final class Roll {
        final int[] faces;
        final int total;
        Roll(int[] faces,int total){this.faces=faces;this.total=total;}
        String detail(){
            StringBuilder text=new StringBuilder();
            for(int i=0;i<faces.length;i++){
                if(i>0)text.append(" + ");
                text.append(faces[i]);
            }
            return text.toString();
        }
    }

    static Roll roll(int dice,int sides,Random random){
        if(random==null||dice<1||dice>12||sides<2||sides>100)
            throw new IllegalArgumentException("Dice must be 1–12 and have 2–100 sides");
        int[] values=new int[dice];
        int total=0;
        for(int i=0;i<dice;i++){
            values[i]=random.nextInt(sides)+1;
            total+=values[i];
        }
        return new Roll(values,total);
    }
}
