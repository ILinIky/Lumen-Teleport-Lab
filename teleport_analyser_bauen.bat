@echo off
rem Baut und testet Lumen Teleport Lab. Installation nur mit -Install.
setlocal
title Lumen Teleport Lab - Build
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-lab.ps1" %*
set "LUMEN_RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %LUMEN_RESULT%
