#!/usr/bin/env bash
# Masa Skrin: a daily TV-time limit for children, enforced by the Glass TV launcher.
#
#   tools/screentime.sh 120 2580          2 jam sehari, PIN 2580
#   tools/screentime.sh 60 2580 180       1 jam hari sekolah, 3 jam hujung minggu
#   WEEKEND=fri,sat tools/screentime.sh 60 2580 180    hujung minggu Jumaat-Sabtu (Kedah, Kelantan, Terengganu)
#   tools/screentime.sh status            berapa lama dah tengok hari ini
#   tools/screentime.sh +30               tambah 30 minit untuk hari ini
#   tools/screentime.sh free              tiada had untuk baki hari ini
#   tools/screentime.sh reset             kira semula dari 0 hari ini
#   tools/screentime.sh off               tutup Masa Skrin
#
# Needs TV_HOST (the TV's IP) and the Glass TV launcher with Masa Skrin installed.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. export TV_HOST=192.168.0.196}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
tv() { "$PY" -I tv.py "$@"; }
send() {   # send a command to the launcher, then show today's status
  tv "am broadcast -n $PKG/.ScreenTimeReceiver -a $PKG.SCREEN_TIME $* >/dev/null; sleep 1; cat $DIR/screentime-status.txt 2>/dev/null || echo 'Tiada status lagi: tekan Home pada TV sekali.'" | tail -n 1
}
usage() { sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

case "${1:-}" in
  ""|-h|--help) usage ;;
  status) send --es cmd reload ;;
  reset)  send --es cmd reset ;;
  free)   send --es cmd free ;;
  +[0-9]*)
    send --es cmd add --ei minutes "${1#+}" ;;
  off)
    tv "rm -f $DIR/screentime.json" >/dev/null
    send --es cmd reload ;;
  [0-9]*)
    LIMIT=$1; PIN=${2:-}; WEEKEND_MIN=${3:-$1}
    [[ "$PIN" =~ ^[0-9]{4,8}$ ]] || { echo "PIN mesti 4 hingga 8 nombor, contoh: tools/screentime.sh $LIMIT 2580" >&2; exit 1; }
    [[ "$LIMIT" =~ ^[0-9]+$ && "$WEEKEND_MIN" =~ ^[0-9]+$ ]] || usage
    DAYS=$(echo "${WEEKEND:-sat,sun}" | tr 'A-Z' 'a-z' | sed 's/[^a-z,]//g; s/,/","/g')
    TMP=$(mktemp)
    trap 'rm -f "$TMP"' EXIT
    printf '{\n  "limit_minutes": %s,\n  "weekend_minutes": %s,\n  "weekend_days": ["%s"],\n  "pin": "%s",\n  "warn_minutes": 5\n}\n' \
      "$LIMIT" "$WEEKEND_MIN" "$DAYS" "$PIN" > "$TMP"
    tv "mkdir -p $DIR" >/dev/null
    tv --push "$TMP" "$DIR/screentime.json" >/dev/null
    echo "Had ditetapkan: $LIMIT minit (hari sekolah), $WEEKEND_MIN minit (${WEEKEND:-sat,sun})."
    send --es cmd reload ;;
  *) usage ;;
esac
