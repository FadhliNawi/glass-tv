package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen "time's up" screen. It sits in its own task above whatever app was playing, so the app
 * pauses behind it. Back does nothing; Home goes to the launcher, which brings this screen straight
 * back while the day's time is used up, and the service brings it back over any other app within
 * a few seconds. A parent types the PIN (remote number keys or the on-screen pad) to add time.
 */
public class LockActivity extends Activity {
    /** Whether the lock screen is in front; read by the service so time stops counting under it. */
    static volatile boolean visible;

    private static final int BG = 0xFF0B1020;
    private static final int ACCENT = 0xFFFFB547;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder typed = new StringBuilder();
    private Typeface font;
    private float dp;
    private TextView usedLine, dots, hint;
    private View keypad, parentPanel;

    static void show(Context ctx) {
        Intent i = new Intent(ctx, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            ctx.startActivity(i);
        } catch (Exception ignored) {
            // the next tick or Home press tries again
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        try {
            font = getResources().getFont(R.font.outfit);
        } catch (Exception e) {
            font = Typeface.DEFAULT;
        }
        setContentView(build());
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A2550, BG, 0xFF05070F});
        root.setBackground(bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        col.addView(text("🌙", 56, Color.WHITE));
        TextView title = text("Masa TV dah habis untuk hari ini", 34, Color.WHITE);
        title.setPadding(0, px(6), 0, 0);
        col.addView(title);
        col.addView(text("Jom rehat 😊  Baca buku, main di luar, atau tolong Mak dan Ayah", 17, 0xCCFFFFFF));
        usedLine = text("", 15, ACCENT);
        usedLine.setPadding(0, px(14), 0, px(22));
        col.addView(usedLine);

        TextView label = text("Mak / Ayah: masukkan PIN untuk tambah masa", 15, 0x99FFFFFF);
        col.addView(label);
        dots = text("", 30, Color.WHITE);
        dots.setLetterSpacing(0.3f);
        dots.setPadding(0, px(4), 0, px(4));
        col.addView(dots);
        hint = text(" ", 14, 0xFFFF8A80);
        col.addView(hint);

        GridLayout pad = new GridLayout(this);
        pad.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓"};
        for (String k : keys) {
            Button b = button(k, 64, 22);
            b.setOnClickListener(v -> onPadKey(k));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(px(5), px(5), px(5), px(5));
            pad.addView(b, lp);
        }
        keypad = pad;
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        padLp.topMargin = px(8);
        col.addView(pad, padLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setVisibility(View.GONE);
        addChoice(panel, "+30 minit", () -> extend(30));
        addChoice(panel, "+1 jam", () -> extend(60));
        addChoice(panel, "Bebas hari ini", () -> {
            ScreenTime.freeToday(this);
            close();
        });
        addChoice(panel, "Kekal kunci", this::backToPin);
        parentPanel = panel;
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.topMargin = px(16);
        col.addView(panel, panelLp);
        return root;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(font);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String label, int sizeDp, float sp) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(sp);
        b.setTypeface(font);
        b.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{BG, Color.WHITE}));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, rounded(Color.WHITE));
        sl.addState(new int[]{}, rounded(0x26FFFFFF));
        b.setBackground(sl);
        b.setStateListAnimator(null);
        b.setMinWidth(px(sizeDp));
        b.setMinimumWidth(px(sizeDp));
        b.setMinHeight(px(52));
        b.setMinimumHeight(px(52));
        b.setPadding(px(18), 0, px(18), 0);
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                .setDuration(120).start());
        return b;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(16));
        return d;
    }

    private void addChoice(LinearLayout panel, String label, Runnable action) {
        Button b = button(label, 120, 18);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(8), 0, px(8), 0);
        panel.addView(b, lp);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    // ---- PIN ----

    private void onPadKey(String k) {
        switch (k) {
            case "⌫":
                if (typed.length() > 0) typed.setLength(typed.length() - 1);
                break;
            case "✓":
                submit();
                return;
            default:
                if (typed.length() < 8) typed.append(k);
                break;
        }
        refreshDots();
        if (typed.length() == ScreenTime.settings(this).pin.length() && typed.length() >= 4) submit();
    }

    private void submit() {
        long wait = ScreenTime.lockoutLeftMs(this);
        if (wait > 0) {
            hint.setText("Terlalu banyak cubaan. Cuba lagi dalam " + ((wait + 999) / 1000) + " saat");
            typed.setLength(0);
            refreshDots();
            return;
        }
        if (ScreenTime.settings(this).pin.isEmpty()) {
            hint.setText("PIN belum ditetapkan. Guna tools/screentime.sh di komputer");
            typed.setLength(0);
            refreshDots();
            return;
        }
        if (ScreenTime.checkPin(this, typed.toString())) {
            typed.setLength(0);
            hint.setText(" ");
            keypad.setVisibility(View.GONE);
            dots.setText("✅");
            parentPanel.setVisibility(View.VISIBLE);
            parentPanel.post(() -> ((ViewGroup) parentPanel).getChildAt(0).requestFocus());
        } else {
            typed.setLength(0);
            refreshDots();
            hint.setText("PIN salah");
            dots.animate().translationX(px(10)).setDuration(50).withEndAction(() ->
                    dots.animate().translationX(-px(10)).setDuration(50).withEndAction(() ->
                            dots.animate().translationX(0).setDuration(50).start()).start()).start();
        }
    }

    private void refreshDots() {
        int len = Math.max(4, ScreenTime.settings(this).pin.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(i < typed.length() ? '●' : '○');
        dots.setText(sb);
    }

    private void backToPin() {
        parentPanel.setVisibility(View.GONE);
        keypad.setVisibility(View.VISIBLE);
        refreshDots();
        keypad.post(() -> ((ViewGroup) keypad).getChildAt(4).requestFocus());
    }

    private void extend(int minutes) {
        ScreenTime.addBonus(this, minutes * ScreenTime.MINUTE_MS);
        close();
    }

    private void close() {
        visible = false;
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- lifecycle and keys ----

    private final Runnable recheck = new Runnable() {
        @Override
        public void run() {
            // Midnight passed, or a parent added time from the computer.
            if (!ScreenTime.isLocked(LockActivity.this)) {
                close();
                return;
            }
            usedLine.setText("Hari ini: " + ScreenTime.format(ScreenTime.usedMs(LockActivity.this)));
            handler.postDelayed(this, 15_000L);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (!ScreenTime.isLocked(this)) {
            close();
            return;
        }
        backToPin();
        handler.removeCallbacks(recheck);
        handler.post(recheck);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(recheck);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Back stays here: clear what was typed instead.
        if (parentPanel.getVisibility() == View.VISIBLE) backToPin();
        typed.setLength(0);
        refreshDots();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 && keypad.getVisibility() == View.VISIBLE) {
            onPadKey(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && keypad.getVisibility() == View.VISIBLE) {
            onPadKey("⌫");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
