#!/usr/bin/env bash
set -e

# Kilit dosyalarını temizle
rm -f /home/OXYGEN/.android/avd/Pixel_Tablet.avd/*.lock

echo "Pixel Tablet emülatörü başlatılıyor..."
QT_QPA_PLATFORM=xcb /home/OXYGEN/Android/Sdk/emulator/emulator -avd Pixel_Tablet -gpu host -no-snapshot-load -no-snapshot-save -netdelay none -netspeed full &

/home/OXYGEN/Android/Sdk/platform-tools/adb wait-for-device
while [ "$(/home/OXYGEN/Android/Sdk/platform-tools/adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 1
done

echo "Not Defteri uygulaması yükleniyor ve açılıyor..."
JAVA_HOME=/home/OXYGEN/.jdks/jbr-21.0.11 ./gradlew installDebug
/home/OXYGEN/Android/Sdk/platform-tools/adb shell am start -n com.tabletnotes.app.debug/com.tabletnotes.app.MainActivity
echo "Hazır! Pixel Tablet üzerinde Not Defteri açıldı."

