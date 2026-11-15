package com.arcanevault.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import org.json.JSONObject;
import java.util.Locale;

/** One Windows-inspired set-card treatment in native Android and RemoteViews.
 * Cached Scryfall art and official set symbols are optional; no network calls
 * occur while a launcher widget is being drawn. */
final class VaultSetProgressCard {
    private VaultSetProgressCard(){}
    private static final int BG=Color.rgb(23,28,40);
    private static final int EDGE=Color.rgb(52,61,81);
    private static final int PURPLE=Color.rgb(181,155,252);
    private static final int GOLD=Color.rgb(229,191,121);
    private static final int WHITE=Color.rgb(240,241,246);
    private static final int MUTED=Color.rgb(197,206,222);

    static Bitmap render(Context context,JSONObject set,String source,int widthPx,int heightPx){
        int w=Math.max(240,Math.min(600,widthPx));
        int h=Math.max(120,Math.min(240,heightPx));
        String code=set==null?"":set.optString("code","");
        String artUrl=set==null?"":set.optString("artUrl","");
        Bitmap backdrop=VaultSetArtwork.cachedArt(context,artUrl,w,h);
        Bitmap icon=VaultSetArtwork.cachedSymbol(context,code);
        try{
            return draw(set,source,w,h,backdrop,icon);
        }finally{
            if(backdrop!=null)backdrop.recycle();
            if(icon!=null)icon.recycle();
        }
    }

    private static Bitmap draw(JSONObject set,String source,int w,int h,
            Bitmap backdrop,Bitmap icon){
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float unit=Math.min(w/430f,h/176f);
        float radius=17*unit;
        RectF bounds=new RectF(1,1,w-1,h-1);
        Path clipping=new Path();
        clipping.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipping);
        paint.setShader(new LinearGradient(0,0,w,h,
            new int[]{Color.rgb(32,39,55),BG,Color.rgb(19,20,38)},
            null,Shader.TileMode.CLAMP));
        canvas.drawRect(bounds,paint);
        paint.setShader(null);
        if(backdrop!=null&&!backdrop.isRecycled()){
            // Crop the representative CARD art exactly like the Windows tile.
            int iw=backdrop.getWidth(),ih=backdrop.getHeight();
            float viewRatio=(float)w/h,artRatio=(float)iw/ih;
            RectF dest=new RectF(0,0,w,h);
            android.graphics.Rect crop;
            if(artRatio>viewRatio){
                int usable=Math.max(1,Math.round(ih*viewRatio));
                int start=(iw-usable)/2;
                crop=new android.graphics.Rect(start,0,start+usable,ih);
            }else{
                int usable=Math.max(1,Math.round(iw/viewRatio));
                int start=(ih-usable)/2;
                crop=new android.graphics.Rect(0,start,iw,start+usable);
            }
            paint.setAlpha(236);
            canvas.drawBitmap(backdrop,crop,dest,paint);
            paint.setAlpha(255);
            // Windows-style dark overlay preserves readable white type.
            paint.setShader(new LinearGradient(0,0,0,h,
                new int[]{0xC5101521,0x8E101520,0xF3101523},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(bounds,paint);
            paint.setShader(null);
        }
        canvas.restore();
        paint.setColor(EDGE);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f,unit*1.1f));
        canvas.drawRoundRect(bounds,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(GOLD);
        canvas.drawRoundRect(new RectF(0,15*unit,4*unit,h-15*unit),
            2*unit,2*unit,paint);

        float left=19*unit,right=w-19*unit;
        String name=set==null?"Choose a Magic set":
            set.optString("name","Unknown set");
        String code=set==null?"":set.optString("code","").toUpperCase(Locale.ROOT);
        int owned=set==null?0:Math.max(0,set.optInt("owned",0));
        int total=set==null?0:Math.max(0,set.optInt("total",0));
        boolean measured=set!=null&&set.has("total")&&total>0;
        float percent=measured?Math.min(100f,100f*owned/total):0f;
        boolean paper=!"arena".equals(source);

        text(canvas,paint,"ARCANE VAULT  /  SET PROGRESS",
            left,20*unit,10*unit,GOLD,true);
        float side=40*unit;
        RectF symbolBox=new RectF(left,30*unit,left+side,30*unit+side);
        paint.setColor(0xC5E5E8F0);
        canvas.drawRoundRect(symbolBox,7*unit,7*unit,paint);
        if(icon!=null&&!icon.isRecycled()){
            float inset=7*unit;
            paint.setAlpha(255);
            canvas.drawBitmap(icon,null,
                new RectF(symbolBox.left+inset,symbolBox.top+inset,
                    symbolBox.right-inset,symbolBox.bottom-inset),paint);
        }else{
            text(canvas,paint,"✦",symbolBox.left+11*unit,
                symbolBox.bottom-11*unit,22*unit,0xFF24283A,true);
        }
        String percentText=measured?
            String.format(Locale.US,"%.1f%%",percent):"—";
        text(canvas,paint,percentText,right-83*unit,60*unit,23*unit,WHITE,true);
        text(canvas,paint,code.isEmpty()?"SET":code,left+side+11*unit,
            58*unit,14*unit,WHITE,true);

        text(canvas,paint,ellipsize(paint,name,right-left,19*unit,true),
            left,100*unit,19*unit,WHITE,true);
        String count=measured?String.format(Locale.US,"%,d / %,d",owned,total):"— / —";
        text(canvas,paint,count,left,122*unit,12*unit,MUTED,false);
        float top=h-38*unit;
        RectF track=new RectF(left,top,right,top+8*unit);
        paint.setColor(0xA5161B27);
        canvas.drawRoundRect(track,5*unit,5*unit,paint);
        if(measured&&percent>0){
            paint.setColor(PURPLE);
            canvas.drawRoundRect(new RectF(left,top,
                left+(right-left)*percent/100f,top+8*unit),5*unit,5*unit,paint);
        }
        text(canvas,paint,!measured?"CATALOG NOT AVAILABLE":
            paper?"VERIFIED PHYSICAL PRINTINGS":"ARENA CARD NAMES",
            left,h-13*unit,10*unit,WHITE,false);
        return bitmap;
    }

    private static void text(Canvas canvas,Paint p,String label,float x,float y,
            float size,int color,boolean bold){
        p.setShader(null);
        p.setColor(color);
        p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):
            Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(Math.max(8,size));
        canvas.drawText(label,x,y,p);
    }
    private static String ellipsize(Paint p,String value,float width,float size,boolean bold){
        p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):
            Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(Math.max(8,size));
        if(p.measureText(value)<=width)return value;
        String text=value;
        while(text.length()>1&&p.measureText(text+"…")>width)
            text=text.substring(0,text.length()-1);
        return text+"…";
    }
}
