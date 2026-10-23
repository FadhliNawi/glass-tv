package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Commands from tools/screentime.sh over adb:
 * <pre>
 *   am broadcast -n com.gathin.tvhome/.ScreenTimeReceiver -a com.gathin.tvhome.SCREEN_TIME --es cmd reload
 *   ... --es cmd add --ei minutes 30      extra time today
 *   ... --es cmd reset                     today's count back to zero
 *   ... --es cmd free                      no limit for the rest of today
 * </pre>
 * The manifest guards it with android.permission.DUMP, which the adb shell holds and ordinary apps
 * cannot get, so no app on the TV can unlock it. The boot broadcast is handled by {@link Boot}.
 */
public class ScreenTimeReceiver extends BroadcastReceiver {
    static final String ACTION = "com.gathin.tvhome.SCREEN_TIME";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        String cmd = intent.getStringExtra("cmd");
        if (cmd == null) cmd = "reload";
        switch (cmd) {
            case "add":
                ScreenTime.addBonus(ctx, Math.max(0, intent.getIntExtra("minutes", 0)) * ScreenTime.MINUTE_MS);
                break;
            case "reset":
                ScreenTime.resetToday(ctx);
                break;
            case "free":
                ScreenTime.freeToday(ctx);
                break;
            default:
                break;
        }
        ScreenTime.writeStatus(ctx);
        setResultData(cmd + " ok");
        ScreenTimeService.start(ctx);
    }

    /** Starts counting again after the TV restarts. */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
                ScreenTimeService.start(ctx);
            }
        }
    }
}
