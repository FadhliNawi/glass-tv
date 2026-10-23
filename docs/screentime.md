# Masa Skrin: a daily TV-time limit

Glass TV can limit how long the TV is used each day. When the time is used up, a full-screen lock appears
over whatever is playing (apps, HDMI, Live TV) until a parent types a PIN and adds time. The count starts
again from zero at midnight.

It is off until a limit is set, and then runs as one small foreground service that counts every 15 seconds.

## Set it up

```bash
export TV_HOST=192.168.0.196
tools/screentime.sh 120 2580          # 2 hours a day, PIN 2580
tools/screentime.sh 60 2580 180       # 1 hour on school days, 3 hours at the weekend
WEEKEND=fri,sat tools/screentime.sh 60 2580 180   # weekend on Friday and Saturday
```

Then press Home on the TV once.

## Everyday commands

| Command | What it does |
|---|---|
| `tools/screentime.sh status` | Time used and left today |
| `tools/screentime.sh +30` | 30 more minutes today |
| `tools/screentime.sh free` | No limit for the rest of today |
| `tools/screentime.sh reset` | Today's count back to zero |
| `tools/screentime.sh off` | Switch Masa Skrin off |

These need network debugging left on. Without it the limit still works and the PIN still works on the TV.

## On the TV

- Five minutes before the end a reminder appears.
- On the lock screen, type the PIN with the remote's number keys or the on-screen pad, then choose
  **+30 minit**, **+1 jam**, **Bebas hari ini** or **Kekal kunci**.
- Back does nothing. Home, or switching input, brings the lock back within 15 seconds. It survives a restart.
- Five wrong PINs pause entry for a minute.
- Time counts while the screen is on, not while the lock screen or the Clock screensaver is showing.
- One limit for the whole TV: everyone shares it.

## Files

- `screentime.json` in `/sdcard/Android/data/com.gathin.tvhome/files/`: the settings, written by
  `tools/screentime.sh` (`limit_minutes`, `weekend_minutes`, `weekend_days`, `pin`, `warn_minutes`).
  It is separate from a theme's `config.json`, so changing a theme never switches the limit off.
- `screentime-status.txt` next to it: today's status, read by `tools/screentime.sh status`.
- Commands reach the launcher through `ScreenTimeReceiver`, which only the adb shell can call
  (it requires `android.permission.DUMP`), so no app on the TV can unlock it.
