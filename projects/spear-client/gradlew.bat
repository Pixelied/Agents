@echo off
setlocal
set GRADLE_VERSION=9.4.1
set DIR=%~dp0
set WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar

if exist "%WRAPPER_JAR%" goto wrapper

where gradle >nul 2>&1
if errorlevel 1 goto missing

set FOUND_VERSION=
for /f "tokens=2" %%V in ('gradle --version ^| findstr /B /C:"Gradle "') do set FOUND_VERSION=%%V
if not "%FOUND_VERSION%"=="%GRADLE_VERSION%" (
  echo Spear Client requires Gradle %GRADLE_VERSION% when gradle-wrapper.jar is unavailable; found %FOUND_VERSION%.
  exit /b 1
)

gradle %*
exit /b %ERRORLEVEL%

:wrapper
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" -Dorg.gradle.appname=gradlew org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%

:missing
echo gradle-wrapper.jar is unavailable and Gradle %GRADLE_VERSION% is not installed on PATH.
echo Install Gradle %GRADLE_VERSION% or restore gradle\wrapper\gradle-wrapper.jar.
exit /b 1
