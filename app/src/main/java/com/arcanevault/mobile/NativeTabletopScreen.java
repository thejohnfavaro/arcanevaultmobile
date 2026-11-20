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

/**
 * Dedicated offline tabletop surface. Uses a separate preference file,
 * deliberately excluded from peer sync and all collection/deck inventories.
 */
final class NativeTabletopScreen {
    private static final String STORE="arcane_tabletop_companion_v1";
    private static final String SESSION="session_v1";
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Runnable exit;
    private final LinearLayout root;
    private TabletopSession session;
    private boolean closed;
    private boolean sessionDirty;
    private int currentOrientation=-1;

    NativeTabletopScreen(Activity activity,Runnable exit){
        this.activity=activity;this.exit=exit;
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
        Button newGame=button("New Game");
        toolbar.addView(newGame,new LinearLayout.LayoutParams(0,dp(42),1));
        newGame.setOnClickListener(v->new AlertDialog.Builder(activity)
            .setTitle("Replace current game?")
            .setMessage("Your current tabletop game will be replaced. Collections and decks are unaffected.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("New Game",(d,w)->setup()).show());
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(50)));

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
    private View playerPanel(int i,boolean landscape){
        int background=TabletopSession.COLORS[session.colors[i]];
        LinearLayout panel=VaultNativeStyle.column(activity);
        panel.setPadding(dp(5),dp(4),dp(5),dp(4));
        panel.setBackground(VaultNativeStyle.rounded(activity,background,Color.TRANSPARENT,14));
        panel.setGravity(Gravity.CENTER);
        TextView name=VaultNativeStyle.label(activity,session.names[i],15,Color.BLACK,true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panel.addView(name,new LinearLayout.LayoutParams(-1,dp(26)));
        name.setContentDescription(session.names[i]+". Long press to rename");
        name.setOnLongClickListener(v->{rename(i);return true;});
        TextView total=VaultNativeStyle.label(activity,Integer.toString(session.life[i]),64,Color.BLACK,true);
        total.setGravity(Gravity.CENTER);
        total.setAutoSizeTextTypeUniformWithConfiguration(32,86,2,1);
        panel.addView(total,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout life=VaultNativeStyle.row(activity);
        Button subtract=counterButton(life,"−",()->persist(()->session.changeLife(i,-1)));
        Button add=counterButton(life,"+",()->persist(()->session.changeLife(i,1)));
        subtract.setContentDescription("Subtract one life from "+session.names[i]);
        add.setContentDescription("Add one life to "+session.names[i]);
        // Long press provides a deliberate five-life adjustment.
        subtract.setOnLongClickListener(v->{persist(()->session.changeLife(i,-5));return true;});
        add.setOnLongClickListener(v->{persist(()->session.changeLife(i,5));return true;});
        panel.addView(life,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout utilities=VaultNativeStyle.row(activity);
        Button poison=counterButton(utilities,"☠ "+session.poison[i],()->poisonDialog(i));
        poison.setContentDescription("Poison counters for "+session.names[i]+": "+session.poison[i]);
        Button commander=counterButton(utilities,"⚔",()->commanderDialog(i));
        commander.setContentDescription("Commander damage to "+session.names[i]);
        Button color=counterButton(utilities,"◉",()->persist(()->session.nextColor(i)));
        color.setContentDescription("Change "+session.names[i]+" panel color");
        panel.addView(utilities,new LinearLayout.LayoutParams(-1,dp(56)));
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
            counterButton(controls,"−1",()->{
                persist(()->session.changeCommanderDamage(recipient,attacker,-1),false);
                refresh.run();
            });
            counterButton(controls,"+1",()->{
                persist(()->session.changeCommanderDamage(recipient,attacker,1),false);
                refresh.run();
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
