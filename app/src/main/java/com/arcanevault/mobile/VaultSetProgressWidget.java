package com.arcanevault.mobile;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Independently configured set and collection per widget instance. */
public final class VaultSetProgressWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        VaultWidgets.updateSets(context,manager,ids,goAsync());
    }
    @Override public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager manager,int id,Bundle options){
        VaultWidgets.updateSets(context,manager,new int[]{id},goAsync());
    }
    @Override public void onDeleted(Context context,int[] ids){
        for(int id:ids)VaultWidgets.removeSetChoice(context,id);
    }
}
