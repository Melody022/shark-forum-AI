@echo off
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8081 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo 端口 8081 已释放
    goto :eof
)
echo 端口 8081 未被占用
pause
