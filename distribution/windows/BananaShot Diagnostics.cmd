@echo off
setlocal
set "APP_HOME=%~dp0"
set "APP_HOME=%APP_HOME:~0,-1%"
"%APP_HOME%\runtime\bin\java.exe" "-Dbananashot.appDir=%APP_HOME%" -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -cp "%APP_HOME%\app\*" org.litvin.SwingMainApp --diagnostics
exit /b %ERRORLEVEL%
