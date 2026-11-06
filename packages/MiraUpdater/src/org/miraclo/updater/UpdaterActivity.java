/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package org.miraclo.updater;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Software update. Reads https://raw.githubusercontent.com/MiraCLO-OS/ota/main/DEVICE.json
 * ({version, codename, datetime, size, url, payload_offset, payload_size, payload_properties,
 * changelog[]}), and when it is newer than this build, lets update_engine stream the A/B payload
 * straight from the URL. The phone stays usable; a restart finishes the update.
 */
public class UpdaterActivity extends Activity {
    private static final String BASE = "https://raw.githubusercontent.com/MiraCLO-OS/ota/main/";
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private UpdateEngine mEngine;
    private SharedPreferences mPrefs;
    private JSONObject mUpdate;
    private int mStatus = UpdateEngine.UpdateStatusConstants.IDLE;
    private boolean mPaused;

    private TextView mTitle, mSub, mProgressText, mButton;
    private ProgressBar mBar;
    private LinearLayout mNews;
    private boolean mDark;
    private Typeface mSans;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mPrefs = getSharedPreferences("updater", Context.MODE_PRIVATE);
        mDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        mSans = Typeface.create("mira-sans", Typeface.NORMAL);
        if (getActionBar() != null) getActionBar().hide();
        buildUi();
        try {
            mEngine = new UpdateEngine();
            mEngine.bind(new UpdateEngineCallback() {
                @Override
                public void onStatusUpdate(int status, float percent) {
                    mMain.post(() -> onEngine(status, percent));
                }

                @Override
                public void onPayloadApplicationComplete(int errorCode) {
                    mMain.post(() -> onDone(errorCode));
                }
            });
        } catch (Throwable t) {
            mEngine = null;
        }
        check();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int fg() { return mDark ? 0xFFEEF0F5 : 0xFF15171C; }
    private int fg2() { return mDark ? 0xFFA3A8B4 : 0xFF5E6370; }
    private int card() { return mDark ? 0xFF1A1C22 : 0xFFFFFFFF; }
    private int link() { return mDark ? 0xFF9FB3FF : 0xFF3550C8; }

    /** A thin left arrow, drawn so it matches the system's back button. */
    private static final class BackArrow extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();
        private final int mSize;

        BackArrow(int color, int stroke) {
            mPaint.setColor(color);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(stroke);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mSize = stroke * 9;
        }

        @Override
        public void draw(Canvas c) {
            float cx = getBounds().exactCenterX();
            float cy = getBounds().exactCenterY();
            float h = mSize / 2f;
            mPath.reset();
            mPath.moveTo(cx - h * 0.1f, cy - h);
            mPath.lineTo(cx - h, cy);
            mPath.lineTo(cx - h * 0.1f, cy + h);
            mPath.moveTo(cx - h, cy);
            mPath.lineTo(cx + h, cy);
            c.drawPath(mPath, mPaint);
        }

        @Override public int getIntrinsicWidth() { return mSize * 2; }
        @Override public int getIntrinsicHeight() { return mSize * 2; }
        @Override public void setAlpha(int a) { mPaint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { mPaint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(mSans, weight, false));
        return t;
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(mDark ? 0xFF0E0F13 : 0xFFF2F3F7);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(16), dp(14), dp(32));
        scroll.addView(col);
        scroll.setFitsSystemWindows(true);

        // Header, as on every Settings page: a round back button and the page title.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(14));
        ImageView back = new ImageView(this);
        back.setImageDrawable(new BackArrow(fg(), dp(2)));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(round(mDark ? 0xFF22252D : 0xFFE6E8EF, 22));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView pageTitle = text("Software update", 24, fg(), 600);
        pageTitle.setPadding(dp(16), 0, 0, 0);
        header.addView(pageTitle);
        col.addView(header);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setBackground(round(card(), 26));
        status.setPadding(dp(18), dp(18), dp(18), dp(18));
        mTitle = text(getString(android.R.string.unknownName), 20, fg(), 700);
        mSub = text("", 13, fg2(), 400);
        mSub.setPadding(0, dp(4), 0, dp(12));
        mBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mBar.setMax(1000);
        mBar.setVisibility(View.GONE);
        mProgressText = text("", 12, fg2(), 400);
        mProgressText.setVisibility(View.GONE);
        mButton = text("", 16, mDark ? 0xFF15171C : Color.WHITE, 600);
        mButton.setGravity(Gravity.CENTER);
        mButton.setBackground(round(mDark ? Color.WHITE : 0xFF15171C, 24));
        mButton.setPadding(dp(16), dp(13), dp(16), dp(13));
        mButton.setOnClickListener(v -> onButton());
        status.addView(mTitle);
        status.addView(mSub);
        status.addView(mBar);
        status.addView(mProgressText);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(12);
        status.addView(mButton, bp);
        col.addView(status);

        TextView h1 = text("What's new", 13, link(), 600);
        h1.setPadding(dp(6), dp(22), dp(6), dp(8));
        col.addView(h1);
        mNews = new LinearLayout(this);
        mNews.setOrientation(LinearLayout.VERTICAL);
        mNews.setBackground(round(card(), 26));
        mNews.setPadding(dp(18), dp(10), dp(18), dp(10));
        col.addView(mNews);

