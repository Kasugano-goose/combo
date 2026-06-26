@echo off
chcp 65001 >nul
title Combo 游戏匹配系统 - 停止服务

echo ============================================
echo    Combo 游戏匹配系统 - 停止服务
echo ============================================
echo.

echo [1/2] 停止 Redis...
"D:\java\Redis-x64-5.0.14.1\redis-cli.exe" shutdown
echo   Redis 已停止

echo [2/2] 提示：请手动关闭以下窗口：
echo   - Combo-Backend 窗口
echo   - Combo-Frontend 窗口
echo.
echo   MySQL 服务未自动停止，如需停止请运行：
echo   net stop MySQL80

echo.
echo ============================================
echo   服务已停止
echo ============================================

pause
