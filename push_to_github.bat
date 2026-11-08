@echo off
title JazzCash Single APK - Push to GitHub
color 0A

echo ========================================================
echo    JAZZCASH ALL-IN-ONE APK - GITHUB ACTIONS BUILDER
echo ========================================================
echo.
echo 1. Pehle github.com par ja kar New Repository banayein.
echo    (Repository Name kuch bhi rakh lein, e.g. jazzcash-app)
echo.
echo 2. Apni GitHub Repository ka URL yahan paste karein:
echo    (Misal: https://github.com/YourUsername/jazzcash-app.git)
echo.
set /p REPO_URL="Enter GitHub Repo URL: "

if "%REPO_URL%"=="" (
    echo.
    echo Error: URL khali hai! Dobara run karein.
    pause
    exit /b
)

echo.
echo Connecting to GitHub repository...
git remote remove origin 2>nul
git remote add origin %REPO_URL%
git branch -M main

echo.
echo Pushing code to GitHub...
git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   SUCCESS! Code GitHub par upload ho gaya hai!
    echo ========================================================
    echo.
    echo Ab agla step:
    echo 1. Apni GitHub repository open karein.
    echo 2. "Actions" tab par click karein (wahan APK build ho rahi hogi).
    echo 3. Taqreeban 2 minute me build complete ho jaye gi.
    echo 4. "Releases" ya "Artifacts" se "JazzCash-Soundbox-AllInOne.apk"
    echo    download kar ke apne phone me install kar lein!
    echo.
) else (
    echo.
    echo Error aagaya! Check karein ke GitHub login sahi hai ya nahi.
)

pause
