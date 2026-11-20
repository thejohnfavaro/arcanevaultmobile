package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolated tabletop session model. This is NOT an MTG collection/deck ledger.
 * Mutations are validated, reversible and encoded in a versioned local JSON.
 */
final class TabletopSession {
    static final int VERSION=1, MAX_PLAYERS=4, MIN_PLAYERS=2, MAX_HISTORY=60, MAX_EVENTS=40;
    static final int[] COLORS={0xFFF2B84B,0xFFEA5975,0xFFB58CF4,0xFF5985F4,
        0xFF57BBA5,0xFFFFA86D,0xFFB8CAD6,0xFFED87BC};
    final int players;
    final int startingLife;
    final String[] names;
    final int[] life, poison, colors;
    // damage[recipient][source], including source index for stable references.
    final int[][] damage;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private final ArrayDeque<String> events=new ArrayDeque<>();
    int activePlayer=0,firstPlayer=0,round=1;

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
    List<String> recentEvents(int count){
        List<String> latest=new ArrayList<>();
        if(count<=0)return latest;
        java.util.Iterator<String> it=events.descendingIterator();
        while(it.hasNext()&&latest.size()<count)latest.add(it.next());
        return latest;
    }
    private void record(String description){
        if(description==null||description.length()>160)return;
        if(events.size()==MAX_EVENTS)events.removeFirst();
        events.addLast("Round "+round+" · "+description);
    }
    void setStartingPlayer(int index){
        player(index);
        if(activePlayer==index&&firstPlayer==index&&round==1)return;
        saveUndo();
        firstPlayer=index;activePlayer=index;round=1;
        record(names[index]+" chosen as starting player");
    }
    void nextTurn(){
        if(round>=9999&&activePlayer==(firstPlayer+players-1)%players)
            throw new IllegalArgumentException("Turn limit reached");
        saveUndo();
        activePlayer=(activePlayer+1)%players;
        if(activePlayer==firstPlayer)round++;
        record(names[activePlayer]+" begins their turn");
    }
    void changeLife(int index,int delta){
        player(index);if(delta==0)return;
        range(life[index]+delta,-9999,9999);saveUndo();life[index]+=delta;
        record(names[index]+(delta>0?" +":" ")+delta+" life ("+life[index]+")");
    }
    void changePoison(int index,int delta){
        player(index);if(delta==0)return;
        range(poison[index]+delta,0,999);saveUndo();poison[index]+=delta;
        record(names[index]+(delta>0?" +":" ")+delta+" poison ("+poison[index]+")");
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
        record(names[recipient]+" "+(delta>0?"+":"")+delta+" commander damage from "+
            names[source]+" ("+damage[recipient][source]+" total)");
    }
    void nextColor(int index){
        player(index);setColor(index,(colors[index]+1)%COLORS.length);
    }
    void setColor(int index,int color){
        player(index);range(color,0,COLORS.length-1);
        if(colors[index]==color)return;
        saveUndo();colors[index]=color;
        record(names[index]+" changed panel color");
    }
    void rename(int index,String value){
        player(index);String trimmed=value==null?"":value.trim();
        if(trimmed.isEmpty()||trimmed.length()>28)
            throw new IllegalArgumentException("Name must be 1–28 characters");
        if(names[index].equals(trimmed))return;
        saveUndo();String previous=names[index];names[index]=trimmed;
        record(previous+" renamed to "+trimmed);
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
        JSONArray audit=new JSONArray();
        for(String entry:events)audit.put(entry);
        root.put("version",VERSION).put("players",players)
            .put("startingLife",startingLife).put("people",people)
            .put("activePlayer",activePlayer).put("firstPlayer",firstPlayer)
            .put("round",round).put("events",audit);
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
        int active=state.optInt("activePlayer",0);
        int first=state.optInt("firstPlayer",0);
        int turnRound=state.optInt("round",1);
        player(active);player(first);range(turnRound,1,9999);
        JSONArray audit=state.optJSONArray("events");
        if(audit!=null&&audit.length()>MAX_EVENTS)
            throw new IllegalArgumentException("Session event history too large");
        ArrayDeque<String> restoredEvents=new ArrayDeque<>();
        if(audit!=null)for(int a=0;a<audit.length();a++){
            String event=audit.getString(a);
            if(event.length()>160)throw new IllegalArgumentException("Invalid session event");
            restoredEvents.addLast(event);
        }
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
        activePlayer=active;firstPlayer=first;round=turnRound;
        events.clear();events.addAll(restoredEvents);
    
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
