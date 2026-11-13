@echo off
rem Shows the Android SDK licences one by one. Answer y to accept each, or n to decline.
set "JAVA_HOME=D:\atomix-launcher-tools\jdk17"
call "D:\atomix-launcher-tools\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
echo.
pause
