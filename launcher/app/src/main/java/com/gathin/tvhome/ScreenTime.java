package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Daily screen-time limit for children ("Masa Skrin").
 *
 * Settings come from {@code screentime.json} in the launcher's external files directory, pushed by
 * {@code tools/screentime.sh}; it is a separate file from a theme's config.json so that changing or
 * resetting a theme never switches the limit off. Keys, all optional:
 * <pre>
 *   limit_minutes    minutes allowed on a school day; 0 or absent = no limit
 *   weekend_minutes  minutes allowed on weekend days; defaults to limit_minutes
 *   weekend_days     e.g. ["sat","sun"] (default) or ["fri","sat"]
 *   pin              4 to 8 digits a parent types on the lock screen
 *   warn_minutes     a reminder this many minutes before the end; default 5, 0 = none
 * </pre>
 * Today's usage lives in SharedPreferences and starts again from zero at local midnight. Everything
 * runs on the main thread (the service tick and the lock screen), so no locking is needed.
 */
final class ScreenTime {
    private static final String TAG = "TvHome.ScreenTime";
    static final String FILE_NAME = "screentime.json";
    static final String STATUS_NAME = "screentime-status.txt";
    private static final String PREFS = "screentime";
    static final long MINUTE_MS = 60_000L;
    static final int MAX_FAILS = 5;
    static final long LOCKOUT_MS = 60_000L;

    static final class Settings {
        final int limitMinutes;
        final int weekendMinutes;
        final boolean[] weekend;   // indexed by Calendar.DAY_OF_WEEK (1 = Sunday … 7 = Saturday)
        final String pin;
        final int warnMinutes;

        Settings(int limitMinutes, int weekendMinutes, boolean[] weekend, String pin, int warnMinutes) {
            this.limitMinutes = limitMinutes;
            this.weekendMinutes = weekendMinutes;
            this.weekend = weekend;
            this.pin = pin;
            this.warnMinutes = warnMinutes;
        }

        boolean enabled() {
            return limitMinutes > 0 || weekendMinutes > 0;
        }

        /** Minutes allowed on a given day of the week; 0 means no limit that day. */
        int minutesFor(int dayOfWeek) {
            return dayOfWeek >= 1 && dayOfWeek <= 7 && weekend[dayOfWeek] ? weekendMinutes : limitMinutes;
        }
    }

    static final Settings OFF = new Settings(0, 0, defaultWeekend(), "", 0);

    private static Settings cached = OFF;
    private static long cachedStamp = Long.MIN_VALUE;

    private ScreenTime() {}

    private static boolean[] defaultWeekend() {
        boolean[] w = new boolean[8];
        w[Calendar.SATURDAY] = true;
        w[Calendar.SUNDAY] = true;
        return w;
    }

    /** The settings file, re-read only when it changes on disk. */
    static Settings settings(Context ctx) {
        File dir = Config.dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        long stamp = file != null && file.isFile() ? file.lastModified() ^ (file.length() << 40) : -1;
        if (stamp == cachedStamp) return cached;
        cachedStamp = stamp;
        cached = stamp == -1 ? OFF : parse(read(file));
        return cached;
    }

