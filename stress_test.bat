@echo off
REM ============================================================
REM  Agent Product Stress Test - Quick Run
REM ============================================================
REM  Usage:
REM    stress_test.bat                    # Default: 5 concurrent, 20 total
REM    stress_test.bat 10 50              # 10 concurrent, 50 total
REM    stress_test.bat 10 50 YOUR_TOKEN   # with JWT
REM ============================================================

set CONCURRENCY=%1
set TOTAL=%2
set TOKEN=%3

if "%CONCURRENCY%"=="" set CONCURRENCY=5
if "%TOTAL%"=="" set TOTAL=20

echo.
echo ============================================================
echo  Agent Product Stress Test
echo ============================================================
echo  Concurrency: %CONCURRENCY%
echo  Total:       %TOTAL%
echo ============================================================
echo.

if "%TOKEN%"=="" (
    python scripts\stress_test.py -c %CONCURRENCY% -n %TOTAL%
) else (
    python scripts\stress_test.py -c %CONCURRENCY% -n %TOTAL% --token %TOKEN%
)
