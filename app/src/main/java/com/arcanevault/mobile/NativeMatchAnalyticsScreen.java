package com.arcanevault.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.TreeSet;

/** Native read-only companion for the Windows Arena match analytics snapshot. */
final class NativeMatchAnalyticsScreen {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final LinearLayout root;
    private final LinearLayout output;
    private final Spinner deck=new Spinner(null);
    private final Spinner comparison=new Spinner(null);
    private final Spinner queue=new Spinner(null);
    private final Spinner period=new Spinner(null);
    private JSONObject data;
    private boolean preparing;

    private NativeMatchAnalyticsScreen(Activity context,SharedPreferences saved){
        activity=context;prefs=saved;
        root=VaultNativeStyle.column(activity);
        root.setPadding(dp(18),dp(14),dp(18),dp(14));
        root.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        ScrollView scroll=VaultNativeStyle.scroll(activity);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout body=VaultNativeStyle.column(activity);
        scroll.addView(body);
        body.addView(text("DECK ANALYTICS",23,VaultNativeStyle.TEXT,true));
        body.addView(text("Read-only results from a confirmed Windows peer snapshot. No match results are created on Android.",12,VaultNativeStyle.SUBTEXT,false));
        LinearLayout controls=VaultNativeStyle.column(activity);
        controls.setPadding(0,dp(12),0,dp(10));
        body.addView(controls);
        controls.addView(text("Primary deck",12,VaultNativeStyle.SUBTEXT,false));deck.setBackgroundTintList(null);controls.addView(deck);
        controls.addView(text("Compare deck",12,VaultNativeStyle.SUBTEXT,false));controls.addView(comparison);
        controls.addView(text("Arena format",12,VaultNativeStyle.SUBTEXT,false));controls.addView(queue);
        controls.addView(text("Time range",12,VaultNativeStyle.SUBTEXT,false));controls.addView(period);
        output=VaultNativeStyle.column(activity);
        body.addView(output);
        try {
            data=new JSONObject(prefs.getString(NativeMatchAnalyticsSnapshot.PREF_KEY,"{}"));
            if(data.optInt("schema")!=1)throw new IllegalArgumentException("No synced history");
            populate();
        }catch(Exception e){
            data=null;
            output.addView(text("No Windows Arena history imported yet. Open Data & Sync, preview a Windows peer snapshot, then choose Import analytics.",14,VaultNativeStyle.SUBTEXT,false));
        }
    }
    static View create(Activity activity,SharedPreferences prefs){
        return new NativeMatchAnalyticsScreen(activity,prefs).root;
    }
    private int dp(int n){return VaultNativeStyle.dp(activity,n);}
    private TextView text(String value,int size,int color,boolean strong){
        TextView view=VaultNativeStyle.label(activity,value,size,color,strong);
        view.setPadding(0,dp(5),0,dp(5));return view;
    }
    private void options(Spinner target,ArrayList<String> values){
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_dropdown_item,values);
        target.setAdapter(adapter);
        target.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(!preparing)render();}
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
    }
    private String selected(Spinner which){
        return which.getSelectedItem()==null?"":which.getSelectedItem().toString();
    }
    private void populate()throws Exception {
        preparing=true;
        TreeSet<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> formats=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        JSONArray rows=data.getJSONArray("records");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            names.add(row.getString("deckName"));
            String f=row.optString("format");
            formats.add(f.isEmpty()?"Unknown format":f);
        }
        ArrayList<String> decks=new ArrayList<>();decks.add("All assigned decks");decks.addAll(names);
        ArrayList<String> others=new ArrayList<>();others.add("No comparison");others.addAll(names);
        ArrayList<String> queues=new ArrayList<>();queues.add("All formats");queues.addAll(formats);
        ArrayList<String> dates=new ArrayList<>();
        dates.add("All time");dates.add("Last 30 days");dates.add("Last 90 days");
        options(deck,decks);options(comparison,others);options(queue,queues);options(period,dates);
        preparing=false;
        render();
    }
    private void render(){
        if(data==null)return;
        output.removeAllViews();
        try {
            String deckName=selected(deck), otherName=selected(comparison), format=selected(queue);
            if("All assigned decks".equals(deckName))deckName="";
            if("All formats".equals(format))format="";
            int days=selected(period).equals("Last 30 days")?30:
                selected(period).equals("Last 90 days")?90:0;
            Instant now=Instant.now();
            NativeMatchAnalyticsSnapshot.Report primary=
                NativeMatchAnalyticsSnapshot.calculate(data,deckName,format,days,now);
            NativeMatchAnalyticsSnapshot.Report other=null;
            if(!"No comparison".equals(otherName)&&!otherName.isEmpty()&&
                !otherName.equalsIgnoreCase(deckName))
                other=NativeMatchAnalyticsSnapshot.calculate(data,otherName,format,days,now);
            final NativeMatchAnalyticsSnapshot.Report secondary=other;
            output.addView(text(primary.wins+" W  /  "+primary.losses+" L    •    "+
                primary.percent()+"% win rate",21,VaultNativeStyle.TEXT,true));
            output.addView(text("Last "+Math.min(10,primary.matches())+" matches: "+
                primary.latestWins+" W / "+(Math.min(10,primary.matches())-primary.latestWins)+
                " L    •    Longest win streak: "+primary.bestWins+
                "    •    Longest loss streak: "+primary.worstLosses,
                12,VaultNativeStyle.SUBTEXT,false));
            if(primary.matches()<10)
                output.addView(text("Small sample: "+primary.matches()+
                    " recorded match(es).",12,VaultNativeStyle.GOLD,false));
            if(other!=null)
                output.addView(text(otherName+": "+other.wins+" W / "+other.losses+
                    " L • "+other.percent()+"%    ("+
                    (primary.percent()-other.percent())+" percentage points difference)",
                    14,VaultNativeStyle.TEXT,true));
            output.addView(text("ROLLING 10-MATCH WIN RATE",13,VaultNativeStyle.GOLD,true));
            View graph=new View(activity){
                final Paint stroke=new Paint(3);
                void line(Canvas canvas,ArrayList<Integer> points,int color){
                    int n=Math.min(20,points.size());
                    if(n<2)return;
                    stroke.setColor(color);stroke.setStrokeWidth(dp(3));
                    stroke.setStyle(Paint.Style.STROKE);
                    for(int i=1;i<n;i++){
                        int from=points.size()-n+i-1,to=from+1;
                        float x1=dp(5)+(getWidth()-dp(10))*(i-1f)/(n-1);
                        float x2=dp(5)+(getWidth()-dp(10))*i/(n-1);
                        float y1=dp(7)+(getHeight()-dp(14))*(100-points.get(from))/100f;
                        float y2=dp(7)+(getHeight()-dp(14))*(100-points.get(to))/100f;
                        canvas.drawLine(x1,y1,x2,y2,stroke);
                    }
                }
                @Override protected void onDraw(Canvas canvas){
                    super.onDraw(canvas);
                    canvas.drawColor(VaultNativeStyle.SURFACE);
                    stroke.setColor(Color.DKGRAY);stroke.setStrokeWidth(1);
                    canvas.drawLine(0,getHeight()/2f,getWidth(),getHeight()/2f,stroke);
                    line(canvas,primary.rollingRates,Color.rgb(255,157,70));
                    if(secondary!=null)line(canvas,secondary.rollingRates,Color.rgb(94,169,239));
                }
            };
            output.addView(graph,new LinearLayout.LayoutParams(-1,dp(115)));
            output.addView(text("Orange: selected • Blue: comparison",11,VaultNativeStyle.SUBTEXT,false));
            output.addView(text("WEEKLY PERFORMANCE (UTC)",13,VaultNativeStyle.GOLD,true));
            if(primary.weeks.isEmpty())output.addView(text("No confirmed matches",13,VaultNativeStyle.SUBTEXT,false));
            for(NativeMatchAnalyticsSnapshot.Weekly week:primary.weeks){
                int total=week.wins+week.losses;
                int pct=total==0?0:(int)Math.round(100d*week.wins/total);
                output.addView(text(week.week+"    "+week.wins+"–"+week.losses+
                    "    •    "+pct+"% ("+total+" matches)",12,VaultNativeStyle.TEXT,false));
            }
            output.addView(text("RECENT MATCHES",13,VaultNativeStyle.GOLD,true));
            for(NativeMatchAnalyticsSnapshot.Row row:primary.newest){
                String f=row.format.isEmpty()?"Unknown format":row.format;
                String day=row.time.atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"));
                output.addView(text((row.result.equals("win")?"W  ":"L  ")+
                    day+"  •  "+row.deck+"  •  "+f,12,
                    row.result.equals("win")?Color.rgb(128,219,159):VaultNativeStyle.TEXT,false));
            }
            output.addView(text("Imported from Windows peer snapshot. Use Data & Sync to refresh the record.",11,VaultNativeStyle.SUBTEXT,false));
        }catch(Exception e){
            output.addView(text("Analytics could not render: "+e.getMessage(),
                13,VaultNativeStyle.SUBTEXT,false));
        }
    }
}
