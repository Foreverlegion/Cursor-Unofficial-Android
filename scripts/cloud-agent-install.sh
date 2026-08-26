#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q 'version "17'; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk unzip wget
fi

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk

if [[ ! -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]]; then
  sudo mkdir -p "${ANDROID_HOME}/cmdline-tools"
  tmp="$(mktemp -d)"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O "${tmp}/cmdline-tools.zip"
  sudo unzip -q "${tmp}/cmdline-tools.zip" -d "${ANDROID_HOME}/cmdline-tools"
  sudo mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
  rm -rf "${tmp}"
fi

export PATH="${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

yes | sdkmanager --sdk_root="${ANDROID_HOME}" \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;36.0.0" > /dev/null

./gradlew --no-daemon :app:assembleDebug
