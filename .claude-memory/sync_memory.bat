@echo off
REM 将仓库中的 Claude Code memory 文件同步到本机正确路径
REM 使用方法：在新设备 clone 仓库后，双击运行此脚本

set REPO_DIR=%~dp0
set MEMORY_DIR=%USERPROFILE%\.claude\projects\C--ZRS-Works\memory

mkdir "%MEMORY_DIR%" 2>nul

copy /Y "%REPO_DIR%MEMORY.md" "%MEMORY_DIR%\MEMORY.md"
copy /Y "%REPO_DIR%project_claude_test_app.md" "%MEMORY_DIR%\project_claude_test_app.md"

echo.
echo Claude Code memory 已同步到：%MEMORY_DIR%
echo 重新打开 Claude Code 即可继续上下文。
pause
