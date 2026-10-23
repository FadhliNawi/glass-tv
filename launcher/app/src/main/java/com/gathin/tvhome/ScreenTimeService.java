package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

/**
 * Counts the minutes the TV is on, whatever is on screen (apps, HDMI, Live TV), and brings up
 * {@link LockActivity} once today's allowance is used. Runs only while a limit is set
 * (screentime.json); with no limit it stops itself straight away.
 *
 * Time counts while the screen is on, and not while the lock screen or the launcher's own clock
 * screensaver is showing. Started by the launcher every time Home appears, at boot, and by
 * tools/screentime.sh after a change.
 */
public class ScreenTimeService extends Service {
    private static final String TAG = "TvHome.ScreenTime";
    private static final String CHANNEL = "screentime";
    private static final int NOTIFICATION_ID = 7;
    private static final long TICK_MS = 15_000L;
    /** A gap longer than this between ticks was sleep, not watching. */
    private static final long MAX_STEP_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTick;
    private boolean foreground;

    static void start(Context ctx) {
        if (!ScreenTime.settings(ctx).enabled()) return;
        Intent i = new Intent(ctx, ScreenTimeService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception e) {
            // e.g. a background-start restriction; the next Home press tries again
            Log.w(TAG, "could not start: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        if (!ScreenTime.settings(this).enabled()) {
            ScreenTime.writeStatus(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(tick);
        lastTick = SystemClock.elapsedRealtime();
        handler.post(tick);
        return START_STICKY;
    }

    private void goForeground() {
        if (foreground) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Masa Skrin", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Masa Skrin")
                .setContentText("Mengira masa tengok TV hari ini")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        foreground = true;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            long step = Math.min(now - lastTick, MAX_STEP_MS);
            lastTick = now;
            Context ctx = ScreenTimeService.this;
            ScreenTime.Settings s = ScreenTime.settings(ctx);
            if (!s.enabled()) {
                ScreenTime.writeStatus(ctx);
                stopSelf();
                return;
            }
            PowerManager pm = getSystemService(PowerManager.class);
            boolean screenOn = pm == null || pm.isInteractive();
            if (screenOn && !LockActivity.visible && !ClockDream.dreaming) {
                ScreenTime.addUsed(ctx, step);
            }
            long left = ScreenTime.remainingMs(ctx);
            if (screenOn && left > 0 && s.warnMinutes > 0 && left <= s.warnMinutes * ScreenTime.MINUTE_MS
                    && ScreenTime.takeWarning(ctx)) {
                Toast.makeText(ctx, "⏰ Tinggal " + ScreenTime.format(left + ScreenTime.MINUTE_MS - 1)
                        + " lagi masa TV hari ini", Toast.LENGTH_LONG).show();
            }
            if (screenOn && left == 0 && !LockActivity.visible) {
                LockActivity.show(ctx);
            }
            ScreenTime.writeStatus(ctx);
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
