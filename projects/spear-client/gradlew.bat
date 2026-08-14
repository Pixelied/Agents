@rem Minimal Gradle wrapper launcher for Spear Client
@echo off
set DIR=%~dp0
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" -Dorg.gradle.appname=gradlew org.gradle.wrapper.GradleWrapperMain %*
