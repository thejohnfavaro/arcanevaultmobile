package com.arcanevault.mobile;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/** Independent per-instance marketplace/currency preference editor. */
public final class VaultValueWidgetConfigActivity extends Activity {
    private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final String[] MARKET_LABELS={
        "Scryfall · cached reference","TCGplayer · integration pending",
        "Card Kingdom · integration pending","Cardmarket · integration pending"};
    private static final String[] CURRENCY_LABELS={
        "Automatic (market currency)","USD · US dollar","EUR · euro",
        "GBP · pound sterling","CAD · Canadian dollar",
        "AUD · Australian dollar","JPY · Japanese yen"};
    private static final String[] PRICE_LABELS={
        "Reference / market","Low","Retail"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
        if(info!=null&&!new ComponentName(this,VaultCollectionValueWidget.class)
            .equals(info.provider)){finish();return;}
        getWindow().setStatusBarColor(VaultNativeStyle.BACKGROUND);
        getWindow().setNavigationBarColor(VaultNativeStyle.BACKGROUND);
        ScrollView scroll=VaultNativeStyle.scroll(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        NativeSystemInsets.apply(this,scroll);
        LinearLayout body=VaultNativeStyle.column(this);
        body.setBackgroundColor(VaultNativeStyle.BACKGROUND);
        body.setPadding(dp(20),dp(16),dp(20),dp(28));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);
        body.addView(VaultNativeStyle.label(this,"COLLECTION VALUE WIDGET",23,
            VaultNativeStyle.TEXT,true));
        TextView explanation=VaultNativeStyle.label(this,
            "Choose this widget's market and currency independently of other widgets. "+
            "Only Scryfall reference quotes are connected currently; other "+
            "markets will show unavailable instead of an incorrect value.",
            13,VaultNativeStyle.SUBTEXT,false);
        explanation.setPadding(0,dp(10),0,dp(18));
        body.addView(explanation);
        body.addView(VaultNativeStyle.label(this,"CARD MARKET",11,VaultNativeStyle.GOLD,true));
        Spinner markets=spinner(MARKET_LABELS);
        body.addView(markets,new LinearLayout.LayoutParams(-1,dp(52)));
        body.addView(VaultNativeStyle.label(this,"CURRENCY",11,VaultNativeStyle.GOLD,true));
        Spinner currencies=spinner(CURRENCY_LABELS);
        body.addView(currencies,new LinearLayout.LayoutParams(-1,dp(52)));
        body.addView(VaultNativeStyle.label(this,"PRICE TYPE",11,VaultNativeStyle.GOLD,true));
        Spinner types=spinner(PRICE_LABELS);
        body.addView(types,new LinearLayout.LayoutParams(-1,dp(52)));
        String chosen=VaultWidgets.configuredMarket(this,widgetId);
        String[] marketsKeys={"scryfall","tcgplayer","cardkingdom","cardmarket"};
        String[] currenciesKeys={"AUTO","USD","EUR","GBP","CAD","AUD","JPY"};
        String[] priceKeys={"market","low","retail"};
        markets.setSelection(index(marketsKeys,chosen));
        currencies.setSelection(index(currenciesKeys,VaultWidgets.configuredCurrency(this,widgetId)));
        types.setSelection(index(priceKeys,VaultWidgets.configuredPriceType(this,widgetId)));
        TextView note=VaultNativeStyle.label(this,
            "Currency conversion uses dated public reference exchange rates. "+
            "If a rate is missing, this widget displays an unavailable amount "+
            "rather than guessing. Your collection data is never modified.",
            12,VaultNativeStyle.SUBTEXT,false);
        note.setPadding(0,dp(14),0,dp(16));
        body.addView(note);
        android.widget.Button save=VaultNativeStyle.button(this,"Save widget preferences",true);
        body.addView(save,new LinearLayout.LayoutParams(-1,dp(52)));
        save.setOnClickListener(v->{
            if(!VaultWidgets.saveValueChoice(this,widgetId,
                marketsKeys[markets.getSelectedItemPosition()],
                currenciesKeys[currencies.getSelectedItemPosition()],
                priceKeys[types.getSelectedItemPosition()])){
                note.setText("Could not save widget settings. Please try again.");
                return;
            }
            VaultWidgets.refreshValue(this);
            Intent result=new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
            setResult(RESULT_OK,result);
            finish();
        });
    }
    private Spinner spinner(String[] labels){
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }
    private static int index(String[] items,String value){
        for(int i=0;i<items.length;i++)if(items[i].equals(value))return i;
        return 0;
    }
    private int dp(float value){return VaultNativeStyle.dp(this,value);}
}
