@echo off
set DIR=%~dp0
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if not exist "%JAVA_EXE%" (
  echo JAVA_HOME is not set or invalid.
  exit /b 1
)

"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
