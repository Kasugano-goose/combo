@echo off
chcp 65001 >nul
title Combo 游戏匹配系统 - 启动中...

echo ============================================
echo    Combo 游戏匹配系统 - 本地启动脚本
echo ============================================
echo.

set "PROJECT_DIR=%~dp0"
set "REDIS_DIR=D:\java\Redis-x64-5.0.14.1"
set "MYSQL_SERVICE=MySQL80"

:: ========== 1. 启动 MySQL ==========
echo [1/4] 启动 MySQL...
sc query %MYSQL_SERVICE% | find "RUNNING" >nul
if %errorlevel% equ 0 (
    echo   MySQL 已在运行中
) else (
    net start %MYSQL_SERVICE% >nul 2>&1
    if %errorlevel% equ 0 (
        echo   MySQL 启动成功
    ) else (
        echo   [警告] MySQL 启动失败（请以管理员身份运行此脚本）
    )
)

:: ========== 2. 启动 Redis ==========
echo [2/4] 启动 Redis...
"%REDIS_DIR%\redis-cli.exe" ping >nul 2>&1
if %errorlevel% equ 0 (
    echo   Redis 已在运行中
) else (
    start "" /MIN "%REDIS_DIR%\redis-server.exe" "%REDIS_DIR%\redis.windows.conf"
    echo   Redis 启动中...（最小化窗口）
    timeout /t 2 >nul
)

:: ========== 3. 启动后端 ==========
echo [3/4] 启动后端 Spring Boot...
start "Combo-Backend" cmd /k "cd /d "%PROJECT_DIR%backend" && mvnw.cmd spring-boot:run"
echo   后端启动中...（新窗口）

:: ========== 4. 启动前端 ==========
echo [4/4] 启动前端 Vite Dev Server...
start "Combo-Frontend" cmd /k "cd /d "%PROJECT_DIR%frontend" && npm run dev"
echo   前端启动中...（新窗口）

echo.
echo ============================================
echo   启动完成！
echo.
echo   前端地址: http://localhost:5173
echo   后端地址: http://localhost:8090
echo.
echo   关闭本窗口不会影响服务运行
echo   如需停止服务，请关闭 Backend 和 Frontend 窗口
echo ============================================
echo.

pause
