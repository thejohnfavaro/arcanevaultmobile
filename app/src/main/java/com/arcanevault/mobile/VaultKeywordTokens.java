package com.arcanevault.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Chip-based Oracle query editor. Emits the existing comma-separated search format. */
final class VaultKeywordTokens {
    private static final String[] TERMS={
        "Adapt","Addendum","Affinity","Afterlife","Aftermath","Alliance","Amass","Amplify",
        "Annihilator","Backup","Banding","Bargain","Battalion","Bestow","Blitz","Bloodthirst",
        "Boast","Bushido","Buyback","Cascade","Casualty","Champion","Changeling",
        "Channel","Cipher","Class","Cleave","Collect evidence","Compleated","Connive",
        "Conspire","Convoke","Corrupted","Craft","Crew","Cycling","Dash","Daybound",
        "Deathtouch","Defender","Delirium","Demonstrate","Descend","Devour","Discover",
        "Disturb","Domain","Double strike","Dredge","Echo","Embalm","Emerge","Encore",
        "Enlist","Entwine","Epic","Escalate","Escape","Evoke","Evolve","Exalted",
        "Exploit","Explore","Extort","Fabricate","Fading","Fear","Ferocious","First strike",
        "Flanking","Flash","Flashback","Flying","Forecast","Foretell","Formidable",
        "Fortify","Frenzy","Fuse","Goad","Gravestorm","Haste","Haunt","Hellbent",
        "Heroic","Hexproof","Hideaway","Horsemanship","Improvise","Infect","Inspired",
        "Investigate","Jump-start","Kicker","Landfall","Landwalk","Learn","Lifelink",
        "Living weapon","Madness","Manifest","Menace","Mentor","Miracle","Modular",
        "Monstrosity","Morph","Mutate","Myriad","Ninjutsu","Offering","Outlast",
        "Overload","Persist","Phasing","Plot","Poisonous","Populate","Proliferate",
        "Protection","Prowess","Prowl","Prototype","Radiance","Raid","Rampage",
        "Ravenous","Reach","Rebound","Reconfigure","Recover","Reinforce","Renown",
        "Replicate","Retrace","Riot","Ripple","Sacrifice","Saddle","Scry","Shadow",
        "Skulk","Soulbond","Soulshift","Spectacle","Splice","Split second","Squad",
        "Storm","Sunburst","Surge","Suspend","Surveil","Toxic","Training","Trample",
        "Transfigure","Transform","Transmute","Tribute","Undaunted","Undying",
        "Unearth","Unleash","Vanishing","Vigilance","Ward","Wither",
        "Discard","Draw","Token","Tokens","Treasure","Clue","Food","Counter",
        "Counters","Exile","Graveyard","Mill","Destroy","Search your library"
    };
    private final Activity activity;
    private final Consumer<String> callback;
    private final ArrayList<String> selected=new ArrayList<>();
    private final LinearLayout root;
    private final LinearLayout chips;
    private final LinearLayout suggestions;
    private final EditText input;
    private boolean changing;

    VaultKeywordTokens(Activity activity,Consumer<String> callback){
        this.activity=activity;
        this.callback=callback;
        root=VaultNativeStyle.column(activity);
        chips=VaultNativeStyle.row(activity);
        chips.setOrientation(LinearLayout.VERTICAL);
        root.addView(chips);
        input=new EditText(activity);
        VaultNativeStyle.search(activity,input);
        input.setSingleLine(true);
        input.setHint("Type a mechanic or rules phrase…");
        root.addView(input,new LinearLayout.LayoutParams(-1,dp(46)));
        suggestions=VaultNativeStyle.column(activity);
        root.addView(suggestions);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(changing)return;
                String text=s.toString();
                if(text.contains(",") || text.endsWith(" ")){
                    String candidate=text.replace(",","").trim();
                    if(!candidate.isEmpty()){
                        String match=best(candidate);
                        if(match!=null)commit(match);
                        else if(text.contains(","))commit(candidate);
                        else notifyChanged();
                    }else if(text.contains(",")){clearInput();}
                    return;
                }
                showSuggestions(text.trim());
                notifyChanged();
            }
            public void afterTextChanged(Editable e){}
        });
        input.setOnEditorActionListener((v,action,event)->{
            acceptInput();
            return true;
        });
        input.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DEL&&input.length()==0&&!selected.isEmpty()){
                selected.remove(selected.size()-1);
                renderChips();notifyChanged();
                return true;
            }
            return false;
        });
        renderChips();
    }
    View view(){return root;}
    private int dp(float size){return VaultNativeStyle.dp(activity,size);}
    private static String best(String prefix){
        if(prefix.isEmpty())return null;
        for(String term:TERMS)if(term.equalsIgnoreCase(prefix))return term;
        for(String term:TERMS)if(term.toLowerCase(Locale.ROOT)
            .startsWith(prefix.toLowerCase(Locale.ROOT)))return term;
        return null;
    }
    private void acceptInput(){
        String text=input.getText().toString().trim();
        if(text.isEmpty())return;
        String candidate=best(text);
        commit(candidate==null?text:candidate);
    }
    private void clearInput(){
        changing=true;
        input.setText("");
        changing=false;
        suggestions.removeAllViews();
        notifyChanged();
    }
    private void commit(String term){
        if(term.length()>100)return;
        boolean exists=false;
        for(String s:selected)if(s.equalsIgnoreCase(term))exists=true;
        if(!exists)selected.add(term);
        changing=true;input.setText("");changing=false;
        renderChips();
        suggestions.removeAllViews();
        notifyChanged();
    }
    private void showSuggestions(String prefix){
        suggestions.removeAllViews();
        if(prefix.isEmpty())return;
        int added=0;
        for(String term:TERMS){
            if(!term.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                continue;
            boolean exists=false;
            for(String token:selected)if(token.equalsIgnoreCase(term))exists=true;
            if(exists)continue;
            Button choice=VaultNativeStyle.button(activity,term+"   ↵",false);
            choice.setTextSize(13);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(40));
            params.bottomMargin=dp(2);
            suggestions.addView(choice,params);
            choice.setOnClickListener(v->commit(term));
            if(++added>=4)break;
        }
    }
    private void renderChips(){
        chips.removeAllViews();
        if(selected.isEmpty())return;
        LinearLayout line=null;
        for(int i=0;i<selected.size();i++){
            if(i%2==0){
                line=VaultNativeStyle.row(activity);
                chips.addView(line,new LinearLayout.LayoutParams(-1,dp(38)));
            }
            final String token=selected.get(i);
            Button chip=VaultNativeStyle.button(activity,token+"  ×",false);
            chip.setTextSize(12);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(35),1);
            params.rightMargin=dp(5);
            line.addView(chip,params);
            chip.setOnClickListener(v->{selected.remove(token);renderChips();notifyChanged();});
        }
    }
    private void notifyChanged(){
        String pending=input.getText().toString().trim();
        List<String> all=new ArrayList<>(selected);
        if(!pending.isEmpty()&&!pending.contains(","))all.add(pending);
        callback.accept(String.join(",",all));
    }
}
