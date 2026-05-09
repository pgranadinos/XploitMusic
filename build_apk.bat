@echo off
title AuraMusic - Build APK Debug
echo ============================================
echo  AuraMusic - Compilando APK Debug...
echo ============================================
echo.

cd /d "%~dp0"

echo [1/2] Limpiando build anterior...
call gradlew.bat clean
echo.

echo [2/2] Compilando APK Debug...
call gradlew.bat assembleDebug --stacktrace 2>&1

IF %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo  BUILD EXITOSO!
    echo  APK generado en:
    echo  app\build\outputs\apk\debug\app-debug.apk
    echo ============================================
    echo.
    echo Abriendo carpeta del APK...
    explorer "app\build\outputs\apk\debug\"
) ELSE (
    echo.
    echo ============================================
    echo  ERROR en el build. Revisa los logs arriba.
    echo ============================================
)

pause
