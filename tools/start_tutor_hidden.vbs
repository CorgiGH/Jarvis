' Hidden launcher for tutor-window.ps1.
' Run via: wscript start_tutor_hidden.vbs [SUBJECT]
' No console window flashes; only the Forms tutor window appears.
Set objShell = CreateObject("WScript.Shell")
Dim subjectArg
subjectArg = ""
If WScript.Arguments.Count >= 1 Then
    subjectArg = " -Subject " & WScript.Arguments(0)
End If
objShell.Run "powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""C:\Users\User\jarvis-kotlin\tools\tutor-window.ps1""" & subjectArg, 0, False
