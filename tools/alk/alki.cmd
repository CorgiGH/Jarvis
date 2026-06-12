@echo off
set ALKDIR=C:\Users\User\jarvis-kotlin-lane-b\tools\alk\v4.3\bin
set PATH=%PATH%;%ALKDIR%;%ALKDIR%\lib
java -Djava.library.path="%ALKDIR%\lib" -cp "%ALKDIR%\alk.jar;%ALKDIR%\lib\com.microsoft.z3.jar" main.ExecutionDriver -a %*
