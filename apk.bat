@echo off

call gradlew.bat app:assembleDebug

mv app\build\outputs\apk\app-debug.apk .\app-syncit-debug.apk
cp .\app-syncit-debug.apk c:\Users\Filip\Dropbox\ 

echo =========== OUTPUT ==========
dir /B *.apk

echo Done!
pause 