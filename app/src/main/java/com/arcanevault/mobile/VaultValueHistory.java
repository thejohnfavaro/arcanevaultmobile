package com.arcanevault.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Historical priced physical value observations. Never writes ownership.
 * Daily observations are coalesced, not fabricated by interpolation. */
final class VaultValueHistory {
    private static final String PREF="arcane_value_history_v1";
    private static final int MAX_DAYS=730;
    private VaultValueHistory(){}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static synchronized void record(Context context,String code,long cents,int priced){
        if(priced<=0||cents<=0||cents>1_000_000_000_000L)return;
        String key="history_"+(code==null?"all":code.toLowerCase(Locale.ROOT));
        if(!key.matches("history_(all|[a-z0-9]{2,12})"))return;
        try{
            SharedPreferences pref=prefs(context);
            JSONArray existing=new JSONArray(pref.getString(key,"[]"));
            String today=LocalDate.now().toString();
            JSONArray fresh=new JSONArray();
            for(int i=0;i<existing.length();i++){
                JSONObject item=existing.optJSONObject(i);
                if(item==null)continue;
                String day=item.optString("day","");
                long value=item.optLong("cents",-1);
                if(day.matches("\\d{4}-\\d{2}-\\d{2}")&&
                    value>0&&day.compareTo(today)<0&&
                    day.compareTo(today.substring(0,4)+"-01-01")>=0)
                    fresh.put(item);
            }
            // Retain up to 730 daily entries while keeping the newest values.
            JSONArray bounded=new JSONArray();
            for(int i=Math.max(0,fresh.length()-MAX_DAYS+1);i<fresh.length();i++)
                bounded.put(fresh.getJSONObject(i));
            bounded.put(new JSONObject().put("day",today).put("cents",cents));
            String value=bounded.toString();
            if(!value.equals(pref.getString(key,"")))
                pref.edit().putString(key,value).apply();
        }catch(Exception error){
            android.util.Log.w("ArcaneVault","Unable to retain value history",error);
        }
    }
    static JSONArray samples(Context c,String code){
        String id=code==null?"all":code.toLowerCase(Locale.ROOT);
        if(!id.matches("all|[a-z0-9]{2,12}"))return new JSONArray();
        try{return new JSONArray(prefs(c).getString("history_"+id,"[]"));}
        catch(Exception ignored){return new JSONArray();}
    }
    static View chart(Context context,String setCode){
        return new HistoryView(context,setCode);
    }
    private static final class HistoryView extends View {
        private final String code;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        HistoryView(Context context,String code){
            super(context);
            this.code=code;
            setMinimumHeight(Math.round(154*getResources().getDisplayMetrics().density));
            setContentDescription((code==null?"Collection":"Set "+code)+
                " value history, daily saved observations");
        }
        private void label(Canvas c,String s,float x,float y,int color,float size){
            p.setColor(color);p.setTextSize(size);p.setStyle(Paint.Style.FILL);
            c.drawText(s,x,y,p);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float density=getResources().getDisplayMetrics().density;
            float w=getWidth(),h=getHeight();
            p.setColor(Color.rgb(22,28,40));p.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(0,0,w,h,13*density,13*density,p);
            JSONArray data=samples(getContext(),code);
            label(canvas,(code==null?"COLLECTION":"SET "+code.toUpperCase(Locale.ROOT))+
                " VALUE HISTORY",12*density,23*density,0xFFE5BF79,11*density);
            if(data.length()<2){
                label(canvas,"History begins with the first priced day",12*density,
                    h/2,0xFFC5CEDE,12*density);
                label(canvas,"No historical values were invented",12*density,
                    h/2+19*density,0xFF95A5C1,10*density);
                return;
            }
            List<Long> values=new ArrayList<>();
            for(int i=0;i<data.length();i++){
                JSONObject obj=data.optJSONObject(i);
                if(obj!=null && obj.optLong("cents",0)>0)values.add(obj.optLong("cents"));
            }
            if(values.size()<2)return;
            long min=values.stream().mapToLong(Long::longValue).min().orElse(0);
            long max=values.stream().mapToLong(Long::longValue).max().orElse(1);
            double range=Math.max(1,max-min);
            float x0=12*density,x1=w-12*density,y0=42*density,y1=h-31*density;
            p.setColor(0xFF38465D);p.setStrokeWidth(density);
            canvas.drawLine(x0,y1,x1,y1,p);
            Path path=new Path();
            for(int i=0;i<values.size();i++){
                float x=x0+(x1-x0)*i/(values.size()-1f);
                float y=y1-(float)((values.get(i)-min)/range)*(y1-y0);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            p.setColor(0xFFB59BFC);p.setStrokeWidth(2.5f*density);
            p.setStyle(Paint.Style.STROKE);canvas.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE5BF79);
            canvas.drawCircle(x1,y1-(float)((values.get(values.size()-1)-min)/range)*(y1-y0),
                3.5f*density,p);
            String first=data.optJSONObject(0).optString("day","");
            String last=data.optJSONObject(data.length()-1).optString("day","");
            label(canvas,first,x0,h-12*density,0xFFC5CEDE,10*density);
            label(canvas,last,Math.max(x0,x1-last.length()*6*density),h-12*density,
                0xFFC5CEDE,10*density);
            label(canvas,"USD "+String.format(Locale.US,"%,.2f",max/100.0),
                x0,y0-3*density,0xFFE9EEF8,10*density);
        }
    }
}
