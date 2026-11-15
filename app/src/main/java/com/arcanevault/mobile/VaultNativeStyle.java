package com.arcanevault.mobile;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;

/** Native Android visual foundation. Never relies on WebView CSS. */
final class VaultNativeStyle {
    static final int BACKGROUND=Color.rgb(13,16,23);
    static final int SURFACE=Color.rgb(23,28,40);
    static final int ELEVATED=Color.rgb(32,39,55);
    static final int BORDER=Color.rgb(52,61,81);
    static final int PRIMARY=Color.rgb(181,155,252);
    static final int GOLD=Color.rgb(229,191,121);
    static final int TEXT=Color.rgb(240,241,246);
    static final int SUBTEXT=Color.rgb(157,169,189);
    static final int DIM=Color.rgb(126,141,165);

    private VaultNativeStyle() {}
    static int dp(Activity a, float n) {
        return (int)(a.getResources().getDisplayMetrics().density*n+.5f);
    }
    static GradientDrawable rounded(Activity a,int color,int border,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(a,radius));
        if(border!=Color.TRANSPARENT)shape.setStroke(dp(a,1),border);
        return shape;
    }
    static void surface(Activity a,View view, boolean elevated) {
        view.setBackground(rounded(a,elevated?ELEVATED:SURFACE,BORDER,14));
    }
    static TextView label(Activity a,String content,int sp,int color,boolean bold) {
        TextView text=new TextView(a);
        text.setText(content);text.setTextSize(sp);text.setTextColor(color);
        if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }
    static Button button(Activity a,String content,boolean active) {
        Button button=new Button(a);
        button.setAllCaps(false);
        button.setText(content);
        button.setTextSize(12);
        button.setFocusable(true);
        button.setTextColor(active?BACKGROUND:TEXT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
        button.setMinHeight(dp(a,42));
        button.setBackground(rounded(a,active?PRIMARY:ELEVATED,active?Color.TRANSPARENT:BORDER,11));
        return button;
    }
    static void search(Activity a,EditText input) {
        input.setTextColor(TEXT);input.setHintTextColor(DIM);input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(a,14),dp(a,9),dp(a,14),dp(a,9));
        input.setBackground(rounded(a,SURFACE,BORDER,12));
        input.setSelectAllOnFocus(false);
    }
    static LinearLayout row(Activity a) {
        LinearLayout row=new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    static LinearLayout column(Activity a) {
        LinearLayout col=new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }
    /** Shared native scrollbar chrome. Android keeps native gesture, kinetic
     * scrolling, accessibility and overscroll semantics. Only the bar's paint
     * and fade behavior change; no layout-wide side padding or overlays. */
    static ScrollView scroll(Activity activity) {
        ScrollView view=new ScrollView(activity);
        scrollChrome(activity,view,false);
        return view;
    }
    static HorizontalScrollView horizontalScroll(Activity activity) {
        HorizontalScrollView view=new HorizontalScrollView(activity);
        scrollChrome(activity,view,true);
        return view;
    }
    private static void scrollChrome(Activity activity,View view,boolean horizontal) {
        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        view.setScrollbarFadingEnabled(true);
        view.setScrollBarFadeDuration(450);
        view.setScrollBarDefaultDelayBeforeFade(750);
        if(android.os.Build.VERSION.SDK_INT>=29){
            GradientDrawable thumb=new GradientDrawable();
            thumb.setColor(PRIMARY);
            thumb.setCornerRadius(dp(activity,6));
            if(horizontal)((HorizontalScrollView)view).setHorizontalScrollbarThumbDrawable(thumb);
            else ((ScrollView)view).setVerticalScrollbarThumbDrawable(thumb);
        }
    }

    static void margin(LinearLayout parent,View child,int bottom,Activity a) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,bottom);
        parent.addView(child,lp);
    }
}
