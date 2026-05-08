@echo off
REM Double-click launcher for the Jarvis Tutor desktop chat window.
REM Optional argument: subject name (PS, PA, ALO, POO, SO, RC).
REM Example: Tutor.bat PS
set SUBJECT=%1
if "%SUBJECT%"=="" (
    start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "C:\Users\User\jarvis-kotlin\tools\tutor-window.ps1"
) else (
    start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "C:\Users\User\jarvis-kotlin\tools\tutor-window.ps1" -Subject %SUBJECT%
)
