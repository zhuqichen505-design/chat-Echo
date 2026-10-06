@echo off
chcp 65001 >nul

echo ===================================
echo   AI API 助手 APK 构建脚本
echo ===================================
echo.

:: 检查 Java
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Java，请先安装 JDK 17
    pause
    exit /b 1
)

:: 清理旧的构建
echo 清理旧的构建文件...
call gradlew.bat clean

:: 生成 Debug APK
echo.
echo 生成 Debug APK...
call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo ===================================
    echo   构建失败!
    echo ===================================
    echo 请检查错误信息并修复问题
    pause
    exit /b 1
)

echo.
echo ===================================
echo   构建成功!
echo ===================================
echo.
echo Debug APK 位置:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo 文件信息:
    dir "app\build\outputs\apk\debug\app-debug.apk"
)

echo.
pause
