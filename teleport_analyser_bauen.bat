@echo off
rem Baut und testet den aktuellen Stand und installiert ihn in .minecraft\mods.
rem Mit -NoInstall nur bauen. Minecraft vor dem Aktualisieren beenden.
setlocal
title Lumen Teleport Lab - Bauen und Installieren
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-lab.ps1" %*
set "LUMEN_RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %LUMEN_RESULT%
