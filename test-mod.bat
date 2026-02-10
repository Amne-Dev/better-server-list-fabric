@echo off
setlocal
cd /d "%~dp0"
set "NO_RUN=0"
if /I "%~1"=="--no-run" set "NO_RUN=1"

echo [1/3] Building mod...
call .\gradlew.bat clean build
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo [2/3] Checking live server API...
powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; try { $r=Invoke-RestMethod -Uri 'https://servers.minespark.org/servers/java' -TimeoutSec 12; if($r -and $r.Count -gt 0) { Write-Host ('Live API OK: entries=' + $r.Count) } else { Write-Host 'Live API returned empty data.' } } catch { Write-Host ('Live API check failed (mod will use bundled fallback): ' + $_.Exception.Message) }"

echo [3/3] Launching Minecraft test client...
if "%NO_RUN%"=="1" (
  echo Skipping runClient because --no-run was provided.
  exit /b 0
)
call .\gradlew.bat runClient
exit /b %errorlevel%