        TextView h2 = text("Settings", 13, link(), 600);
        h2.setPadding(dp(6), dp(22), dp(6), dp(8));
        col.addView(h2);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(card(), 26));
        row.setPadding(dp(18), dp(14), dp(14), dp(14));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text("Download over Wi-Fi only", 16, fg(), 500));
        texts.addView(text("Updates wait for Wi-Fi so mobile data is not used", 12, fg2(), 400));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch wifi = new Switch(this);
        wifi.setChecked(mPrefs.getBoolean("wifi_only", true));
        wifi.setOnCheckedChangeListener((b, on) -> mPrefs.edit().putBoolean("wifi_only", on).apply());
        row.addView(wifi);
        col.addView(row);

        TextView current = text("This phone: " + SystemProperties.get("ro.miraclo.version", "")
                + " " + SystemProperties.get("ro.miraclo.codename", ""), 12, fg2(), 400);
        current.setPadding(dp(6), dp(18), dp(6), 0);
        col.addView(current);
        setContentView(scroll);
    }

    private void setState(String title, String sub, String button) {
        mTitle.setText(title);
        mSub.setText(sub);
        mButton.setText(button);
        mButton.setVisibility(button == null ? View.GONE : View.VISIBLE);
    }

    private void check() {
        setState("Checking for updates", "", null);
        final String device = SystemProperties.get("ro.miraclo.device", "");
        Executors.newSingleThreadExecutor().execute(() -> {
            JSONObject json = null;
            String error = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + device + ".json").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    try (InputStream in = c.getInputStream()) {
                        json = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } else {
                    error = "No update information for this phone yet";
                }
            } catch (Exception e) {
                error = "Could not check. Connect to the internet and try again.";
            }
            final JSONObject j = json;
            final String err = error;
            mMain.post(() -> onChecked(j, err));
        });
    }

    private void onChecked(JSONObject json, String error) {
        if (json == null) {
            setState("Software update", error, "Check again");
            mUpdate = null;
            return;
        }
        long built = SystemProperties.getLong("ro.build.date.utc", 0);
        long theirs = json.optLong("datetime", 0);
        mNews.removeAllViews();
        JSONArray log = json.optJSONArray("changelog");
        if (log != null) {
            for (int i = 0; i < log.length(); i++) {
                TextView t = text("•  " + log.optString(i), 14, fg(), 400);
                t.setPadding(0, dp(6), 0, dp(6));
                mNews.addView(t);
            }
        }
        if (theirs <= built) {
            mUpdate = null;
            setState("Up to date", "MIRACLO " + SystemProperties.get("ro.miraclo.version", "")
                    + " is the latest version", "Check again");
            return;
        }
        mUpdate = json;
        long mb = json.optLong("size", 0) / (1024 * 1024);
        setState(json.optString("version") + " is ready", mb + " MB · " + json.optString("codename"),
                "Download and install");
    }

    private boolean onWifi() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void onButton() {
        if (mStatus == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT) {
            getSystemService(PowerManager.class).reboot(null);
            return;
        }
        if (mStatus == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
            try {
                if (mPaused) {
                    mEngine.resume();
                    mPaused = false;
                    mButton.setText("Pause");
                } else {
                    mEngine.suspend();
                    mPaused = true;
                    mButton.setText("Resume");
                }
            } catch (Exception ignored) {
            }
            return;
        }
        if (mUpdate == null) {
            check();
            return;
        }
        if (mPrefs.getBoolean("wifi_only", true) && !onWifi()) {
            setState(mTitle.getText().toString(), "Connect to Wi-Fi, or turn off Download over Wi-Fi only",
                    "Download and install");
            return;
        }
        if (mEngine == null) {
            setState("Can't install here", "This phone's update service is not available", null);
            return;
        }
        try {
            JSONArray props = mUpdate.getJSONArray("payload_properties");
            String[] headers = new String[props.length()];
            for (int i = 0; i < headers.length; i++) headers[i] = props.getString(i);
            mEngine.applyPayload(mUpdate.getString("url"), mUpdate.getLong("payload_offset"),
                    mUpdate.getLong("payload_size"), headers);
            mBar.setVisibility(View.VISIBLE);
            mProgressText.setVisibility(View.VISIBLE);
            setState(mUpdate.optString("version") + " is downloading", "You can keep using your phone",
                    "Pause");
        } catch (Exception e) {
            setState("Couldn't start the update", String.valueOf(e.getMessage()), "Try again");
        }
    }

    private void onEngine(int status, float percent) {
        mStatus = status;
        switch (status) {
            case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                mBar.setVisibility(View.VISIBLE);
                mProgressText.setVisibility(View.VISIBLE);
                mBar.setProgress(Math.round(percent * 1000));
                mProgressText.setText("Downloading · " + Math.round(percent * 100) + "%");
                if (!mPaused) mButton.setText("Pause");
                mButton.setVisibility(View.VISIBLE);
                break;
            case UpdateEngine.UpdateStatusConstants.VERIFYING:
            case UpdateEngine.UpdateStatusConstants.FINALIZING:
                mBar.setVisibility(View.VISIBLE);
                mBar.setProgress(Math.round(percent * 1000));
                mProgressText.setText("Installing · " + Math.round(percent * 100) + "%");
                mButton.setVisibility(View.GONE);
                break;
            case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT:
                showRestart();
                break;
            default:
                break;
        }
    }

    private void showRestart() {
        mStatus = UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT;
        mBar.setVisibility(View.GONE);
        mProgressText.setVisibility(View.GONE);
        setState("Restart to finish", "The update is installed", "Restart now");
    }

    private void onDone(int error) {
        if (error == UpdateEngine.ErrorCodeConstants.SUCCESS) {
            showRestart();
        } else {
            mBar.setVisibility(View.GONE);
            mProgressText.setVisibility(View.GONE);
            setState("The update didn't install", "Error " + error + ". Your phone is unchanged.", "Try again");
            mStatus = UpdateEngine.UpdateStatusConstants.IDLE;
        }
    }

    @Override
    protected void onDestroy() {
        if (mEngine != null) {
            try {
                mEngine.unbind();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
