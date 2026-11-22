package com.arcanevault.mobile;

import java.util.Locale;

/** Display-only Windows-style ordering for verified local Scryfall type lines.
 * Multi-type cards use the first matching category in priority order.
 * Unknown card names stay unclassified rather than being guessed.
 */
final class NativeDeckTypeGroups {
    static final String[] ORDER={
        "Creatures","Instants","Sorceries","Artifacts","Enchantments",
        "Planeswalkers","Battles","Other / Unknown","Lands"
    };
    private NativeDeckTypeGroups(){}

    static String classify(String typeLine){
        if(typeLine==null||typeLine.trim().isEmpty())return "Other / Unknown";
        String type=typeLine.toLowerCase(Locale.ROOT);
        // Land takes priority for artifact lands and creature lands.
        if(type.contains("land"))return "Lands";
        if(type.contains("creature"))return "Creatures";
        if(type.contains("instant"))return "Instants";
        if(type.contains("sorcery"))return "Sorceries";
        if(type.contains("artifact"))return "Artifacts";
        if(type.contains("enchantment"))return "Enchantments";
        if(type.contains("planeswalker"))return "Planeswalkers";
        if(type.contains("battle"))return "Battles";
        return "Other / Unknown";
    }
}
