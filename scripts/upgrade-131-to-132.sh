#!/usr/bin/env bash
# Install a 1.0.31 debug build, seed a sign-in key and demo mode, then upgrade to 1.0.32.
# A second 1.0.32 install checks that a folder seeded on 1.0.32 is still there.
# Both APKs must be debug builds signed with the same key. The probe receiver exists only on 1.0.32.
set -euo pipefail

old_apk="${1:?path to the 1.0.31 debug apk}"
new_apk="${2:?path to the 1.0.32 debug apk}"
key="${UPGRADE_API_KEY:-cursor_test_key_131}"
pkg=com.cursorandroid.app
adb=(adb)
if [ -n "${ANDROID_SERIAL:-}" ]; then
  adb=(adb -s "$ANDROID_SERIAL")
fi

write_prefs() {
  "${adb[@]}" shell "run-as $pkg mkdir -p shared_prefs"
  "${adb[@]}" shell "run-as $pkg sh -c 'cat > shared_prefs/cursor_secure_fallback.xml'" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="api_key">${key}</string>
</map>
EOF
  "${adb[@]}" shell "run-as $pkg sh -c 'cat > shared_prefs/cursor_prefs.xml'" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="demo_mode" value="true" />
</map>
EOF
}

dump() {
  "${adb[@]}" logcat -c
  "${adb[@]}" shell am broadcast -a com.cursorandroid.app.PROBE_DUMP -n "$pkg/.debug.UpgradeProbe" >/dev/null
  sleep 1
  "${adb[@]}" logcat -d -s UpgradeProbe:I
}

echo "install 1.0.31"
"${adb[@]}" install -r -d "$old_apk"
write_prefs
"${adb[@]}" shell am start -n "$pkg/.MainActivity" >/dev/null
sleep 4
"${adb[@]}" shell am force-stop "$pkg"

echo "upgrade to 1.0.32"
"${adb[@]}" install -r "$new_apk"
"${adb[@]}" shell am start -n "$pkg/.MainActivity" >/dev/null
sleep 4
echo "dump after upgrade"
dump

echo "seed folder and reinstall 1.0.32"
"${adb[@]}" shell am broadcast -a com.cursorandroid.app.PROBE_SEED_FOLDER -n "$pkg/.debug.UpgradeProbe" >/dev/null
sleep 1
"${adb[@]}" shell am force-stop "$pkg"
"${adb[@]}" install -r "$new_apk"
"${adb[@]}" shell am start -n "$pkg/.MainActivity" >/dev/null
sleep 4
echo "dump after folder reinstall"
dump
