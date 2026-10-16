package com.kidsvideo.app;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static int widthDp(Context c) {
        Configuration cfg = c.getResources().getConfiguration();
        if (cfg.screenWidthDp > 0) return cfg.screenWidthDp;
        return Math.round(c.getResources().getDisplayMetrics().widthPixels / c.getResources().getDisplayMetrics().density);
    }

    public static int heightDp(Context c) {
        Configuration cfg = c.getResources().getConfiguration();
        if (cfg.screenHeightDp > 0) return cfg.screenHeightDp;
        return Math.round(c.getResources().getDisplayMetrics().heightPixels / c.getResources().getDisplayMetrics().density);
    }

    public static boolean isLandscape(Context c) {
        return c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isTv(Context c) {
        UiModeManager ui = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        return c.getPackageManager().hasSystemFeature("android.software.leanback")
                || (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
    }

    public static boolean isTablet(Context c) {
        return !isTv(c) && c.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static boolean useHorizontalCards(Context c) {
        if (isTv(c) || isTablet(c)) return true;
        return isLandscape(c) && widthDp(c) >= 560;
    }

    public static int thumbnailWidthDp(Context c) {
        if (isTv(c)) return 360;
        if (isTablet(c)) {
            int w = Math.round(widthDp(c) * 0.40f);
            return Math.max(280, Math.min(420, w));
        }
        if (useHorizontalCards(c)) return Math.max(250, Math.min(340, Math.round(widthDp(c) * 0.44f)));
        return Math.max(280, widthDp(c) - 40);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, (int) radiusDp));
        return g;
    }

    public static void styleButton(TextView v, boolean selected) {
        styleCategory(v, selected, false);
    }

    public static void styleCategory(TextView v, boolean selected, boolean focused) {
        int fill;
        int stroke;
        int strokeWidth;
        if (selected) {
            fill = Color.rgb(220, 38, 38);
            stroke = Color.rgb(255, 220, 220);
            strokeWidth = 2;
        } else if (focused) {
            fill = Color.rgb(37, 99, 235);
            stroke = Color.WHITE;
            strokeWidth = 3;
        } else {
            fill = Color.rgb(52, 52, 58);
            stroke = Color.rgb(85, 85, 92);
            strokeWidth = 1;
        }

        GradientDrawable g = rounded(fill, 18, v.getContext());
        g.setStroke(dp(v.getContext(), strokeWidth), stroke);
        v.setTextColor(Color.WHITE);
        v.setBackground(g);
        v.setPadding(dp(v.getContext(), 17), dp(v.getContext(), 10), dp(v.getContext(), 17), dp(v.getContext(), 10));
        v.setTypeface(null, selected ? 1 : 0);
        v.setAlpha(1f);
    }

    public static void focusScale(View v, boolean focused) {
        v.setScaleX(focused ? 1.04f : 1f);
        v.setScaleY(focused ? 1.04f : 1f);
        v.setElevation(focused ? dp(v.getContext(), 8) : 0);
        v.setAlpha(focused ? 1f : 0.96f);
    }
}
