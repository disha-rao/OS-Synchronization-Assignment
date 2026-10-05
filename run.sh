#!/bin/bash
# Compile all sources and run all 4 programs; each program writes its own HTML file
# into generated_html/ and opens each in the browser.  Usage:  ./run.sh   (add -Dnoopen=true to java commands to disable)
set -e
rm -rf out && mkdir -p out generated_html
javac -d out $(find . -name "*.java")
echo "Build OK"
java -cp out ReadersWritersSemaphore      # -> generated_html/readers_writers_semaphore.html
java -cp out ReadersWritersMonitor        # -> generated_html/readers_writers_monitor.html
java -cp out DiningPhilosophersSemaphore  # -> generated_html/dining_philosophers_semaphore.html
java -cp out DiningPhilosophersMonitor    # -> generated_html/dining_philosophers_monitor.html
echo "All done. Open the four files in generated_html/ with a browser."
