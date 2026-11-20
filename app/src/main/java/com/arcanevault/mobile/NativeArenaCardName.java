package com.arcanevault.mobile;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only MTG Arena printing annotation normalization.
 * Saved entry names and their selected printings are never rewritten.
 * Only known trailing "(SET) 123" annotations are removed for catalog and
 * ownership lookup, matching the Windows ArenaDeckCardName contract.
 */
final class NativeArenaCardName {
    private static final Pattern PRINTING=Pattern.compile(
        "^(.+?)\\s+\\([A-Za-z0-9]{2,8}\\)\\s+[0-9]{1,6}[A-Za-z]?$");

    private NativeArenaCardName(){}

    static String identity(String savedName){
        String name=savedName==null?"":savedName.trim();
        if(name.isEmpty()||name.length()>255)return name;
        Matcher match=PRINTING.matcher(name);
        return match.matches()?match.group(1).trim():name;
    }

    static String key(String savedName){
        return identity(savedName).toLowerCase(Locale.ROOT);
    }
}
