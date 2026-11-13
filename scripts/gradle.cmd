@echo off
rem Runs the project's Gradle wrapper with the local JDK 17 and Android SDK.
rem   scripts\gradle.cmd :app:testDebugUnitTest
set "JAVA_HOME=D:\atomix-launcher-tools\jdk17"
set "ANDROID_HOME=D:\atomix-launcher-tools\android-sdk"
pushd "%~dp0.."
call gradlew.bat --console=plain %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
