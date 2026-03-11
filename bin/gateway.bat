@echo off
rem WebSocket Gateway launcher (Windows)
rem Usage: bin\gateway.bat <config.xml>
rem
rem Requires Java 21+ on PATH or JAVA_HOME set.
rem The fat jar (gateway.jar) must be in the same directory as this script.

setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%gateway.jar"

if not exist "%JAR%" (
    echo ERROR: %JAR% not found.
    echo Build it first with:  gradlew.bat shadowJar
    echo Then copy bin\gateway.bat and build\libs\gateway.jar to your target directory.
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

"%JAVA%" %JAVA_OPTS% -jar "%JAR%" %*
