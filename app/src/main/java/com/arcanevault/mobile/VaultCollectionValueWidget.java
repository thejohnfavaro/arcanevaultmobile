package com.arcanevault.mobile;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Independent launcher widget: price coverage is never inferred from cost
 * basis or from unpriced card names. */
public final class VaultCollectionValueWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        VaultWidgets.refreshValue(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager manager,int id,Bundle options){
        VaultWidgets.refreshValue(context);
    }
}
