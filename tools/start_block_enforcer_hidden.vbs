Set objShell = CreateObject("WScript.Shell")
objShell.Run "powershell -NoProfile -ExecutionPolicy Bypass -File """ & _
    "C:\Users\User\jarvis-kotlin\tools\block-enforcer.ps1" & """ -Loop", 0, False
