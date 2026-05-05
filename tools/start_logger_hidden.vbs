' Launch the Jarvis activity logger with no visible console window.
' Used by the JarvisActivityLogger scheduled task. Edit the path below
' if the install dir moves.
Set WshShell = CreateObject("WScript.Shell")
WshShell.Run """C:\Users\User\jarvis-kotlin\build\install\jarvis-kotlin\bin\jarvis-kotlin.bat"" logger", 0, False
Set WshShell = Nothing
