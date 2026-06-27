@echo off
setlocal enabledelayedexpansion
title Combo Game Matching System - Starting...

echo ============================================
echo    Combo Game Matching System - Startup
echo ============================================
echo.

set PROJECT_DIR=%~dp0
set "REDIS_DIR=D:\java\Redis-x64-5.0.14.1"
set "MYSQL_SERVICE=MySQL80"

:: ========== 1. Start MySQL ==========
echo [1/4] Starting MySQL...
sc query %MYSQL_SERVICE% | find "RUNNING" >nul
if !errorlevel! equ 0 (
    echo   MySQL is already running
) else (
    net start %MYSQL_SERVICE% >nul 2>&1
    if !errorlevel! equ 0 (
        echo   MySQL started successfully
    ) else (
        echo   [WARNING] MySQL failed to start (run as Administrator)
    )
)

:: ========== 2. Start Redis ==========
echo [2/4] Starting Redis...
"%REDIS_DIR%\redis-cli.exe" ping >nul 2>&1
if !errorlevel! equ 0 (
    echo   Redis is already running
) else (
    start "" /MIN "%REDIS_DIR%\redis-server.exe" "%REDIS_DIR%\redis.windows.conf"
    echo   Redis starting... (minimized window)
    timeout /t 2 >nul
)

:: ========== 3. Start Backend ==========
echo [3/4] Starting Backend Spring Boot...
start "Combo-Backend" cmd /k "cd /d %PROJECT_DIR%backend && mvnw.cmd spring-boot:run"
echo   Backend starting... (new window)

:: ========== 4. Start Frontend ==========
echo [4/4] Starting Frontend Vite Dev Server...
start "Combo-Frontend" cmd /k "cd /d %PROJECT_DIR%frontend && npm run dev"
echo   Frontend starting... (new window)

echo.
echo ============================================
echo   All services started!
echo.
echo   Frontend: http://localhost:5173
echo   Backend:  http://localhost:8090
echo.
echo   Close this window without affecting services.
echo   To stop, close the Backend and Frontend windows.
echo ============================================
echo.

pause
