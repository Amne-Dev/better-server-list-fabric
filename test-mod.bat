@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "NO_RUN=0"
set "MC_PROFILE=current"
set "USE_JAVA_21=0"
if not "%~1"=="" (
	if /I not "%~1"=="--no-run" set "MC_PROFILE=%~1"
)
if /I "%~1"=="--no-run" set "NO_RUN=1"
if /I "%~2"=="--no-run" set "NO_RUN=1"

if /I "%MC_PROFILE%"=="1.20.x" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.21.11" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.21.x" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.16.x" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.15.x" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.17.x" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.18.x" set "USE_JAVA_21=1"
if /I "%MC_PROFILE%"=="1.19.x" set "USE_JAVA_21=1"

if "%USE_JAVA_21%"=="1" (
	set "JAVA21_HOME="
	for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA21_HOME=%%~fD"
	if not defined JAVA21_HOME (
		echo Java 21 is required for profile !MC_PROFILE! but was not found in C:\Program Files\Eclipse Adoptium.
		echo Install Temurin JDK 21 and re-run this script.
		exit /b 1
	)
	set "JAVA_HOME=!JAVA21_HOME!"
	set "PATH=!JAVA_HOME!\bin;%PATH%"
	echo Using Java 21 runtime: !JAVA_HOME!
)
set "ORG_GRADLE_JAVA_HOME="
if defined JAVA_HOME set "ORG_GRADLE_JAVA_HOME=%JAVA_HOME%"

call .\gradlew.bat --stop >nul 2>&1

echo [1/3] Building mod (profile=%MC_PROFILE%)...
call .\gradlew.bat clean build -P"mc_profile=%MC_PROFILE%"
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo [2/3] Checking live server API...
powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; try { $r=Invoke-RestMethod -Uri 'https://minecraft-list.info/api/v1/servers?game.slug=minecraft&page=1' -Headers @{'Accept'='application/json';'User-Agent'='better-server-list-fabric/1.2'} -TimeoutSec 12; if($r -and $r.Count -gt 0) { Write-Host ('Live API OK: entries=' + $r.Count) } else { Write-Host 'Live API returned empty data.' } } catch { Write-Host ('Live API check failed (mod will use bundled fallback): ' + $_.Exception.Message) }"

echo [3/3] Launching Minecraft test client...
if "%NO_RUN%"=="1" (
  echo Skipping runClient because --no-run was provided.
  exit /b 0
)
call .\gradlew.bat runClient -P"mc_profile=%MC_PROFILE%"
exit /b %errorlevel%
