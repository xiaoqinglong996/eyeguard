#!/usr/bin/env bash
# 护眼助手 APK 构建脚本（不使用 Gradle，直接用 Android 构建工具链）
set -e

ROOT="/c/users/administrator/workbuddy/2026-10-23-16-51-22/eyeguard"
APP="$ROOT/app/src/main"
JAVA_HOME="/c/tools/jdk-17.0.13+11"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="/c/android-sdk"

BT="/c/android-sdk/build-tools/34.0.0"
PLAT="/c/android-sdk/platforms/android-34"
AAPT2="$BT/aapt2"
D8="$BT/d8.bat"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner.bat"
ANDROID_JAR="$PLAT/android.jar"

cd "$ROOT"
rm -rf build
mkdir -p build/gen build/obj build/apk

echo "[1/6] 编译资源 (aapt2 compile)..."
"$AAPT2" compile --dir "$APP/res" -o build/res.zip

echo "[2/6] 链接资源与清单 (aapt2 link)..."
"$AAPT2" link build/res.zip -I "$ANDROID_JAR" \
    --manifest "$APP/AndroidManifest.xml" \
    --java build/gen -o build/apk/base.apk

echo "[3/6] 编译 Java (javac)..."
javac -source 1.8 -target 1.8 -encoding UTF-8 -cp "$ANDROID_JAR" -d build/obj \
    $(find "$APP/java" -name '*.java') \
    $(find build/gen -name '*.java')

echo "[4/6] 转换为 DEX (d8)..."
( cd build/obj && jar cf ../classes.jar . )
rm -rf build/dex && mkdir -p build/dex
"$D8" --lib "$ANDROID_JAR" --output build/dex build/classes.jar

echo "[5/6] 打包 APK 并对齐 (zipalign)..."
cp build/dex/classes.dex build/apk/classes.dex
( cd build/apk && jar uf base.apk classes.dex )
"$ZIPALIGN" -p 4 build/apk/base.apk build/eyeguard-unsigned.apk

echo "[6/6] 生成签名密钥并签名 (apksigner)..."
if [ ! -f "$ROOT/keystore.jks" ]; then
    keytool -genkeypair -v -keystore "$ROOT/keystore.jks" -alias eyeguard \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass eyeguard -keypass eyeguard \
        -dname "CN=EyeGuard, OU=Dev, O=EyeGuard, L=CN, S=CN, C=CN"
fi
"$APKSIGNER" sign --ks "$ROOT/keystore.jks" --ks-key-alias eyeguard \
    --ks-pass pass:eyeguard --key-pass pass:eyeguard \
    --out "$ROOT/eyeguard-release.apk" build/eyeguard-unsigned.apk

echo "=========================================="
echo "构建完成！APK 输出："
ls -la "$ROOT/eyeguard-release.apk"
echo "=========================================="
