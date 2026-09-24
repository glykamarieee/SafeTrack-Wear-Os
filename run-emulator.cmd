@echo off
rem Runs run-emulator.ps1 without changing the PowerShell execution policy.
rem Usage: run-emulator [-NoBuild] [-Logs] [-Serial emulator-5556]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-emulator.ps1" %*
