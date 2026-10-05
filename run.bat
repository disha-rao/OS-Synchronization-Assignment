@echo off
REM Compile all sources and run all 4 programs (Windows). Usage: run.bat
if exist out rmdir /s /q out
mkdir out
if not exist generated_html mkdir generated_html
dir /s /b *.java > sources.txt
javac -d out @sources.txt || exit /b 1
del sources.txt
echo Build OK
java -cp out ReadersWritersSemaphore
java -cp out ReadersWritersMonitor
java -cp out DiningPhilosophersSemaphore
java -cp out DiningPhilosophersMonitor
echo All done. Open the four files in generated_html\ with a browser.
