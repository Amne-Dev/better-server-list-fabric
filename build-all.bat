@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "PROFILES=current 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11 1.21.x 1.20.x 1.19.x 1.18.x 1.17.x 1.16.x 1.15.x"
set "DIST_DIR=dist\publish"
set "BASE_PATH=%PATH%"
set "ACTIVE_JAVA_HOME="

set "JAVA21_HOME="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA21_HOME=%%~fD"
set "JAVA25_HOME="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*") do set "JAVA25_HOME=%%~fD"

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%DIST_DIR%"

for %%P in (%PROFILES%) do (
	call :bsl_select_java_home "%%P"
	if errorlevel 1 exit /b 1

	if /I not "!JAVA_HOME!"=="!ACTIVE_JAVA_HOME!" (
		set "ACTIVE_JAVA_HOME=!JAVA_HOME!"
		call .\gradlew.bat --stop >nul 2>&1
	)

	echo Using Java runtime for %%P: !JAVA_HOME!
	echo [build] profile=%%P
	call .\gradlew.bat clean build -P"mc_profile=%%P"
	if errorlevel 1 (
		echo Build failed for profile %%P.
		exit /b 1
	)

	set "OUT_DIR=%DIST_DIR%\%%P"
	if not exist "!OUT_DIR!" mkdir "!OUT_DIR!"
	for %%F in (build\libs\*.jar) do copy /y "%%~fF" "!OUT_DIR!\" >nul
)

echo.
echo Build complete. Artifacts are in %DIST_DIR%.
exit /b 0

:bsl_select_java_home
set "PROFILE_NAME=%~1"
set "PROFILE_NAME=%PROFILE_NAME:"=%"

if /I "%PROFILE_NAME%"=="current" (
	if defined JAVA25_HOME (
		set "JAVA_HOME=%JAVA25_HOME%"
	) else if defined JAVA21_HOME (
		set "JAVA_HOME=%JAVA21_HOME%"
	) else (
		echo Missing Java runtime for current profile. Install Temurin JDK 25 or JDK 21.
		exit /b 1
	)
) else (
	if defined JAVA21_HOME (
		set "JAVA_HOME=%JAVA21_HOME%"
	) else if defined JAVA25_HOME (
		set "JAVA_HOME=%JAVA25_HOME%"
	) else (
		echo Missing Java runtime for profile %PROFILE_NAME%. Install Temurin JDK 21 or newer.
		exit /b 1
	)
)

set "PATH=%JAVA_HOME%\bin;%BASE_PATH%"
set "ORG_GRADLE_JAVA_HOME=%JAVA_HOME%"
exit /b 0
