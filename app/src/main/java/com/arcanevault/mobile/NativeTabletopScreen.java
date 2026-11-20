package com.arcanevault.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.security.SecureRandom;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.os.Looper;

/**
 * Dedicated offline tabletop surface. Uses a separate preference file,
 * deliberately excluded from peer sync and all collection/deck inventories.
 */
final class NativeTabletopScreen {
    private static final String STORE="arcane_tabletop_companion_v1";
    private static final String SESSION="session_v1";
    private static final String COMPLETED="completed_games_v1";
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Runnable exit;
    private final NativeWorkspaceScreen.DeckLoader deckLoader;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final LinearLayout root;
    private final SecureRandom tableRandom=new SecureRandom();
    private TabletopSession session;
    private boolean closed;
    private boolean sessionDirty;
    private int currentOrientation=-1;

    NativeTabletopScreen(Activity activity,Runnable exit,
            NativeWorkspaceScreen.DeckLoader deckLoader){
        this.activity=activity;this.exit=exit;this.deckLoader=deckLoader;
        prefs=activity.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        root=VaultNativeStyle.column(activity);
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Android handles sensor orientation and recreates this view from the
        // independently persisted session. Never force landscape or portrait.
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        String stored=prefs.getString(SESSION,null);
        if(stored!=null){
            try {session=TabletopSession.decode(stored);}
            catch(Exception corrupted){showRecoveryError();}
        }
        if(session==null)setup();
        else render();
    }

    View view(){return root;}

