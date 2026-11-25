#!/bin/sh
set -e

# Temporary CI-style verification script. Runs on a Vercel build machine where
# JDK/Android SDK downloads are available; the local sandbox has neither.
JDK_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21.1_2.tar.gz'
CMDLINE_URL='https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip'

echo '== Installing JDK 17 =='
curl -fsSL -o /tmp/kb-jdk.tgz "$JDK_URL"
mkdir -p /tmp/kb-jdk
tar -xzf /tmp/kb-jdk.tgz -C /tmp/kb-jdk --strip-components=1
export JAVA_HOME=/tmp/kb-jdk
export PATH=/tmp/kb-jdk/bin:$PATH
java -version

echo '== Installing Android SDK platform 36 =='
curl -fsSL -o /tmp/kb-cmdline.zip "$CMDLINE_URL"
mkdir -p /tmp/kb-sdk/cmdline-tools
unzip -q /tmp/kb-cmdline.zip -d /tmp/kb-sdk/cmdline-tools
mv /tmp/kb-sdk/cmdline-tools/cmdline-tools /tmp/kb-sdk/cmdline-tools/latest
export ANDROID_HOME=/tmp/kb-sdk
export ANDROID_SDK_ROOT=/tmp/kb-sdk
yes | /tmp/kb-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/tmp/kb-sdk --licenses >/dev/null
/tmp/kb-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/tmp/kb-sdk 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' >/dev/null

echo '== Compiling app =='
./gradlew --no-daemon --stacktrace assembleDebug
echo 'ANDROID_BUILD_OK'

mkdir -p public
cp app/build/outputs/apk/debug/app-debug.apk public/kotha-bolbo-debug.apk
