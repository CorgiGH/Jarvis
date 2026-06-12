@echo off
set ALKDIR=%~dp0v4.3\bin
set PATH=%PATH%;%ALKDIR%;%ALKDIR%\lib
java -Djava.library.path="%ALKDIR%\lib" -cp "%ALKDIR%\alk.jar;%ALKDIR%\lib\com.microsoft.z3.jar" main.ExecutionDriver -a %*
