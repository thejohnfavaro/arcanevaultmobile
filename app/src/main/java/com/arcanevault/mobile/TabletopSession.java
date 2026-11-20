package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;

/**
 * Isolated tabletop session model. This is NOT an MTG collection/deck ledger.
 * Mutations are validated, reversible and encoded in a versioned local JSON.
 */
final class TabletopSession {
    static final int VERSION=1, MAX_PLAYERS=4, MIN_PLAYERS=2, MAX_HISTORY=60;
    static final int[] COLORS={0xFFF2B84B,0xFFEA5975,0xFFB58CF4,0xFF5985F4,
        0xFF57BBA5,0xFFFFA86D,0xFFB8CAD6,0xFFED87BC};
    final int players;
    final int startingLife;
    final String[] names;
    final int[] life, poison, colors;
    // damage[recipient][source], including source index for stable references.
    final int[][] damage;
    private final ArrayDeque<String> history=new ArrayDeque<>();

    TabletopSession(int playerCount,int start){
        if(playerCount<MIN_PLAYERS||playerCount>MAX_PLAYERS||start<1||start>999)
            throw new IllegalArgumentException("Invalid tabletop game setup");
        players=playerCount;startingLife=start;
        names=new String[players];life=new int[players];poison=new int[players];
        colors=new int[players];damage=new int[players][players];
        for(int i=0;i<players;i++){
            names[i]="Player "+(i+1);life[i]=start;colors[i]=i;
        }
    }

    private static void range(int value,int min,int max){
        if(value<min||value>max)throw new IllegalArgumentException("Counter outside allowed range");
    }
    private void player(int index){
        if(index<0||index>=players)throw new IllegalArgumentException("Unknown player");
    }
    private void saveUndo(){
        if(history.size()==MAX_HISTORY)history.removeFirst();
        history.addLast(snapshot().toString());
    }
    boolean canUndo(){return !history.isEmpty();}
    void changeLife(int index,int delta){
        player(index);if(delta==0)return;
        range(life[index]+delta,-9999,9999);saveUndo();life[index]+=delta;
    }
    void changePoison(int index,int delta){
        player(index);if(delta==0)return;
        range(poison[index]+delta,0,999);saveUndo();poison[index]+=delta;
    }
    void changeCommanderDamage(int recipient,int source,int delta){
        player(recipient);player(source);
        if(source==recipient)throw new IllegalArgumentException("Commander cannot damage itself");
        if(delta==0)return;
        range(damage[recipient][source]+delta,0,999);
        range(life[recipient]-delta,-9999,9999);
        saveUndo();
        damage[recipient][source]+=delta;
        // Commander combat damage is also ordinary life loss.
        life[recipient]-=delta;
    }
    void nextColor(int index){
        player(index);setColor(index,(colors[index]+1)%COLORS.length);
    }
    void setColor(int index,int color){
        player(index);range(color,0,COLORS.length-1);
        if(colors[index]==color)return;
        saveUndo();colors[index]=color;
    }
    void rename(int index,String value){
        player(index);String trimmed=value==null?"":value.trim();
        if(trimmed.isEmpty()||trimmed.length()>28)
            throw new IllegalArgumentException("Name must be 1–28 characters");
        if(names[index].equals(trimmed))return;
        saveUndo();names[index]=trimmed;
    }
    boolean undo(){
        try {
        if(history.isEmpty())return false;
        apply(new JSONObject(history.removeLast()));
        return true;
    
        } catch(org.json.JSONException error){
            throw new IllegalArgumentException("Invalid tabletop session JSON",error);
        }
    }
    JSONObject snapshot(){
        try {
        JSONObject root=new JSONObject();
        JSONArray people=new JSONArray();
        for(int i=0;i<players;i++){
            JSONArray dealt=new JSONArray();
            for(int j=0;j<players;j++)dealt.put(damage[i][j]);
            people.put(new JSONObject().put("name",names[i]).put("life",life[i])
                .put("poison",poison[i]).put("color",colors[i]).put("damage",dealt));
        }
        root.put("version",VERSION).put("players",players)
            .put("startingLife",startingLife).put("people",people);
        return root;
    
        } catch(org.json.JSONException error){
            throw new IllegalArgumentException("Invalid tabletop session JSON",error);
        }
    }
    private void apply(JSONObject state){
        try {
        if(state.optInt("version")!=VERSION||state.optInt("players")!=players||
            state.optInt("startingLife")!=startingLife)
            throw new IllegalArgumentException("Session schema mismatch");
        JSONArray people=state.optJSONArray("people");
        if(people==null||people.length()!=players)
            throw new IllegalArgumentException("Invalid session roster");
        for(int i=0;i<players;i++){
            JSONObject item=people.optJSONObject(i);
            if(item==null)throw new IllegalArgumentException("Invalid player");
            String name=item.optString("name","").trim();
            if(name.isEmpty()||name.length()>28)throw new IllegalArgumentException("Invalid player name");
            int l=item.getInt("life"),p=item.getInt("poison"),c=item.getInt("color");
            range(l,-9999,9999);range(p,0,999);range(c,0,COLORS.length-1);
            JSONArray d=item.optJSONArray("damage");
            if(d==null||d.length()!=players)throw new IllegalArgumentException("Invalid commander entries");
            for(int j=0;j<players;j++){
                int value=d.getInt(j);range(value,0,999);
                if(i==j&&value!=0)throw new IllegalArgumentException("Self damage not allowed");
                damage[i][j]=value;
            }
            names[i]=name;life[i]=l;poison[i]=p;colors[i]=c;
        }
    
        } catch(org.json.JSONException error){
            throw new IllegalArgumentException("Invalid tabletop session JSON",error);
        }
    }
    JSONObject encode(){
        try {
        JSONArray previous=new JSONArray();
        for(String item:history)previous.put(new JSONObject(item));
        return new JSONObject().put("session",snapshot()).put("undo",previous);
    
        } catch(org.json.JSONException error){
            throw new IllegalArgumentException("Invalid tabletop session JSON",error);
        }
    }
    static TabletopSession decode(String data){
        try {
        JSONObject encoded=new JSONObject(data);
        JSONObject current=encoded.getJSONObject("session");
        int n=current.getInt("players"),start=current.getInt("startingLife");
        TabletopSession state=new TabletopSession(n,start);
        state.apply(current);
        JSONArray undo=encoded.optJSONArray("undo");
        if(undo!=null){
            if(undo.length()>MAX_HISTORY)throw new IllegalArgumentException("Too many undo records");
            for(int i=0;i<undo.length();i++){
                JSONObject item=undo.getJSONObject(i);
                // Validate historical snapshots without mutating the current session.
                TabletopSession test=new TabletopSession(n,start);
                test.apply(item);
                state.history.addLast(item.toString());
            }
        }
        return state;
    
        } catch(org.json.JSONException error){
            throw new IllegalArgumentException("Invalid tabletop session JSON",error);
        }
    }
}