    void close(){
        if(closed)return;
        closed=true;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    boolean onBack(){
        if(closed)return false;
        new AlertDialog.Builder(activity).setTitle("Leave Tabletop Companion?")
            .setMessage("Your game remains saved locally. Return anytime to resume.")
            .setNegativeButton("Stay",null)
            .setPositiveButton("Leave",(d,w)->exit.run()).show();
        return true;
    }

    private int dp(int size){return VaultNativeStyle.dp(activity,size);}
    private TextView label(String text,int size,boolean bold){
        return VaultNativeStyle.label(activity,text,size,VaultNativeStyle.TEXT,bold);
    }
    private Button button(String text){return VaultNativeStyle.button(activity,text,false);}
    private void showRecoveryError(){
        new AlertDialog.Builder(activity).setTitle("Tabletop recovery unavailable")
            .setMessage("The saved Tabletop Companion session cannot be read. Your collection and decks are unaffected. Start a new game to replace only the tabletop session.")
            .setPositiveButton("OK",null).show();
    }
    private void replaceContent(){root.removeAllViews();}
    private void persist(Runnable operation){persist(operation,true);}
    private void persist(Runnable operation,boolean redraw){
        if(closed||session==null)return;
        String before=session.encode().toString();
        try{
            operation.run();
            if(!prefs.edit().putString(SESSION,session.encode().toString()).commit()){
                session=TabletopSession.decode(before);
                throw new IllegalStateException("Device could not save the game");
            }
            if(redraw)render();
            else sessionDirty=true;
        }catch(Exception error){
            session=TabletopSession.decode(before);
            new AlertDialog.Builder(activity).setTitle("Change not saved")
                .setMessage(error.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void setup(){
        replaceContent();
        LinearLayout body=VaultNativeStyle.column(activity);
        body.setPadding(dp(18),dp(28),dp(18),dp(18));
        body.addView(label("Tabletop Companion",25,true));
        TextView sub=label("Offline life counter · Commander preset · automatic orientation",13,false);
        sub.setTextColor(VaultNativeStyle.SUBTEXT);
        body.addView(sub);
        TextView title=label("Start a game",19,true);
        title.setPadding(0,dp(28),0,dp(10));body.addView(title);
        for(int n:new int[]{4,3,2}){
            final int players=n;
            Button choice=button(n+" players · Commander · 40 life");
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(62));
            lp.bottomMargin=dp(12);body.addView(choice,lp);
            choice.setOnClickListener(v->start(players));
        }
        Button leave=button("Back to Arcane Vault");
        body.addView(leave,new LinearLayout.LayoutParams(-1,dp(54)));
        leave.setOnClickListener(v->exit.run());
        root.addView(body,new LinearLayout.LayoutParams(-1,-1));
    }
    private void start(int players){
        new AlertDialog.Builder(activity).setTitle("Start a new game?")
            .setMessage("This resets only the current tabletop session. Decks and collections are untouched.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Start",(d,w)->{
                TabletopSession fresh=new TabletopSession(players,40);
                if(!prefs.edit().putString(SESSION,fresh.encode().toString()).commit()){
                    new AlertDialog.Builder(activity).setMessage("Unable to save new game")
                        .setPositiveButton("OK",null).show();
                    return;
                }
                session=fresh;render();
            }).show();
    }
    private Button counterButton(LinearLayout row,String text,Runnable action){
        Button b=button(text);
        b.setTextSize(17);
        b.setMinHeight(dp(54));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);
        lp.setMargins(dp(2),0,dp(2),0);
        row.addView(b,lp);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void render(){
        if(closed)return;
        if(session!=null&&session.finished()){renderCompleted();return;}
        replaceContent();
        LinearLayout toolbar=VaultNativeStyle.row(activity);
        toolbar.setPadding(dp(8),dp(4),dp(8),dp(4));
        Button leave=button("← Vault");
        toolbar.addView(leave,new LinearLayout.LayoutParams(0,dp(42),1));
        leave.setOnClickListener(v->onBack());
        Button undo=button("↶ Undo");
        undo.setEnabled(session.canUndo());
        toolbar.addView(undo,new LinearLayout.LayoutParams(0,dp(42),1));
        undo.setOnClickListener(v->persist(()->session.undo()));
        Button tools=button("⚄ Table Tools");
        tools.setContentDescription("Dice, coin flip, starting player, new game");
        toolbar.addView(tools,new LinearLayout.LayoutParams(0,dp(42),1));
        tools.setOnClickListener(v->openTableTools());
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(50)));

        // Keep turn and game history controls visible without leaving the table.
        LinearLayout turnBar=VaultNativeStyle.row(activity);
        turnBar.setPadding(dp(7),0,dp(7),dp(3));
        Button current=button("Round "+session.round+" · "+session.names[session.activePlayer]);
        current.setTextSize(12);
        current.setContentDescription("Round "+session.round+", "+session.names[session.activePlayer]+
            " is active. Tap to choose starting player.");
        turnBar.addView(current,new LinearLayout.LayoutParams(0,dp(42),2));
        current.setOnClickListener(v->chooseCurrentPlayer());
        Button next=button("Next →");
        next.setContentDescription("End current turn and advance to next player");
        turnBar.addView(next,new LinearLayout.LayoutParams(0,dp(42),1));
        next.setOnClickListener(v->persist(()->session.nextTurn()));
        Button recent=button("History");
        recent.setContentDescription("Review recent tabletop counter and turn changes");
        turnBar.addView(recent,new LinearLayout.LayoutParams(0,dp(42),1));
        recent.setOnClickListener(v->showHistory());
        root.addView(turnBar,new LinearLayout.LayoutParams(-1,dp(46)));

        // Two opposing panels for 2 players; responsive four-player grid.
        // One device orientation change reflows panels; state is independent.
        int orientation=activity.getResources().getConfiguration().orientation;
        boolean landscape=orientation==Configuration.ORIENTATION_LANDSCAPE;
        currentOrientation=orientation;
        int columns=session.players==2?(landscape?2:1):
            landscape?2:2;
        int rows=(session.players+columns-1)/columns;
        LinearLayout field=VaultNativeStyle.column(activity);
        field.setPadding(dp(4),dp(2),dp(4),dp(4));
        for(int r=0;r<rows;r++){
            LinearLayout row=VaultNativeStyle.row(activity);
            row.setGravity(Gravity.CENTER);
            int items=Math.min(columns,session.players-r*columns);
            for(int col=0;col<items;col++){
                int i=r*columns+col;
                View panel=playerPanel(i,landscape);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(dp(2),dp(2),dp(2),dp(2));
                row.addView(panel,lp);
                // Opponents on the far side read their own counter upright.
                if((!landscape&&r==0&&rows>1)||(landscape&&col==0&&columns==2))
                    panel.setRotation(180f);
            }
            // For a three-player game, the third player gets the entire lower row.
            field.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        }
        root.addView(field,new LinearLayout.LayoutParams(-1,0,1));
    }
    /** Dice and coin flips are transient; selecting a starting player explicitly saves the turn. */
    private void openTableTools(){
        if(closed||session==null)return;
        LinearLayout content=VaultNativeStyle.column(activity);
        content.setPadding(dp(12),dp(10),dp(12),dp(12));
        TextView result=label("Choose a tool below",19,true);
        result.setGravity(Gravity.CENTER);
        result.setMinHeight(dp(72));
        result.setContentDescription("Table tool result");
        content.addView(result,new LinearLayout.LayoutParams(-1,dp(78)));

        Button first=button("Choose starting player");
        first.setMinHeight(dp(54));
        content.addView(first,new LinearLayout.LayoutParams(-1,dp(56)));
        first.setOnClickListener(v->{
            int choice=TabletopRandomizer.chooseStartingPlayer(session.players,tableRandom);
            persist(()->session.setStartingPlayer(choice),false);
            result.setText(session.names[choice]+" starts · Round 1");
        });

        Button coin=button("Flip coin");
        coin.setMinHeight(dp(54));
        content.addView(coin,new LinearLayout.LayoutParams(-1,dp(56)));
        coin.setOnClickListener(v->result.setText(
            "Coin flip: "+TabletopRandomizer.flipCoin(tableRandom)));

        LinearLayout diceRow=VaultNativeStyle.row(activity);
        for(int sides:new int[]{6,20}){
            Button die=button("Roll D"+sides);
            die.setMinHeight(dp(54));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);
            lp.setMargins(dp(2),0,dp(2),0);
            diceRow.addView(die,lp);
            die.setOnClickListener(v->{
                TabletopRandomizer.Roll roll=TabletopRandomizer.roll(1,sides,tableRandom);
                result.setText("D"+sides+" → "+roll.total);
            });
        }
        content.addView(diceRow,new LinearLayout.LayoutParams(-1,dp(58)));

        Button twoDice=button("Roll 2D6");
        twoDice.setMinHeight(dp(54));
        content.addView(twoDice,new LinearLayout.LayoutParams(-1,dp(56)));
        twoDice.setOnClickListener(v->{
            TabletopRandomizer.Roll roll=TabletopRandomizer.roll(2,6,tableRandom);
            result.setText("2D6 → "+roll.total+" ("+roll.detail()+")");
        });

        TextView note=label("Dice and coins are display-only. Starting player changes turn order.",12,false);
        note.setTextColor(VaultNativeStyle.SUBTEXT);
        note.setGravity(Gravity.CENTER);
        content.addView(note,new LinearLayout.LayoutParams(-1,dp(32)));
        Button decks=button("Associate saved decks");
        content.addView(decks,new LinearLayout.LayoutParams(-1,dp(55)));
        decks.setOnClickListener(v->showDeckAssignments());
        Button finish=button("End Game · Choose winner");
        content.addView(finish,new LinearLayout.LayoutParams(-1,dp(55)));
        finish.setOnClickListener(v->showEndGame());

        android.widget.ScrollView scroll=VaultNativeStyle.scroll(activity);
        scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Table Tools")
            .setView(scroll)
            .setNegativeButton("Close",null)
            .setPositiveButton("New Game",null)
            .create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->new AlertDialog.Builder(activity)
                .setTitle("Replace current game?")
                .setMessage("Start a new tabletop game? Your current saved game will remain intact until you select and confirm a new game.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Choose players",(confirm,w)->{
                    dialog.dismiss();
                    setup();
                }).show()));
        dialog.setOnDismissListener(d->{
            if(sessionDirty&&!closed){sessionDirty=false;render();}
        });
        dialog.show();
    }

    private String finalSummary(){
        StringBuilder summary=new StringBuilder();
        summary.append("Round ").append(session.round).append(" · Commander\\n");
        for(int i=0;i<session.players;i++){
            summary.append("\\n").append(session.names[i]).append(": ")
                .append(session.life[i]).append(" life · ")
                .append(session.poison[i]).append(" poison");
            if(!session.deckNames[i].isEmpty())
                summary.append("\\nDeck: ").append(session.deckNames[i]);
            for(int source=0;source<session.players;source++){
                if(source!=i&&session.damage[i][source]>0)
                    summary.append("\\n  Commander from ")
                        .append(session.names[source]).append(": ")
                        .append(session.damage[i][source]);
            }
            summary.append("\\n");
        }
        return summary.toString();
    }

    private void showDeckAssignments(){
        if(closed||session==null||session.finished())return;
        String[] seats=new String[session.players];
        for(int i=0;i<seats.length;i++)seats[i]=session.names[i]+
            (session.deckNames[i].isEmpty()?" · No linked deck":
                " · "+session.deckNames[i]);
        new AlertDialog.Builder(activity).setTitle("Associate saved decks")
            .setMessage("Optional. Links are saved in this tabletop session only and do not update deck statistics.")
            .setItems(seats,(dialog,seat)->loadDeckChoices(seat))
            .setNegativeButton("Done",null).show();
    }

    private void loadDeckChoices(int seat){
        if(deckLoader==null){new AlertDialog.Builder(activity)
            .setMessage("Saved deck library unavailable.")
            .setPositiveButton("OK",null).show();return;}
        deckLoader.fetch(json->ui.post(()->{
            if(closed||activity.isFinishing()||session==null||session.finished())return;
            try{
                JSONArray library=new JSONArray(json);
                if(library.length()>5000)throw new IllegalArgumentException("Deck list exceeds safety limit");
                java.util.ArrayList<String> labels=new java.util.ArrayList<>();
                java.util.ArrayList<String> ids=new java.util.ArrayList<>();
                java.util.ArrayList<String> names=new java.util.ArrayList<>();
                labels.add("None · Unlink saved deck");ids.add("");names.add("");
                for(int i=0;i<library.length();i++){
                    JSONObject deck=library.optJSONObject(i);
                    if(deck==null)continue;
                    String id=deck.optString("id","").trim();
                    String name=deck.optString("name","").trim();
                    if(id.isEmpty()||name.isEmpty()||id.length()>128||name.length()>160)continue;
                    ids.add(id);names.add(name);
                    labels.add(name+(deck.optString("commander","").isEmpty()?"":
                        " · "+deck.optString("commander","")));
                }
                new AlertDialog.Builder(activity).setTitle("Deck for "+session.names[seat])
                    .setItems(labels.toArray(new String[0]),(dialog,which)->{
                        persist(()->session.associateDeck(seat,ids.get(which),names.get(which)));
                        if(!closed&&!session.finished())showDeckAssignments();
                    }).setNegativeButton("Cancel",null).show();
            }catch(Exception error){
                new AlertDialog.Builder(activity).setTitle("Deck library unavailable")
                    .setMessage("No deck was changed. "+error.getMessage())
                    .setPositiveButton("OK",null).show();
            }
        }));
    }

    private void showEndGame(){
        if(closed||session==null||session.finished())return;
        new AlertDialog.Builder(activity).setTitle("End Game · Choose winner")
            .setMessage("Who won? No deck wins or losses will be recorded.")
            .setItems(session.names.clone(),(dialog,seat)->confirmEndGame(seat))
            .setNegativeButton("Cancel",null).show();
    }

    private void confirmEndGame(int seat){
        if(closed||session==null||session.finished())return;
        new AlertDialog.Builder(activity).setTitle("Confirm winner: "+session.names[seat])
            .setMessage(finalSummary()+"\\nA local match summary will be saved. No deck statistics will change.")
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Link decks",(dialog,w)->showDeckAssignments())
            .setPositiveButton("Finish Game",(dialog,w)->finishGame(seat)).show();
    }

    private void finishGame(int winner){
        if(closed||session==null||session.finished())return;
        String previous=session.encode().toString();
        try{
            session.finish(winner,System.currentTimeMillis());
            JSONArray archive=new JSONArray(prefs.getString(COMPLETED,"[]"));
            // Keep only recent local result snapshots. Not part of peer sync.
            if(archive.length()>29){
                JSONArray retained=new JSONArray();
                for(int i=Math.max(0,archive.length()-29);i<archive.length();i++)
                    retained.put(archive.getJSONObject(i));
                archive=retained;
            }
            archive.put(session.snapshot());
            boolean saved=prefs.edit()
                .putString(SESSION,session.encode().toString())
                .putString(COMPLETED,archive.toString()).commit();
            if(!saved)throw new IllegalStateException("Could not save game result");
            render();
        }catch(Exception error){
            session=TabletopSession.decode(previous);
            new AlertDialog.Builder(activity).setTitle("Game not finished")
                .setMessage("Local result was not saved: "+error.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }

    private void renderCompleted(){
        replaceContent();
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        LinearLayout body=VaultNativeStyle.column(activity);
        body.setPadding(dp(18),dp(20),dp(18),dp(18));
        TextView title=label("Game Complete",26,true);
        title.setTextColor(VaultNativeStyle.GOLD);body.addView(title);
        TextView winner=label("Winner: "+session.names[session.winner],21,true);
        winner.setPadding(0,dp(12),0,dp(12));body.addView(winner);
        TextView report=label(finalSummary(),14,false);
        body.addView(report);
        TextView note=label("Saved locally only · Deck win/loss statistics unchanged",12,false);
        note.setTextColor(VaultNativeStyle.SUBTEXT);
        note.setPadding(0,dp(12),0,dp(12));body.addView(note);
        Button reopen=button("Reopen game for corrections");
        body.addView(reopen,new LinearLayout.LayoutParams(-1,dp(54)));
        reopen.setOnClickListener(v->new AlertDialog.Builder(activity)
            .setTitle("Reopen completed game?")
            .setMessage("Counters become editable again. The recorded match summary remains in local history.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Reopen",(dialog,w)->persist(()->session.reopen())).show());
        Button newGame=button("New tabletop game");
        body.addView(newGame,new LinearLayout.LayoutParams(-1,dp(54)));
        newGame.setOnClickListener(v->setup());
        Button back=button("Return to Arcane Vault");
        body.addView(back,new LinearLayout.LayoutParams(-1,dp(54)));
        back.setOnClickListener(v->exit.run());
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void chooseCurrentPlayer(){
        if(closed||session==null)return;
        String[] roster=session.names.clone();
        new AlertDialog.Builder(activity).setTitle("Choose starting player")
            .setMessage("This resets turn tracking to Round 1. Life, commander damage, poison, and game history are preserved.")
            .setSingleChoiceItems(roster,session.activePlayer,(dialog,choice)->{
                persist(()->session.setStartingPlayer(choice));
                dialog.dismiss();
            })
            .setNegativeButton("Cancel",null).show();
    }

    private void showHistory(){
        if(closed||session==null)return;
        java.util.List<String> events=session.recentEvents(30);
        LinearLayout content=VaultNativeStyle.column(activity);
        content.setPadding(dp(14),dp(8),dp(14),dp(10));
        if(events.isEmpty()){
            TextView empty=label("No changes recorded yet. Life, poison, commander damage, and turns will appear here.",14,false);
            empty.setTextColor(VaultNativeStyle.SUBTEXT);
            content.addView(empty);
        }else for(String event:events){
            TextView line=label(event,14,false);
            line.setPadding(dp(5),dp(8),dp(5),dp(8));
            content.addView(line,new LinearLayout.LayoutParams(-1,-2));
        }
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        scroll.addView(content);
        new AlertDialog.Builder(activity).setTitle("Game History · newest first")
            .setView(scroll)
            .setPositiveButton("Done",null).show();
    }

    private View playerPanel(int i,boolean landscape){
        int background=TabletopSession.COLORS[session.colors[i]];
        LinearLayout panel=VaultNativeStyle.column(activity);
        panel.setPadding(dp(5),dp(4),dp(5),dp(4));
        panel.setBackground(VaultNativeStyle.rounded(activity,background,
            session.activePlayer==i?Color.BLACK:Color.TRANSPARENT,14));
        panel.setGravity(Gravity.CENTER);
        TextView name=VaultNativeStyle.label(activity,
            (session.activePlayer==i?"▶ ":"")+session.names[i],15,Color.BLACK,true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panel.addView(name,new LinearLayout.LayoutParams(-1,dp(26)));
        name.setContentDescription(session.names[i]+". Long press to rename");
        name.setOnLongClickListener(v->{rename(i);return true;});
        TextView total=VaultNativeStyle.label(activity,Integer.toString(session.life[i]),64,Color.BLACK,true);
        total.setGravity(Gravity.CENTER);
        total.setAutoSizeTextTypeUniformWithConfiguration(32,86,2,1);
        LinearLayout life=landscape?VaultNativeStyle.column(activity):VaultNativeStyle.row(activity);
        Button subtract=button("−");
        Button add=button("+");
        for(Button control:new Button[]{subtract,add}){
            control.setTextSize(22);
            control.setMinHeight(dp(42));
            LinearLayout.LayoutParams lp=landscape?
                new LinearLayout.LayoutParams(-1,0,1):
                new LinearLayout.LayoutParams(0,dp(55),1);
            lp.setMargins(dp(2),dp(1),dp(2),dp(1));
            life.addView(control,lp);
        }
        subtract.setOnClickListener(v->persist(()->session.changeLife(i,-1)));
        add.setOnClickListener(v->persist(()->session.changeLife(i,1)));
        subtract.setContentDescription("Subtract one life from "+session.names[i]);
        add.setContentDescription("Add one life to "+session.names[i]);
        // Long press provides a deliberate five-life adjustment.
        subtract.setOnLongClickListener(v->{persist(()->session.changeLife(i,-5));return true;});
        add.setOnLongClickListener(v->{persist(()->session.changeLife(i,5));return true;});
        if(landscape){
            LinearLayout main=VaultNativeStyle.row(activity);
            main.addView(total,new LinearLayout.LayoutParams(0,-1,2));
            main.addView(life,new LinearLayout.LayoutParams(0,-1,1));
            panel.addView(main,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            panel.addView(total,new LinearLayout.LayoutParams(-1,0,1));
            panel.addView(life,new LinearLayout.LayoutParams(-1,dp(57)));
        }
        LinearLayout utilities=VaultNativeStyle.row(activity);
        Button poison=counterButton(utilities,"☠ "+session.poison[i],()->poisonDialog(i));
        poison.setContentDescription("Poison counters for "+session.names[i]+": "+session.poison[i]);
        Button commander=counterButton(utilities,"⚔",()->commanderDialog(i));
        commander.setContentDescription("Commander damage to "+session.names[i]);
        Button color=counterButton(utilities,"◉",()->persist(()->session.nextColor(i)));
        color.setContentDescription("Change "+session.names[i]+" panel color");
        color.setOnLongClickListener(v->{chooseColor(i);return true;});
        panel.addView(utilities,new LinearLayout.LayoutParams(-1,dp(landscape?47:56)));
        boolean danger=session.poison[i]>=10;
        for(int j=0;j<session.players;j++)
            if(j!=i&&session.damage[i][j]>=21)danger=true;
        if(danger){
            TextView warning=VaultNativeStyle.label(activity,"LOSS THRESHOLD",11,Color.BLACK,true);
            warning.setGravity(Gravity.CENTER);
            panel.addView(warning);
        }
        return panel;
    }
    private void chooseColor(int i){
        final String[] names={"Amber","Rose","Lavender","Blue","Teal","Orange","Silver","Pink"};
        new AlertDialog.Builder(activity).setTitle("Panel color · "+session.names[i])
            .setSingleChoiceItems(names,session.colors[i],(dialog,choice)->{
                persist(()->session.setColor(i,choice));
                dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void rename(int i){
        EditText input=new EditText(activity);
        input.setSingleLine(true);input.setText(session.names[i]);
        new AlertDialog.Builder(activity).setTitle("Player name")
            .setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->persist(()->session.rename(i,input.getText().toString())))
            .show();
    }
    private void poisonDialog(int recipient){
        LinearLayout body=VaultNativeStyle.column(activity);
        body.setPadding(dp(16),dp(12),dp(16),dp(8));
        TextView count=label("",23,true);
        count.setGravity(Gravity.CENTER);
        body.addView(count,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout controls=VaultNativeStyle.row(activity);
        Button minus=counterButton(controls,"−1",()->{
            persist(()->session.changePoison(recipient,-1),false);
            count.setText("Poison: "+session.poison[recipient]+" / 10");
        });
        counterButton(controls,"+1",()->{
            persist(()->session.changePoison(recipient,1),false);
            count.setText("Poison: "+session.poison[recipient]+" / 10");
        });
        body.addView(controls,new LinearLayout.LayoutParams(-1,dp(58)));
        count.setText("Poison: "+session.poison[recipient]+" / 10");
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle(session.names[recipient]+" · Poison counters")
            .setView(body).setPositiveButton("Done",null).create();
        dialog.setOnDismissListener(d->{if(sessionDirty&&!closed){sessionDirty=false;render();}});
        dialog.show();
    }
    private void commanderDialog(int recipient){
        LinearLayout list=VaultNativeStyle.column(activity);
        list.setPadding(dp(12),dp(10),dp(12),dp(8));
        for(int source=0;source<session.players;source++){
            if(source==recipient)continue;
            final int attacker=source;
            TextView heading=label("",16,true);
            heading.setGravity(Gravity.CENTER);
            heading.setPadding(0,dp(8),0,dp(4));
            list.addView(heading);
            LinearLayout controls=VaultNativeStyle.row(activity);
            Runnable refresh=()->{
                int amount=session.damage[recipient][attacker];
                heading.setText(session.names[attacker]+": "+amount+" / 21"+
                    (amount>=21?" · threshold reached":""));
            };
            refresh.run();
            Button minus=counterButton(controls,"−1",()->{
                persist(()->session.changeCommanderDamage(recipient,attacker,-1),false);
                refresh.run();
            });
            Button plus=counterButton(controls,"+1",()->{
                persist(()->session.changeCommanderDamage(recipient,attacker,1),false);
                refresh.run();
            });
            minus.setOnLongClickListener(v->{
                persist(()->session.changeCommanderDamage(recipient,attacker,-5),false);
                refresh.run();return true;
            });
            plus.setOnLongClickListener(v->{
                persist(()->session.changeCommanderDamage(recipient,attacker,5),false);
                refresh.run();return true;
            });
            list.addView(controls,new LinearLayout.LayoutParams(-1,dp(58)));
        }
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Commander damage to "+session.names[recipient])
            .setMessage("Each source is tracked independently. Adjusting combat damage also adjusts life.")
            .setView(scroll).setPositiveButton("Done",null).create();
        dialog.setOnDismissListener(d->{if(sessionDirty&&!closed){sessionDirty=false;render();}});
        dialog.show();
    }
}
