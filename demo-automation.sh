#!/bin/bash
# JavaGame Water Demo Automation Script
# Launches game, records, automates keystrokes

set -e

GAME_DIR="$HOME/Desktop/Code/MyProjects/JavaGame"
OUTPUT="$GAME_DIR/water-demo.mp4"

echo "=== Phase 1: Clean State ==="
pkill -f "com.voxelgame" 2>/dev/null || true
pkill -f "gradlew run" 2>/dev/null || true
sleep 2
echo "Clean state verified"

echo "=== Phase 2: Launch Game ==="
cd "$GAME_DIR"

# Launch game in background
JAVA_HOME=$(/usr/libexec/java_home) ./gradlew run &
GRADLE_PID=$!
echo "Gradle PID: $GRADLE_PID"

# Wait for game window to appear (check for java process with high CPU)
echo "Waiting for game to start..."
for i in $(seq 1 60); do
    if ps aux | grep "com.voxelgame.Main" | grep -v grep > /dev/null 2>&1; then
        echo "Game process found after ${i}s"
        break
    fi
    sleep 1
done

# Wait extra time for world generation
echo "Waiting for world generation..."
sleep 8

echo "=== Phase 3: Focus Game Window ==="
# Focus the java game window
osascript -e '
tell application "System Events"
    set javaProcs to every process whose name is "java"
    repeat with p in javaProcs
        set frontmost of p to true
    end repeat
end tell'
sleep 1

# Click on game window to capture cursor
cliclick c:640,400
sleep 1

echo "=== Phase 4: Start Recording ==="
# Start screen recording in background
ffmpeg -y -f avfoundation -framerate 30 -i "1:none" -t 90 \
    -c:v libx264 -pix_fmt yuv420p -preset ultrafast \
    "$OUTPUT" &
FFMPEG_PID=$!
echo "FFmpeg PID: $FFMPEG_PID"
sleep 2

echo "=== Phase 5: Automate Gameplay ==="

# Helper function - hold key for duration
hold_key() {
    local key="$1"
    local duration="$2"
    local end_time=$((SECONDS + duration))
    while [ $SECONDS -lt $end_time ]; do
        osascript -e "tell application \"System Events\" to keystroke \"$key\""
        sleep 0.15
    done
}

# Helper - press key once
press_key() {
    osascript -e "tell application \"System Events\" to keystroke \"$1\""
    sleep 0.3
}

# Re-focus game (in case recording stole focus)
osascript -e '
tell application "System Events"
    set javaProcs to every process whose name is "java"
    repeat with p in javaProcs
        set frontmost of p to true
    end repeat
end tell'
sleep 0.5
cliclick c:640,400
sleep 0.5

echo "--- Walking forward (walk mode) ---"
# Walk forward for a few seconds to show movement
hold_key "w" 3

echo "--- Toggling fly mode ---"
# Press F to toggle fly mode
press_key "f"
sleep 0.5

echo "--- Flying forward towards water ---"
# Fly forward for several seconds
hold_key "w" 5

echo "--- Flying up to get altitude ---"
# Space to fly up (in fly mode space = up)
for i in $(seq 1 15); do
    osascript -e 'tell application "System Events" to keystroke " "'
    sleep 0.15
done

echo "--- Flying forward more ---"
hold_key "w" 5

echo "--- Looking around ---"
# Strafe left
hold_key "a" 2
# Strafe right
hold_key "d" 4
# Back to center
hold_key "a" 2

echo "--- Toggling back to walk mode ---"
press_key "f"
sleep 0.5

echo "--- Walking forward ---"
hold_key "w" 5

echo "--- Strafing to show water from angle ---"
hold_key "a" 2
hold_key "w" 3
hold_key "d" 3
hold_key "w" 3

echo "--- Flying up for aerial view ---"
press_key "f"
sleep 0.3
for i in $(seq 1 20); do
    osascript -e 'tell application "System Events" to keystroke " "'
    sleep 0.15
done
hold_key "w" 5
hold_key "a" 3
hold_key "d" 3

echo "--- Back to walk mode, descend to water ---"
press_key "f"
sleep 0.3
hold_key "w" 5

echo "=== Phase 6: Cleanup ==="
# Wait for ffmpeg to finish (90s recording)
echo "Waiting for recording to finish..."
wait $FFMPEG_PID 2>/dev/null || true

echo "Recording complete: $OUTPUT"
ls -la "$OUTPUT"

# Kill the game
kill $GRADLE_PID 2>/dev/null || true
pkill -f "com.voxelgame" 2>/dev/null || true

echo "=== DONE ==="
