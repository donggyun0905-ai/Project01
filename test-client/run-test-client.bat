@echo off
cd /d "%~dp0"
echo dmart 테스트 콘솔을 http://localhost:5500 에서 엽니다...
echo (이 창을 닫으면 서버가 꺼집니다. 톰캣은 IntelliJ에서 별도로 실행돼 있어야 합니다.)
npx http-server -p 5500 -a localhost -o http://localhost:5500 -c-1
pause
