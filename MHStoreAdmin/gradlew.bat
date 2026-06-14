@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem Set local scope
setlocal
set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
goto init

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

:init
@rem Get command-line arguments
set CMD_LINE_ARGS=%*

:execute
"%JAVA_EXE%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%

:end
endlocal
