@echo off
title GitHub Force Push - md0331400/Test
color 0A

echo ==========================================
echo      GitHub Force Push Tool
echo      Repo: md0331400/Test
echo ==========================================
echo.

git --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Git is not installed or not in PATH.
    echo.
    pause
    exit /b 1
)

if not exist ".git" (
    echo Initializing Git repository...
    git init
    if errorlevel 1 goto :error
)

git remote get-url origin >nul 2>&1
if errorlevel 1 (
    git remote add origin https://github.com/md0331400/Test.git
) else (
    git remote set-url origin https://github.com/md0331400/Test.git
)

echo.
echo Adding files...
git add .
if errorlevel 1 goto :error

echo.
echo Creating commit...
git diff --cached --quiet
if errorlevel 1 (
    git commit -m "Update files"
    if errorlevel 1 goto :error
) else (
    echo No new changes to commit.
)

echo.
echo Setting branch to main...
git branch -M main
if errorlevel 1 goto :error

echo.
echo WARNING: This will FORCE PUSH to:
echo https://github.com/md0331400/Test
echo Existing remote main history may be overwritten.
echo.
choice /C YN /N /M "Continue? [Y/N]: "
if errorlevel 2 (
    echo.
    echo Cancelled.
    pause
    exit /b 0
)

echo.
echo Force pushing...
git push -u origin main --force
if errorlevel 1 goto :error

echo.
echo ==========================================
echo SUCCESS: Files pushed to GitHub.
echo ==========================================
pause
exit /b 0

:error
echo.
echo ==========================================
echo ERROR: Git command failed.
echo Check the message above.
echo ==========================================
pause
exit /b 1
