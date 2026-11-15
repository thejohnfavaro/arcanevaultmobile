package com.arcanevault.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import org.json.JSONObject;
import java.util.Locale;

/** One visual language for Set Progress cards in the native app AND launcher.
 * Rendered to a bitmap because Android launcher RemoteViews cannot host the
 * app's custom native view hierarchy. No remote data or artwork downloads. */
final class VaultSetProgressCard {
    private VaultSetProgressCard(){}
    private static final int BG=Color.rgb(23,28,40);
    private static final int EDGE=Color.rgb(52,61,81);
    private static final int PURPLE=Color.rgb(181,155,252);
    private static final int GOLD=Color.rgb(229,191,121);
    private static final int WHITE=Color.rgb(240,241,246);
    private static final int MUTED=Color.rgb(157,169,189);

    static Bitmap render(Context context,JSONObject set,String source,int widthPx,int heightPx){
        int w=Math.max(240,Math.min(1100,widthPx));
        int h=Math.max(120,Math.min(420,heightPx));
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float unit=Math.min(w/430f,h/176f);
        float radius=17*unit;
        RectF bounds=new RectF(1,1,w-1,h-1);
        paint.setShader(new LinearGradient(0,0,w,h,
            new int[]{Color.rgb(32,39,55),BG,Color.rgb(19,20,38)},
            null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds,radius,radius,paint);
        paint.setShader(null);
        paint.setColor(EDGE);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f,unit*1.1f));
        canvas.drawRoundRect(bounds,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);
        // One gold accent matches the Vault's set-card treatment.
        paint.setColor(GOLD);
        canvas.drawRoundRect(new RectF(0,17*unit,4*unit,h-17*unit),
            2*unit,2*unit,paint);

        float left=19*unit, right=w-19*unit;
        String name=set==null?"Choose a Magic set":
            set.optString("name","Unknown set");
        String code=set==null?"":set.optString("code","").toUpperCase(Locale.ROOT);
        int owned=set==null?0:Math.max(0,set.optInt("owned",0));
        int total=set==null?0:Math.max(0,set.optInt("total",0));
        float percent=total>0?Math.min(100f,100f*owned/total):0f;
        boolean paper=!"arena".equals(source);

        text(canvas,paint,"ARCANE VAULT  /  SET PROGRESS",
            left,27*unit,10*unit,GOLD,false);

        // Keep set code distinct from the title (not a counterfeit set icon).
        paint.setColor(Color.rgb(52,48,77));
        float badgeWidth=Math.min(80*unit,Math.max(46*unit,code.length()*10*unit+18*unit));
        RectF badge=new RectF(right-badgeWidth,37*unit,right,62*unit);
        canvas.drawRoundRect(badge,7*unit,7*unit,paint);
        text(canvas,paint,code.isEmpty()?"SET":code,
            badge.left+9*unit,54*unit,12*unit,PURPLE,true);

        float titleMax=right-badgeWidth-left-16*unit;
        text(canvas,paint,ellipsize(paint,name,titleMax,20*unit,true),
            left,57*unit,20*unit,WHITE,true);

        String count=String.format(Locale.US,"%,d / %,d",owned,total);
        text(canvas,paint,count,left,96*unit,19*unit,WHITE,true);
        text(canvas,paint,String.format(Locale.US,"%.1f%%",percent),
            right-82*unit,96*unit,19*unit,PURPLE,true);
        float top=h-49*unit;
        RectF track=new RectF(left,top,right,top+11*unit);
        paint.setColor(Color.rgb(52,61,81));
        canvas.drawRoundRect(track,6*unit,6*unit,paint);
        if(percent>0){
            paint.setColor(PURPLE);
            RectF fill=new RectF(left,top,left+(right-left)*percent/100f,top+11*unit);
            canvas.drawRoundRect(fill,6*unit,6*unit,paint);
        }
        text(canvas,paint,paper?"VERIFIED PHYSICAL PRINTINGS":"ARENA CARD NAMES",
            left,h-16*unit,10.5f*unit,MUTED,false);
        return bitmap;
    }

    private static void text(Canvas c,Paint p,String text,float x,float y,
            float size,int color,boolean bold){
        p.setColor(color);
        p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):
            Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(Math.max(8,size));
        c.drawText(text,x,y,p);
    }
    private static String ellipsize(Paint p,String value,float available,float size,boolean bold){
        p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):
            Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(Math.max(8,size));
        if(p.measureText(value)<=available)return value;
        String shortName=value;
        while(shortName.length()>1&&p.measureText(shortName+"…")>available)
            shortName=shortName.substring(0,shortName.length()-1);
        return shortName+"…";
    }
}
