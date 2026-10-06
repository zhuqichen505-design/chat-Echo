#!/bin/bash

# AI API 助手 APK 构建脚本

echo "==================================="
echo "  AI API 助手 APK 构建脚本"
echo "==================================="

# 检查是否安装了必要的工具
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java，请先安装 JDK 17"
    exit 1
fi

# 设置环境变量
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# 清理旧的构建
echo "清理旧的构建文件..."
./gradlew clean

# 生成 Debug APK
echo "生成 Debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "==================================="
    echo "  构建成功!"
    echo "==================================="
    echo ""
    echo "Debug APK 位置:"
    echo "  app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "文件大小:"
    ls -lh app/build/outputs/apk/debug/app-debug.apk 2>/dev/null || echo "  未找到APK文件"
    echo ""
else
    echo ""
    echo "==================================="
    echo "  构建失败!"
    echo "==================================="
    echo "请检查错误信息并修复问题"
    exit 1
fi
