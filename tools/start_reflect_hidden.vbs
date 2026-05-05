' Run the daily reflection job hidden (no console window).
' Used by the JarvisDailyReflection scheduled task / Startup shortcut.
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "C:\Users\User\jarvis-kotlin"
WshShell.Run """C:\Users\User\jarvis-kotlin\build\install\jarvis-kotlin\bin\jarvis-kotlin.bat"" reflect", 0, False
Set WshShell = Nothing
