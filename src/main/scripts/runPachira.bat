@echo off
java -version 1>nul 2>nul || (
  echo no java installed
  pause
  exit /b 1
)

REM put an extra ^" just so emacs can be happy
for /f tokens^=2^ delims^=^"^"^. %%m in ('java -version 2^>^&1 ^|^ findstr version') do set "major=%%m"

if %major% LSS 11 (
  echo java version is too low 
  echo at least 11 is needed
  pause
  exit /b 1
)

if not exist %~dp0%Pachira.jar (
  echo Pachira.jar not found
  pause
  exit /b 1
)

if not defined PATH_TO_FX (
  set "PATH_TO_FX=C:\Program Files\Java\javafx-sdk-11.0.2\lib"
)
java --module-path="%PATH_TO_FX%" --add-modules javafx.controls,javafx.fxml -jar %~dp0%Pachira.jar
