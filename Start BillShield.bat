@echo off
title Bill Shield Server
cd /d C:\BillShieldApp
javac *.java
start "" cmd /c "timeout /t 4 >nul & start chrome --app=http://localhost:8080"
java Server