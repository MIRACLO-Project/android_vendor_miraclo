/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package org.miraclo.setup;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * MIRACLO's own first screens: Hello (language, accessibility, emergency call), then your look
 * (dark or light, Quick Settings style). Finishing switches this screen off for good, so Android
 * goes on to the next setup (Google's, or Android's own without Google apps).
 */
public class MiraSetupActivity extends Activity {
    private static final String KEY_QS_STYLE = "miraclo_qs_style";
    private FrameLayout mRoot;
    private int mPage = 0;
    private boolean mDark = true;
    private int mStyle = 1;
    private Typeface mSans;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (setupDone()) {
            finishSetup();
            return;
        }
        mSans = Typeface.create("mira-sans", Typeface.NORMAL);
        mRoot = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B2558, 0xFF4A3A8E, 0xFF8E5C86});
        mRoot.setBackground(bg);
        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(28), i.top + dp(24), dp(28), i.bottom + dp(24));
            return insets;
        });
        setContentView(mRoot);
        getWindow().setDecorFitsSystemWindows(false);
        show();
    }

    @Override
    public void onBackPressed() {
        if (mPage > 0) {
            mPage--;
            show();
        }
    }

    private boolean setupDone() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0
                && Settings.Secure.getInt(getContentResolver(), "user_setup_complete", 0) != 0;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(mSans, weight, false));
        return t;
    }

    private TextView pill(String s, boolean primary, View.OnClickListener click) {
        TextView t = text(s, 16, primary ? 0xFF15171C : Color.WHITE, 600);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(28));
        g.setColor(primary ? Color.WHITE : 0x2EFFFFFF);
        t.setBackground(g);
        t.setPadding(dp(20), dp(15), dp(20), dp(15));
        t.setOnClickListener(click);
        return t;
    }

    private TextView chip(String s, boolean selected, View.OnClickListener click) {
        TextView t = text(s, 15, selected ? 0xFF15171C : Color.WHITE, 600);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(selected ? Color.WHITE : 0x26FFFFFF);
        t.setBackground(g);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setOnClickListener(click);
        return t;
    }

    private void show() {
        mRoot.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        mRoot.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (mPage == 0) {
            View space = new View(this);
            col.addView(space, new LinearLayout.LayoutParams(-1, 0, 1f));
            col.addView(text("Hello", 56, Color.WHITE, 700));
            TextView sub = text("Let's set up your phone. It takes about three minutes.", 17,
                    0xD9FFFFFF, 400);
            sub.setPadding(0, dp(10), 0, 0);
            col.addView(sub);
            View space2 = new View(this);
            col.addView(space2, new LinearLayout.LayoutParams(-1, 0, 1.4f));
            TextView lang = chip("Language: " + getResources().getConfiguration().getLocales()
                    .get(0).getDisplayName(), false, v -> open(Settings.ACTION_LOCALE_SETTINGS));
            lang.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            col.addView(lang, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(18);
            col.addView(pill("Get started", true, v -> {
                mPage = 1;
                show();
            }), lp);
            LinearLayout links = new LinearLayout(this);
            links.setGravity(Gravity.CENTER);
            TextView a11y = text("Accessibility", 13, 0xCCFFFFFF, 500);
            a11y.setPadding(dp(12), dp(14), dp(12), dp(4));
            a11y.setOnClickListener(v -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            TextView sos = text("Emergency call", 13, 0xCCFFFFFF, 500);
            sos.setPadding(dp(12), dp(14), dp(12), dp(4));
            sos.setOnClickListener(v -> {
                try {
                    startActivity(new Intent("com.android.phone.EmergencyDialer.DIAL")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            });
            links.addView(a11y);
            links.addView(sos);
            col.addView(links, new LinearLayout.LayoutParams(-1, -2));
        } else {
            col.addView(text("Make it yours", 34, Color.WHITE, 700));
            TextView sub = text("You can change all of this later in Mira Studio.", 15, 0xD9FFFFFF, 400);
            sub.setPadding(0, dp(8), 0, dp(26));
            col.addView(sub);

            col.addView(text("Theme", 14, 0xB3FFFFFF, 600));
            LinearLayout theme = new LinearLayout(this);
            theme.setPadding(0, dp(10), 0, dp(24));
            addChip(theme, chip("Dark", mDark, v -> { mDark = true; show(); }));
            addChip(theme, chip("Light", !mDark, v -> { mDark = false; show(); }));
            col.addView(theme);

            col.addView(text("Quick Settings", 14, 0xB3FFFFFF, 600));
            LinearLayout r1 = new LinearLayout(this);
            r1.setPadding(0, dp(10), 0, 0);
            addChip(r1, chip("ColorOS", mStyle == 1, v -> { mStyle = 1; show(); }));
            addChip(r1, chip("iOS", mStyle == 2, v -> { mStyle = 2; show(); }));
            col.addView(r1);
            LinearLayout r2 = new LinearLayout(this);
            r2.setPadding(0, dp(10), 0, 0);
            addChip(r2, chip("One UI", mStyle == 4, v -> { mStyle = 4; show(); }));
            addChip(r2, chip("Android", mStyle == 0, v -> { mStyle = 0; show(); }));
            col.addView(r2);

            View space = new View(this);
            col.addView(space, new LinearLayout.LayoutParams(-1, 0, 1f));
            col.addView(pill("Continue", true, v -> {
                apply();
                finishSetup();
            }), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void addChip(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (row.getChildCount() > 0) lp.setMarginStart(dp(10));
        row.addView(chip, lp);
    }

    private void open(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private void apply() {
        try {
            Settings.Secure.putInt(getContentResolver(), KEY_QS_STYLE, mStyle);
        } catch (Exception ignored) {
        }
        try {
            UiModeManager ui = getSystemService(UiModeManager.class);
            if (ui != null) {
                ui.setNightMode(mDark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
            }
        } catch (Exception ignored) {
        }
    }

    /** Switches this screen off for good; Android then opens the next setup (or home). */
    private void finishSetup() {
        try {
            getPackageManager().setComponentEnabledSetting(
                    new ComponentName(this, MiraSetupActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (Exception ignored) {
        }
        finish();
    }
}