    /** Parses the JSON text; anything wrong yields OFF rather than a crash. Package-private for tests. */
    static Settings parse(String text) {
        if (text == null) return OFF;
        try {
            if (text.startsWith("﻿")) text = text.substring(1);
            JSONObject o = new JSONObject(text);
            int limit = clamp(o.optInt("limit_minutes", 0), 0, 1440);
            int weekendMin = clamp(o.optInt("weekend_minutes", limit), 0, 1440);
            boolean[] weekend = defaultWeekend();
            JSONArray days = o.optJSONArray("weekend_days");
            if (days != null) {
                weekend = new boolean[8];
                for (int i = 0; i < days.length(); i++) {
                    int d = dayIndex(days.optString(i, ""));
                    if (d > 0) weekend[d] = true;
                }
            }
            String pin = o.optString("pin", "").trim();
            if (!pin.matches("[0-9]{4,8}")) pin = "";
            int warn = clamp(o.optInt("warn_minutes", 5), 0, 60);
            return new Settings(limit, weekendMin, weekend, pin, warn);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + FILE_NAME + ": " + e.getMessage());
            return OFF;
        }
    }

    static int dayIndex(String name) {
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "sun": case "ahad": return Calendar.SUNDAY;
            case "mon": case "isnin": return Calendar.MONDAY;
            case "tue": case "selasa": return Calendar.TUESDAY;
            case "wed": case "rabu": return Calendar.WEDNESDAY;
            case "thu": case "khamis": return Calendar.THURSDAY;
            case "fri": case "jumaat": return Calendar.FRIDAY;
            case "sat": case "sabtu": return Calendar.SATURDAY;
            default: return 0;
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    private static String read(File file) {
        if (file == null || file.length() > 16 * 1024) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- today's usage ----

    static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.ROOT, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** The day's state, cleared when the date has changed since it was last written. */
    private static SharedPreferences prefs(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String day = today();
        if (!day.equals(p.getString("day", ""))) {
            p.edit().clear().putString("day", day).apply();
        }
        return p;
    }

    static long usedMs(Context ctx) {
        return prefs(ctx).getLong("used_ms", 0);
    }

    static void addUsed(Context ctx, long ms) {
        if (ms <= 0) return;
        SharedPreferences p = prefs(ctx);
        p.edit().putLong("used_ms", p.getLong("used_ms", 0) + ms).apply();
    }

    /** Milliseconds allowed today, or Long.MAX_VALUE for no limit today. */
    static long allowedMs(Context ctx) {
        Settings s = settings(ctx);
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean("free", false)) return Long.MAX_VALUE;
        int minutes = s.minutesFor(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        if (minutes <= 0) return Long.MAX_VALUE;
        return minutes * MINUTE_MS + p.getLong("bonus_ms", 0);
    }

    static long remainingMs(Context ctx) {
        long allowed = allowedMs(ctx);
        return allowed == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, allowed - usedMs(ctx));
    }

    static boolean isLocked(Context ctx) {
        return remainingMs(ctx) == 0;
    }

    static void addBonus(Context ctx, long ms) {
        SharedPreferences p = prefs(ctx);
        long used = p.getLong("used_ms", 0);
        long base = allowedMs(ctx);
        // Extending after the time ran out starts the extra time from now, not from the old limit.
        long bonus = p.getLong("bonus_ms", 0);
        if (base != Long.MAX_VALUE && used > base) bonus += used - base;
        p.edit().putLong("bonus_ms", bonus + ms).putBoolean("warned", false).apply();
    }

    static void freeToday(Context ctx) {
        prefs(ctx).edit().putBoolean("free", true).apply();
    }

    static void resetToday(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putString("day", today()).apply();
    }

    static boolean takeWarning(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean("warned", false)) return false;
        p.edit().putBoolean("warned", true).apply();
        return true;
    }

    // ---- PIN attempts ----

    /** Milliseconds until another PIN may be tried; 0 when allowed now. */
    static long lockoutLeftMs(Context ctx) {
        long until = prefs(ctx).getLong("lockout_until", 0);
        long left = until - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    /** True when the PIN is right; a wrong one counts towards a one-minute pause. */
    static boolean checkPin(Context ctx, String typed) {
        Settings s = settings(ctx);
        SharedPreferences p = prefs(ctx);
        if (!s.pin.isEmpty() && s.pin.equals(typed)) {
            p.edit().putInt("fails", 0).apply();
            return true;
        }
        int fails = p.getInt("fails", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("fails", fails);
        if (fails >= MAX_FAILS) e.putInt("fails", 0).putLong("lockout_until", System.currentTimeMillis() + LOCKOUT_MS);
        e.apply();
        return false;
    }

    // ---- display ----

    /** "1 jam 25 minit", "45 minit", "0 minit". */
    static String format(long ms) {
        long min = Math.max(0, ms) / MINUTE_MS;
        long h = min / 60, m = min % 60;
        if (h == 0) return m + " minit";
        if (m == 0) return h + " jam";
        return h + " jam " + m + " minit";
    }

    /** One line for tools/screentime.sh status. */
    static void writeStatus(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return;
        Settings s = settings(ctx);
        long allowed = allowedMs(ctx);
        String line;
        if (!s.enabled()) {
            line = "Masa Skrin: tutup";
        } else {
            line = "Hari ini (" + today() + "): guna " + format(usedMs(ctx))
                    + (allowed == Long.MAX_VALUE ? ", tiada had hari ini"
                    : " daripada " + format(allowed) + ", baki " + format(remainingMs(ctx)))
                    + (isLocked(ctx) ? "  [TERKUNCI]" : "")
                    + (s.pin.isEmpty() ? "  (AMARAN: PIN belum ditetapkan)" : "");
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, STATUS_NAME))) {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // status is a convenience for the Mac tool; never worth failing over
        }
    }
}
