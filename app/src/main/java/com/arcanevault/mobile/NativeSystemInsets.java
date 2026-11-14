package com.arcanevault.mobile;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Retains real screen space above Android 15+ edge-to-edge system navigation.
 * Attach at the shared root, not to individual scroll views or their child rows.
 * Older Android versions already fit application content within system bars.
 */
final class NativeSystemInsets {
    private NativeSystemInsets() {}

    static void apply(Activity activity, View root) {
        if(Build.VERSION.SDK_INT<35)return;
        final int left=root.getPaddingLeft();
        final int top=root.getPaddingTop();
        final int right=root.getPaddingRight();
        final int bottom=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe=insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            // Insets belong to the container only; retaining original paddings
            // prevents doubling them each time Android dispatches an update.
            view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
